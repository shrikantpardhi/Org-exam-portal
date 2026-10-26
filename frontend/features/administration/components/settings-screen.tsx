"use client";

import { useEffect, useState } from "react";

import { DangerActionDialog } from "@/components/admin/danger-action-dialog";
import { describeAdminError } from "@/components/admin/format";
import { OrganizationProfileCard } from "@/features/administration/components/organization-profile-card";
import type { SettingKeyDescriptor } from "@/features/administration/schemas/administration";
import { useSettings, useUpdateSettings } from "@/features/administration/hooks/use-administration";
import { useAuth } from "@/lib/auth/auth-context";

/**
 * Organization settings ({@code specs/20-P11-Administration-V1.5.md §8, §71}).
 *
 * <p>Fields come from the approved server-side registry. Boolean, numeric and string keys get typed
 * controls; a structured key is edited as validated JSON rather than an arbitrary whole-settings
 * document.
 */
export function SettingsScreen() {
  const { can } = useAuth();
  const query = useSettings();
  const update = useUpdateSettings();
  const [values, setValues] = useState<Record<string, unknown>>({});
  const [jsonErrors, setJsonErrors] = useState<Record<string, string>>({});
  const [initialized, setInitialized] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (query.data && !initialized) {
      setValues(query.data.configuration ?? {});
      setInitialized(true);
    }
  }, [query.data, initialized]);

  if (query.isPending) {
    return <p role="status" className="text-sm text-muted-foreground">Loading settings…</p>;
  }

  if (query.isError) {
    return (
      <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
        <p>{describeAdminError(query.error, "Settings could not be loaded.")}</p>
        <button
          type="button"
          onClick={() => {
            void query.refetch();
          }}
          className="mt-2 rounded border border-red-300 bg-surface px-3 py-1 text-sm font-medium text-red-700"
        >
          Try again
        </button>
      </div>
    );
  }

  const setting = query.data;
  if (!setting) {
    return <p role="status" className="text-sm text-muted-foreground">Loading settings…</p>;
  }

  const canUpdate = can("ORGANIZATION_SETTING_UPDATE");

  const valueFor = (descriptor: SettingKeyDescriptor): unknown =>
    Object.prototype.hasOwnProperty.call(values, descriptor.key)
      ? values[descriptor.key]
      : descriptor.defaultValue;

  const setValue = (key: string, value: unknown) => {
    setValues((current) => ({ ...current, [key]: value }));
  };

  const renderControl = (descriptor: SettingKeyDescriptor) => {
    const raw = valueFor(descriptor);
    const type = (descriptor.type ?? "").toLowerCase();
    const disabled = !canUpdate || !descriptor.mutable;

    if (type.includes("bool")) {
      return (
        <input
          id={descriptor.key}
          type="checkbox"
          checked={Boolean(raw)}
          disabled={disabled}
          onChange={(event) => setValue(descriptor.key, event.target.checked)}
          className="h-4 w-4"
        />
      );
    }

    if (type.includes("int") || type.includes("number") || type.includes("decimal") || type.includes("long")) {
      return (
        <input
          id={descriptor.key}
          type="number"
          step={type.includes("decimal") ? "0.01" : "1"}
          value={typeof raw === "number" ? raw : ""}
          disabled={disabled}
          onChange={(event) =>
            setValue(descriptor.key, event.target.value === "" ? null : Number(event.target.value))
          }
          className="w-full rounded-md border border-input px-3 py-2 text-sm"
        />
      );
    }

    if (type.includes("string") || type === "" || type.includes("text")) {
      return (
        <input
          id={descriptor.key}
          type="text"
          value={raw === null || raw === undefined ? "" : String(raw)}
          disabled={disabled}
          onChange={(event) => setValue(descriptor.key, event.target.value)}
          className="w-full rounded-md border border-input px-3 py-2 text-sm"
        />
      );
    }

    return (
      <div className="space-y-1">
        <textarea
          id={descriptor.key}
          rows={4}
          defaultValue={JSON.stringify(raw ?? null, null, 2)}
          disabled={disabled}
          onBlur={(event) => {
            try {
              const parsed: unknown = JSON.parse(event.target.value);
              setValue(descriptor.key, parsed);
              setJsonErrors((current) => {
                const rest = { ...current };
                delete rest[descriptor.key];
                return rest;
              });
            } catch {
              setJsonErrors((current) => ({
                ...current,
                [descriptor.key]: "Enter valid JSON.",
              }));
            }
          }}
          className="w-full rounded-md border border-input px-3 py-2 font-mono text-sm"
        />
        {jsonErrors[descriptor.key] ? (
          <p role="alert" className="text-sm text-red-600">{jsonErrors[descriptor.key]}</p>
        ) : null}
      </div>
    );
  };

  if (setting.registry.length === 0) {
    return (
      <p role="note" className="rounded-lg border border-dashed border-input bg-muted p-4 text-sm text-foreground">
        No configurable settings are registered.
      </p>
    );
  }

  return (
    <div className="space-y-6">
      {/* The organization's own identity, above the behaviour settings. */}
      <OrganizationProfileCard />

      <div>
        <h2 className="text-base font-semibold">Behaviour settings</h2>
        <p className="text-sm text-muted-foreground">
          {setting.registry.length} registered setting{setting.registry.length === 1 ? "" : "s"} for
          this organization. Values are validated by the backend on save.
        </p>
      </div>

      <div className="space-y-4">
        {setting.registry.map((descriptor) => (
          <section key={descriptor.key} aria-label={descriptor.key} className="space-y-1 rounded-lg border border-border bg-surface p-4">
            <label htmlFor={descriptor.key} className="text-sm font-medium">
              {descriptor.key}
            </label>
            <p className="text-xs text-muted-foreground">
              {descriptor.type}
              {descriptor.securityClassification
                ? ` · ${descriptor.securityClassification}`
                : ""}
              {descriptor.mutable ? "" : " · read-only"}
            </p>
            {renderControl(descriptor)}
          </section>
        ))}
      </div>

      {canUpdate ? (
        <button
          type="button"
          onClick={() => setConfirmOpen(true)}
          disabled={Object.keys(jsonErrors).length > 0}
          className="rounded bg-blue-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700"
        >
          Save settings
        </button>
      ) : (
        <p className="text-xs text-muted-foreground">You can view settings but not change them.</p>
      )}

      {message ? <p role="status" className="text-sm text-emerald-700">{message}</p> : null}
      {update.isError ? (
        <p role="alert" className="text-sm text-red-600">
          {describeAdminError(update.error, "The settings could not be saved.")}
        </p>
      ) : null}
      <p className="text-xs text-muted-foreground">Configuration changes are audited by the backend.</p>

      <DangerActionDialog
        open={confirmOpen}
        title="Update organization settings"
        description="Change the organization configuration. The backend validates the values and records an audit event."
        confirmLabel="Save settings"
        busy={update.isPending}
        onConfirm={() => {
          update.mutate(values, {
            onSuccess: () => {
              setConfirmOpen(false);
              setMessage("Organization settings updated.");
            },
          });
        }}
        onCancel={() => setConfirmOpen(false)}
      />
    </div>
  );
}
