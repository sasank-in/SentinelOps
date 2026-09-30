#!/usr/bin/env bash
# Injects a failure into every pod of a service in the cluster.
#   chaos/k8s-inject.sh payment-service database 120 100
#   chaos/k8s-inject.sh payment-service stop          # DELETE /failure on all pods
# Usage: k8s-inject.sh <service> <type|stop> [duration_seconds] [intensity]
#
# To hit a single pod instead (e.g. one "bad" replica), use:
#   kubectl -n sentinelops port-forward pod/<pod> 8082:8080 and curl localhost:8082/failure/...
set -euo pipefail

NAMESPACE=sentinelops
service=${1:?service name required}
type=${2:?failure type or 'stop' required}
duration=${3:-180}
intensity=${4:-50}

pods=$(kubectl -n "$NAMESPACE" get pods -l "app.kubernetes.io/name=$service" \
  --field-selector=status.phase=Running -o name)
[[ -n "$pods" ]] || { echo "No running pods for $service" >&2; exit 1; }

for pod in $pods; do
  if [[ "$type" == "stop" ]]; then
    kubectl -n "$NAMESPACE" exec "$pod" -- curl -fsS -X DELETE localhost:8080/failure
  else
    kubectl -n "$NAMESPACE" exec "$pod" -- curl -fsS -X POST "localhost:8080/failure/$type" \
      -H 'Content-Type: application/json' \
      -d "{\"duration_seconds\":$duration,\"intensity\":$intensity}"
  fi
  echo "  <- $pod"
done
