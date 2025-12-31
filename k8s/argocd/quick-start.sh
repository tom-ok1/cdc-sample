#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "===================================="
echo "ArgoCD Quick Start for CDC Platform"
echo "===================================="
echo ""

# Check if ArgoCD is installed
if ! kubectl get namespace argocd &> /dev/null; then
    echo "ArgoCD not found. Installing ArgoCD..."
    ./install-argocd.sh
    echo ""
    echo "Waiting for ArgoCD to be fully ready..."
    sleep 10
else
    echo "✓ ArgoCD is already installed"
    echo ""
fi

# Deploy using App of Apps pattern
echo "Deploying CDC Platform using App of Apps pattern..."
kubectl apply -f app-of-apps.yaml
echo ""

echo "Waiting for applications to be created..."
sleep 5
echo ""

echo "===================================="
echo "ArgoCD Deployment Started!"
echo "===================================="
echo ""

echo "Access ArgoCD UI:"
echo "  1. Run in a separate terminal:"
echo "     kubectl port-forward svc/argocd-server -n argocd 8080:443"
echo ""
echo "  2. Open: https://localhost:8080"
echo ""
echo "  3. Login with:"
echo "     Username: admin"
echo "     Password: (run command below)"
echo ""

echo "Get admin password:"
echo "  kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d && echo"
echo ""

echo "Monitor deployment:"
echo "  kubectl get applications -n argocd"
echo "  kubectl get pods -n data-platform -w"
echo ""
echo "===================================="
