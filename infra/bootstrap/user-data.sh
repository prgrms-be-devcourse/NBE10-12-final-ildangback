#!/bin/bash
# Amazon Linux 2023 (arm64) 첫 부팅 프로비저닝.
# cloud-init 이 root 로 1회 실행. 배포물(compose/.env/nginx/certs)은 CD 가 따로 배치.
set -euxo pipefail

APP_DIR=/opt/team1-app

# ---- swap 2GiB (2GB RAM 박스, ffmpeg 인코딩 대비 안전망) --------------------
# dd 유지: AL2023 루트 파일시스템은 XFS 라 `fallocate /swapfile` 은 unwritten 익스텐트가 되어
# `swapon` 이 "swapfile has holes" 로 거부한다 (fallocate 는 ext4 에서만 통함).
if ! swapon --show | grep -q /swapfile; then
  dd if=/dev/zero of=/swapfile bs=1M count=2048
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
  sysctl -w vm.swappiness=10
  echo 'vm.swappiness=10' > /etc/sysctl.d/99-swappiness.conf
fi

# ---- 패키지 --------------------------------------------------------------
dnf -y install docker git rsync cronie
systemctl enable --now docker
systemctl enable --now crond
usermod -aG docker ec2-user

# ---- Docker Compose 플러그인 (arm64) --------------------------------------
COMPOSE_VERSION=v2.32.4
mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-aarch64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# ---- SSM 에이전트 (AL2023 기본 포함, 실행 보장) --------------------------
systemctl enable --now amazon-ssm-agent

# ---- SSH 예외 접속 (Q14 추가결정) --------------------------------------
# 배포·운영은 SSM 이 원칙. IAM 을 못 나눠 SSM 을 못 쓰는 운영자 1인만 SSH 를 쓴다.
# SG 22 개방은 var.ssh_allowed_cidrs 로 하고, 공개키 등록은 이 부트스트랩이 아니라
# 런북 "SSH 예외 접속" 절차(SSM 으로 authorized_keys 에 append)로 한다.
# 부트스트랩에 키를 박지 않는 이유: user_data_replace_on_change=false 라 현재 인스턴스엔
# 어차피 반영 안 되고, 리포에 공개키가 남는다. 인스턴스 재빌드 시 런북 체크리스트로 재등록.

# ---- 앱 디렉터리 ---------------------------------------------------------
install -d -o ec2-user -g ec2-user "${APP_DIR}"
install -d -o ec2-user -g ec2-user "${APP_DIR}/certs"       # Cloudflare Origin CA 인증서
install -d -o ec2-user -g ec2-user "${APP_DIR}/backups"     # mysqldump 출력
install -d -o ec2-user -g ec2-user "${APP_DIR}/nginx"       # deploy.sh 가 리포에서 동기화
# src/(리포 clone), .env, certs/*, 최초 docker login 은 runbook 의 "최초 1회" 절차 참고.

# ---- 야간 mysqldump cron (04:20 KST) -----------------------------------
# 03:30 자동 start + 04:00 배치 이후. dockerd/mysql health 올라올 시간 확보.
cat > /etc/cron.d/team1-db-backup <<'CRON'
CRON_TZ=Asia/Seoul
20 4 * * * ec2-user /bin/bash /opt/team1-app/backup.sh >> /opt/team1-app/backups/backup.log 2>&1
CRON
chmod 644 /etc/cron.d/team1-db-backup

# ---- 컨테이너 자동 복귀 (18:00 정지 → 아침 start 시) --------------------
# compose 서비스는 restart: unless-stopped 라 dockerd 만 부팅에 뜨면 됨(위에서 enable).
# 배포물이 있으면 부팅 때 up 을 한 번 보장하는 oneshot 유닛.
cat > /etc/systemd/system/team1-app.service <<'UNIT'
[Unit]
Description=gommit docker compose stack
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/opt/team1-app
ExecStart=/usr/bin/docker compose up -d
ExecStop=/usr/bin/docker compose down
User=ec2-user

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable team1-app.service

echo "bootstrap done"
