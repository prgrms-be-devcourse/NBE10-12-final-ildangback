import { readFileSync } from "node:fs";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

const { version } = JSON.parse(readFileSync("./package.json", "utf-8"));

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  define: {
    __APP_VERSION__: JSON.stringify(version),
  },
  build: {
    // 아이콘 webp 가 1~5KB 라 기본값(4KB)이면 대부분 base64 로 JS 에 박힌다.
    // 그러면 프로필 · 설정에서만 쓰는 그림이 초기 번들에 들어가고 base64 라 33% 더 크다.
    // 0 으로 두면 전부 별도 파일이 되어 loading="lazy" 가 실제로 동작한다.
    assetsInlineLimit: 0,
  },
});
