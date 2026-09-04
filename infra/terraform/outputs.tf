output "instance_id" {
  description = "GitHub Actions Secret EC2_INSTANCE_ID 에 넣을 값"
  value       = aws_instance.app.id
}

output "public_ip" {
  description = "탄력적 IP. Pages 도메인/네임서버와 무관, api 레코드가 자동으로 가리킴"
  value       = aws_eip.app.public_ip
}

output "api_fqdn" {
  value = "${var.api_subdomain}.${var.domain}"
}

output "deploy_role_arn" {
  description = "GitHub Actions Secret AWS_DEPLOY_ROLE_ARN 에 넣을 값"
  value       = aws_iam_role.deploy.arn
}

output "ssm_start_command" {
  description = "수동으로 인스턴스를 켤 때"
  value       = "aws ec2 start-instances --instance-ids ${aws_instance.app.id} --region ${var.aws_region}"
}

output "ssm_session_command" {
  description = "SSH 대신 셸 접속"
  value       = "aws ssm start-session --target ${aws_instance.app.id} --region ${var.aws_region}"
}
