import { existsSync } from "node:fs";
import path from "node:path";

import { describe, expect, it } from "vitest";

import { roleHome } from "@/lib/auth/role-home";

/**
 * The post-login destinations must be real routes.
 *
 * <p>{@code roleHome} is what the login form navigates to, so a destination without a matching
 * {@code app/<route>/page.tsx} sends every user with that role to a 404 immediately after a
 * successful sign-in. That is exactly what happened for students: `/student` was the declared home
 * in {@code docs/frontend/navigation.md} and in this module, but no page existed at that path.
 *
 * <p>Turning the mapping into a filesystem assertion makes the next such drift fail here instead of
 * in a browser.
 */
const APP_DIR = path.resolve(__dirname, "../../app");

function pageExists(href: string): boolean {
  return existsSync(path.join(APP_DIR, href.replace(/^\//, ""), "page.tsx"));
}

describe("roleHome destinations exist on disk", () => {
  it.each([
    ["SUPER_ADMIN", ["SUPER_ADMIN"], "/platform"],
    ["ADMIN", ["ADMIN"], "/admin"],
    ["STUDENT", ["STUDENT"], "/student"],
  ])("the %s home has a page component", (_label, roles, expected) => {
    const destination = roleHome(roles, false);
    expect(destination).toBe(expected);
    expect(pageExists(destination), `no app${destination}/page.tsx exists`).toBe(true);
  });

  it("the forced-password-change destination has a page component", () => {
    const destination = roleHome(["ADMIN"], true);
    expect(destination).toBe("/change-password");
    expect(pageExists(destination), `no app${destination}/page.tsx exists`).toBe(true);
  });
});
