/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

/** vite.config.ts 가 package.json 의 version 을 넣어 준다. */
declare const __APP_VERSION__: string;
