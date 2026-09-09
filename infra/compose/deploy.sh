#!/usr/bin/env bash
# EC2 /opt/team1-app/deploy.sh. GitHub Actions 가 SSM RunCommand 로 호출:
#   bash /opt/team1-app/deploy.sh <image-tag>
# 롤백: 이미지·설정이 함께 되돌아가도록 SHA 를 두 번 넘긴다
#   (bash deploy.sh <old-12자-sha> <old-full-sha>). 인자 1개면 설정은 최신 main 이 됨.
#
# 전제 (최초 1회, infra/docs/infra-runbook.md 참고):
#   - /opt/team1-app/src        : 이 리포지토리 clone
#   - /opt/team1-app/.env       : 시크릿 (chmod 600)
#   - /opt/team1-app/certs/     : origin.pem, origin.key (Cloudflare Origin CA)
#   - GHCR 패키지는 private → ec2-user 로 `docker login ghcr.io` 1회 (read:packages PAT)
set -euo pipefail

APP_DIR=/opt/team1-app
cd "$APP_DIR"

TAG="${1:?usage: deploy.sh <image-tag> [git-ref]}"
REF="${2:-origin/main}"

# 1. 리포지토리를 배포 대상 커밋으로 맞춘다 (compose / nginx 설정도 여기서 옴).
#    CD 가 2번째 인자로 배포 커밋 SHA 를 넘기면 그 커밋에 고정 → 이미지와 설정이 같은 커밋.
#    인자 없으면 origin/main HEAD (수동 호출 하위호환).
git -C "$APP_DIR/src" fetch --depth 1 origin main
if [ "$REF" != "origin/main" ]; then
  git -C "$APP_DIR/src" fetch --depth 1 origin "$REF"
fi
# 직전 fetch 결과로 고정 — shallow 에서 SHA 는 로컬 ref 가 안 생김
git -C "$APP_DIR/src" reset --hard FETCH_HEAD

# 2. 배포물을 작업 디렉터리로 동기화
rsync -a --delete "$APP_DIR/src/infra/nginx/" "$APP_DIR/nginx/"
cp "$APP_DIR/src/infra/compose/docker-compose.yml" "$APP_DIR/docker-compose.yml"
cp "$APP_DIR/src/infra/compose/backup.sh"          "$APP_DIR/backup.sh"

# deploy.sh 자신도 갱신. 실행 중 파일을 in-place 로 덮으면 bash 가 깨지므로
# 임시파일 → mv(원자적 rename, inode 교체). 새 버전은 다음 배포부터 적용.
# src 에 스크립트가 없으면(옛 SHA 로 config 롤백 등) 건너뜀 — set -e 로 죽지 않게.
if [ -f "$APP_DIR/src/infra/compose/deploy.sh" ]; then
  cp -p "$APP_DIR/deploy.sh" "$APP_DIR/deploy.sh.bak" 2>/dev/null || true
  install -m 755 "$APP_DIR/src/infra/compose/deploy.sh" "$APP_DIR/deploy.sh.new"
  mv "$APP_DIR/deploy.sh.new" "$APP_DIR/deploy.sh"
fi

# 3. 이미지 태그 갱신
if grep -q '^IMAGE_TAG=' .env; then
  sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=${TAG}/" .env
else
  echo "IMAGE_TAG=${TAG}" >> .env
fi

# 3.5 새 nginx 설정 사전 검증 — 백엔드 이미지 스왑 전에 실패하도록.
#     (bind-mount 라 파일은 이미 위에서 갱신됨. 실행 중 nginx 로 새 파일을 test.)
if docker compose ps --status running --quiet nginx | grep -q .; then
  docker compose exec -T nginx nginx -t
fi

# 4. 재기동
#    --wait: 컨테이너 내부 healthcheck 가 통과할 때까지 블록 (8080 은 호스트 미노출).
#    타임아웃은 넉넉히 — 아침 콜드스타트면 mysql init + Spring 기동에 2분 넘게 걸릴 수 있음.
echo "pulling ghcr image (tag=${TAG})..."
docker compose pull back
echo "restarting..."
if ! docker compose up -d --wait --wait-timeout 300; then
  echo "STACK UNHEALTHY — dumping logs" >&2
  docker compose ps >&2
  docker compose logs --tail=80 back >&2
  exit 1
fi

# 5. nginx 설정 반영 — bind-mount 라 파일만 바뀌면 컨테이너가 재생성되지 않음.
#    문법 검증은 3.5 에서 이미 함. 여기선 reload 만.
if docker compose ps --status running --quiet nginx | grep -q .; then
  docker compose exec -T nginx nginx -s reload
  echo "nginx reloaded"
fi

# 6. 안 쓰는 이미지 정리. -a = 태그만 있고 컨테이너가 안 쓰는 것도 대상(옛 back:<sha>).
#    until=72h = 최근 3일치는 남겨 빠른 롤백 시 재pull 없이 되돌림. 실행 중 이미지는 항상 보호됨.
docker image prune -af --filter "until=72h" >/dev/null || true
echo "deploy ok"
