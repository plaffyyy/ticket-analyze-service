#!/usr/bin/env bash
#
# On the VM (as root): installs k3s and base packages. Optionally installs Helm +
# kube-prometheus-stack if a values file is present on this machine.
#
# Default workflow: copy only this script to the server — no k8s/ on the VM.
# Then install monitoring from your laptop with helm and -f path/to/values.yaml.
#
# Optional: set VALUES_FILE=/path/on/server/to/values.yaml to install monitoring here.
#
# Idempotent. Safe to re-run.
#
# Usage (on the target VM, as user with sudo):
#   sudo bash install-k8s.sh
#
set -euo pipefail

log() { echo "[$(date +%H:%M:%S)] $*"; }

if [[ $EUID -ne 0 ]]; then
  echo "Must run as root (use sudo)." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VALUES_FILE="${VALUES_FILE:-$SCRIPT_DIR/../k8s/monitoring/values-kube-prometheus-stack.yaml}"
MONITORING_NS="monitoring"
GRAFANA_NODEPORT="${GRAFANA_NODEPORT:-30030}"

PUBLIC_IP="$(
  curl -fsS --max-time 5 http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null \
    || curl -fsS --max-time 5 https://ipinfo.io/ip 2>/dev/null \
    || true
)"
if [[ -z "${PUBLIC_IP}" ]]; then
  log "WARN: could not detect public IPv4; k3s API cert will not include --tls-san for external IP."
  log "WARN: fix kubeconfig server URL manually or re-run after setting PUBLIC_IP."
fi

# ---------- 1. Base packages ----------
log "Installing base packages..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y curl ca-certificates gnupg apt-transport-https jq tar util-linux

# ---------- 1b. Swap (OOM / SSH resilience on small nodes) ----------
SWAP_FILE="${SWAP_FILE:-/swapfile}"
SWAP_SIZE_GB="${SWAP_SIZE_GB:-4}"
if ! swapon --show 2>/dev/null | grep -qF "$SWAP_FILE"; then
  if [[ ! -f "$SWAP_FILE" ]]; then
    log "Creating ${SWAP_SIZE_GB}G swap at ${SWAP_FILE}..."
    if ! fallocate -l "${SWAP_SIZE_GB}G" "$SWAP_FILE" 2>/dev/null; then
      log "fallocate failed; using dd (slow)..."
      dd if=/dev/zero of="$SWAP_FILE" bs=1M "count=$((SWAP_SIZE_GB * 1024))" status=progress
    fi
    chmod 600 "$SWAP_FILE"
    mkswap "$SWAP_FILE"
  fi
  swapon "$SWAP_FILE"
fi
if ! grep -qF "$SWAP_FILE" /etc/fstab; then
  echo "$SWAP_FILE none swap sw 0 0" >>/etc/fstab
fi
install -d -m 755 /etc/sysctl.d
cat >/etc/sysctl.d/99-k8s-node-swap.conf <<'EOF'
# Prefer RAM; allow swap under pressure (avoids total lockup on 4G nodes).
vm.swappiness=10
EOF
sysctl --system >/dev/null || sysctl -p /etc/sysctl.d/99-k8s-node-swap.conf >/dev/null || true
log "Swap: $(swapon --show | tr '\n' ' ')"

# ---------- 2. k3s ----------
if ! command -v k3s >/dev/null 2>&1; then
  log "Installing k3s..."
  K3S_EXEC="--write-kubeconfig-mode=644 --disable=traefik"
  if [[ -n "${PUBLIC_IP}" ]]; then
    K3S_EXEC+=" --tls-san=${PUBLIC_IP}"
    log "k3s API SAN includes public IP: ${PUBLIC_IP}"
  fi
  curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="${K3S_EXEC}" sh -
else
  log "k3s already installed."
fi

log "Waiting for k3s node to become Ready..."
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
for _ in {1..60}; do
  if k3s kubectl get nodes 2>/dev/null | grep -q ' Ready '; then
    break
  fi
  sleep 5
done
k3s kubectl get nodes

TARGET_USER="${SUDO_USER:-ubuntu}"
TARGET_HOME="$(getent passwd "$TARGET_USER" | cut -d: -f6)"
if [[ -n "$TARGET_HOME" && -d "$TARGET_HOME" ]]; then
  install -d -m 700 -o "$TARGET_USER" -g "$TARGET_USER" "$TARGET_HOME/.kube"
  install -m 600 -o "$TARGET_USER" -g "$TARGET_USER" /etc/rancher/k3s/k3s.yaml "$TARGET_HOME/.kube/config"
  if [[ -n "${PUBLIC_IP}" && -f "$TARGET_HOME/.kube/config" ]]; then
    sed -i.bak "s#https://127.0.0.1:6443#https://${PUBLIC_IP}:6443#g" "$TARGET_HOME/.kube/config" || true
    rm -f "$TARGET_HOME/.kube/config.bak" || true
    log "Patched ${TARGET_HOME}/.kube/config server URL -> https://${PUBLIC_IP}:6443"
  fi
fi

if ! command -v kubectl >/dev/null 2>&1; then
  ln -sf /usr/local/bin/k3s /usr/local/bin/kubectl
fi
export PATH=/usr/local/bin:$PATH

# ---------- 2b. Kubelet resource reservations (single-node ~4G RAM / 2 vCPU) ----------
# Leaves headroom for k3s, containerd, sshd; tune if you resize the VM.
K3S_CONF_DIR="/etc/rancher/k3s"
K3S_CONF="${K3S_CONF_DIR}/config.yaml"
RESERVED_MARKER="# kubelet-reserved (install-k8s.sh)"
if [[ ! -f "$K3S_CONF" ]] || ! grep -qF "$RESERVED_MARKER" "$K3S_CONF" 2>/dev/null; then
  mkdir -p "$K3S_CONF_DIR"
  {
    echo "$RESERVED_MARKER"
    cat <<'EOF'
kubelet-arg:
  - "kube-reserved=cpu=150m,memory=384Mi"
  - "system-reserved=cpu=100m,memory=384Mi"
EOF
  } >>"$K3S_CONF"
  log "Appended kubelet kube-reserved/system-reserved to ${K3S_CONF}"
  if systemctl is-active --quiet k3s 2>/dev/null; then
    log "Restarting k3s to apply kubelet configuration..."
    systemctl restart k3s
    export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
    for _ in {1..60}; do
      if k3s kubectl get nodes 2>/dev/null | grep -q ' Ready '; then
        break
      fi
      sleep 5
    done
    k3s kubectl get nodes
  fi
fi

# ---------- 3. Monitoring (optional, only if values file exists on this host) ----------
if [[ -f "$VALUES_FILE" ]]; then
  if ! command -v helm >/dev/null 2>&1; then
    log "Installing Helm..."
    curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
  else
    log "Helm already installed."
  fi

  log "VALUES_FILE found on server (${VALUES_FILE}): installing kube-prometheus-stack..."
  helm repo add prometheus-community https://prometheus-community.github.io/helm-charts 2>/dev/null || true
  helm repo update

  helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
    --namespace "$MONITORING_NS" \
    --create-namespace \
    -f "$VALUES_FILE" \
    --wait --timeout 20m

  log "Waiting for Grafana..."
  kubectl -n "$MONITORING_NS" rollout status deploy/monitoring-grafana --timeout=5m || true

  SUMMARY_IP="${PUBLIC_IP:-<VM-public-ip>}"
  echo
  echo "============================================================"
  echo "  k3s + monitoring (Prometheus + Grafana + Alertmanager)"
  echo "------------------------------------------------------------"
  kubectl get nodes
  echo
  echo "  kubectl -n ${MONITORING_NS} get pods"
  kubectl -n "$MONITORING_NS" get pods
  echo
  echo "  Grafana:  http://${SUMMARY_IP}:${GRAFANA_NODEPORT}"
  echo "  Login:    admin / (see grafana.adminPassword in values file)"
  echo "============================================================"
else
  log "No values file on server at ${VALUES_FILE} — skipping Helm and monitoring."
  log "Install kube-prometheus-stack from your laptop (see scripts/README.md)."
  SUMMARY_IP="${PUBLIC_IP:-<VM-public-ip>}"
  echo
  echo "============================================================"
  echo "  k3s is ready"
  echo "------------------------------------------------------------"
  kubectl get nodes
  echo
  echo "  API: https://${SUMMARY_IP}:6443"
  echo "  Copy ~/.kube/config to your laptop and run helm there with:"
  echo "    -f k8s/monitoring/values-kube-prometheus-stack.yaml"
  echo "============================================================"
fi
