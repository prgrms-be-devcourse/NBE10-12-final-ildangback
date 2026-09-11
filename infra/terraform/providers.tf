provider "aws" {
  region = var.aws_region

  # 계정 규칙: 모든 리소스에 Team 태그 필수. default_tags 로 일괄 적용.
  default_tags {
    tags = {
      Team    = var.team_tag
      Project = "gommit"
      Managed = "terraform"
    }
  }
}

provider "cloudflare" {
  api_token = var.cloudflare_api_token
}
