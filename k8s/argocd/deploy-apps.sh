#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "===================================="
echo "Deploying ArgoCD Applications"
echo "===================================="
echo ""

# Deploy applications in order
echo "Deploying namespace application..."
kubectl apply -f apps/namespace-app.yaml
echo ""

echo "Waiting for namespace to be synced..."
sleep 5
echo ""

echo "Deploying infrastructure applications..."
kubectl apply -f apps/kafka-app.yaml
kubectl apply -f apps/postgres-app.yaml
echo ""

echo "Waiting for infrastructure to sync..."
sleep 10
echo ""

echo "Deploying Kafka Connect and Debezium..."
kubectl apply -f apps/kafka-connect-app.yaml
kubectl apply -f apps/debezium-app.yaml
echo ""

echo "Waiting for Kafka Connect to sync..."
sleep 10
echo ""

echo "Deploying application services..."
kubectl apply -f apps/producer-app.yaml
kubectl apply -f apps/consumer-app.yaml
echo ""

echo "===================================="
echo "ArgoCD Applications Deployed!"
echo "===================================="
echo ""
echo "Check application status:"
echo "  kubectl get applications -n argocd"
echo ""
echo "Or view in ArgoCD UI:"
echo "  kubectl port-forward svc/argocd-server -n argocd 8080:443"
echo "  Open: https://localhost:8080"
echo "===================================="
