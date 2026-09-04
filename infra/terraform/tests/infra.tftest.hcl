# `terraform test` — AWS 자격증명 없이 plan 단계에서만 검증한다.
# mock_provider 로 프로바이더 응답을 가짜로 채우므로 실제 리소스 생성·비용·네트워크가 없다.
# 실행: infra/terraform 에서 `terraform init -backend=false && terraform test`
#
# 각 run 블록은 "이 설정이 깨지면 무슨 사고가 나는가"를 error_message 에 적었다.

mock_provider "aws" {}
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

  # 막는 사고: 누가 인그레스에 22(SSH)나 넓은 포트를 추가 → 인터넷에서 EC2 직접 접근.
  assert {
    condition = alltrue([
      for r in values(aws_vpc_security_group_ingress_rule.https_from_cloudflare) :
      r.from_port == 443 && r.to_port == 443 && r.ip_protocol == "tcp"
    ])
    error_message = "보안그룹 인그레스에 443/tcp 외 규칙이 있다. SSH(22)나 임의 포트가 열리면 배포 채널이 SSM 인데도 인스턴스가 직접 노출된다."
  }

  # 막는 사고: 누가 cidr_ipv4 를 "0.0.0.0/0" 으로 바꿈 → Cloudflare 우회(엣지 DDoS/WAF 무력화).
  assert {
    condition = alltrue([
      for r in values(aws_vpc_security_group_ingress_rule.https_from_cloudflare) :
      r.cidr_ipv4 != "0.0.0.0/0" && r.cidr_ipv4 != "::/0"
    ])
    error_message = "인그레스에 0.0.0.0/0 이 있다. 443 은 Cloudflare IP 대역만 허용해야 오리진 우회를 막는다 (design Q14)."
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

  # 막는 사고: 누가 encrypted 를 뺌 → 루트 볼륨·DLM 스냅샷이 평문 → 유출 시 DB·미디어 임시파일 노출.
  assert {
    condition     = aws_instance.app.root_block_device[0].encrypted == true
    error_message = "루트 EBS 가 암호화되지 않는다. 스냅샷/볼륨 유출 시 평문."
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
run "backup_policy_enabled" {
  command = plan

  # 막는 사고: 누가 state 를 DISABLED 로 두거나 정책을 지움 → EBS 스냅샷 없음
  #           → mysqldump 도 실패한 날 사고 나면 복구 불가.
  assert {
    condition     = aws_dlm_lifecycle_policy.ebs_daily.state == "ENABLED"
    error_message = "DLM 스냅샷 정책이 ENABLED 가 아니다. 볼륨 단위 2차 백업이 사라진다 (design Q13)."
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
