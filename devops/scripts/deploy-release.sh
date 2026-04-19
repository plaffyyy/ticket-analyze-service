#!/usr/bin/env bash
#
# Blue-green release for ticket-analyze-service.
#
#   IMAGE_REPO=ghcr.io/org/repo scripts/deploy-release.sh <image-tag>
#
# Steps:
#   1. Detects which color is currently "live" (selected by the main Service).
#   2. Scales the OTHER color to 3 replicas and points it at the new image tag.
#   3. Waits for the new color to become Ready.
#   4. Flips the main Service selector to the new color.
#   5. Scales the old color down to 0.
#
set -euo pipefail

NS=dealer
SVC=ticket-analyze-service
IMAGE_REPO="${IMAGE_REPO:-}"
TAG="${1:-}"

if [[ -z "$IMAGE_REPO" ]]; then
  echo "Set IMAGE_REPO to your container registry path (no secrets in this script)." >&2
  echo "Example: export IMAGE_REPO=ghcr.io/myorg/ticket-analyze-service" >&2
  exit 1
fi

if [[ -z "$TAG" ]]; then
  echo "Usage: IMAGE_REPO=... $0 <image-tag>" >&2
  exit 1
fi

IMAGE="${IMAGE_REPO}:${TAG}"

log() { echo "[$(date +%H:%M:%S)] $*"; }

CURRENT=$(kubectl -n "$NS" get svc "$SVC" -o jsonpath='{.spec.selector.color}')
if [[ "$CURRENT" == "blue" ]]; then
  NEXT=green
else
  NEXT=blue
fi
DEPLOY_NEXT="${SVC}-${NEXT}"
DEPLOY_CURR="${SVC}-${CURRENT}"

log "Live color=${CURRENT}. Deploying new image to ${NEXT}: ${IMAGE}"

kubectl -n "$NS" set image "deployment/${DEPLOY_NEXT}" "app=${IMAGE}"
kubectl -n "$NS" scale "deployment/${DEPLOY_NEXT}" --replicas=3

log "Waiting for ${NEXT} rollout..."
kubectl -n "$NS" rollout status "deployment/${DEPLOY_NEXT}" --timeout=5m

log "Smoke-test ${NEXT} via ClusterIP..."
kubectl -n "$NS" run curl-smoke --rm -i --restart=Never --image=curlimages/curl:8.8.0 -- \
  curl -sSf "http://${SVC}-${NEXT}.${NS}.svc.cluster.local/actuator/health" || {
    log "Smoke test FAILED. Aborting switch; rolling back ${NEXT} to 0 replicas."
    kubectl -n "$NS" scale "deployment/${DEPLOY_NEXT}" --replicas=0
    exit 1
  }

log "Flipping Service ${SVC} selector: ${CURRENT} -> ${NEXT}"
kubectl -n "$NS" patch svc "$SVC" --type merge \
  -p "{\"spec\":{\"selector\":{\"app.kubernetes.io/name\":\"${SVC}\",\"color\":\"${NEXT}\"}}}"

log "Draining ${CURRENT}..."
kubectl -n "$NS" scale "deployment/${DEPLOY_CURR}" --replicas=0

log "Done. Live color=${NEXT}, image=${IMAGE}"
kubectl -n "$NS" get deploy,svc
