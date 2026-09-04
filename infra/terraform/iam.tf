data "aws_caller_identity" "current" {}

# =============================================================================
# 1. EC2 인스턴스 역할 — SSM(Session Manager + RunCommand) 용
#    없으면: SSM 이 안 되고, 22 포트도 안 열려 있어서 배포·셸 접속 경로가 0.
# =============================================================================

resource "aws_iam_role" "app" {
  name = "${var.name_prefix}-app-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "app_ssm" {
  role       = aws_iam_role.app.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "app" {
  name = "${var.name_prefix}-app-profile"
  role = aws_iam_role.app.name
}

# =============================================================================
# 2. GitHub Actions OIDC — 배포 워크플로가 ssm:SendCommand 호출
#    없으면: Actions 가 EC2 에 배포 명령을 못 보냄. 대안(장기 AWS 키를 Secret)은
#    로테이션·유출 리스크가 커서 배제.
# =============================================================================

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
  # GitHub OIDC 루트 CA 지문(고정). AWS 콘솔에서 자동 검증되지만 TF 는 목록을 요구한다.
  thumbprint_list = [
    "6938fd4d98bab03faadb97b34396831e3780aea1",
    "1c58a3a8518e8759bf075b76b750d4f2df264fcd",
  ]
}

resource "aws_iam_role" "deploy" {
  name = "${var.name_prefix}-deploy-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRoleWithWebIdentity"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Condition = {
        StringEquals = { "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com" }
        # 이 리포지토리에서 온 OIDC 토큰만. 없으면 아무 GitHub 리포나 이 역할을 탈취.
        StringLike = { "token.actions.githubusercontent.com:sub" = "repo:${var.github_repo}:*" }
      }
    }]
  })
}

resource "aws_iam_role_policy" "deploy" {
  name = "${var.name_prefix}-deploy-policy"
  role = aws_iam_role.deploy.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # 우리 인스턴스 1대 + RunShellScript 문서로만 한정(최소권한).
        # 넓히면 유출된 OIDC 로 계정 내 임의 인스턴스에 명령 실행 가능.
        Sid    = "SendCommand"
        Effect = "Allow"
        Action = "ssm:SendCommand"
        Resource = [
          "arn:aws:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:instance/${aws_instance.app.id}",
          "arn:aws:ssm:${var.aws_region}::document/AWS-RunShellScript",
        ]
      },
      {
        Sid      = "ReadResultAndState"
        Effect   = "Allow"
        Action   = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations", "ec2:DescribeInstances"]
        Resource = "*"
      },
      {
        # 배포 시각에 인스턴스가 꺼져 있으면(18:00~03:30) 켜야 함.
        Sid      = "StartForDeploy"
        Effect   = "Allow"
        Action   = "ec2:StartInstances"
        Resource = "arn:aws:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:instance/${aws_instance.app.id}"
      },
    ]
  })
}

# =============================================================================
# 3. EventBridge Scheduler 역할 — 매일 03:30 KST 인스턴스 start
#    없으면: 스케줄러가 StartInstances 호출 권한이 없어 04:00 배치 전에 안 켜짐.
# =============================================================================

resource "aws_iam_role" "scheduler" {
  name = "${var.name_prefix}-scheduler-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "scheduler.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy" "scheduler" {
  name = "${var.name_prefix}-scheduler-policy"
  role = aws_iam_role.scheduler.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "ec2:StartInstances"
      Resource = "arn:aws:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:instance/${aws_instance.app.id}"
    }]
  })
}

# =============================================================================
# 4. DLM 역할 — 매일 EBS 스냅샷
#    없으면: 볼륨 단위 2차 백업이 안 돌아 mysqldump 실패한 날 사고 시 복구 불가.
# =============================================================================

resource "aws_iam_role" "dlm" {
  name = "${var.name_prefix}-dlm-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "dlm.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "dlm" {
  role       = aws_iam_role.dlm.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSDataLifecycleManagerServiceRole"
}
