/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string;
  /** 소셜 로그인 인가 요청에 실린다. 공개 값이라 프론트 번들에 들어가도 된다. */
  readonly VITE_GOOGLE_CLIENT_ID: string;
  readonly VITE_NAVER_CLIENT_ID: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

/** vite.config.ts 가 package.json 의 version 을 넣어 준다. */
declare const __APP_VERSION__: string;
