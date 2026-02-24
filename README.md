# S14P21A205 Backend

## 1) Local Development (Team)

### 1-1. First setup
```bash
cp env.template .env
```

Fill required values in `.env`:
- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`

### 1-2. Run
```bash
./gradlew bootRun
```

- Swagger: `http://localhost:8080/swagger-ui/index.html`
- Stop app: `Ctrl + C`

Notes:
- This project includes Spring Boot Docker Compose integration.
- `bootRun` automatically links local infra in `compose.yaml` (postgres/redis/kafka).
- `compose.yaml` does not use fixed container names, so team members can still auto-start via `bootRun` without name conflicts.
- Do not commit `.env`.

If startup fails due to leftover containers, clean with:
```bash
docker compose -f compose.yaml down --remove-orphans
```
and run `./gradlew bootRun` again.

## 2) Production Server (Ubuntu + Nginx)

### 2-0. Fast path (automated script)
After preparing `app.jar` and `.env.prod`, run:
```bash
DOMAIN=pss6161.bunnect.kr APP_DIR=/home/ubuntu/S14P21A205 CERTBOT_EMAIL=you@example.com \
bash ops/scripts/setup_server_nginx.sh
```

### 2-1. Prepare env file
Use template:
```bash
cp env.prod.template .env.prod
```
Set real values in `.env.prod`.

### 2-2. Prepare app binary
```bash
./gradlew clean bootJar
```
Upload `build/libs/*.jar` to server as `/home/ubuntu/S14P21A205/app.jar`.

### 2-3. Register systemd service
```bash
sudo cp ops/systemd/S14P21A205.service /etc/systemd/system/S14P21A205.service
sudo systemctl daemon-reload
sudo systemctl enable --now S14P21A205
sudo systemctl status S14P21A205
```

### 2-4. Install Nginx + Certbot
```bash
sudo apt update
sudo apt install -y nginx certbot python3-certbot-nginx
```

### 2-5. Apply Nginx HTTP proxy config
```bash
sudo cp ops/nginx/pss6161.bunnect.kr.http.conf /etc/nginx/sites-available/S14P21A205
sudo ln -sf /etc/nginx/sites-available/S14P21A205 /etc/nginx/sites-enabled/S14P21A205
sudo nginx -t
sudo systemctl reload nginx
```

### 2-6. Issue certificate
```bash
sudo certbot certonly --nginx -d pss6161.bunnect.kr
```

### 2-7. Switch to HTTPS config
```bash
sudo cp ops/nginx/pss6161.bunnect.kr.https.conf /etc/nginx/sites-available/S14P21A205
sudo nginx -t
sudo systemctl reload nginx
```

### 2-8. Verify
```bash
curl -I https://pss6161.bunnect.kr
```

## 3) Google OAuth Redirect URI

Google OAuth Client must include both:
- `http://localhost:8080/login/oauth2/code/google`
- `https://pss6161.bunnect.kr/login/oauth2/code/google`

## 4) GitLab CI/CD (Self-Hosted Runner)

This repo includes `.gitlab-ci.yml` with:
- `build_jar`: build boot jar
- `bootstrap_ec2` (manual, one-time): install server packages and prepare deploy permissions
- `deploy_prod`: upload jar/env and restart `S14P21A205` service

Required GitLab CI variables:
- `SSH_PRIVATE_KEY`: private key for server SSH
- `PROD_HOST`: EC2 public DNS/IP
- `PROD_USER`: default `ubuntu`
- `PROD_APP_DIR`: default `/home/ubuntu/S14P21A205`
- `PROD_ENV_FILE_B64`: base64-encoded `.env.prod` content

Optional variable:
- `PROD_DOMAIN`: default `pss6161.bunnect.kr`
