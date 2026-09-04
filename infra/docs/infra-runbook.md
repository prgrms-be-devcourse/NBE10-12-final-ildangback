# 인프라 운영 절차 (Runbook)

> 설계 배경은 `infra/docs/infra-design.md`. 이 문서는 "무엇을 어떻게 실행하나".
> 도메인은 `go-mmit.site` 로 표기 — 임시 placeholder, 구매 시 실제 값으로 치환.

---

## 0. 사전 준비 (계정·크레덴셜)

| 항목 | 발급처 | 쓰이는 곳 |
|---|---|---|
| AWS IAM 사용자 (인프라 담당자) | 강의 AWS 계정 | `terraform apply`, 수동 조작 |
| Cloudflare 계정 + 존 | cloudflare.com | DNS, Pages, Origin CA |
| Cloudflare API 토큰 (Zone.DNS 편집) | CF 대시보드 → My Profile → API Tokens | `terraform.tfvars` |
| 도메인 | Cloudflare Registrar (권장) | — |
| GitHub 리포 관리자 권한 | — | Actions Secrets 등록 |

---

## 1. 최초 배포 (한 번만)

### 1-1. 도메인 + Cloudflare

1. Cloudflare Registrar 에서 `go-mmit.*` 구매 (첫해 가격 확인 — `infra/docs/infra-design.md` Q23).
   Cloudflare 에서 사면 네임서버는 자동.
2. 대시보드에서 **존 ID** 확인 (Overview 우측 하단).
3. SSL/TLS → **Full (strict)**.
4. SSL/TLS → Origin Server → **Create Certificate** →
   `*.go-mmit.site`, `go-mmit.site` → PEM 저장 → 나중에 EC2 `certs/` 에 배치.

### 1-2. Terraform

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars   # cloudflare_zone_id, cloudflare_api_token 채움
terraform init
terraform apply
```

출력값 기록:
- `instance_id`      → GitHub Secret `EC2_INSTANCE_ID`
- `deploy_role_arn`  → GitHub Secret `AWS_DEPLOY_ROLE_ARN`

> ⚠️ `apply` 직후 `terraform.tfstate` 를 팀 드라이브에 업로드 (시크릿 파일 취급, git 아님).
> 이후에도 `apply` 할 때마다 갱신본 업로드. **인프라 담당자 1인만 apply.**

### 1-3. GitHub Actions Secrets

리포 Settings → Secrets and variables → Actions:

| Secret | 값 |
|---|---|
| `EC2_INSTANCE_ID` | terraform output |
| `AWS_DEPLOY_ROLE_ARN` | terraform output |

`GITHUB_TOKEN` 은 자동 제공. GHCR 패키지는 **public 으로 전환** 권장(Settings → Packages →
change visibility) → EC2 에서 `docker login` 불필요. private 로 두려면 1-4 에서 로그인.

### 1-4. EC2 최초 셋업 (SSM 세션으로)

```bash
aws ssm start-session --target <instance-id> --region ap-northeast-2
sudo su - ec2-user
cd /opt/team1-app

# 리포 clone (private 이면 read-only deploy key 등록 후)
git clone --depth 1 https://github.com/prgrms-be-devcourse/NBE10-12-final-ildangback.git src

# Origin CA 인증서 배치
vi certs/origin.pem      # 1-1 에서 만든 인증서 본문
vi certs/origin.key      # 개인 키
chmod 600 certs/origin.key

# .env 작성 (템플릿: src/infra/compose/.env.example)
cp src/infra/compose/.env.example .env
vi .env                  # DB 비번, JWT_SECRET_KEY, Cloudinary, CORS 등
chmod 600 .env

# (GHCR private 인 경우만) 로그인 — config.json 에 저장돼 유지됨
echo <GHCR_PAT> | docker login ghcr.io -u <github-user> --password-stdin

# 최초 기동
cp src/infra/compose/docker-compose.yml .
cp src/infra/compose/backup.sh .
rsync -a src/infra/nginx/ nginx/
docker compose up -d
docker compose ps
```

### 1-5. Cloudflare Pages (프론트)

1. Pages → Create → Connect to Git → 리포 선택.
2. Build: root `front`, command `pnpm build`, output `dist`, Node 20+.
3. 환경변수: `VITE_API_BASE_URL=https://api.go-mmit.site` (프론트 코드 기준으로 조정).
4. Custom domains → `go-mmit.site` 추가 → Cloudflare 가 apex 레코드 자동 생성.

### 1-6. 확인

```
curl -I https://api.go-mmit.site/actuator/health   # 200
open https://go-mmit.site                          # 프론트
```

---

## 2. 일상 배포

`main` 에 백엔드/인프라 변경 머지 → `deploy.yml` 자동 실행:
빌드 → GHCR push (`:<sha>` + `:latest`) → SSM 이 `deploy.sh <sha>` 실행 → 헬스체크.

수동 실행: Actions → Deploy Backend → Run workflow.

**배포 금지 시간대**: 03:30~04:30 (자동 start + 배치), 17:45~18:15 (루트 정지). 런북 상단 참고.

---

## 3. 롤백

```bash
aws ssm start-session --target <instance-id>
sudo -u ec2-user bash /opt/team1-app/deploy.sh <이전-커밋-SHA-12자>
```

이전 이미지는 GHCR 에 `back:<sha>` 로 남아 있다. GHCR Packages 에서 태그 목록 확인.

---

## 4. DB 백업 / 복구

- **자동**: 호스트 cron 이 매일 03:50 `backup.sh` → `/opt/team1-app/backups/gommit-YYYYMMDD-HHMM.sql.gz`, 7일 보관.
- **자동 2차**: DLM 이 매일 18:30 루트 EBS 볼륨 스냅샷, 7일 보관.

### 논리 복구 (mysqldump 에서)

```bash
cd /opt/team1-app
gunzip -c backups/gommit-YYYYMMDD-HHMM.sql.gz | \
  docker compose exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" gommit
docker compose restart back
```

### 볼륨 복구 (EBS 스냅샷에서)

인스턴스 교체가 필요한 수준의 사고일 때. EC2 콘솔에서 스냅샷 → 볼륨 생성 → 루트 교체,
또는 `terraform taint aws_instance.app && terraform apply` 후 논리 복구.

> **규칙**: DB 를 초기화(wipe)하면 미디어 스토리지도 함께 정리한다 (Cloudinary 폴더 / 로컬 dir).
> 안 그러면 고아 파일이 쌓인다. — `infra/docs/infra-design.md`, 미디어 설계 메모.

---

## 5. 인스턴스 정지/기동

- **정지**: 매일 18:00 루트 계정 (우리가 막을 수 없음).
- **기동**: 매일 03:30 EventBridge Scheduler (`team1-ec2-start-0330`). 이미 running 이면 무시.
- **수동 기동**: `aws ec2 start-instances --instance-ids <id> --region ap-northeast-2`
- 기동 후 컨테이너는 `systemd team1-app.service` + `restart: unless-stopped` 로 자동 복귀.
  안 뜨면: `sudo systemctl start team1-app` 또는 `cd /opt/team1-app && docker compose up -d`.
- EIP 덕분에 정지/기동 후에도 공인 IP 는 그대로 → DNS 수정 불필요.

---

## 6. 셸 접속 / 로그

```bash
aws ssm start-session --target <instance-id> --region ap-northeast-2

cd /opt/team1-app
docker compose ps
docker compose logs -f --tail=100 back
docker compose logs --tail=50 nginx
docker stats --no-stream          # 메모리 압박 확인 (2GB 박스)
free -h; swapon --show
```

---

## 7. 트러블슈팅

| 증상 | 확인 |
|---|---|
| 배포 실패 (health check failed) | `docker compose logs back` — Flyway/DB 연결/OOM |
| `back` 계속 재시작 | `docker stats` 메모리, `-Xmx` 초과? `.env` DB 값? |
| 502 from Cloudflare | nginx up? `certs/origin.*` 존재? `docker compose logs nginx` |
| 526 (invalid SSL) from Cloudflare | Origin CA 인증서 만료/불일치, SSL 모드 Full(strict) 확인 |
| ffmpeg 중 앱 느려짐 | 정상 (동시성 1, swap 사용). 지속되면 인코딩을 새벽으로 이동 |
| 4시 배치 안 돎 | 인스턴스가 03:30 에 켜졌나? Scheduler 로그, `journalctl -u ...` |
| SSM 명령 안 감 | 인스턴스 running? SSM 에이전트? IAM 역할(`team1-app-role`) 붙었나 |

---

## 8. 정리 (프로젝트 종료 시)

```bash
cd infra/terraform && terraform destroy
```

- Cloudflare Pages 프로젝트 삭제, 도메인 갱신 해제(자동갱신 off).
- GHCR 패키지 삭제.
