# CDC Sample Project

A minimal Change Data Capture (CDC) implementation demonstrating real-time data synchronization from PostgreSQL to an in-memory document store using Debezium and Apache Kafka.

## Architecture

```
Producer Service (Spring Boot)
        ↓ writes
PostgreSQL (orders, order_items, products)
        ↓ CDC
Debezium Connector
        ↓ publishes
Apache Kafka (3 topics)
        ↓ consumes
Consumer Service (Spring Boot)
        ↓ aggregates & stores
In-Memory Document Store (ConcurrentHashMap)
```

## Features

- **Multi-table CDC**: Captures changes from 3 PostgreSQL tables
- **Event Streaming**: Uses Kafka to stream change events
- **Data Aggregation**: Consumer aggregates data from multiple topics into denormalized documents
- **Real-time Sync**: Changes propagate in near real-time
- **RESTful APIs**: Both producer and consumer expose REST endpoints

## Prerequisites

- Docker and Docker Compose
- At least 4GB of available RAM

## Quick Start

### 1. Start All Services

```bash
docker-compose up -d
```

This will start:
- Zookeeper (port 2181)
- Kafka (port 9092)
- PostgreSQL (port 5432)
- Kafka Connect with Debezium (port 8083)
- Producer Service (port 8080)
- Consumer Service (port 8081)

### 2. Wait for Services to Initialize

```bash
# Check if all containers are running
docker-compose ps

# Check Debezium connector status
curl http://localhost:8083/connectors/postgres-connector/status
```

### 3. Verify Sample Data

The database is pre-populated with 5 products. Verify they're cached in the consumer:

```bash
curl http://localhost:8081/api/products
```

## Rebuilding Services After Code Changes

When you make code changes to the producer or consumer services, you need to rebuild and restart the containers:

### Rebuild a Specific Service

```bash
# Rebuild and restart the producer service
docker-compose up -d --build producer

# Rebuild and restart the consumer service
docker-compose up -d --build consumer
```

### Rebuild All Services

```bash
# Rebuild and restart all services
docker-compose up -d --build
```

### Verify the Service is Running

```bash
# Check container status
docker-compose ps

# Check service logs
docker-compose logs -f producer
docker-compose logs -f consumer
```

## Usage Examples

### Create an Order

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

### Wait for CDC Propagation

Wait 2-3 seconds for the change events to propagate through Kafka.

### View Aggregated Document

```bash
curl http://localhost:8081/api/documents/1
```

Expected response:
```json
{
  "orderId": 1,
  "userId": 999,
  "status": "PENDING",
  "totalPrice": 2425.00,
  "items": [
    {
      "productId": 1,
      "name": "Laptop",
      "unitPrice": 1200.00,
      "qty": 2
    },
    {
      "productId": 2,
      "name": "Mouse",
      "unitPrice": 25.00,
      "qty": 1
    }
  ],
  "orderedAt": "2025-12-30T..."
}
```

### Update Order Status

```bash
curl -X PUT "http://localhost:8080/api/orders/1/status?status=PAID"

# Wait 2-3 seconds, then verify
curl http://localhost:8081/api/documents/1
```

The document's `status` field should now be "PAID".

### Update Product Name

```bash
curl -X PUT http://localhost:8080/api/products/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Gaming Laptop",
    "price": 1200.00,
    "description": "High-performance gaming laptop"
  }'

# Wait 2-3 seconds, then verify
curl http://localhost:8081/api/documents/1
```

All documents containing this product should show the updated name "Gaming Laptop".

## API Documentation

### Producer Service (Port 8080)

#### Products
- `POST /api/products` - Create a product
- `GET /api/products` - List all products
- `GET /api/products/{id}` - Get product by ID
- `PUT /api/products/{id}` - Update product

#### Orders
- `POST /api/orders` - Create an order
- `GET /api/orders` - List all orders
- `GET /api/orders/{id}` - Get order by ID
- `PUT /api/orders/{id}/status?status={STATUS}` - Update order status

### Consumer Service (Port 8081)

#### Documents
- `GET /api/documents` - List all aggregated documents
- `GET /api/documents/{orderId}` - Get document by order ID
- `GET /api/documents/stats` - Get document statistics

#### Products
- `GET /api/products` - View cached products

## Database Schema

### orders
```sql
id BIGSERIAL PRIMARY KEY
user_id BIGINT NOT NULL
status VARCHAR(50) NOT NULL
total_price DECIMAL(10, 2) NOT NULL
ordered_at TIMESTAMP
updated_at TIMESTAMP
```

### order_items
```sql
id BIGSERIAL PRIMARY KEY
order_id BIGINT NOT NULL REFERENCES orders(id)
product_id BIGINT NOT NULL
quantity INT NOT NULL
unit_price DECIMAL(10, 2) NOT NULL
created_at TIMESTAMP
```

### products
```sql
id BIGSERIAL PRIMARY KEY
name VARCHAR(255) NOT NULL
price DECIMAL(10, 2) NOT NULL
description TEXT
created_at TIMESTAMP
updated_at TIMESTAMP
```

## Kafka Topics

Debezium creates the following topics:
- `dbserver1.public.orders` - Order change events
- `dbserver1.public.order_items` - Order item change events
- `dbserver1.public.products` - Product change events

### View Kafka Topics

```bash
docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092
```

### Consume Messages from a Topic

```bash
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic dbserver1.public.orders \
  --from-beginning
```

## Monitoring

### Check Service Health

```bash
# Producer
curl http://localhost:8080/actuator/health

# Consumer
curl http://localhost:8081/actuator/health

# Kafka Connect
curl http://localhost:8083/
```

### View Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f producer
docker-compose logs -f consumer
docker-compose logs -f kafka-connect
```

## Troubleshooting

### Debezium Connector Not Registered

If the connector isn't automatically registered:

```bash
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @debezium/register-connector.json
```

### Consumer Not Receiving Events

1. Check if Kafka Connect is running: `curl http://localhost:8083/`
2. Check connector status: `curl http://localhost:8083/connectors/postgres-connector/status`
3. Verify topics exist: `docker exec kafka kafka-topics --list --bootstrap-server localhost:9092`
4. Check consumer logs: `docker-compose logs -f consumer`

### Database Connection Issues

```bash
# Access PostgreSQL
docker exec -it postgres psql -U postgres -d producer_db

# List tables
\dt

# Query orders
SELECT * FROM orders;
```

## Clean Up

### Stop All Services

```bash
docker-compose down
```

### Remove All Data (including volumes)

```bash
docker-compose down -v
```

## How It Works

1. **Write Operation**: Producer service writes to PostgreSQL (orders, order_items, products)
2. **CDC Capture**: Debezium reads PostgreSQL's Write-Ahead Log (WAL)
3. **Event Publishing**: Debezium publishes change events to Kafka topics
4. **Event Consumption**: Consumer service listens to all 3 topics
5. **Aggregation**: Consumer aggregates data from multiple events:
   - Order events provide header info (user, status, total)
   - Order item events provide line items (product_id, quantity, price)
   - Product events provide product names for enrichment
6. **Document Creation**: Once all related events arrive, consumer creates a denormalized document
7. **Storage**: Document is stored in-memory (ConcurrentHashMap)
8. **Retrieval**: Documents are accessible via REST API

## Kubernetes Deployment

The application can also be deployed to Kubernetes (Docker Desktop). See the [k8s/README.md](k8s/README.md) for detailed instructions.

### Quick Start with Kubernetes

1. Enable Kubernetes in Docker Desktop (Settings → Kubernetes)
2. Build the Docker images:
   ```bash
   docker build -t cdc-producer:latest ./producer
   docker build -t cdc-consumer:latest ./consumer
   ```
3. Deploy to Kubernetes:
   ```bash
   cd k8s
   ./deploy.sh
   ```
4. Test the deployment:
   ```bash
   curl http://localhost:8081/api/products
   ```

The Kubernetes manifests are organized by component:
- `zookeeper/` - Coordination service
- `kafka/` - Message broker
- `postgres/` - Source database
- `kafka-connect/` - Debezium connector runtime
- `debezium/` - Connector registration
- `producer/` - Order management API
- `consumer/` - CDC consumer and aggregator

## Future Enhancements

- Replace in-memory storage with Elasticsearch
- Add error handling and dead letter queues
- Implement event sourcing patterns
- Add integration tests
- Add monitoring with Prometheus and Grafana
- Handle schema evolution
- Add data validation and sanitization

## License

MIT
