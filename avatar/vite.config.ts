import { defineConfig } from "vite-plus";

export default defineConfig({
  // One classic script, so the app's WebView loads it without module fetches.
  build: {
    outDir: "../app/src/main/assets/avatar",
    emptyOutDir: false,
    lib: { entry: "src/main.ts", formats: ["iife"], name: "signsetu", fileName: () => "avatar.js" },
  },
  lint: { options: { typeAware: true, typeCheck: true } },
  fmt: { printWidth: 140 },
  test: { include: ["src/**/*.test.ts"] },
});
