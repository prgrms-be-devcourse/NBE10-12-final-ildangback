import { readFileSync } from "node:fs";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";
import { VitePWA } from "vite-plugin-pwa";

const { version } = JSON.parse(readFileSync("./package.json", "utf-8"));

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    // PWA: manifest.json 은 public/ 에 직접 두므로(manifest: false) service worker 만 생성한다.
    // manifest 만으로는 설치가 안 되고 fetch 핸들러가 있는 SW 가 있어야 한다 (infra/docs/infra-design.md Q8).
    VitePWA({
      registerType: "autoUpdate",
      injectRegister: "auto",
      manifest: false,
      workbox: {
        // 앱 셸 precache + SPA 라우팅 fallback. /api 응답은 캐시하지 않는다(습관 데이터는 최신이 중요).
        globPatterns: ["**/*.{js,css,html,woff2}"],
        navigateFallback: "/index.html",
        navigateFallbackDenylist: [/^\/api/],
        cleanupOutdatedCaches: true,
      },
      devOptions: { enabled: false },
    }),
  ],
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
