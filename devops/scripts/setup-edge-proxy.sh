#!/usr/bin/env bash
#
# Runs on the VM. Installs nginx + certbot and configures an edge reverse proxy:
#   80  -> 301 redirect to 443
#   443 -> reverse proxy to http://127.0.0.1:${APP_NODEPORT}  (k3s NodePort of the app)
#
# TLS cert: Let's Encrypt via sslip.io (free, no domain registration needed).
# The hostname derives from the public IP, e.g. 89.169.134.38 -> 89.169.134.38.sslip.io.
#
# Idempotent.
#
set -euo pipefail

APP_NODEPORT="${APP_NODEPORT:-30080}"
LE_EMAIL="${LE_EMAIL:-}"  # optional, empty -> register without email

log() { echo "[$(date +%H:%M:%S)] $*"; }

if [[ $EUID -ne 0 ]]; then
  echo "Must run as root (use sudo)." >&2
  exit 1
fi

PUBLIC_IP="$(
  curl -fsS --max-time 5 http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null \
    || curl -fsS --max-time 5 https://ipinfo.io/ip 2>/dev/null \
    || hostname -I | awk '{print $1}'
)"
if [[ -z "${PUBLIC_IP}" ]]; then
  echo "Could not determine public IP." >&2
  exit 1
fi
HOSTNAME_FQDN="${PUBLIC_IP}.sslip.io"
log "Public IP: ${PUBLIC_IP}"
log "Edge hostname: ${HOSTNAME_FQDN}"

export DEBIAN_FRONTEND=noninteractive
log "Installing nginx + certbot..."
apt-get update -y
apt-get install -y nginx certbot python3-certbot-nginx

systemctl enable --now nginx

SITE=/etc/nginx/sites-available/dealer
cat >"${SITE}" <<EOF
# Managed by scripts/setup-edge-proxy.sh
server {
    listen 80;
    listen [::]:80;
    # Only the FQDN here - raw IP falls through to the default_server in
    # sites-enabled/redirect-to-fqdn and is redirected to https://${HOSTNAME_FQDN}.
    server_name ${HOSTNAME_FQDN};

    # ACME http-01 challenge
    location /.well-known/acme-challenge/ {
        root /var/www/html;
    }

    # Before TLS is issued, proxy so the app is reachable on :80.
    # certbot --nginx will add an HTTPS server block and rewrite this
    # one to redirect to https://\$host\$request_uri.
    location / {
        proxy_pass         http://127.0.0.1:${APP_NODEPORT};
        proxy_http_version 1.1;
        proxy_set_header   Host              \$host;
        proxy_set_header   X-Real-IP         \$remote_addr;
        proxy_set_header   X-Forwarded-For   \$proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto \$scheme;
        proxy_set_header   Upgrade           \$http_upgrade;
        proxy_set_header   Connection        \$connection_upgrade;
        proxy_read_timeout 60s;
        client_max_body_size 25m;
    }
}
EOF

# Map for websocket-safe Connection header
cat >/etc/nginx/conf.d/upgrade-map.conf <<'EOF'
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}
EOF

# Default-server redirect: any request by raw IP (or any other Host) -> sslip FQDN over HTTPS.
# This ensures http://<ip>/ and https://<ip>/ both work (the latter with a cert-name
# warning on the first hop, but the redirect itself is followed by browsers/curl -L).
cat >/etc/nginx/sites-available/redirect-to-fqdn <<EOF
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;

    location /.well-known/acme-challenge/ {
        root /var/www/html;
    }
    location / {
        return 301 https://${HOSTNAME_FQDN}\$request_uri;
    }
}

server {
    listen 443 ssl default_server;
    listen [::]:443 ssl default_server;
    server_name _;

    ssl_certificate     /etc/letsencrypt/live/${HOSTNAME_FQDN}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/${HOSTNAME_FQDN}/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;

    return 301 https://${HOSTNAME_FQDN}\$request_uri;
}
EOF

ln -sf "${SITE}" /etc/nginx/sites-enabled/dealer
rm -f /etc/nginx/sites-enabled/default

nginx -t
systemctl reload nginx

log "Requesting/renewing Let's Encrypt cert for ${HOSTNAME_FQDN}..."
CERTBOT_ARGS=(--nginx -d "${HOSTNAME_FQDN}" --non-interactive --agree-tos --redirect --keep-until-expiring)
if [[ -n "${LE_EMAIL}" ]]; then
  CERTBOT_ARGS+=(-m "${LE_EMAIL}")
else
  CERTBOT_ARGS+=(--register-unsafely-without-email)
fi

certbot "${CERTBOT_ARGS[@]}"

# Now that the cert exists, enable the catch-all IP -> FQDN redirect on 80/443.
ln -sf /etc/nginx/sites-available/redirect-to-fqdn /etc/nginx/sites-enabled/redirect-to-fqdn

# Ensure auto-renew
systemctl enable --now certbot.timer || true

nginx -t
systemctl reload nginx

log "Done."
echo "============================================================"
echo "  HTTPS:  https://${HOSTNAME_FQDN}/"
echo "  HTTP :  http://${HOSTNAME_FQDN}/      (-> redirects to HTTPS)"
echo "  IP   :  http://${PUBLIC_IP}/          (-> redirects to HTTPS ${HOSTNAME_FQDN})"
echo "  Cert :  Let's Encrypt, auto-renew via certbot.timer"
echo "============================================================"
