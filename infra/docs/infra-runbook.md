# 인프라 운영 절차 (Runbook)

> 설계 배경은 `infra/docs/infra-design.md`. 이 문서는 "무엇을 어떻게 실행하나".
> 도메인은 `go-mmit.site` 로 표기 — 임시 placeholder, 구매 시 실제 값으로 치환.

---

## 0. 사전 준비 (계정·크레덴셜)

| 항목 | 발급처 | 쓰이는 곳 |
|---|---|---|
| AWS IAM 사용자 (인프라 담당자) | 강의 AWS 계정 | `terraform apply`, 수동 조작 |
| Cloudflare 계정 + 존 | cloudflare.com | DNS, Workers, Origin CA |
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

### 1-3. GitHub Actions Secrets + Environment

리포 Settings → Secrets and variables → Actions:

| Secret | 값 |
|---|---|
| `EC2_INSTANCE_ID` | terraform output |
| `AWS_DEPLOY_ROLE_ARN` | terraform output |

리포 Settings → Environments → **`production` 생성**:
- `deploy.yml` 의 `deploy` job 이 이 환경에서 돈다. OIDC 신뢰 정책(`iam.tf`)이
  `repo:<repo>:environment:production` sub 만 허용하므로 환경 이름이 안 맞으면
  `configure-aws-credentials` 단계가 실패한다.
- Terraform `deploy_environment` 변수(기본 `production`)와 이름 일치시킬 것.
- **Deployment protection rules → Required reviewers 를 반드시 1명 이상 건다.**
  `deploy.yml` 은 push(main) 외에 `workflow_dispatch` 로도 돈다. 옛 커밋이나 다른 브랜치에서
  dispatch 하면 `back:latest` 태그가 그 코드로 이동해 prod 포인터가 뒤로 갈 수 있다.
  승인 게이트가 그걸 막는 유일한 장치다. (롤백은 이 워크플로 대신 런북 3장의 SSM 직접 호출로 한다.)

`GITHUB_TOKEN` 은 자동 제공 (CI 의 GHCR push 용).

GHCR 패키지는 **private 로 둔다.** 리포는 public 이지만 이미지에는 빌드 산출물·의존성이 들어가므로
익명 pull 을 열지 않는다. EC2 에서 pull 하려면 자격증명이 필요하다:

1. GitHub → Settings → Developer settings → Personal access tokens **(classic)** →
   Generate → `read:packages` **스코프만** 체크.
2. 만료일: 설정하면 만료 전 재발급 + EC2 재로그인 필요 (아래 §2 에 갱신 메모). 무기한 토큰은 지양.
3. 토큰 문자열을 1-4 의 `docker login` 단계에서 쓴다. `~ec2-user/.docker/config.json` 에 저장돼
   재부팅·재배포 후에도 유지된다.

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

# GHCR 로그인 (패키지 private) — ec2-user 로 실행할 것. deploy.sh 도 ec2-user 로 돌기 때문.
# config.json 에 저장돼 재부팅·재배포 후에도 유지됨. 토큰은 1-3 의 read:packages PAT.
echo <GHCR_PAT> | docker login ghcr.io -u <github-user> --password-stdin

# 최초 기동
cp src/infra/compose/docker-compose.yml .
cp src/infra/compose/backup.sh .
cp src/infra/compose/deploy.sh .          # 이후 SSM 배포가 절대경로로 호출. 이후엔 deploy.sh 가 스스로 갱신.
chmod +x deploy.sh
rsync -a src/infra/nginx/ nginx/
docker compose up -d
docker compose ps
```

### 1-5. Cloudflare Workers (프론트) — Pages 아님, 대시보드가 통합돼 신규 프로젝트는 기본 Workers

> Git 연동(Workers Builds)이 끊긴 상태라 지금은 `.github/workflows/deploy-front.yml`
> (GitHub Actions, `wrangler deploy`)로 배포함 — `infra-design.md` Q8 참고. 아래는 연동
> 복구 시 참고용 원래 절차.

1. Workers & Pages → Create → Connect to Git → 리포 선택.
2. Build: root `front`, command `pnpm build`, output `dist`, Node 20+.
3. 환경변수: `VITE_API_BASE_URL=https://api.go-mmit.site` — `front/.env.production`에 이미
   커밋돼있어 보통 불필요.
4. Custom domains → `go-mmit.site` 추가 → Cloudflare 가 apex 레코드 자동 생성.

### 1-6. 확인

```
curl -I https://api.go-mmit.site/actuator/health   # 200
open https://go-mmit.site                          # 프론트
```

---

## 2. 일상 배포

`main` 에 백엔드/인프라 변경 머지 → `deploy.yml` 자동 실행:
빌드 → GHCR push (`:<sha>` + `:latest`) → SSM 이 `deploy.sh <12자-sha> <풀-sha>` 실행 → 헬스체크.
(2번째 인자로 배포 커밋에 `src` 를 고정 → 이미지와 compose/nginx 설정이 같은 커밋.)

수동 실행: Actions → Deploy Backend → Run workflow.

**배포 금지 시간대**: 03:30~04:30 (자동 start + 배치), 17:45~18:15 (루트 정지). 런북 상단 참고.

**GHCR PAT 갱신**: 1-3 에서 만료일 있는 PAT 를 썼다면 만료 전에 새 토큰 발급 → EC2 에서
`docker login` 다시 (1-4 명령 동일). 만료되면 `deploy.sh` 의 `docker compose pull` 이
`denied` / `unauthorized` 로 실패한다.

---

## 3. 롤백

```bash
aws ssm start-session --target <instance-id>
# 1번째 = 이미지 태그(12자), 2번째 = 같은 커밋의 풀 SHA (src 를 그 커밋으로 되돌림)
sudo -u ec2-user bash /opt/team1-app/deploy.sh <이전-12자-SHA> <이전-풀-SHA>
```

이전 이미지는 GHCR 에 `back:<sha>` 로 남아 있다. GHCR Packages 에서 태그 목록 확인.
`deploy.sh` 가 `docker image prune` 로 72시간 지난 이미지를 지우므로, 3일보다 오래된
버전으로 롤백하려면 GHCR 에서 다시 pull 된다(자동). 문제 없음.

---

## 4. DB 백업 / 복구

- **자동**: 호스트 cron 이 매일 04:20 `backup.sh` → `/opt/team1-app/backups/gommit-YYYYMMDD-HHMM.sql.gz`, 7일 보관.
- 볼륨 단위 자동 스냅샷(DLM)은 계정 규칙상 부수 서비스 결재 회피를 위해 제외했다.
  필요 시 EC2 콘솔에서 루트 볼륨 스냅샷을 수동으로 찍을 수 있다.

### 논리 복구 (mysqldump 에서)

```bash
cd /opt/team1-app
gunzip -c backups/gommit-YYYYMMDD-HHMM.sql.gz | \
  docker compose exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" gommit
docker compose restart back
```

### 볼륨 복구 (수동 스냅샷에서)

인스턴스 교체가 필요한 수준의 사고일 때. 수동으로 찍어둔 스냅샷이 있으면 EC2 콘솔에서
스냅샷 → 볼륨 생성 → 루트 교체. 없으면 `terraform taint aws_instance.app && terraform apply`
로 인스턴스를 새로 만든 뒤 mysqldump 백업에서 논리 복구.

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

**기본 경로 (SSM) — IAM 자격증명이 있는 사람 (인프라 담당자·CI):**

```bash
aws ssm start-session --target <instance-id> --region ap-northeast-2

cd /opt/team1-app
docker compose ps
docker compose logs -f --tail=100 back
docker compose logs --tail=50 nginx
docker stats --no-stream          # 메모리 압박 확인 (2GB 박스)
free -h; swapon --show
```

### 6-1. SSH 예외 접속 (Q14 추가결정)

IAM 을 나눠줄 수 없어 SSM 을 못 쓰는 운영자 1인 전용. 그 외에는 위 SSM 을 쓴다.

**최초 1회 설정 (인프라 담당자가):**

1. 그 사람에게 키쌍 생성 요청 — `ssh-keygen -t ed25519 -C team1-ops -f ~/.ssh/team1` .
   `-C` 는 이름 대신 `team1-ops` 같은 중립 문자열로. 공개키(`team1.pub`)만 전달받는다.
2. 그 사람 공인 IP 확인 (`curl ifconfig.me`), `infra/terraform/terraform.tfvars` 에:
   ```hcl
   ssh_allowed_cidrs = ["<그사람-IP>/32"]
   ```
   `terraform apply` — SG 22 규칙만 라이브 추가, 인스턴스 재시작 없음.
3. 공개키를 인스턴스 `authorized_keys` 에 등록 (SSM 으로):
   ```bash
   aws ssm start-session --target <instance-id> --region ap-northeast-2
   sudo -u ec2-user tee -a /home/ec2-user/.ssh/authorized_keys <<'KEY'
   ssh-ed25519 AAAA... team1-ops
   KEY
   ```

**접속 (그 사람이):** `ssh -i ~/.ssh/team1 ec2-user@<EIP>`
인스턴스가 켜진 03:30~18:00 에만 된다.

**IP 가 바뀌면:** `terraform.tfvars` 의 `/32` 갱신 → `terraform apply`. 키는 그대로.

**인스턴스 재빌드 시:** `authorized_keys` 는 새 볼륨이라 3번을 다시 한다 (부트스트랩에 안 박음).

---

## 7. 트러블슈팅

| 증상 | 확인 |
|---|---|
| 배포 실패 (health check failed) | `docker compose logs back` — Flyway/DB 연결/OOM |
| `back` 계속 재시작 | `docker stats` 메모리, `-Xmx` 초과? `.env` DB 값? ffmpeg 겹침이면 아래 참고 |
| `back` OOM-kill 반복 (인코딩 중) | 동시성 1 확인. 2회 이상이면 사이드카 분리 검토 — `infra-design.md` Q24 "ffmpeg 인코딩 확장 사다리" |
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

- Cloudflare Workers 프로젝트 삭제, 도메인 갱신 해제(자동갱신 off).
- GHCR 패키지 삭제.
