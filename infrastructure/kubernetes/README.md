# Kubernetes

Kustomize manifests for running SentinelOps on a local cluster. Tested on Docker Desktop
Kubernetes v1.34; `scripts/k8s-up.sh` also handles kind, k3d and minikube.

```text
base/              environment-neutral manifests (namespace, postgres, 3 services)
overlays/local/    local cluster: image tags, demo Secret, chaos profile, NodePort 30080
```

## Deploy

```bash
scripts/k8s-up.sh                 # build images, apply overlays/local, wait for rollout
scripts/k8s-down.sh               # remove workloads, keep the Postgres volume
scripts/k8s-down.sh --purge       # delete the namespace including data
```

The script refuses to run unless the current kubectl context looks local (`docker-desktop`,
`kind-*`, `k3d-*`, `minikube`, ...).

- API: `http://localhost:30080/api/orders` on Docker Desktop, or on any cluster
  `kubectl -n sentinelops port-forward svc/api-gateway 8080:8080`.
- Memory: the cluster needs about 3 GB. Stop docker-compose first (`docker compose stop`)
  if Docker has 8 GB or less.

## What runs

| Workload        | Kind        | Replicas | Requests / limits       |
| --------------- | ----------- | -------- | ----------------------- |
| postgres        | StatefulSet | 1        | 100m, 256Mi / 1, 512Mi  |
| payment-service | Deployment  | 2        | 250m, 512Mi / 1, 768Mi  |
| order-service   | Deployment  | 2        | 250m, 512Mi / 1, 768Mi  |
| api-gateway     | Deployment  | 1        | 200m, 384Mi / 1, 512Mi  |

## Design decisions

**Release settings live in the pod template.** `APP_VERSION`, `DB_POOL_SIZE` and
`PAYMENT_FRAUD_CHECK_DELAY` are set directly in each Deployment (not in a ConfigMap), so every
Deployment revision captures them and `kubectl rollout undo` fully restores the previous
release. This is what the future `rollback_deployment` MCP tool relies on. The version is
defined once, in the `app.kubernetes.io/version` pod label, and passed to the app with the
Downward API, so `/actuator/info`, metric tags and the pod label always agree.

**Generated config is hash-suffixed.** `sentinelops-runtime` (ConfigMap) and
`postgres-credentials` (Secret) come from Kustomize generators. Changing them changes their
name, which triggers a rolling restart instead of pods silently keeping stale values.

**Probes.**

| Probe     | Endpoint                     | Purpose                                             |
| --------- | ---------------------------- | --------------------------------------------------- |
| startup   | `/actuator/health/liveness`  | up to 150 s for JVM start under a 1-CPU limit       |
| liveness  | `/actuator/health/liveness`  | restart a hung JVM; **excludes the database**       |
| readiness | `/actuator/health/readiness` | take the pod out of the Service while not ready     |

The database is deliberately not part of liveness: a database outage must show up as
errors (evidence for the agent), not as a restart storm that hides the cause.

**Least privilege.**
- All pods run as non-root, with a read-only root filesystem (Java pods; `/tmp` is an
  `emptyDir`), no privilege escalation, all capabilities dropped, and the `RuntimeDefault`
  seccomp profile.
- `automountServiceAccountToken: false` everywhere: no app pod can call the Kubernetes API.
  Later, only the MCP server gets a ServiceAccount, with narrowly scoped RBAC.

**Zero-downtime rollouts.** `maxUnavailable: 0`, `maxSurge: 1`, and a 5 s `preStop` sleep, so
kube-proxy stops routing to a pod before Spring's graceful shutdown begins.

## Operating it

```bash
kubectl -n sentinelops get pods
kubectl -n sentinelops logs deploy/payment-service -f
kubectl -n sentinelops rollout history deployment/payment-service
kubectl -n sentinelops scale deployment/order-service --replicas=3
```

Failure injection in the cluster:

```bash
chaos/k8s-inject.sh payment-service database 120 100   # all payment pods
chaos/k8s-inject.sh payment-service stop
chaos/scenarios/payment-bad-release.sh                 # roll out the bad 1.8.2
kubectl -n sentinelops rollout undo deployment/payment-service   # remediate
```

## Known limitations (local only)

- Docker Desktop does not enforce NetworkPolicy, so none are defined yet.
- No metrics-server, so no HorizontalPodAutoscaler; scaling is manual (and later a
  human-approved remediation action).
- Right after a pod (re)starts, the first ~15 s are slow while the JVM warms up. The
  verification engine (Phase 9) must wait that out before judging recovery.
