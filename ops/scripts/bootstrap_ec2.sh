#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   sudo DEPLOY_USER=ubuntu APP_DIR=/home/ubuntu/S14P21A205 DOMAIN=pss6161.bunnect.kr \
#   bash ops/scripts/bootstrap_ec2.sh
#
# What this script does (idempotent):
# 1) Install runtime packages (java/nginx/certbot)
# 2) Create app directory
# 3) Add sudoers rule for non-interactive service restart from CI

if [[ "${EUID}" -ne 0 ]]; then
  echo "[ERROR] Please run as root (use sudo)." >&2
  exit 1
fi

DEPLOY_USER="${DEPLOY_USER:-ubuntu}"
APP_DIR="${APP_DIR:-/home/${DEPLOY_USER}/S14P21A205}"
DOMAIN="${DOMAIN:-pss6161.bunnect.kr}"
SYSTEMCTL_BIN="$(command -v systemctl || true)"

if [[ -z "${SYSTEMCTL_BIN}" ]]; then
  echo "[ERROR] systemctl not found. This script expects Ubuntu with systemd." >&2
  exit 1
fi

echo "[1/4] Install packages"
apt update
apt install -y openjdk-17-jre-headless nginx certbot python3-certbot-nginx

echo "[2/4] Prepare app directory: ${APP_DIR}"
install -d -m 755 "${APP_DIR}"
chown -R "${DEPLOY_USER}:${DEPLOY_USER}" "${APP_DIR}"

echo "[3/4] Configure sudoers for deploy user: ${DEPLOY_USER}"
cat > "/etc/sudoers.d/S14P21A205-deploy-${DEPLOY_USER}" <<EOF
${DEPLOY_USER} ALL=(root) NOPASSWD: ${SYSTEMCTL_BIN} daemon-reload
${DEPLOY_USER} ALL=(root) NOPASSWD: ${SYSTEMCTL_BIN} restart S14P21A205
${DEPLOY_USER} ALL=(root) NOPASSWD: ${SYSTEMCTL_BIN} start S14P21A205
${DEPLOY_USER} ALL=(root) NOPASSWD: ${SYSTEMCTL_BIN} stop S14P21A205
${DEPLOY_USER} ALL=(root) NOPASSWD: ${SYSTEMCTL_BIN} status S14P21A205
${DEPLOY_USER} ALL=(root) NOPASSWD: ${SYSTEMCTL_BIN} enable S14P21A205
EOF
chmod 440 "/etc/sudoers.d/S14P21A205-deploy-${DEPLOY_USER}"

echo "[4/4] Done"
echo "Domain: ${DOMAIN}"
echo "Next:"
echo "  1) Upload app.jar to ${APP_DIR}/app.jar"
echo "  2) Upload .env.prod to ${APP_DIR}/.env.prod"
echo "  3) Run: bash ${APP_DIR}/ops/scripts/setup_server_nginx.sh"
