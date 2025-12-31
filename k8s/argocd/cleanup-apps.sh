#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "===================================="
echo "Cleaning Up ArgoCD Applications"
echo "===================================="
echo ""

# Delete applications in reverse order (applications -> infrastructure -> namespace)
echo "Deleting application services..."
kubectl delete -f apps/consumer-app.yaml --ignore-not-found=true
kubectl delete -f apps/producer-app.yaml --ignore-not-found=true
echo ""

echo "Waiting for applications to be removed..."
sleep 5
echo ""

echo "Deleting Kafka Connect and Debezium..."
kubectl delete -f apps/debezium-app.yaml --ignore-not-found=true
kubectl delete -f apps/kafka-connect-app.yaml --ignore-not-found=true
echo ""

echo "Waiting for Kafka Connect/Debezium to be removed..."
sleep 5
echo ""

echo "Deleting infrastructure applications..."
kubectl delete -f apps/postgres-app.yaml --ignore-not-found=true
kubectl delete -f apps/kafka-app.yaml --ignore-not-found=true
echo ""

echo "Waiting for infrastructure to be removed..."
sleep 10
echo ""

echo "Deleting namespace application..."
kubectl delete -f apps/namespace-app.yaml --ignore-not-found=true
echo ""

echo "===================================="
echo "Cleanup Complete!"
echo "===================================="
echo ""
echo "Waiting for all resources to be fully deleted..."
echo "You can check the status with:"
echo "  kubectl get applications -n argocd"
echo "  kubectl get pods -n cdc-platform"
echo ""
echo "To redeploy, run:"
echo "  ./deploy-apps.sh"
echo "===================================="
