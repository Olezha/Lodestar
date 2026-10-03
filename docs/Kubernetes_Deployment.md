# Kubernetes Deployment & Load Balancing Architecture Guide

This document describes the production-grade containerization, multi-node Kubernetes deployment architecture, and traffic balancing strategies for **Lodestar**.

---

## 1. Local Cluster Topology (`k3d`)

To achieve complete parity with cloud Kubernetes environments (AWS EKS, GCP GKE) without incurring cloud infrastructure costs or excessive local overhead, Lodestar deploys to a local **`k3d`** cluster (lightweight **k3s** running inside Docker).

### Cluster Specifications
* **Control Plane (Server Node):** `k3d-lodestar-server-0`  
  Runs Kubernetes control plane components: `kube-apiserver`, `kube-scheduler`, and `kube-controller-manager`.
* **Worker Nodes (Agent Nodes):** `k3d-lodestar-agent-0`, `k3d-lodestar-agent-1`  
  Dedicated nodes hosting container runtimes, `kubelet`, and `kube-proxy` where application pods execute.
* **Footprint:** The entire multi-node cluster consumes **~500–600 MB of RAM**, allowing full multi-node scheduling and network testing directly on a developer workstation.

```
                           +-------------------------------------+
                           |      k3d Ingress Load Balancer      |
                           |           (:8080 -> :80)            |
                           +------------------+------------------+
                                              |
                     +------------------------+------------------------+
                     |                                                 |
       +-------------v--------------+                    +-------------v--------------+
       |   Worker Node 1 (Agent 0)  |                    |   Worker Node 2 (Agent 1)  |
       |  +----------------------+  |                    |  +----------------------+  |
       |  | Lodestar Pod (Rep 1) |  |                    |  | Lodestar Pod (Rep 2) |  |
       |  +----------------------+  |                    |  +----------------------+  |
       |  kube-proxy (iptables)     |                    |  kube-proxy (iptables)     |
       +----------------------------+                    +----------------------------+
```

---

## 2. Pod Topology & Distributed Coordination

### 2.1. Symmetric Stateless Service Replicas
Lodestar follows 12-Factor App design principles. All application replicas deployed via Kubernetes `Deployment` are **symmetric and stateless**:
* Every pod can service incoming REST API requests (`/api/v1/*`).
* Every pod executes consumer group listeners for Kafka topics (`lodestar-analytics-group`, `lodestar-anomaly-group`) and RabbitMQ queues with automatic partition balancing.
* Scaling the deployment (e.g., from 1 to 5 pods) creates identical, interchangeable instances without master/worker code divergence.

### 2.2. Distributed Ingestion: The Redis-Backed Leader Election Pattern
While REST and messaging workloads scale horizontally, polling external rate-limited APIs (such as `alerts.in.ua`) requires strict singleton execution:
* **The Problem:** If 5 horizontally scaled pods each poll an external API every 15 seconds, the effective request rate multiplies to once every 3 seconds, triggering immediate `HTTP 429 Too Many Requests` and API token suspension.
* **The Architecture:** Lodestar uses **Redis-Backed Leader Election**:
  * An elected leader lock (`lodestar:leader:alert-ingest`) is acquired using an atomic Redis `SETNX` lease with a rolling heartbeat TTL.
  * Only the active leader pod executes `AlertIngestWorker`.
  * Non-leader replicas remain in hot standby for ingestion while continuing to process incoming HTTP requests and messaging streams.
  * If the leader pod terminates, an active standby replica acquires the lease on the subsequent cycle, ensuring continuous ingestion without duplicate external requests.

### 2.3. Infrastructure Placement Strategy
* **Data & Messaging Tier (PostgreSQL, Kafka, RabbitMQ, Redis):**  
  To optimize memory footprint and avoid running heavy Kubernetes Operators locally, the stateful infrastructure runs in Docker Compose on the host machine. Pods inside the `k3d` cluster reach these services via `host.k3d.internal` (mirrored in Kubernetes manifests via `ExternalName` or host alias configurations). In cloud enterprise environments, this reflects standard industry practice: hosting data tiers on cloud managed services (AWS RDS, AWS MSK) while running stateless compute in Kubernetes.

---

## 3. Cloud-Native Containerization & JVM Tuning

### 3.1. Multi-Stage Layered Dockerfile
Spring Boot 3.3 layered jars are leveraged to minimize container build times and registry transfer bandwidth:
1. **Extractor Stage:** Runs `java -Djarmode=layertools -jar application.jar extract` to split the artifact into distinct semantic layers:
   * `dependencies/` (third-party libraries, cached long-term)
   * `spring-boot-loader/`
   * `snapshot-dependencies/`
   * `application/` (application classes and resources, updated frequently)
2. **Runtime Stage:** Built upon `eclipse-temurin:21-jre-alpine` (total final image footprint < 150 MB). Runs under a dedicated non-root user (`USER spring:spring`).

### 3.2. Cgroups v2 & Container Memory Awareness
Hardcoding `-Xmx` inside a container is an anti-pattern. Instead, dynamic percentage-based allocation tuned for cgroups v2 ensures reliable operation:
```bash
JAVA_OPTS="-XX:+UseContainerSupport \
           -XX:MaxRAMPercentage=75.0 \
           -XX:+ExitOnOutOfMemoryError"
```
* **Heap Boundary:** Allocates 75% of the container's memory limit to the JVM heap.
* **Non-Heap Safety Margin:** Leaves 25% unallocated for Metaspace, thread stacks (1 MB per platform thread / off-heap structures for Virtual Threads), Netty direct buffers, and GC tracking. This prevents OS kernel OOM killing (`Exit Code 137`).

---

## 4. Reliability & Zero-Downtime Deployments

### 4.1. Health Probes Isolation
Spring Boot Actuator health groups are mapped to Kubernetes probes:
* **Liveness Probe (`/actuator/health/liveness`):**  
  * *Purpose:* Determines if the JVM process is alive and responsive.
  * *Architectural Requirement:* Strictly isolated from external network dependencies (PostgreSQL, Kafka, Redis). If an external database experiences temporary latency, a dependent liveness check would trigger cascading pod restarts across the entire cluster.
* **Readiness Probe (`/actuator/health/readiness`):**  
  * *Purpose:* Determines if the pod is ready to accept incoming customer traffic.
  * *Behavior:* Validates internal component readiness. If negative, the pod IP is removed from the `Endpoints` list of the Kubernetes `Service` without terminating the process.

### 4.2. Graceful Shutdown & The `preStop` Race Condition
When Kubernetes terminates a pod during a deployment rollout or scale-down event, two actions happen asynchronously in parallel:
1. Kubelet sends a `SIGTERM` signal to the container process.
2. The endpoint controller removes the pod IP from the `Endpoints` resource and propagates the update to `kube-proxy` across all worker nodes.

**The Race Condition:** Propagating network rule updates (`iptables` / `IPVS`) across the cluster takes 1 to 4 seconds. If the application terminates immediately upon receiving `SIGTERM`, in-flight requests and new connections routed during this window fail with `502 Bad Gateway` or `Connection Reset by Peer`.

**Production Mitigation:**
```yaml
lifecycle:
  preStop:
    exec:
      command: ["/bin/sh", "-c", "sleep 10"]
```
* The `preStop` hook delays the `SIGTERM` delivery by 10 seconds.
* `kube-proxy` completes route removal across all nodes while the pod continues accepting and finishing active traffic.
* Spring Boot's graceful shutdown (`server.shutdown=graceful` with a 30s phase timeout) then drains remaining requests cleanly before process termination (`Exit Code 143`).

---

## 5. Horizontal Pod Autoscaling (HPA) & Load Injection

### 5.1. Autoscaler Configuration
An HPA resource targets the Lodestar deployment:
* **Minimum Replicas:** 1
* **Maximum Replicas:** 5
* **Metric:** Target CPU utilization of 60%

### 5.2. Multi-Channel Load Injection
1. **Synthetic Background Event Ingestion:**  
   The built-in synthetic alert generator streams continuous batches of simulated alert events into the Kafka `events.raw-alerts` topic.
2. **HTTP Stress Traffic (k6):**  
   A dedicated `k6` script executes a spike profile ramping from 10 to 200 virtual users in 30 seconds against Lodestar's Ingress REST endpoints (`/api/v1/subscriptions`, `/api/v1/regions/unrecognized`), inducing CPU load to trigger automatic HPA expansion.

---

## 6. Cloud Load Balancing Taxonomy & Mechanics

### 6.1. The 7 Infrastructure Layers in Cloud Services
In modern cloud architectures, load balancing is not an isolated component operation; it is an end-to-end multi-tiered cascade where each tier balances traffic under distinct trade-offs between context-awareness and latency overhead:

1. **Global & Anycast Edge Tier:** GeoDNS, Anycast BGP, Cloudflare/CloudFront. Routes incoming internet traffic to the optimal geographical region or availability zone, terminating DDoS and minimizing Round-Trip Time (RTT).
2. **Network / Transport Infrastructure Tier (L3/L4):** BGP ECMP (Equal-Cost Multi-Path), cloud L4 balancers (AWS NLB), Linux IPVS, and node-local `kube-proxy` (iptables/IPVS). Routes raw TCP/UDP packets via 5-tuple hashing at kernel speed ($O(1)$) with near-zero latency, but without inspecting application headers or measuring active in-flight request queues ($L$).
3. **Application / Reverse Proxy Tier (L7 Ingress):** Ingress Controllers (Envoy, NGINX, AWS ALB). Terminates TLS, parses HTTP/1.1, HTTP/2, and gRPC, enforces path/header routing, applies rate limiting and circuit breaking, and dispatches via Least Connections or Power of Two Choices (P2C).
4. **Service Mesh Tier (East-West Inter-Service):** Envoy sidecars (Istio, Linkerd). Balances point-to-point inter-service calls directly between pods, eliminating central gateway bottlenecks while supporting mutual TLS (mTLS) and Outlier Detection.
5. **Client-Side Load Balancing Tier:** Application-level routing embedded in microservices (Spring Cloud LoadBalancer, gRPC subchannel selection). Balances requests inside the JVM process using Service Discovery registries, incurring Zero Network Hops.
6. **Compute & Orchestration Scheduling Tier:** Cluster schedulers (`kube-scheduler`, HPA, CloudSim VM placement). Balances workload placement across physical worker nodes according to multi-dimensional resource vectors (CPU, RAM, topology spread constraints), optimizing multi-objective cost/performance functions ($E = \sum w_i M_i$).
7. **Asynchronous Messaging & Queue Tier:** Partition assignments in Apache Kafka (`CooperativeStickyAssignor`) and task dispatching across competing consumers in RabbitMQ (`basic.qos` / `prefetch_count`).

```
                     END-TO-END LOAD BALANCING CASCADE
  [ Client ]
      │
  (1) ▼ [ Global Edge / GeoDNS / Anycast CDN ]
      │
  (2) ▼ [ L3/L4 Network Infrastructure / BGP ECMP / Cloud NLB ]
      │
  (3) ▼ [ L7 Ingress Gateway / TLS Termination / Reverse Proxy ]
      │
  (2) ▼ [ Node Kernel L4: kube-proxy (iptables / IPVS ClusterIP DNAT) ]
      │
  (4) ▼ [ Pod Envoy Sidecar (Service Mesh East-West) ]
      │
  (5) ▼ [ JVM Process: Client-Side LoadBalancer (Spring Cloud / gRPC) ]
      │
  (6) ◄── [ Kubernetes Scheduler & HPA: Compute & Resource Placement ]
      │
  (7) ◄── [ Kafka / RabbitMQ: Queue & Partition Consumer Balancing ]
```

### 6.2. Layer 4 (Transport Layer) — `kube-proxy`
* **Input:** Raw TCP/UDP packets addressing a `ClusterIP` virtual IP and port.
* **Mechanism:** In-kernel Destination NAT (DNAT). Modifies packet headers without terminating the TCP handshake or inspecting payload contents.
  * *iptables mode:* Evaluates sequential rule chains (`PREROUTING` ➔ `KUBE-SERVICES` ➔ `KUBE-SVC-*` ➔ `KUBE-SEP-*`). Implements random distribution using `--probability`. Scales with algorithmic complexity $O(N)$.
  * *IPVS mode:* Uses Linux kernel IP Virtual Server hash tables ($O(1)$ lookup complexity), maintaining stable latency even with thousands of services.
* **Output:** The original TCP packet rewritten with the destination physical IP of the chosen pod.

### 6.3. Layer 7 (Application Layer) — Ingress / Reverse Proxy
* **Input:** Application protocol streams (HTTP/1.1, HTTP/2, gRPC). Terminates client TLS connections.
* **Mechanism:** Parses HTTP requests, paths, headers, and session identifiers. Evaluates routing rules and opens a separate TCP connection to an upstream backend pod.
* **Output:** A newly assembled HTTP request with injected headers (`X-Forwarded-For`, `X-Request-ID`) transmitted over the upstream socket.

### 6.4. Theoretical Foundations & Mathematical Bounds
The design and evaluation of load balancing strategies across L4 and L7 are rooted in classical distributed systems and queueing theory theorems:

1. **Power of Two Choices Theorem (Mitzenmacher 2001; Azar et al. 1999):**
   * *Randomized Allocation ($d=1$):* Allocating $n$ requests across $n$ pods independently at random (as in iptables `--probability`) yields a maximum queue length of $(1 + o(1))\frac{\ln n}{\ln \ln n}$ with high probability.
   * *Two Random Choices ($d=2$):* Sampling $d=2$ pods uniformly at random and routing to the one with the shorter queue drops the maximum queue length exponentially to $\frac{\ln \ln n}{\ln 2} + \Theta(1)$.
   * *Application:* Explains why L4 random routing suffers under long-lived multiplexed connections, while L7 balancers using P2C achieve near-optimal queue uniformity without centralized state synchronization.

2. **Little's Law (Little 1961):**
   * $L = \lambda W$ (Concurrency = Throughput $\times$ Latency).
   * *Application:* L4 kube-proxy is fundamentally unaware of $L$ (active in-flight requests at the application tier). L7 proxies explicitly track $L$. In high-concurrency spikes, Virtual Threads (Project Loom) enable servicing large $L$ values with minimal heap footprint compared to traditional 1 MB OS platform thread stacks.

3. **Kingman's Formula / VUT Equation (Kingman 1961):**
   * Approximate waiting time in a $G/G/1$ queue:
     $$W_q \approx \left(\frac{c_a^2 + c_s^2}{2}\right) \left(\frac{\rho}{1 - \rho}\right) \frac{1}{\mu}$$
   * *Application:* Quantifies the hyperbolic explosion of queue delay as CPU utilization $\rho \to 1$. Formally justifies Kubernetes HPA target utilization thresholds (e.g., 60% CPU) rather than 90–95%, providing headroom against traffic variance ($c_a^2$).

4. **Universal Scalability Law (Gunther 1993):**
   * $C(N) = \frac{N}{1 + \sigma (N - 1) + \kappa N (N - 1)}$
   * *Application:* Models scaling limits of horizontal pod replication. Least Connections introduces contention delay $\sigma$ and coherency overhead $\kappa$ when coordinating global connection counts across nodes, whereas P2C maintains $\sigma \approx 0$ and $\kappa \approx 0$ via independent local sampling.

5. **CAP / PACELC Asymmetry in Kubernetes:**
   * Kubernetes employs a strict **CP** control plane (etcd via Raft consensus) alongside an **AP (Eventual Consistency)** data plane (asynchronous rule distribution to worker nodes via kube-proxy).
   * *Application:* This fundamental distributed systems asymmetry produces the transient propagation window ($\Delta t \approx 1-4$s) during pod termination, necessitating the `preStop: sleep 10` hook to maintain zero-downtime availability.

6. **End-to-End Arguments in System Design (Saltzer, Reed, Clark 1984):**
   * Low-level transport mechanisms cannot fully guarantee end-to-end application correctness. Resilience patterns (retries with idempotency, circuit breakers, fallback degradation) must reside at the application tier (L7) rather than the packet filter level (L4).

---

## 7. Custom Load Balancer Research Prototype

To provide empirical validation for dissertation research and demonstrate deep systems expertise beyond standard Kubernetes abstraction, this project includes designing and benchmarking a **lightweight custom Layer 7 load balancer in Java 21**:

### Architecture
* Built using **Java 21 Virtual Threads (Project Loom)** to handle high-concurrency client sockets with minimal thread overhead.
* Maintains dynamic upstream pools tracking active Lodestar pod instances.

### Supported Algorithms for Comparative Study
1. **Round Robin (RR):** Sequential cyclic dispatch ($1 \rightarrow 2 \rightarrow 3 \rightarrow 1$). Serves as the performance baseline.
2. **Least Connections (LC):** Routes requests to the backend pod with the lowest number of active in-flight requests ($L$). Minimizes instantaneous queue depth at the cost of contention overhead $\sigma$.
3. **Power of Two Choices (P2C / Mitzenmacher 2001):** Selects two upstream candidate pods at random and routes to the one with fewer active connections. Achieves $O(\frac{\ln \ln n}{\ln 2})$ queue bounds without lock contention or the herd effect.

### Benchmark Setup & Empirical Validation
Using `k6` to inject synthetic traffic against the custom balancer, collecting P95 and P99 latency percentiles and upstream request distribution graphs under asymmetric backend load conditions to validate theoretical bounds against empirical measurements.
