#!/bin/bash

cd "$(dirname "$0")"

echo "==================================="
echo "CDC Application Cleanup"
echo "==================================="
echo ""

read -p "This will delete all resources in the data-platform namespace. Continue? (y/N) " -n 1 -r
echo ""

if [[ $REPLY =~ ^[Yy]$ ]]
then
    echo "Deleting namespace and all resources..."
    kubectl delete namespace data-platform
    echo ""
    echo "✓ Cleanup complete!"
    echo ""
    echo "Note: This also deleted all PersistentVolumes and data."
    echo "To redeploy, run: ./deploy.sh"
else
    echo "Cleanup cancelled."
fi
