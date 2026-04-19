#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

terraform init -input=false
terraform apply -auto-approve

echo
echo "=== Outputs ==="
terraform output
