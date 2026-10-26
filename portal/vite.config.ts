import { defineConfig } from "vite-plus";

export default defineConfig({
  // Relative asset paths, so the built portal opens from any folder or file share.
  base: "./",
  lint: {
    options: { typeAware: true, typeCheck: true },
  },
  fmt: { printWidth: 140 },
  test: { include: ["src/**/*.test.ts"] },
});
