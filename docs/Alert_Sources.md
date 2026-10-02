# External Alert Sources Integration Guide

This document describes the external alert data ingestion architecture in **Lodestar**, including configuration, rate limiting, debouncing, and distributed scaling considerations.

---

## 1. Supported Ingestion Modes

Lodestar supports two interchangeable alert ingestion strategies configured via `ALERT_SOURCE_TYPE`:

| Mode | Environment Variable | Description | Use Cases |
| :--- | :--- | :--- | :--- |
| **Synthetic** | `ALERT_SOURCE_TYPE=synthetic` | Built-in workload generator producing periodic simulated events across regions. | Local development, offline work, automated integration tests, heavy load benchmarks. |
| **Live** | `ALERT_SOURCE_TYPE=live` | Real-time HTTP polling against the [alerts.in.ua](https://alerts.in.ua/) API. | Staging and Production deployments with actual civil protection threat streams. |

---

## 2. Setting Up alerts.in.ua API

1. **Obtain an API Token:**
   * Register or request a community/developer access token at [alerts.in.ua](https://alerts.in.ua/).
2. **Configure Environment Variables:**
   * Add your token to `.env` (or pass as container environment variables):
     ```env
     ALERT_SOURCE_TYPE=live
     ALERTS_IN_UA_BASE_URL=https://api.alerts.in.ua/v1
     ALERTS_IN_UA_TOKEN=your_token_here
     ALERTS_POLL_INTERVAL=15s
     ALERTS_DEBOUNCE_TTL=1h
     ```
3. **Endpoint Details:**
   * Endpoint polled: `GET /alerts/active.json`
   * Authorization: `Authorization: Bearer <ALERTS_IN_UA_TOKEN>`
   * Payload: Array of currently active alerts including `location_title`, `alert_type`, and timestamps.

---

## 3. Alert Debouncing & State Tracking

### The Challenge of Polling-Based APIs
External alert providers like `alerts.in.ua` return the list of **all currently ongoing alerts** on every poll request.
For example, if an alert lasts for 45 minutes and the poll interval is 15 seconds, the service will receive 180 identical responses for that region.

Without debouncing, Lodestar would publish 180 duplicate events to Kafka and deliver 180 push notifications for a single alert.

### The Solution: Redis-Backed Debouncing
[`AlertDebounceService`](file:///home/oleh/workspace/lodestar/src/main/java/com/olehshklyar/lodestar/service/AlertDebounceService.java) maintains state:
* Key format: `lodestar:alert:active:{regionId}:{eventType}`
* Storage: Atomic Redis `SETNX` with a configurable TTL (`ALERTS_DEBOUNCE_TTL=1h`).
* Behavior:
  * First occurrence: `SETNX` returns `true` ➔ Event is published to Kafka and dispatched to subscribers.
  * Subsequent polls: `SETNX` returns `false` ➔ Event is silently suppressed as an active duplicate.
  * Resilience: Falls back to an in-memory `ConcurrentHashMap` if Redis is temporarily unreachable.

---

## 4. Geographic Region Normalization

External alert providers use human-readable titles (e.g., `м. Київ`, `Львівська область`), whereas internal matching rules rely on canonical region codes (e.g., `KYIV_REGION`, `LVIV_REGION`).

* **Relational Storage & Flyway V3:** Standard regions (`canonical_regions`), normalization rules (`region_mappings`), and unmapped strings (`unrecognized_locations`) are stored in PostgreSQL.
* **Sub-Millisecond In-Memory Cache:** `RegionMappingService` maintains a thread-safe `ConcurrentHashMap` initialized at startup, ensuring zero database round-trips during high-frequency stream processing.
* **Fallback Slugification:** Encountered unknown locations are recorded in `unrecognized_locations` for administrative review, while generating a deterministic slug fallback (e.g., `ПОЛТАВА_ТГ_REGION`) to prevent stream interruptions.

---

## 5. Operational Discovery & Low-Code Administrative Tooling

Lodestar provides a strictly typed OpenAPI 3.0 specification (`/v3/api-docs`) that powers both engineering workflows and low-code operational consoles.

### Engineering Console (Swagger UI)
Springdoc OpenAPI generates an interactive console at `/swagger-ui.html` out of the box:
* **Discover unmapped strings:** `GET /api/v1/regions/unrecognized` returns candidate titles with occurrence frequency and last-seen timestamps.
* **Assign mapping:** `POST /api/v1/regions/unrecognized/{id}/assign` binds an unrecognized string to a target canonical region, automatically cleaning up the candidate record and hot-reloading the in-memory normalization cache.
* **Register new regions & aliases:** `POST /api/v1/regions/canonical` and `POST /api/v1/regions/mappings` allow registering new territorial units at runtime without restarting the application.

### Low-Code Operations Tooling (Retool, Appsmith, Budibase)
For operational support, external low-code platforms can connect to Lodestar:

1. **Schema Ingestion:** Point Retool or Appsmith to `http://<lodestar-host>:8080/v3/api-docs`. The platform auto-generates REST data sources and typed schema models.
2. **Review Table:** A table widget bound to `GET /api/v1/regions/unrecognized` displays incoming unknown locations sorted by encounter frequency (`occurrences_count`).
3. **Assignment Action:** A select widget populated from `GET /api/v1/regions/canonical` paired with an "Assign" button calls `POST /api/v1/regions/unrecognized/{{table.selectedRow.id}}/assign`.
4. **Automated Notification:** When new unknown strings are recorded, an alert or Slack webhook can be triggered from Actuator metrics or log monitors to inform the team that a new territorial unit requires categorization.

---

## 6. Horizontal Scaling & Rate Limiting in Kubernetes

When running Lodestar in a multi-pod cluster (e.g., Kubernetes Deployment scaled via HPA to 3–5 pods), executing scheduled polling on every pod introduces critical challenges:

1. **Multiplying Requests:** 5 pods polling every 15 seconds would produce a request every 3 seconds, immediately exceeding provider rate limits (`HTTP 429 Too Many Requests`) and risking token bans.
2. **Duplicated Kafka Ingestion:** Multiple pods would attempt to process and publish identical external events.

### Recommended Architectural Patterns:

* **Pattern A: Leader Election via Redis (Universal / Active-Standby):**
  * All pods share Redis. Exactly one pod acquires a distributed lease (`lodestar:leader:alert-ingest`) with a heartbeat TTL.
  * Only the active leader runs the scheduled ingestion worker. If the leader fails, another pod acquires the lock on the next cycle without interruption.
  * *Advantage:* Operates consistently across local Docker Compose and cloud Kubernetes environments.

* **Pattern B: Singleton Ingest Deployment (Kubernetes Separation of Concerns):**
  * In production, the alert ingestion module runs as a dedicated deployment with `replicas: 1`.
  * Downstream components (Stream Analysis Engine, Notification Dispatcher, Gateways) scale horizontally from 1 to N pods based on CPU and Kafka consumer lag metrics.

### Current Architectural Decision:
Lodestar adopts **Pattern A (Redis-Backed Leader Election)** as the primary coordination strategy:
* **Environment Parity:** The same scheduling logic operates consistently in single-instance local development (where the lone instance acquires the lease without contention) and in multi-replica Kubernetes clusters (`k3d` / production) where only one elected leader polls external APIs while standing-by replicas remain idle.
* **Zero Cluster RBAC Overhead:** Utilizing existing Redis infrastructure avoids granting pods sensitive cluster-level Kubernetes RBAC permissions to the `coordination.k8s.io` Lease API.
* **Fail-Safe Operation:** If Redis becomes temporarily unreachable, instances drop leadership and suspend polling, preventing duplicate requests and rate-limit violations (*split-brain* protection).
