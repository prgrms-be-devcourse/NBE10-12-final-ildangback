#!/usr/bin/env bash
# EC2 /opt/team1-app/deploy.sh. GitHub Actions 가 SSM RunCommand 로 호출:
#   bash /opt/team1-app/deploy.sh <image-tag>
# 롤백: 이전 태그로 다시 호출 (bash deploy.sh <old-sha>).
#
# 전제 (최초 1회, infra/docs/infra-runbook.md 참고):
#   - /opt/team1-app/src        : 이 리포지토리 clone
#   - /opt/team1-app/.env       : 시크릿 (chmod 600)
#   - /opt/team1-app/certs/     : origin.pem, origin.key (Cloudflare Origin CA)
#   - GHCR 패키지가 private 이면 `docker login ghcr.io` 1회
set -euo pipefail

APP_DIR=/opt/team1-app
cd "$APP_DIR"

TAG="${1:?usage: deploy.sh <image-tag>}"

# 1. 리포지토리 최신화 (compose / nginx 설정도 여기서 옴)
git -C "$APP_DIR/src" fetch --depth 1 origin main
git -C "$APP_DIR/src" reset --hard origin/main

# 2. 배포물을 작업 디렉터리로 동기화
rsync -a --delete "$APP_DIR/src/infra/nginx/" "$APP_DIR/nginx/"
cp "$APP_DIR/src/infra/compose/docker-compose.yml" "$APP_DIR/docker-compose.yml"
cp "$APP_DIR/src/infra/compose/backup.sh"          "$APP_DIR/backup.sh"

# 3. 이미지 태그 갱신
if grep -q '^IMAGE_TAG=' .env; then
  sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=${TAG}/" .env
else
  echo "IMAGE_TAG=${TAG}" >> .env
fi

# 4. 재기동
echo "pulling ghcr image (tag=${TAG})..."
docker compose pull back
echo "restarting..."
docker compose up -d

# 5. 헬스 확인
echo "waiting for health..."
for i in $(seq 1 40); do
  if curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo "healthy after $((i * 3))s"
    docker image prune -f >/dev/null || true
    exit 0
  fi
  sleep 3
done

echo "HEALTH CHECK FAILED — dumping logs" >&2
docker compose logs --tail=80 back >&2
exit 1
