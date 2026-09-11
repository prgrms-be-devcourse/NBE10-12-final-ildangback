# 매일 03:30 KST 인스턴스 start (04:00 배치가 돌 때 켜져 있게).
# stop 은 걸지 않는다 — 루트 계정이 18:00 에 stop 한다.
# StartInstances 는 이미 running 이면 no-op 이라 안전.

resource "aws_scheduler_schedule" "ec2_start" {
  name       = "${var.name_prefix}-ec2-start-0330"
  group_name = "default"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression          = "cron(30 3 * * ? *)"
  schedule_expression_timezone = "Asia/Seoul"

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:ec2:startInstances"
    role_arn = aws_iam_role.scheduler.arn

    input = jsonencode({
      InstanceIds = [aws_instance.app.id]
    })
  }
}
