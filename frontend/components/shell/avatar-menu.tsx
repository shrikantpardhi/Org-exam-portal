"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { useAuth } from "@/lib/auth/auth-context";

function initials(firstName?: string | null, lastName?: string | null): string {
 const a = firstName?.trim().charAt(0) ?? "";
 const b = lastName?.trim().charAt(0) ?? "";
 return (a + b).toUpperCase() || "?";
}

/** Avatar menu: initials button expanding to Profile / Change password / Sign out. */
export function AvatarMenu() {
 const { user, logout } = useAuth();
 const router = useRouter();
 const [open, setOpen] = useState(false);

 async function onSignOut() {
 setOpen(false);
 await logout();
 router.push("/login");
  }

  return (
    <div className="relative">
      <button
 type="button"
        onClick={() => setOpen((value) => !value)}
 aria-expanded={open}
 aria-label="Account menu"
 className="flex h-9 w-9 items-center justify-center rounded-full bg-primary text-sm font-medium text-white"
      >
        {initials(user?.firstName, user?.lastName)}
      </button>
      {open ? (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} aria-hidden />
          <div className="absolute right-0 z-20 mt-2 w-48 rounded-lg border border-border bg-surface py-1 shadow-lg">
            <Link
 href="/account"
              onClick={() => setOpen(false)}
 className="block px-3 py-2 text-sm text-foreground hover:bg-muted"
            >
              Profile
            </Link>
            <Link
 href="/change-password"
              onClick={() => setOpen(false)}
 className="block px-3 py-2 text-sm text-foreground hover:bg-muted"
            >
              Change password
            </Link>
            <button
 type="button"
 onClick={onSignOut}
 className="block w-full px-3 py-2 text-left text-sm text-foreground hover:bg-muted"
            >
              Sign out
            </button>
          </div>
        </>
      ) : null}
    </div>
  );
}
