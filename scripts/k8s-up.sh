#!/usr/bin/env bash
# Builds the service images and deploys SentinelOps to a LOCAL Kubernetes cluster.
#   scripts/k8s-up.sh            build + deploy
#   SKIP_BUILD=1 scripts/k8s-up.sh
set -euo pipefail

cd "$(dirname "$0")/.."
NAMESPACE=sentinelops
OVERLAY=infrastructure/kubernetes/overlays/local

context=$(kubectl config current-context)
case "$context" in
  docker-desktop | kind-* | k3d-* | minikube | rancher-desktop | orbstack) ;;
  *)
    echo "Refusing to deploy: '$context' does not look like a local cluster." >&2
    echo "Switch with: kubectl config use-context docker-desktop" >&2
    exit 1
    ;;
esac
echo "Cluster context: $context"

if [[ -z "${SKIP_BUILD:-}" ]]; then
  echo "Building images..."
  docker compose build
  # 1.8.2 is the same code as 1.8.1; the "bad release" differs only in configuration
  # (see chaos/scenarios/payment-bad-release.sh).
  docker tag sentinelops/payment-service:1.8.1 sentinelops/payment-service:1.8.2
fi

images=(sentinelops/api-gateway:1.0.0 sentinelops/order-service:1.0.0
  sentinelops/payment-service:1.8.1 sentinelops/payment-service:1.8.2)
case "$context" in
  kind-*) for image in "${images[@]}"; do kind load docker-image "$image" --name "${context#kind-}"; done ;;
  k3d-*) k3d image import "${images[@]}" --cluster "${context#k3d-}" ;;
  minikube) for image in "${images[@]}"; do minikube image load "$image"; done ;;
  *) ;; # docker-desktop etc. share the local Docker image store
esac

echo "Applying manifests..."
kubectl apply -k "$OVERLAY"

echo "Waiting for rollout..."
kubectl -n "$NAMESPACE" rollout status statefulset/postgres --timeout=180s
for deployment in payment-service order-service api-gateway; do
  kubectl -n "$NAMESPACE" rollout status "deployment/$deployment" --timeout=300s
done

kubectl -n "$NAMESPACE" get pods -o wide
cat <<EOF

SentinelOps is running in namespace '$NAMESPACE'.
  API (Docker Desktop NodePort): http://localhost:30080/api/orders
  Any cluster:                   kubectl -n $NAMESPACE port-forward svc/api-gateway 8080:8080
EOF
