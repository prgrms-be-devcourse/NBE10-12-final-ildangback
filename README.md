# 꼬밋 (Go!mmit)

1~6명의 소규모 그룹이 매일 사진·영상으로 서로 인증하며 습관을 함께 끌고 가는 챌린지 서비스입니다.

인증은 미리 찍어둔 파일이 아닌 그 자리에서 촬영한 짧은 클립으로만 가능하고, 쌓인 포인트로 캐릭터와 그룹 공간을 꾸밀 수 있습니다. 한 달이 지나면 월간 머지, 챌린지가 끝나면 최종 머지가 영수증처럼 발행되어
노력의 흔적이 기록으로 남습니다.

---

## 시작하기 (Getting Started)

로컬 개발 환경은 **H2 파일 기반 데이터베이스**를 사용합니다. 별도 설정이 필요 없습니다.

아래 명령은 모두 **프로젝트 루트에서** 실행합니다. 각 명령은 서브셸에서 돌아가므로 실행 후 루트로 돌아옵니다.

### 1. 백엔드 실행

```bash
(cd back && ./gradlew bootRun)
```

### 2. 프론트엔드 실행

```bash
(cd front && pnpm install && pnpm dev)
```

### 이메일 인증 확인 (로컬)

로컬에서는 메일을 보내지 않고 인증 링크를 콘솔에 찍습니다.

```
[인증 링크] user@example.com -> http://localhost:8080/api/auth/verify-email?token=...
```

이 주소를 브라우저에서 열면 인증이 완료됩니다.

### 엔드포인트

| 서비스              | URL                                   |
|------------------|---------------------------------------|
| 프론트엔드            | http://localhost:5173                 |
| API 문서 (Swagger) | http://localhost:8080/swagger-ui.html |
| H2 콘솔            | http://localhost:8080/h2-console      |

### 테스트

```bash
(cd back && ./gradlew test)
```

CI와 동일하게 Checkstyle·커버리지 검증까지 포함하려면:

```bash
(cd back && ./gradlew check)
```

커버리지 리포트는 `back/build/reports/jacoco/test/html/index.html`에 생성됩니다.
프론트엔드 테스트는 아직 설정되어 있지 않습니다.

---

## 코드 스타일 (Lint & Format)

커밋 전에 실행하면 고칠 수 있는 위반은 자동으로 수정됩니다.

```bash
(cd back && ./gradlew spotlessApply)
```

```bash
(cd front && pnpm format && pnpm lint:fix)
```

수정 없이 확인만 하려면 (CI와 동일한 검사):

```bash
(cd back && ./gradlew spotlessCheck checkstyleMain)
```

```bash
(cd front && pnpm format:check && pnpm lint)
```

Checkstyle은 자동 수정 기능이 없어 네이밍·복잡도 같은 위반은 직접 고쳐야 합니다.

| 대상        | 도구                                   | 설정 파일                                   |
|-----------|--------------------------------------|-----------------------------------------|
| 백엔드 포맷    | Spotless + Google Java Format (AOSP) | `back/build.gradle`                     |
| 백엔드 정적 분석 | Checkstyle                           | `back/config/checkstyle/checkstyle.xml` |
| 프론트엔드 린트  | ESLint                               | `front/eslint.config.js`                |
| 프론트엔드 포맷  | Prettier                             | `front/.prettierrc`                     |
| 에디터 공통    | EditorConfig                         | `.editorconfig`                         |

> CI에서 머지를 막는 검사는 **백엔드 테스트(`./gradlew check`)와 프론트엔드 빌드(`pnpm build`)** 입니다.
> 포맷·린트 검사는 `continue-on-error`로 실행되어 PR에 리포트 코멘트만 남기고 머지를 막지 않습니다.

---

## 핵심 기능

| 기능                    | 설명                              |
|-----------------------|---------------------------------|
| **챌린지 & 그룹**          | 개인 또는 그룹 챌린지 생성, 초대 코드 기반 참여    |
| **실시간 인증 (Check-in)** | 그 자리에서 촬영한 영상/사진으로만 인증 가능       |
| **포인트 & 보상**          | 인증 시 포인트 지급, 캐릭터·환경 커스터마이징      |
| **결과 & 기록**           | 일일로그, 월간 머지, 최종 머지 발행           |
| **스케줄러 기반 정산**        | 매일 새벽 4시 자동 정산 (포인트 지급, 스트릭 계산) |

---

## 기술 스택

| 구분                 | 스택                                                                |
|--------------------|-------------------------------------------------------------------|
| **Backend**        | Java, Spring Boot, Spring Security, Spring Batch, Spring Data JPA |
| **Frontend**       | React, TypeScript, Vite                                           |
| **Database**       | H2 (개발) / MySQL (배포)                                              |
| **Infrastructure** | Docker, AWS                                                       |
| **Quality**        | Swagger, JaCoCo                                                   |

---

## 팀원 (Team - 일당백)

| 이름  | 역할 | 담당 도메인                        |
|-----|----|-------------------------------|
| 오준서 | 팀장 | 사용자(User), 대시보드               |
| 황보람 | 팀원 | 그룹(Group), 챌린지(Challenge)     |
| 남효림 | 팀원 | 인증(Check-in), 인프라             |
| 한철완 | 팀원 | 포인트(Point), 결과·기록(Record)     |
| 최성혁 | 팀원 | 캐릭터·상점(Character/Shop), 홈/프로필 |

