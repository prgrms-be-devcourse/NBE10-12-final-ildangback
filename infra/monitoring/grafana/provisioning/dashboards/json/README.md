# 대시보드 JSON 놓는 곳 (Q29, TODO)

`dashboards.yml` 의 file provider 가 이 폴더의 `*.json` 을 Grafana 부팅 시 자동으로 불러온다.
지금은 비어있음(git이 빈 디렉터리를 못 담아서 이 README로 채워둠).

넣는 방법 두 가지:
1. Grafana UI 에서 대시보드 만든 뒤 Dashboard settings → JSON Model → 복사해서 `*.json` 으로 커밋.
2. Grafana.com 커뮤니티 대시보드 다운로드(예: JVM/Micrometer, Loki) 해서 그대로 커밋.
