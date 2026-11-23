import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
    // Playwright の出力（レポートに同梱された JS など）
    "playwright-report/**",
    "test-results/**",
    "blob-report/**",
    "e2e-results/**",
    "playwright/.cache/**",
  ]),
]);

export default eslintConfig;
