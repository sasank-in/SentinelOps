#!/usr/bin/env bash
# Removes SentinelOps from the local cluster.
#   scripts/k8s-down.sh           keep the Postgres volume (data survives redeploys)
#   scripts/k8s-down.sh --purge   also delete the namespace, including the volume
set -euo pipefail

cd "$(dirname "$0")/.."
NAMESPACE=sentinelops

if [[ "${1:-}" == "--purge" ]]; then
  kubectl delete namespace "$NAMESPACE" --ignore-not-found
else
  # Deleting the workloads leaves the StatefulSet's PersistentVolumeClaim in place.
  kubectl -n "$NAMESPACE" delete deployment,statefulset,service,configmap,secret \
    -l app.kubernetes.io/part-of=sentinelops --ignore-not-found
fi
