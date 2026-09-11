/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string;
  /** "off" 로 두면 dev 에서도 체크인 스텁을 끄고 실제 백엔드를 붙인다. checkin 도메인 임시. */
  readonly VITE_CHECKIN_STUB?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

/** vite.config.ts 가 package.json 의 version 을 넣어 준다. */
declare const __APP_VERSION__: string;
