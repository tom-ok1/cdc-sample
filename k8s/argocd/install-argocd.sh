#!/bin/bash
set -e

echo "===================================="
echo "Installing ArgoCD"
echo "===================================="
echo ""

# Create ArgoCD namespace
echo "Creating argocd namespace..."
kubectl create namespace argocd || true
echo ""

# Install ArgoCD
echo "Installing ArgoCD..."
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
echo ""

# Wait for ArgoCD to be ready
echo "Waiting for ArgoCD to be ready..."
kubectl wait --for=condition=available deployment/argocd-server -n argocd --timeout=300s
kubectl wait --for=condition=available deployment/argocd-repo-server -n argocd --timeout=300s
kubectl wait --for=condition=available deployment/argocd-dex-server -n argocd --timeout=300s
kubectl wait --for=condition=available deployment/argocd-redis -n argocd --timeout=300s
echo "✓ ArgoCD is ready"
echo ""

# Patch ArgoCD server to use NodePort or port-forward
echo "Patching ArgoCD server service..."
kubectl patch svc argocd-server -n argocd -p '{"spec": {"type": "NodePort"}}'
echo ""

# Get initial admin password
echo "===================================="
echo "ArgoCD Installation Complete!"
echo "===================================="
echo ""
echo "Initial admin password:"
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d
echo ""
echo ""
echo "To access ArgoCD UI, run in a separate terminal:"
echo "  kubectl port-forward svc/argocd-server -n argocd 8080:443"
echo ""
echo "Then open: https://localhost:8080"
echo "  Username: admin"
echo "  Password: (shown above)"
echo ""
echo "Or install ArgoCD CLI and login:"
echo "  brew install argocd"
echo "  argocd login localhost:8080"
echo "===================================="
