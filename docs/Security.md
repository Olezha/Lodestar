# Security Profiles

Lodestar ships two Spring Security filter chains selected by the active Spring profile
(`config/SecurityConfig.java`).

| | Default (dev) | `prod` |
|---|---|---|
| Business API (`/api/v1/**`) | open | JWT with scope (see below) |
| Swagger UI / OpenAPI | enabled | disabled (`application-prod.yml`) and denied |
| Actuator | `health`, `info`, `metrics`, `env`, `configprops` | `health`, `info` only, no details |
| Sessions | default | stateless |
| Unlisted paths | authenticated | denied |

The default profile keeps local development, Swagger UI and the k6 load tests
(`tests/load/`) working without tokens.

## `prod` profile

Lodestar acts as an **OAuth2 Resource Server**: it only verifies tokens and does not issue them.
Tokens are signed with a shared **HMAC-SHA256** key and validated by `NimbusJwtDecoder`:

* signature (`HS256` only);
* `exp` / `nbf` with the default 60 s clock skew;
* `iss` must equal `JWT_ISSUER` (default `lodestar`).

The application fails to start in `prod` if `JWT_SECRET` is missing or shorter than 32 bytes.

### Endpoint scopes

The `scope` claim (space-separated) is mapped to `SCOPE_*` authorities.

| Endpoint | Required scope |
|---|---|
| `POST /api/v1/alerts` | `alerts:write` |
| `/api/v1/subscriptions/**` | `subscriptions:manage` |
| `/api/v1/regions/**` | `regions:admin` |
| `/`, `/actuator/health/**`, `/actuator/info` | public (Kubernetes probes) |

Responses: `401` with `WWW-Authenticate: Bearer` for a missing or invalid token,
`403` for a valid token without the required scope.

### Running locally with `prod`

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
```

Minting a short-lived test token (Python, `pip install pyjwt`):

```bash
python3 -c 'import jwt, os, time; print(jwt.encode(
    {"sub": "admin", "iss": "lodestar", "scope": "subscriptions:manage regions:admin alerts:write",
     "iat": int(time.time()), "exp": int(time.time()) + 3600},
    os.environ["JWT_SECRET"], algorithm="HS256"))'
```

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/subscriptions
```

### Kubernetes

The manifests in `k8s/` run the default profile so that the k6 spike test works unchanged.
To run `prod` in the cluster, add `SPRING_PROFILES_ACTIVE: "prod"` to `k8s/configmap.yaml` and
`JWT_SECRET` to `k8s/secret.yaml`; the probes keep working because `/actuator/health/**` stays public.

## Known limitations

* Subscriptions are not yet bound to the token subject (`sub`): any caller with
  `subscriptions:manage` can manage every user's subscriptions.
* A shared symmetric key suits a single service; with an external identity provider
  (Keycloak, Auth0) the decoder would switch to `issuer-uri` / JWKS with asymmetric keys.
