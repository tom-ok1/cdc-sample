#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "===================================="
echo "Restarting All ArgoCD Applications"
echo "===================================="
echo ""

# Run cleanup
echo "Step 1: Cleaning up existing applications..."
./cleanup-apps.sh

echo ""
echo "Waiting 15 seconds for resources to be fully removed..."
sleep 15
echo ""

# Run deployment
echo "Step 2: Deploying applications..."
./deploy-apps.sh

echo ""
echo "===================================="
echo "Restart Complete!"
echo "===================================="
echo ""
echo "Monitor the deployment:"
echo "  watch kubectl get applications -n argocd"
echo "  watch kubectl get pods -n cdc-platform"
echo "===================================="
