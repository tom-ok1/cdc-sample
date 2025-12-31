#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "==================================="
echo "CDC Application Kubernetes Deployment"
echo "==================================="
echo ""

echo "Creating namespace..."
kubectl apply -f namespace.yaml
echo ""

echo "Deploying Zookeeper..."
kubectl apply -f zookeeper/
echo "Waiting for Zookeeper to be ready..."
kubectl wait --for=condition=ready pod -l app=zookeeper -n data-platform --timeout=300s
echo "✓ Zookeeper is ready"
echo ""

echo "Deploying Kafka..."
kubectl apply -f kafka/
echo "Waiting for Kafka to be ready..."
kubectl wait --for=condition=ready pod -l app=kafka -n data-platform --timeout=300s
echo "✓ Kafka is ready"
echo ""

echo "Deploying PostgreSQL..."
kubectl apply -f postgres/
echo "Waiting for PostgreSQL to be ready..."
kubectl wait --for=condition=ready pod -l app=postgres -n data-platform --timeout=300s
echo "✓ PostgreSQL is ready"
echo ""

echo "Deploying Kafka Connect..."
kubectl apply -f kafka-connect/
echo "Waiting for Kafka Connect to be ready..."
kubectl wait --for=condition=ready pod -l app=kafka-connect -n data-platform --timeout=300s
echo "✓ Kafka Connect is ready"
echo ""

echo "Registering Debezium connector..."
kubectl apply -f debezium/
echo "Waiting for Debezium connector registration..."
kubectl wait --for=condition=complete job/debezium-init -n data-platform --timeout=120s
echo "✓ Debezium connector registered"
echo ""

echo "Deploying Producer application..."
kubectl apply -f producer/
echo ""

echo "Deploying Consumer application..."
kubectl apply -f consumer/
echo ""

echo "Waiting for applications to be ready..."
kubectl wait --for=condition=ready pod -l app=producer -n data-platform --timeout=300s
kubectl wait --for=condition=ready pod -l app=consumer -n data-platform --timeout=300s
echo "✓ Applications are ready"
echo ""

echo "==================================="
echo "Deployment Complete!"
echo "==================================="
echo ""

echo "Pod Status:"
kubectl get pods -n data-platform
echo ""

echo "Service Status:"
kubectl get svc -n data-platform
echo ""

echo "==================================="
echo "Application Endpoints:"
echo "  Producer: http://localhost:8080"
echo "  Consumer: http://localhost:8081"
echo ""
echo "Test the deployment:"
echo "  curl http://localhost:8081/api/products"
echo "==================================="
