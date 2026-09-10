# 매일 EBS 스냅샷 7일 롤링. Backup=true 태그가 붙은 볼륨(= 루트 볼륨) 대상.
# 18:30 = 루트 계정 stop(18:00) 직후라 스냅샷이 정지 상태에서 찍혀 일관성이 좋다.
# mysqldump(앱 내부 야간 크론)와 별개의 2차 백업.

resource "aws_dlm_lifecycle_policy" "ebs_daily" {
  description        = "${var.name_prefix} daily EBS snapshot 7d rolling"
  execution_role_arn = aws_iam_role.dlm.arn
  state              = "ENABLED"

  policy_details {
    resource_types = ["VOLUME"]

    target_tags = {
      Backup = "true"
    }

    schedule {
      name = "daily-7d"

      create_rule {
        interval      = 24
        interval_unit = "HOURS"
        times         = ["09:30"] # UTC = 18:30 KST
      }

      retain_rule {
        count = 7
      }

      copy_tags = true

      tags_to_add = {
        SnapshotCreator = "dlm"
      }
    }
  }
}
