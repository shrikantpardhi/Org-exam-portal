import path from "path";
import { defineConfig } from "vitest/config";

/**
 * Vitest configuration.
 *
 * JSX is compiled by Vite's esbuild using the automatic runtime, so no separate React plugin is
 * required for tests (tests do not need Fast Refresh).
 */
export default defineConfig({
  esbuild: {
    jsx: "automatic",
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "."),
    },
  },
  test: {
    environment: "jsdom",
    globals: false,
    // React selects its development or production build from NODE_ENV at import time, and only the
    // development build exports `act`, which Testing Library requires. Force the test environment so
    // the suite does not fail when NODE_ENV is set to `production` in the ambient shell.
    env: {
      NODE_ENV: "test",
    },
    setupFiles: ["./vitest.setup.ts"],
    include: ["**/*.test.{ts,tsx}"],
    exclude: ["node_modules/**", ".next/**", "coverage/**"],
  },
});
