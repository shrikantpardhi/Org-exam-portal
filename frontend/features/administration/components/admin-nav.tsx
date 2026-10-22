"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { useAuth } from "@/lib/auth/auth-context";

/**
 * Permission-driven administration navigation
 * ({@code specs/20-P11-Administration-V1.5.md §63, §67}).
 *
 * <p>An item is shown only when the current-user permission context contains one of its required
 * codes. Hiding a link is not authorization; the backend re-checks every request.
 */
export interface AdminNavItem {
  href: string;
  label: string;
  permissions: readonly string[];
}

export const ADMIN_NAV_ITEMS: readonly AdminNavItem[] = [
  { href: "/admin/dashboard", label: "Dashboard", permissions: ["ADMIN_DASHBOARD_VIEW"] },
  { href: "/admin/users", label: "Users", permissions: ["USER_VIEW"] },
  { href: "/admin/students", label: "Students", permissions: ["USER_VIEW"] },
  { href: "/admin/roles", label: "Roles", permissions: ["ROLE_VIEW"] },
  { href: "/admin/permissions", label: "Permissions", permissions: ["PERMISSION_VIEW"] },
  { href: "/admin/settings", label: "Settings", permissions: ["ORGANIZATION_SETTING_VIEW"] },
  { href: "/admin/business-rules", label: "Business rules", permissions: ["BUSINESS_RULE_VIEW"] },
  { href: "/admin/lookups", label: "Lookups", permissions: ["LOOKUP_VIEW"] },
  { href: "/admin/audit", label: "Audit", permissions: ["AUDIT_LOG_VIEW"] },
  { href: "/admin/system-health", label: "System health", permissions: ["SYSTEM_HEALTH_VIEW"] },
  { href: "/admin/commerce", label: "Commerce", permissions: ["ACCESS_PACKAGE_MANAGE"] },
  {
    href: "/admin/communication",
    label: "Communication",
    permissions: ["COMMUNICATION_TEMPLATE_READ", "ANNOUNCEMENT_READ"],
  },
];

export function AdminNav() {
  const { can, status } = useAuth();
  const pathname = usePathname();

  if (status === "loading") {
    return (
      <p role="status" aria-live="polite" className="text-sm text-slate-500">
        Loading navigation…
      </p>
    );
  }

  const items = ADMIN_NAV_ITEMS.filter((item) => item.permissions.some((permission) => can(permission)));

  if (items.length === 0) {
    return (
      <p role="note" className="text-sm text-slate-500">
        No administration sections are available for your permissions.
      </p>
    );
  }

  return (
    <nav aria-label="Administration">
      <ul className="flex flex-wrap gap-2 text-sm">
        {items.map((item) => {
          const current = pathname === item.href;
          return (
            <li key={item.href}>
              <Link
                href={item.href}
                aria-current={current ? "page" : undefined}
                className={`inline-block rounded border px-3 py-1 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 ${
                  current
                    ? "border-blue-300 bg-blue-50 font-medium text-blue-800"
                    : "border-slate-300 bg-white text-slate-700 hover:bg-slate-50"
                }`}
              >
                {item.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
