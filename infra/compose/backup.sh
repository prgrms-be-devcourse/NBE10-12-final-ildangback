#!/usr/bin/env bash
# 야간 mysqldump — 7일 로컬 보관. 호스트 cron 이 03:50 KST 에 실행 (user-data.sh 등록).
# DLM EBS 스냅샷과 별개의 논리 백업. 복구 절차는 infra/docs/infra-runbook.md.
set -euo pipefail

APP_DIR=/opt/team1-app
cd "$APP_DIR"

# .env 에서 DB 접속정보 로드
set -a
# shellcheck disable=SC1091
. "$APP_DIR/.env"
set +a

OUT="$APP_DIR/backups/gommit-$(date +%Y%m%d-%H%M).sql.gz"

docker compose exec -T mysql \
  mysqldump -uroot -p"${MYSQL_ROOT_PASSWORD}" --single-transaction --routines --events "${DB_NAME}" \
  | gzip > "$OUT"

echo "$(date -Is) backup ok: $OUT ($(du -h "$OUT" | cut -f1))"

# 7일 초과 삭제
find "$APP_DIR/backups" -name 'gommit-*.sql.gz' -mtime +7 -delete
