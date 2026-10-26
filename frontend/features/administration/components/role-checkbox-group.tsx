"use client";

import type { RoleSummary } from "@/features/administration/schemas/administration";

/** A permission-role multi-select for user create/edit ({@code docs/frontend/admin-flow.md} §5, §7). */
export function RoleCheckboxGroup({
  roles,
  selected,
  onChange,
  disabled = false,
}: {
  roles: RoleSummary[];
  selected: string[];
  onChange: (next: string[]) => void;
  disabled?: boolean;
}) {
  const toggle = (id: string) => {
    if (disabled) {
      return;
    }
    onChange(selected.includes(id) ? selected.filter((role) => role !== id) : [...selected, id]);
  };

  return (
    <fieldset className="space-y-1" disabled={disabled}>
      <legend className="text-sm font-medium">Roles</legend>
      <div className="grid gap-2 sm:grid-cols-2">
        {roles.map((role) => (
          <label key={role.id} className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={selected.includes(role.id)}
              onChange={() => toggle(role.id)}
              className="h-4 w-4"
            />
            <span>{role.name}</span>
          </label>
        ))}
      </div>
      {roles.length === 0 ? <p className="text-sm text-muted-foreground">No assignable roles.</p> : null}
    </fieldset>
  );
}
