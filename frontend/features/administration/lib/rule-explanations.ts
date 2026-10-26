/**
 * Plain-language explanations of the business rules this deployment ships with.
 *
 * <p>Every explanation here is derived from the code that actually consumes the rule — not from the
 * rule code alone. A rule that is not listed is shown with its raw configuration and an explicit
 * "no documented explanation" note, rather than a guess, because a wrong explanation of an
 * enforcement rule is worse than none.
 *
 * <p>Source of enforcement:
 *   - {@code MAX_ADMINS_PER_ORG}: {@code UserService}, {@code PlatformOrganizationService},
 *     {@code PlatformAdminRecoveryService} (ADR-0032).
 *   - {@code RESERVED_PERMISSIONS}: {@code RolePermissionPolicy} (ADR-0039).
 */

export type RuleExplanation = {
  code: string;
  title: string;
  purpose: string;
  enforcedBy: string;
  keys: { key: string; meaning: string }[];
  caution?: string;
};

const RULES: RuleExplanation[] = [
  {
    code: "MAX_ADMINS_PER_ORG",
    title: "Administrators per organization",
    purpose:
      "Caps how many administrators an organization may have. V1.5 runs with one administrator per " +
      "organization, which is what makes the account recoverable: a lost password has a single known " +
      "owner to recover rather than an ambiguous set.",
    enforcedBy: "Checked before any user-role write, so the cap cannot be exceeded by racing requests.",
    keys: [
      {
        key: "maxAdmins",
        meaning:
          "The largest number of ADMIN users the organization may hold. Raising it is what would enable " +
          "multiple administrators; lowering it below the current count does not remove existing accounts.",
      },
    ],
    caution:
      "Raising this above 1 changes the recovery model described in ADR-0032 and should be a deliberate, " +
      "reviewed decision rather than a quick edit.",
  },
  {
    code: "RESERVED_PERMISSIONS",
    title: "Reserved permissions",
    purpose:
      "Declares the permissions that only the platform may hold. Any attempt to grant a reserved " +
      "permission to an organization role is rejected, which stops an organization administrator from " +
      "granting themselves platform-level reach.",
    enforcedBy: "Enforced on every role-permission assignment and every role created inside an organization.",
    keys: [
      {
        key: "codes",
        meaning:
          "The permission codes treated as reserved. Only the platform may hold these. Removing a code from " +
          "this list makes it grantable inside an organization — that is the part to review before editing.",
      },
    ],
  },
];

/** Returns the explanation for a rule code, if one is documented. */
export function explainRule(code: string): RuleExplanation | undefined {
  return RULES.find((rule) => rule.code === code);
}

/** Every documented rule, for reference screens and tests. */
export function documentedRules(): RuleExplanation[] {
  return [...RULES];
}
