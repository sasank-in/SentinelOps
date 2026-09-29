# SentinelOps

**AI-native incident response & remediation platform.**

SentinelOps watches a distributed application and detects incidents. An AI agent then investigates them using read-only operational tools exposed over MCP and writes an evidence-backed diagnosis. After an engineer approves, SentinelOps runs the remediation and checks that the service recovered.

```text
Failure → Telemetry → Alert → n8n → Incident created → AI investigates (read-only MCP tools)
       → Diagnosis + recommendation → Human approval → Remediation → Verification → Resolved
```

## Core principle

> The AI reasons over evidence. It never controls infrastructure without explicit tool permissions and human approval.

## Stack

| Layer          | Technology                                              |
| -------------- | ------------------------------------------------------- |
| Infrastructure | Kubernetes, Docker, OpenTelemetry                       |
| Observability  | Prometheus, Alertmanager, Loki, Tempo, Grafana          |
| Automation     | n8n                                                     |
| Intelligence   | Python / FastAPI AI service, MCP server, LLM            |
| Application    | Spring Boot (gateway, order, payment, incident), React  |
| Data           | PostgreSQL (Redis / Kafka later)                        |

## Status

See [Project_Documentation.md](Project_Documentation.md) for the full design, build order, and definition of done.

- ✅ **Phase 1: base microservices**: API Gateway → Order → Payment → PostgreSQL, failure injection, load generator
- ⏭️ Phase 2: Kubernetes

## Quick start

Requirements: Docker (with Compose), and optionally Java 21 + Maven 3.9 and Python 3.12 for local builds and tests.

```bash
cp .env.example .env          # optional; defaults work without it
docker compose up --build -d  # first build takes a few minutes

# Place an order through the gateway
curl -X POST localhost:8080/api/orders -H 'Content-Type: application/json' \
  -d '{"customerId":"cust-1","currency":"USD","items":[{"sku":"SKU-1","quantity":2,"unitPrice":19.99}]}'

# Baseline traffic
python load-generator/load_generator.py --rps 5

# Break something (see chaos/README.md)
curl -X POST localhost:8082/failure/database -H 'Content-Type: application/json' \
  -d '{"duration_seconds":120,"intensity":100}'
```

| Service         | Port | Endpoints                                                              |
| --------------- | ---- | ---------------------------------------------------------------------- |
| api-gateway     | 8080 | `/api/orders/**`, `/api/payments/**` routed downstream                 |
| order-service   | 8081 | `POST /api/orders`, `GET /api/orders/{id}`, `GET /api/orders?customerId=` |
| payment-service | 8082 | `POST /api/payments`, `GET /api/payments/{id}`, `GET /api/payments?orderId=` |
| postgres        | 5432 | databases `orders`, `payments` (one shared instance)                   |

Every service exposes `/actuator/health`, `/actuator/info` (release version) and `/actuator/prometheus`.

## Repository layout

```text
libs/chaos-spring-boot-starter/   shared /failure/* injection (cpu, memory, database, latency, error-rate, crash)
services/api-gateway/             Spring Cloud Gateway (WebMVC)
services/order-service/           orders; calls payment-service with 2s/5s timeouts
services/payment-service/         idempotent payments; limit-based declines; configurable slow query
infrastructure/docker/            shared Java Dockerfile, Postgres init
load-generator/                   stdlib-only Python traffic generator
chaos/                            failure-injection docs and (later) scripted scenarios
```

## Build and test

```bash
mvn verify   # unit + integration tests; integration tests use Testcontainers (Docker required)
```
