import { defineConfig } from "vite-plus";

export default defineConfig({
  // Relative asset paths, so the built portal opens from any folder or file share.
  base: "./",
  lint: {
    options: { typeAware: true, typeCheck: true },
  },
  // The rig fixtures are written by RigFixtureTest.kt, which compares the exact text.
  fmt: { printWidth: 140, ignorePatterns: ["src/rig-fixtures.json"] },
  test: { include: ["src/**/*.test.ts"] },
});
