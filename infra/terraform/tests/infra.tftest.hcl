# `terraform test` — AWS 자격증명 없이 plan 단계에서만 검증한다.
# mock_provider 로 프로바이더 응답을 가짜로 채우므로 실제 리소스 생성·비용·네트워크가 없다.
# 실행: infra/terraform 에서 `terraform init -backend=false && terraform test`
#
# 각 run 블록은 "이 설정이 깨지면 무슨 사고가 나는가"를 error_message 에 적었다.

mock_provider "aws" {
  # 모의 apply 시 IAM 역할 ARN 이 유효한 형식이어야 scheduler 의 role_arn 검증을 통과.
  mock_resource "aws_iam_role" {
    defaults = { arn = "arn:aws:iam::123456789012:role/mock" }
  }
}
mock_provider "cloudflare" {}

# security.tf 의 data.http(Cloudflare IP 목록)를 가짜 3개 대역으로 대체.
mock_provider "http" {
  mock_data "http" {
    defaults = {
      response_body = "173.245.48.0/20\n103.21.244.0/22\n103.22.200.0/22\n"
      status_code   = 200
    }
  }
}

variables {
  cloudflare_zone_id   = "test-zone-id"
  cloudflare_api_token = "test-token"
}

# -----------------------------------------------------------------------------
run "security_group_locks_origin" {
  command = plan

  # 막는 사고: 누가 443 규칙의 포트/프로토콜을 바꿈.
  assert {
    condition = alltrue([
      for r in values(aws_vpc_security_group_ingress_rule.https_from_cloudflare) :
      r.from_port == 443 && r.to_port == 443 && r.ip_protocol == "tcp"
    ])
    error_message = "443 인그레스에 443/tcp 외 규칙이 섞였다."
  }

  # 막는 사고: 누가 cidr_ipv4 를 "0.0.0.0/0" 으로 바꿈 → Cloudflare 우회(엣지 DDoS/WAF 무력화).
  assert {
    condition = alltrue([
      for r in values(aws_vpc_security_group_ingress_rule.https_from_cloudflare) :
      r.cidr_ipv4 != "0.0.0.0/0" && r.cidr_ipv4 != "::/0"
    ])
    error_message = "인그레스에 0.0.0.0/0 이 있다. 443 은 Cloudflare IP 대역만 허용해야 오리진 우회를 막는다 (design Q14)."
  }

  # 기본값(ssh_allowed_cidrs=[])이면 SSH 규칙이 0개여야 한다 (SSM 전용 = 원래 설계).
  assert {
    condition     = length(aws_vpc_security_group_ingress_rule.ssh_operator) == 0
    error_message = "ssh_allowed_cidrs 를 안 줬는데 22 규칙이 생겼다. 기본은 SSH 미개방이어야 한다."
  }
}

# -----------------------------------------------------------------------------
run "ssh_exception_is_narrow" {
  command = plan

  variables {
    ssh_allowed_cidrs = ["203.0.113.7/32"]
  }

  # 막는 사고: 예외 SSH 를 열되 포트가 22 아님 / 와일드카드로 넓힘 → 오리진 전면 노출.
  assert {
    condition = alltrue([
      for r in values(aws_vpc_security_group_ingress_rule.ssh_operator) :
      r.from_port == 22 && r.to_port == 22 && r.ip_protocol == "tcp" &&
      r.cidr_ipv4 != "0.0.0.0/0" && r.cidr_ipv4 != "::/0" && endswith(r.cidr_ipv4, "/32")
    ])
    error_message = "SSH 예외 규칙이 22/tcp·단일 호스트(/32) 조건을 벗어났다 (design Q14 추가결정)."
  }
}

# -----------------------------------------------------------------------------
run "instance_is_hardened_and_cheap" {
  command = plan

  # 막는 사고: 누가 instance_type 을 m5.large 등으로 올림 → 결재 없이 생성 불가 + 예산 초과.
  assert {
    condition     = can(regex("^t4g[.]", aws_instance.app.instance_type))
    error_message = "인스턴스 타입이 t4g(ARM 버스터블) 계열이 아니다. small 초과는 결재 필요, 월 8만원 예산도 위험 (design Q3)."
  }

  # 막는 사고: 누가 metadata_options 를 지움 → IMDSv1 허용 → SSRF 한 방으로 인스턴스 역할 크레덴셜 탈취.
  assert {
    condition     = aws_instance.app.metadata_options[0].http_tokens == "required"
    error_message = "IMDSv2 가 강제되지 않는다. SSRF 취약점 하나로 SSM 역할 자격증명이 유출될 수 있다."
  }

  # 막는 사고: 누가 encrypted 를 뺌 → 루트 볼륨이 평문 → 유출 시 DB·미디어 임시파일 노출.
  assert {
    condition     = aws_instance.app.root_block_device[0].encrypted == true
    error_message = "루트 EBS 가 암호화되지 않는다. 볼륨 유출 시 평문."
  }

  # 막는 사고: 누가 true 로 되돌림 → 부트스트랩 스크립트 한 줄만 고쳐도 인스턴스 재생성
  #           → 수동 배치한 .env / Origin CA 인증서 / 리포 clone 전부 날아가고 서비스 다운.
  assert {
    condition     = aws_instance.app.user_data_replace_on_change == false
    error_message = "user_data_replace_on_change 가 true 다. bootstrap 수정이 인스턴스를 재생성해 .env/certs/src 를 유실시킨다 (runbook 1-4 재실행 필요)."
  }

  # 막는 사고: 누가 iam_instance_profile 를 뗌 → SSM 에이전트가 등록 안 됨 → 배포·셸 접속 전부 불가.
  assert {
    condition     = aws_instance.app.iam_instance_profile != ""
    error_message = "인스턴스에 IAM 프로파일이 없다. SSM RunCommand/Session 이 안 되면 유일한 배포·접속 경로가 사라진다 (22 미개방)."
  }
}

# -----------------------------------------------------------------------------
run "auto_start_before_batch" {
  command = plan

  # 막는 사고: 크론이 밀리거나 지워짐 → 03:30 에 인스턴스가 안 켜짐 → 04:00 정산 배치(포인트·스트릭) 누락.
  assert {
    condition     = aws_scheduler_schedule.ec2_start.schedule_expression == "cron(30 3 * * ? *)"
    error_message = "자동 기동 크론이 03:30 이 아니다. 루트가 18:00 에 끈 인스턴스가 04:00 배치 전에 안 켜진다 (design Q19)."
  }

  # 막는 사고: 타임존이 빠지면 UTC 로 해석 → cron(30 3) = 12:30 KST 기동 → 배치 시간과 무관.
  assert {
    condition     = aws_scheduler_schedule.ec2_start.schedule_expression_timezone == "Asia/Seoul"
    error_message = "스케줄 타임존이 Asia/Seoul 이 아니다. UTC 로 해석되면 기동 시각이 9시간 어긋난다."
  }
}

# -----------------------------------------------------------------------------
run "deploy_oidc_is_environment_scoped" {
  # assume_role_policy 는 AWS 가 정규화해 plan 단계엔 unknown → apply(모의) 로 확정값 검사.
  command = apply

  # 막는 사고: 누가 sub 조건을 repo:<repo>:* 같은 와일드카드로 넓힘 → 아무 브랜치/태그/PR
  #           워크플로가 deploy-role 을 탈취해 ssm:SendCommand 로 EC2 임의 명령 실행 (design 보안 메모).
  assert {
    condition     = can(regex("repo:[^\"]+:environment:[^\"]+", aws_iam_role.deploy.assume_role_policy))
    error_message = "deploy-role 신뢰 정책 sub 가 GitHub Environment 로 한정돼 있지 않다. repo:<repo>:environment:<env> 형태여야 브랜치/PR 에서의 역할 탈취를 막는다."
  }

  assert {
    condition     = !can(regex("repo:[^\"]*:[*]", aws_iam_role.deploy.assume_role_policy))
    error_message = "deploy-role 신뢰 정책에 repo:...:* 와일드카드 sub 가 있다. 특정 environment 로 좁혀야 한다."
  }
}

# -----------------------------------------------------------------------------
run "api_dns_is_proxied" {
  command = plan

  # 막는 사고: proxied=false(그레이 클라우드) → EC2 공인 IP 가 DNS 로 그대로 노출
  #           → 보안그룹이 Cloudflare IP 만 허용하므로 사이트가 즉시 다운 + DDoS 무방비.
  assert {
    condition     = cloudflare_record.api.proxied == true
    error_message = "api 레코드가 proxied 가 아니다. 오리진 IP 노출 + 보안그룹(CF IP only)과 충돌해 접속 불가."
  }
}
