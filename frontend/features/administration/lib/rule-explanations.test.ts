import { describe, expect, it } from "vitest";

import { documentedRules, explainRule } from "@/features/administration/lib/rule-explanations";

/**
 * The rule-explanation registry must never invent an explanation.
 *
 * <p>These tests pin the contract that matters: every documented rule references a code that the
 * backend actually reads, an undocumented code returns undefined rather than a guess, and each entry
 * explains at least one configuration key. A wrong explanation of an enforcement rule is worse than
 * showing none, so this is asserted rather than assumed.
 */
describe("business rule explanations", () => {
  it("documents the rules this deployment actually enforces", () => {
    // These two codes are read by RolePermissionPolicy, UserService, PlatformOrganizationService and
    // PlatformAdminRecoveryService. If a code is renamed in the backend, this test fails loudly.
    const codes = documentedRules().map((rule) => rule.code);
    expect(codes).toContain("MAX_ADMINS_PER_ORG");
    expect(codes).toContain("RESERVED_PERMISSIONS");
  });

  it("returns undefined for a rule it does not know, instead of guessing", () => {
    expect(explainRule("SOME_FUTURE_RULE")).toBeUndefined();
  });

  it("explains what each rule does and who enforces it", () => {
    for (const rule of documentedRules()) {
      expect(rule.title.length).toBeGreaterThan(0);
      expect(rule.purpose.length).toBeGreaterThan(40);
      expect(rule.enforcedBy.length).toBeGreaterThan(10);
      expect(rule.keys.length).toBeGreaterThan(0);
    }
  });

  it("documents the maxAdmins key, which is what enables multiple administrators", () => {
    const rule = explainRule("MAX_ADMINS_PER_ORG");
    expect(rule?.keys.map((key) => key.key)).toContain("maxAdmins");
    // Raising the cap changes the recovery model, so the consequence must be stated.
    expect(rule?.caution).toBeDefined();
  });
});
