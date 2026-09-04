terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.70"
    }
    cloudflare = {
      # v5 에서 cloudflare_record → cloudflare_dns_record 로 리소스명·속성이 바뀌었다.
      # 이 코드는 v4 스키마(cloudflare_record, content 속성) 기준.
      source  = "cloudflare/cloudflare"
      version = "~> 4.52"
    }
    http = {
      source  = "hashicorp/http"
      version = "~> 3.4"
    }
  }

  # state backend: 로컬 (Q22 = C안).
  #   - 인프라 담당자 1인만 `terraform apply` 한다.
  #   - apply 직후 terraform.tfstate 를 팀 드라이브에 백업한다(시크릿 파일 취급).
  #   - .gitignore 에 *.tfstate* 등록됨.
  # 팀에서 여러 명이 apply해야 하면 HCP Terraform 무료 tier(B안)로 전환.
}
