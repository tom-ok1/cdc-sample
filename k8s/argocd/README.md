# ArgoCD Setup for CDC Data Platform

This directory contains ArgoCD configuration for managing the CDC data platform using GitOps principles.

## Prerequisites

- Kubernetes cluster (minikube, kind, or Docker Desktop)
- kubectl configured to access your cluster

## Installation Steps

### 1. Install ArgoCD

```bash
cd k8s/argocd
chmod +x install-argocd.sh
./install-argocd.sh
```

This script will:
- Create the `argocd` namespace
- Install ArgoCD components
- Configure the ArgoCD server
- Display the initial admin password

### 2. Access ArgoCD UI

In a separate terminal, set up port forwarding:

```bash
kubectl port-forward svc/argocd-server -n argocd 8080:443
```

Then open https://localhost:8080 in your browser.

Login credentials:
- Username: `admin`
- Password: Run this command to get it:
  ```bash
  kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d
  ```

### 3. Deploy Applications

```bash
chmod +x deploy-apps.sh
./deploy-apps.sh
```

This will create ArgoCD Application resources for:
- Namespace
- Zookeeper
- Kafka
- PostgreSQL
- Kafka Connect
- Debezium
- Producer application
- Consumer application

### 4. Monitor Deployment

View applications in the ArgoCD UI or use kubectl:

```bash
# List all ArgoCD applications
kubectl get applications -n argocd

# Check sync status
kubectl get applications -n argocd -o wide

# View pods in data-platform namespace
kubectl get pods -n data-platform -w
```

## ArgoCD Features

### Automatic Sync

All applications are configured with `automated sync`:
- **prune: true** - Automatically removes resources when removed from Git
- **selfHeal: true** - Automatically syncs when cluster state differs from Git

### Manual Sync

To manually sync an application:

```bash
# Using ArgoCD CLI
argocd app sync cdc-kafka

# Or in the UI, click the "Sync" button on the application card
```

### Rollback

To rollback to a previous version:

```bash
# View history
argocd app history cdc-producer

# Rollback to specific revision
argocd app rollback cdc-producer <revision-number>
```

## Application Structure

Each application points to a specific directory in the k8s folder:

```
k8s/
├── argocd/
│   └── apps/
│       ├── namespace-app.yaml      → k8s/namespace.yaml
│       ├── zookeeper-app.yaml      → k8s/zookeeper/
│       ├── kafka-app.yaml          → k8s/kafka/
│       ├── postgres-app.yaml       → k8s/postgres/
│       ├── kafka-connect-app.yaml  → k8s/kafka-connect/
│       ├── debezium-app.yaml       → k8s/debezium/
│       ├── producer-app.yaml       → k8s/producer/
│       └── consumer-app.yaml       → k8s/consumer/
```

## Making Changes

With ArgoCD, changes to your infrastructure are made through Git:

1. Edit the Kubernetes manifests in the `k8s/` directory
2. Commit and push changes to Git (if using Git repo)
3. ArgoCD automatically detects changes and syncs (or sync manually)
4. View the sync progress in the ArgoCD UI

## Using Git Repository (Optional)

To use a Git repository instead of local files:

1. Update the `repoURL` in each application YAML:
   ```yaml
   source:
     repoURL: https://github.com/your-username/cdc-sample.git
     targetRevision: main
     path: k8s/kafka
   ```

2. If using a private repository, configure credentials in ArgoCD:
   ```bash
   argocd repo add https://github.com/your-username/cdc-sample.git \
     --username <username> \
     --password <password>
   ```

## Cleanup

To remove all applications and ArgoCD:

```bash
# Delete all ArgoCD applications
kubectl delete applications -n argocd --all

# Wait for resources to be cleaned up
sleep 10

# Delete ArgoCD
kubectl delete namespace argocd

# Delete data platform namespace
kubectl delete namespace data-platform
```

## Useful Commands

```bash
# List all applications
kubectl get applications -n argocd

# Get application details
kubectl describe application cdc-kafka -n argocd

# View application logs
kubectl logs -f deployment/argocd-server -n argocd

# Check ArgoCD status
kubectl get pods -n argocd

# Access ArgoCD server
kubectl port-forward svc/argocd-server -n argocd 8080:443
```

## Troubleshooting

### Application stuck in "Progressing"

```bash
# Check application status
argocd app get cdc-kafka

# View sync details
kubectl describe application cdc-kafka -n argocd
```

### Pods not starting

```bash
# Check pod status
kubectl get pods -n data-platform

# View pod logs
kubectl logs <pod-name> -n data-platform

# Describe pod for events
kubectl describe pod <pod-name> -n data-platform
```

### Reset admin password

```bash
# Generate new password
kubectl -n argocd patch secret argocd-secret \
  -p '{"stringData": {"admin.password": "<bcrypt-hash>","admin.passwordMtime": "'$(date +%FT%T%Z)'"}}'
```

## References

- [ArgoCD Documentation](https://argo-cd.readthedocs.io/)
- [ArgoCD Getting Started](https://argo-cd.readthedocs.io/en/stable/getting_started/)
- [ArgoCD Best Practices](https://argo-cd.readthedocs.io/en/stable/user-guide/best_practices/)
