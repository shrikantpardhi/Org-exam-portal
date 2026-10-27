import {
  BarChart3,
  Bell,
  Building2,
  ClipboardList,
  Compass,
  FileText,
  Info,
  KeyRound,
  LayoutDashboard,
  Megaphone,
  Plug,
  ScrollText,
  Settings,
  ShieldCheck,
  ShoppingCart,
  SlidersHorizontal,
  Users,
  type LucideIcon,
} from "lucide-react";

export interface ShellMenuItem {
  label: string;
  /**
   * The destination. Optional because an entry can be listed before its route exists — such an
   * entry is `disabled` and rendered as a non-link, so the sidebar can never offer a dead anchor.
   */
  href?: string;
  icon: LucideIcon;
  permission?: string;
  /** Listed but not usable yet; rendered as a non-link with `note` as its tooltip. */
  disabled?: boolean;
  /** Why a disabled entry is unavailable, shown as a tooltip and to assistive technology. */
  note?: string;
  /** Short status tag rendered beside the label, e.g. "V2". */
  badge?: string;
}

export interface ShellMenuSection {
  title?: string;
  items: ShellMenuItem[];
}

/**
 * SUPER_ADMIN sidebar ({@code docs/frontend/navigation.md}).
 *
 * <p>The Lookups entry was removed: the lookup master data it points at is created by the seed
 * migration and has no administrative workflow in V1.5, so the route existed while being unusable
 * from this menu. The route itself is untouched.
 */
export const SUPER_ADMIN_MENU: ShellMenuSection[] = [
  {
    items: [
      { label: "Dashboard", href: "/platform", icon: LayoutDashboard },
      { label: "Organizations", href: "/platform/organizations", icon: Building2, permission: "PLATFORM_ORGANIZATION_VIEW" },
      { label: "Providers", href: "/platform/providers", icon: Plug, permission: "PLATFORM_PROVIDER_VIEW" },
      { label: "System info", href: "/platform/system", icon: Info, permission: "PLATFORM_SYSTEM_VIEW" },
    ],
  },
  {
    title: "Seeds",
    items: [
      { label: "Roles", href: "/platform/seeds/roles", icon: KeyRound, permission: "PLATFORM_SYSTEM_VIEW" },
      { label: "Permissions", href: "/platform/seeds/permissions", icon: ShieldCheck, permission: "PLATFORM_SYSTEM_VIEW" },
    ],
  },
  {
    items: [
      {
        label: "Multi-org",
        icon: Building2,
        permission: "PLATFORM_ORGANIZATION_VIEW",
        disabled: true,
        badge: "V2",
        note: "Multi-organization management arrives in V2 (ADR-0033); V1.5 runs one business organization plus PLATFORM.",
      },
    ],
  },
];

/**
 * ADMIN sidebar ({@code docs/frontend/navigation.md}).
 *
 * <p>The Lookups entry was removed for the same reason as its SUPER_ADMIN counterpart: V1.5 has no
 * administrative lookup workflow. The route and the backing read are untouched.
 */
export const ADMIN_MENU: ShellMenuSection[] = [
  {
    items: [
      { label: "Dashboard", href: "/admin", icon: LayoutDashboard },
      { label: "Users", href: "/admin/users", icon: Users, permission: "USER_VIEW" },
      { label: "Roles", href: "/admin/roles", icon: KeyRound, permission: "ROLE_VIEW" },
      { label: "Permissions", href: "/admin/permissions", icon: ShieldCheck, permission: "PERMISSION_VIEW" },
      { label: "Settings", href: "/admin/settings", icon: Settings, permission: "ORGANIZATION_SETTING_VIEW" },
      { label: "Business rules", href: "/admin/business-rules", icon: SlidersHorizontal, permission: "BUSINESS_RULE_VIEW" },
      { label: "Analytics", href: "/analytics/organization", icon: BarChart3, permission: "ANALYTICS_VIEW" },
      { label: "Reports", href: "/admin/reports", icon: FileText, permission: "REPORT_EXECUTIVE_READ" },
      { label: "Audit", href: "/admin/audit", icon: ScrollText, permission: "AUDIT_LOG_VIEW" },
    ],
  },
];

/**
 * STUDENT sidebar.
 *
 * <p>Every destination here already existed as a route — the student workspace simply had no shell,
 * so none of them were reachable without going back to `/student` first. Items carry no `permission`
 * because a student's reads are scoped to the caller by the backend
 * ({@code specs/17-P8 §52}); there is nothing for the client to gate.
 */
export const STUDENT_MENU: ShellMenuSection[] = [
  {
    items: [
      { label: "Home", href: "/student", icon: LayoutDashboard },
      { label: "Explore", href: "/student/store", icon: Compass },
      { label: "My access", href: "/student/access", icon: KeyRound },
    ],
  },
  {
    title: "Progress",
    items: [
      { label: "Results", href: "/results", icon: ClipboardList },
      { label: "Analytics", href: "/analytics/student", icon: BarChart3 },
    ],
  },
  {
    title: "Account",
    items: [
      { label: "Purchases", href: "/student/purchases", icon: ShoppingCart },
      { label: "Announcements", href: "/student/announcements", icon: Megaphone },
      { label: "Notifications", href: "/student/notifications", icon: Bell },
      { label: "Profile", href: "/account", icon: Settings },
    ],
  },
];
