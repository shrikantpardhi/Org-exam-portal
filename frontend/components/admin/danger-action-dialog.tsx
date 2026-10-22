"use client";

import { ConfirmDialog, type ConfirmDialogProps } from "./confirm-dialog";

/**
 * The confirmation used for archive, revoke, role, permission, rule, setting, publish and bulk
 * actions ({@code specs/20-P11-Administration-V1.5.md §68}).
 */
export function DangerActionDialog(props: Omit<ConfirmDialogProps, "destructive">) {
  return <ConfirmDialog {...props} destructive />;
}
