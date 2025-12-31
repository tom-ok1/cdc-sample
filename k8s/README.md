# Kubernetes Deployment Guide

This directory contains Kubernetes manifests for deploying the CDC (Change Data Capture) application to a local Kubernetes cluster.

## Directory Structure

```
k8s/
├── namespace.yaml              # data-platform namespace
├── zookeeper/                  # Zookeeper coordination service
│   ├── pvc.yaml               # PersistentVolumeClaim for data
│   ├── service.yaml           # ClusterIP service
│   └── statefulset.yaml       # StatefulSet with 1 replica
├── kafka/                      # Kafka message broker
│   ├── pvc.yaml               # PersistentVolumeClaim for logs
│   ├── service.yaml           # ClusterIP service
│   └── statefulset.yaml       # StatefulSet with 1 replica
├── postgres/                   # PostgreSQL database
│   ├── secret.yaml            # Database credentials
│   ├── configmap.yaml         # init.sql for schema setup
│   ├── pvc.yaml               # PersistentVolumeClaim for data
│   ├── service.yaml           # ClusterIP service
│   └── statefulset.yaml       # StatefulSet with 1 replica
├── kafka-connect/              # Kafka Connect with Debezium
│   ├── service.yaml           # ClusterIP service (REST API)
│   └── deployment.yaml        # Deployment with 1 replica
├── debezium/                   # Debezium connector registration
│   ├── configmap.yaml         # Connector configuration
│   └── job.yaml               # One-time registration job
├── producer/                   # Producer Spring Boot app
│   ├── service.yaml           # LoadBalancer service (port 8080)
│   └── deployment.yaml        # Deployment with 1 replica
└── consumer/                   # Consumer Spring Boot app
    ├── service.yaml           # LoadBalancer service (port 8081)
    └── deployment.yaml        # Deployment with 1 replica
```

## Prerequisites

1. **Docker Desktop** with Kubernetes enabled
   - Go to Settings → Kubernetes → Enable Kubernetes
   - Allocate at least 6GB RAM and 4 CPUs

2. **kubectl** command-line tool (included with Docker Desktop)

3. **Docker images** built locally:
   ```bash
   docker build -t cdc-producer:latest ./producer
   docker build -t cdc-consumer:latest ./consumer
   ```

## Deployment Steps

### 1. Create Namespace

```bash
kubectl apply -f namespace.yaml
```

### 2. Deploy Infrastructure Layer

Deploy in order, waiting for each component to be ready:

```bash
# Deploy Zookeeper
kubectl apply -f zookeeper/
kubectl wait --for=condition=ready pod -l app=zookeeper -n data-platform --timeout=300s

# Deploy Kafka
kubectl apply -f kafka/
kubectl wait --for=condition=ready pod -l app=kafka -n data-platform --timeout=300s

# Deploy PostgreSQL
kubectl apply -f postgres/
kubectl wait --for=condition=ready pod -l app=postgres -n data-platform --timeout=300s
```

### 3. Deploy Platform Layer

```bash
# Deploy Kafka Connect
kubectl apply -f kafka-connect/
kubectl wait --for=condition=ready pod -l app=kafka-connect -n data-platform --timeout=300s

# Register Debezium Connector
kubectl apply -f debezium/
kubectl wait --for=condition=complete job/debezium-init -n data-platform --timeout=120s
```

### 4. Deploy Application Layer

```bash
# Deploy Producer and Consumer
kubectl apply -f producer/
kubectl apply -f consumer/

# Wait for readiness
kubectl wait --for=condition=ready pod -l app=producer -n data-platform --timeout=300s
kubectl wait --for=condition=ready pod -l app=consumer -n data-platform --timeout=300s
```

### 5. Verify Deployment

```bash
# Check all pods are running
kubectl get pods -n data-platform

# Check services
kubectl get svc -n data-platform

# Check PVCs are bound
kubectl get pvc -n data-platform
```

Expected output:
```
NAME                        READY   STATUS      RESTARTS   AGE
zookeeper-0                 1/1     Running     0          5m
kafka-0                     1/1     Running     0          4m
postgres-0                  1/1     Running     0          3m
kafka-connect-xxx           1/1     Running     0          2m
debezium-init-xxx           0/1     Completed   0          1m
producer-xxx                1/1     Running     0          1m
consumer-xxx                1/1     Running     0          1m
```

## Quick Deploy Script

Deploy everything in the correct order:

```bash
#!/bin/bash
set -e

echo "Creating namespace..."
kubectl apply -f namespace.yaml

echo "Deploying Zookeeper..."
kubectl apply -f zookeeper/
kubectl wait --for=condition=ready pod -l app=zookeeper -n data-platform --timeout=300s

echo "Deploying Kafka..."
kubectl apply -f kafka/
kubectl wait --for=condition=ready pod -l app=kafka -n data-platform --timeout=300s

echo "Deploying PostgreSQL..."
kubectl apply -f postgres/
kubectl wait --for=condition=ready pod -l app=postgres -n data-platform --timeout=300s

echo "Deploying Kafka Connect..."
kubectl apply -f kafka-connect/
kubectl wait --for=condition=ready pod -l app=kafka-connect -n data-platform --timeout=300s

echo "Registering Debezium connector..."
kubectl apply -f debezium/
kubectl wait --for=condition=complete job/debezium-init -n data-platform --timeout=120s

echo "Deploying applications..."
kubectl apply -f producer/
kubectl apply -f consumer/
kubectl wait --for=condition=ready pod -l app=producer -n data-platform --timeout=300s
kubectl wait --for=condition=ready pod -l app=consumer -n data-platform --timeout=300s

echo "Deployment complete!"
kubectl get pods -n data-platform
```

Save this as `deploy.sh`, make it executable (`chmod +x deploy.sh`), and run it.

## Testing the CDC Pipeline

### 1. Verify Pre-populated Products

```bash
curl http://localhost:8081/api/products
```

Expected: 5 products (Laptop, Mouse, Keyboard, Monitor, Headphones)

### 2. Create an Order

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 999,
    "items": [
      {"productId": 1, "quantity": 2},
      {"productId": 2, "quantity": 1}
    ]
  }'
```

### 3. Check Aggregated Document

Wait 3-5 seconds for CDC propagation, then:

```bash
curl http://localhost:8081/api/documents/1
```

Expected: Denormalized document with order details, items, and product names

### 4. Update Order Status

```bash
curl -X PUT "http://localhost:8080/api/orders/1/status?status=PAID"

# Wait 3 seconds, then verify
curl http://localhost:8081/api/documents/1
```

The status field should now show "PAID".

## Troubleshooting

### Check Pod Logs

```bash
# Zookeeper
kubectl logs -n data-platform zookeeper-0

# Kafka
kubectl logs -n data-platform kafka-0

# PostgreSQL
kubectl logs -n data-platform postgres-0

# Kafka Connect
kubectl logs -n data-platform -l app=kafka-connect

# Debezium init job
kubectl logs -n data-platform -l app=debezium-init

# Producer
kubectl logs -n data-platform -l app=producer

# Consumer
kubectl logs -n data-platform -l app=consumer
```

### Check Debezium Connector Status

```bash
kubectl exec -n data-platform deployment/kafka-connect -- \
  curl http://localhost:8083/connectors/postgres-connector/status
```

Expected: `"state": "RUNNING"`

### Check Kafka Topics

```bash
kubectl exec -n data-platform kafka-0 -- \
  kafka-topics --list --bootstrap-server localhost:9092
```

Expected topics:
- `dbserver1.public.orders`
- `dbserver1.public.order_items`
- `dbserver1.public.products`

### View Kafka Messages

```bash
kubectl exec -n data-platform kafka-0 -- \
  kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic dbserver1.public.orders --from-beginning --max-messages 5
```

### Database Connection Issues

```bash
# Access PostgreSQL directly
kubectl exec -it -n data-platform postgres-0 -- psql -U postgres -d producer_db

# List tables
\dt

# Query products
SELECT * FROM products;
```

### Re-register Debezium Connector

If the connector fails to register:

```bash
# Delete the job
kubectl delete job debezium-init -n data-platform

# Re-run the job
kubectl apply -f debezium/job.yaml
```

## Clean Up

### Delete All Resources

```bash
kubectl delete namespace data-platform
```

This will delete all resources including PVCs and data.

### Delete Everything Except Data

```bash
kubectl delete -f consumer/
kubectl delete -f producer/
kubectl delete -f debezium/
kubectl delete -f kafka-connect/
kubectl delete deployment,statefulset -n data-platform --all
kubectl delete svc -n data-platform --all
```

PVCs will remain, so data persists for next deployment.

## Configuration Details

### PostgreSQL Credentials

Stored in `postgres/secret.yaml`:
- Username: `postgres`
- Password: `postgres`
- Database: `producer_db`

### Kafka Configuration

- Bootstrap servers (internal): `kafka:29092`
- Bootstrap servers (client): `kafka:9092`
- Replication factor: 1 (single broker)

### External Access

- Producer: `http://localhost:8080`
- Consumer: `http://localhost:8081`

### Storage

- Zookeeper: 1Gi PersistentVolume
- Kafka: 5Gi PersistentVolume
- PostgreSQL: 2Gi PersistentVolume

## Component Dependencies

```
namespace
  ↓
postgres, zookeeper
  ↓
kafka (depends on zookeeper)
  ↓
kafka-connect (depends on kafka + postgres)
  ↓
debezium-init (depends on kafka-connect)
  ↓
producer (depends on postgres), consumer (depends on kafka)
```

## Resource Requests and Limits

| Component     | Memory Request | Memory Limit | CPU Request | CPU Limit |
|---------------|----------------|--------------|-------------|-----------|
| Zookeeper     | 256Mi          | 512Mi        | 250m        | 500m      |
| Kafka         | 1Gi            | 2Gi          | 500m        | 1000m     |
| PostgreSQL    | 512Mi          | 1Gi          | 500m        | 1000m     |
| Kafka Connect | 1Gi            | 2Gi          | 500m        | 1000m     |
| Producer      | 512Mi          | 1Gi          | 250m        | 500m      |
| Consumer      | 512Mi          | 1Gi          | 250m        | 500m      |

**Total**: ~3.5Gi memory requests, ~8.5Gi memory limits

## Next Steps

- Add monitoring with Prometheus and Grafana
- Implement horizontal pod autoscaling
- Add network policies for security
- Configure ingress for external access
- Set up CI/CD pipeline for deployments
