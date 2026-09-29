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

🚧 Planning. See [Project_Documentation.md](Project_Documentation.md) for the full design, build order, and definition of done.

Current phase: **Phase 1: base microservices** (API Gateway → Order → Payment → PostgreSQL, with failure-injection endpoints).
