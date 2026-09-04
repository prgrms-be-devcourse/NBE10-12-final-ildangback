# 443 인바운드는 Cloudflare 서버 IP 대역만 허용한다(오리진 우회 차단, Q14).
# SSH(22)는 열지 않는다 — 배포·디버그 모두 SSM Session Manager 사용.

data "http" "cloudflare_ipv4" {
  url = "https://www.cloudflare.com/ips-v4"
}

locals {
  cloudflare_ipv4_cidrs = compact(split("\n", trimspace(data.http.cloudflare_ipv4.response_body)))
}

resource "aws_security_group" "app" {
  name        = "${var.name_prefix}-app-sg"
  description = "gommit app - 443 from Cloudflare only, no SSH"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${var.name_prefix}-app-sg" }
}

resource "aws_vpc_security_group_ingress_rule" "https_from_cloudflare" {
  for_each = toset(local.cloudflare_ipv4_cidrs)

  security_group_id = aws_security_group.app.id
  description       = "HTTPS from Cloudflare edge"
  cidr_ipv4         = each.value
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "all_out" {
  security_group_id = aws_security_group.app.id
  description       = "all outbound"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}
