# Failure injection

Every Java service includes `chaos-spring-boot-starter`
([libs/chaos-spring-boot-starter](../libs/chaos-spring-boot-starter)), which adds
`/failure/*` endpoints when the service runs with the `chaos` Spring profile. docker-compose
enables that profile by default (`SPRING_PROFILES_ACTIVE=chaos`); without it the endpoints
do not exist.

| Service         | Base URL                |
| --------------- | ----------------------- |
| api-gateway     | `http://localhost:8080` |
| order-service   | `http://localhost:8081` |
| payment-service | `http://localhost:8082` |

## API

```text
GET    /failure          list active failures
POST   /failure/{type}   start a failure   body: {"duration_seconds": 180, "intensity": 50}
DELETE /failure/{type}   stop one failure
DELETE /failure          stop everything
```

Both body fields are optional (defaults: 180 s, intensity 50). The maximum duration is 900 s.
Starting a type that is already active replaces it. All failures expire on their own.

| Type         | What `intensity` (1–100) means                              | Expected symptoms                                      |
| ------------ | ----------------------------------------------------------- | ------------------------------------------------------ |
| `cpu`        | % of cores kept busy                                        | CPU saturation, rising latency                         |
| `memory`     | target % of max heap, grown over up to 60 s (a "leak")      | heap/GC growth; at ~100 the JVM exits and restarts     |
| `database`   | % of the JDBC pool held by long `pg_sleep` queries          | `Connection is not available` errors, 503s, pool 100%  |
| `latency`    | added delay = intensity × 50 ms (±20 % jitter)              | high p95/p99; callers may time out (order → payment 5 s) |
| `error-rate` | % of API requests failed with HTTP 500                      | 5xx spike, `Request failed` error logs                 |
| `crash`      | ignored; JVM halts after 2 s                                | container restart, restart count increases             |

`/actuator/**` and `/failure/**` are never affected, so health checks and the injector keep working.

## Examples

```bash
# Payment latency ~4 s for 3 minutes
curl -X POST localhost:8082/failure/latency -H 'Content-Type: application/json' \
     -d '{"duration_seconds":180,"intensity":80}'

# Exhaust payment-service's connection pool
curl -X POST localhost:8082/failure/database -H 'Content-Type: application/json' \
     -d '{"duration_seconds":180,"intensity":100}'

# Stop everything on payment-service
curl -X DELETE localhost:8082/failure
```

## On Kubernetes

The services aren't exposed individually in the cluster; use the helper, which calls
`/failure` inside every pod of a service:

```bash
chaos/k8s-inject.sh payment-service latency 180 80
chaos/k8s-inject.sh payment-service stop
```

To break a single replica only, `kubectl port-forward pod/<name> 8082:8080` and call it
directly.

## The "bad deployment" scenario

The main demo needs a failure that a **rollback** really fixes, so it is caused by
configuration rather than by `/failure/*`.

On Kubernetes (the real demo):

```bash
chaos/scenarios/payment-bad-release.sh                          # rolls out 1.8.2
kubectl -n sentinelops rollout undo deployment/payment-service  # remediation
```

With docker-compose:

```bash
# Bad release: tiny pool + slow fraud-check query
PAYMENT_VERSION=1.8.2 PAYMENT_DB_POOL_SIZE=2 PAYMENT_FRAUD_CHECK_DELAY=500ms \
  docker compose up -d payment-service

# Rollback
docker compose up -d payment-service   # back to the .env / default values (1.8.1)
```

The release version is exposed at `/actuator/info` and as the `version` tag on every metric,
so the incident agent can correlate the start of the incident with the deployment.

## Note for the investigation tooling

The injector logs its own actions under the `io.sentinelops.chaos` logger. Log tools that
the AI agent uses **must filter that logger out**; otherwise the agent reads the answer
instead of diagnosing the symptoms. Symptoms themselves (500s, pool timeouts) are logged
by application loggers, just like real failures.

Scripted scenarios with expected diagnoses (for the evaluation framework) will go in
`chaos/scenarios/`.
