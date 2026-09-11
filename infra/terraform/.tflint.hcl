config {
  call_module_type = "local"
}

plugin "terraform" {
  enabled = true
  preset  = "recommended"
}

# AWS 리소스 인자·타입 검증. 예: 존재하지 않는 instance_type, 잘못된 IAM 액션 문자열.
plugin "aws" {
  enabled = true
  version = "0.35.0"
  source  = "github.com/terraform-linters/tflint-ruleset-aws"
}
