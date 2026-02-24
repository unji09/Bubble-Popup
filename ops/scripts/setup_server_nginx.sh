#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   DOMAIN=pss6161.bunnect.kr APP_DIR=/home/ubuntu/S14P21A205 bash ops/scripts/setup_server_nginx.sh
# Preconditions:
#   1) Run on Ubuntu server
#   2) This repo exists at APP_DIR
#   3) APP_DIR/app.jar exists
#   4) APP_DIR/.env.prod exists

DOMAIN="${DOMAIN:-pss6161.bunnect.kr}"
APP_DIR="${APP_DIR:-/home/ubuntu/S14P21A205}"
CERTBOT_EMAIL="${CERTBOT_EMAIL:-}"

if [[ ! -f "$APP_DIR/app.jar" ]]; then
  echo "[ERROR] Missing $APP_DIR/app.jar"
  exit 1
fi

if [[ ! -f "$APP_DIR/.env.prod" ]]; then
  echo "[ERROR] Missing $APP_DIR/.env.prod"
  exit 1
fi

echo "[1/6] Install nginx + certbot"
sudo apt update
sudo apt install -y nginx certbot python3-certbot-nginx

echo "[2/6] Apply HTTP nginx config"
sudo cp "$APP_DIR/ops/nginx/$DOMAIN.http.conf" /etc/nginx/sites-available/S14P21A205
sudo ln -sf /etc/nginx/sites-available/S14P21A205 /etc/nginx/sites-enabled/S14P21A205
sudo nginx -t
sudo systemctl reload nginx

echo "[3/6] Register systemd service"
sudo cp "$APP_DIR/ops/systemd/S14P21A205.service" /etc/systemd/system/S14P21A205.service
sudo systemctl daemon-reload
sudo systemctl enable --now S14P21A205
sudo systemctl status S14P21A205 --no-pager || true

echo "[4/6] Issue TLS certificate"
if [[ -n "${CERTBOT_EMAIL}" ]]; then
  sudo certbot certonly --nginx -d "$DOMAIN" \
    --non-interactive --agree-tos --email "${CERTBOT_EMAIL}"
else
  sudo certbot certonly --nginx -d "$DOMAIN"
fi

echo "[5/6] Apply HTTPS nginx config"
sudo cp "$APP_DIR/ops/nginx/$DOMAIN.https.conf" /etc/nginx/sites-available/S14P21A205
sudo nginx -t
sudo systemctl reload nginx

echo "[6/6] Done"
echo "Check: https://$DOMAIN"
