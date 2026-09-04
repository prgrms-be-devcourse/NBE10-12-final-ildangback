variable "aws_region" {
  description = "AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "availability_zone" {
  description = "단일 퍼블릭 서브넷을 둘 AZ"
  type        = string
  default     = "ap-northeast-2a"
}

variable "name_prefix" {
  description = "리소스 이름 접두사 (계정 규칙: team1-<컴포넌트>)"
  type        = string
  default     = "team1"
}

variable "team_tag" {
  description = "모든 리소스에 붙는 필수 태그 값"
  type        = string
  default     = "devcos-team01"
}

variable "instance_type" {
  description = "EC2 타입. small 은 즉시 허가, 그 이상은 결재 필요"
  type        = string
  default     = "t4g.small"
}

variable "root_volume_size" {
  description = "루트 EBS(gp3) 크기 GiB. MySQL named volume + 미디어 임시파일 포함"
  type        = number
  default     = 30
}

variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

variable "public_subnet_cidr" {
  type    = string
  default = "10.0.1.0/24"
}

# ---- 도메인 / Cloudflare -------------------------------------------------------

variable "domain" {
  description = "루트 도메인. go-mmit.site 는 임시 placeholder — 구매 시 실제 값으로 교체"
  type        = string
  default     = "go-mmit.site"
}

variable "api_subdomain" {
  description = "백엔드 서브도메인 (앞부분만). apex 는 Cloudflare Pages 가 대시보드에서 관리"
  type        = string
  default     = "api"
}

variable "cloudflare_zone_id" {
  description = "Cloudflare 존 ID (대시보드 우측 하단)"
  type        = string
}

variable "cloudflare_api_token" {
  description = "Cloudflare API 토큰 — Zone.DNS 편집 권한만"
  type        = string
  sensitive   = true
}

# ---- GitHub Actions OIDC 배포 -----------------------------------------------

variable "github_repo" {
  description = "OIDC 신뢰 대상 리포지토리 (owner/name)"
  type        = string
  default     = "prgrms-be-devcourse/NBE10-12-final-ildangback"
}
