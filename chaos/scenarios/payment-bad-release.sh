#!/usr/bin/env bash
# Scenario: bad deployment of payment-service (the INC-1042 demo).
#
# Rolls out "v1.8.2", which shrinks the connection pool to 2 and adds a 500 ms
# fraud-check query. Under normal load this exhausts the pool: latency rises to
# seconds and orders fail with 503. Because everything lives in the Deployment's pod
# template, the correct remediation genuinely works:
#
#   kubectl -n sentinelops rollout undo deployment/payment-service
#
# Expected diagnosis: database connection pool exhaustion in payment-service, starting
# right after the rollout of 1.8.2; other services on the same database stay healthy.
set -euo pipefail

NAMESPACE=sentinelops

kubectl -n "$NAMESPACE" patch deployment payment-service --type strategic --patch '
metadata:
  annotations:
    kubernetes.io/change-cause: "release 1.8.2: tune connection pool, add fraud check"
spec:
  template:
    metadata:
      labels:
        app.kubernetes.io/version: "1.8.2"
    spec:
      containers:
        - name: payment-service
          image: sentinelops/payment-service:1.8.2
          env:
            - name: DB_POOL_SIZE
              value: "2"
            - name: PAYMENT_FRAUD_CHECK_DELAY
              value: 500ms
'
kubectl -n "$NAMESPACE" rollout status deployment/payment-service --timeout=300s
kubectl -n "$NAMESPACE" rollout history deployment/payment-service
