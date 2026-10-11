// Registers jest-dom matchers with Vitest's expect (and their TypeScript types).
import "@testing-library/jest-dom/vitest";

import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// Testing Library's automatic cleanup relies on globals; this project keeps globals disabled,
// so unmount rendered trees explicitly between tests.
afterEach(() => {
  cleanup();
});
