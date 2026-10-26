"use client";

/**
 * Blueprint navigation, timer, submission, presentation, calculator, and language editors
 * ({@code specs/13-P4-Assessment-V1.5.md §19-§21, §99}).
 *
 * <p>Only fields present in the frozen typed configuration are exposed. The language options come
 * from the Academic Language catalog, never a hard-coded enum (§21).
 */
import {
  NAVIGATION_MODE_OPTIONS,
  PALETTE_POSITION_OPTIONS,
  TIMER_MODE_OPTIONS,
  type BlueprintFormValues,
} from "@/features/assessment/schemas/blueprint-config";
import type { Language } from "@/features/academic/schemas/academic";

const fieldClass =
  "w-full rounded-md border border-input px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-900";

function Checkbox({
  id,
  label,
  checked,
  disabled,
  onChange,
}: {
  id: string;
  label: string;
  checked: boolean;
  disabled?: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <label htmlFor={id} className="flex items-center gap-2 text-sm">
      <input
        id={id}
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onChange={(event) => onChange(event.target.checked)}
        className="h-4 w-4 rounded border-input"
      />
      {label}
    </label>
  );
}

export function BlueprintConfigurationEditor({
  values,
  onChange,
  languages,
  disabled = false,
}: {
  values: BlueprintFormValues;
  onChange: (values: BlueprintFormValues) => void;
  languages: Language[];
  disabled?: boolean;
}) {
  function patch<K extends keyof BlueprintFormValues>(
    key: K,
    value: Partial<BlueprintFormValues[K]>,
  ) {
    onChange({ ...values, [key]: { ...values[key], ...value } });
  }

  function toggleLanguage(languageId: string, selected: boolean) {
    const languageIds = selected
      ? [...values.language.languageIds, languageId]
      : values.language.languageIds.filter((id) => id !== languageId);
    const defaultLanguageId =
      selected && values.language.defaultLanguageId.length === 0
        ? languageId
        : values.language.defaultLanguageId;
    onChange({
      ...values,
      language: {
        languageIds,
        defaultLanguageId: languageIds.includes(defaultLanguageId)
          ? defaultLanguageId
          : (languageIds[0] ?? ""),
      },
    });
  }

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <section aria-labelledby="blueprint-navigation-heading" className="space-y-3">
        <h2 id="blueprint-navigation-heading" className="text-lg font-semibold tracking-tight">
          Navigation
        </h2>
        <div>
          <label htmlFor="navigation-mode" className="text-sm font-medium">
            Mode
          </label>
          <select
            id="navigation-mode"
            aria-label="Navigation mode"
            value={values.navigation.mode}
            disabled={disabled}
            onChange={(event) =>
              patch("navigation", { mode: event.target.value as BlueprintFormValues["navigation"]["mode"] })
            }
            className={fieldClass}
          >
            {NAVIGATION_MODE_OPTIONS.map((mode) => (
              <option key={mode} value={mode}>
                {mode}
              </option>
            ))}
          </select>
        </div>
        <Checkbox
          id="navigation-allow-section-switch"
          label="Allow section switching"
          checked={values.navigation.allowSectionSwitch}
          disabled={disabled}
          onChange={(checked) => patch("navigation", { allowSectionSwitch: checked })}
        />
        <p className="text-xs text-muted-foreground">
          P4 records navigation intent. The runtime policy is produced by the ExamEngine and enforced by
          Delivery.
        </p>
      </section>

      <section aria-labelledby="blueprint-timer-heading" className="space-y-3">
        <h2 id="blueprint-timer-heading" className="text-lg font-semibold tracking-tight">
          Timer
        </h2>
        <div>
          <label htmlFor="timer-mode" className="text-sm font-medium">
            Mode
          </label>
          <select
            id="timer-mode"
            aria-label="Timer mode"
            value={values.timer.mode}
            disabled={disabled}
            onChange={(event) =>
              patch("timer", { mode: event.target.value as BlueprintFormValues["timer"]["mode"] })
            }
            className={fieldClass}
          >
            {TIMER_MODE_OPTIONS.map((mode) => (
              <option key={mode} value={mode}>
                {mode}
              </option>
            ))}
          </select>
        </div>
        <Checkbox
          id="timer-server-authoritative"
          label="Server authoritative"
          checked={values.timer.serverAuthoritative}
          disabled
          onChange={(checked) => patch("timer", { serverAuthoritative: checked })}
        />
        <Checkbox
          id="timer-auto-submit"
          label="Auto-submit on expiry"
          checked={values.timer.autoSubmitOnExpiry}
          disabled={disabled}
          onChange={(checked) => patch("timer", { autoSubmitOnExpiry: checked })}
        />
        <p className="text-xs text-muted-foreground">
          The server is always the timer authority; the browser only renders a countdown.
        </p>
      </section>

      <section aria-labelledby="blueprint-submission-heading" className="space-y-3">
        <h2 id="blueprint-submission-heading" className="text-lg font-semibold tracking-tight">
          Submission
        </h2>
        <Checkbox
          id="submission-manual"
          label="Allow manual submit"
          checked={values.submission.allowManualSubmit}
          disabled={disabled}
          onChange={(checked) => patch("submission", { allowManualSubmit: checked })}
        />
        <Checkbox
          id="submission-auto-expiry"
          label="Auto-submit on expiry"
          checked={values.submission.autoSubmitOnExpiry}
          disabled={disabled}
          onChange={(checked) => patch("submission", { autoSubmitOnExpiry: checked })}
        />
        <Checkbox
          id="submission-confirmation"
          label="Require confirmation"
          checked={values.submission.requireConfirmation}
          disabled={disabled}
          onChange={(checked) => patch("submission", { requireConfirmation: checked })}
        />
      </section>

      <section aria-labelledby="blueprint-presentation-heading" className="space-y-3">
        <h2 id="blueprint-presentation-heading" className="text-lg font-semibold tracking-tight">
          Presentation
        </h2>
        <div>
          <label htmlFor="presentation-palette" className="text-sm font-medium">
            Palette position
          </label>
          <select
            id="presentation-palette"
            value={values.presentation.palettePosition}
            disabled={disabled}
            onChange={(event) =>
              patch("presentation", {
                palettePosition: event.target.value as BlueprintFormValues["presentation"]["palettePosition"],
              })
            }
            className={fieldClass}
          >
            {PALETTE_POSITION_OPTIONS.map((position) => (
              <option key={position} value={position}>
                {position}
              </option>
            ))}
          </select>
        </div>
        <Checkbox
          id="presentation-progress"
          label="Show progress"
          checked={values.presentation.showProgress}
          disabled={disabled}
          onChange={(checked) => patch("presentation", { showProgress: checked })}
        />
      </section>

      <section aria-labelledby="blueprint-validation-heading" className="space-y-3">
        <h2 id="blueprint-validation-heading" className="text-lg font-semibold tracking-tight">
          Response validation
        </h2>
        <Checkbox
          id="validation-response-required"
          label="Response required before next"
          checked={values.validation.responseRequiredBeforeNext}
          disabled={disabled}
          onChange={(checked) => patch("validation", { responseRequiredBeforeNext: checked })}
        />
        <Checkbox
          id="validation-section-completion"
          label="Section completion required"
          checked={values.validation.sectionCompletionRequired}
          disabled={disabled}
          onChange={(checked) => patch("validation", { sectionCompletionRequired: checked })}
        />
      </section>

      <section aria-labelledby="blueprint-calculator-heading" className="space-y-3">
        <h2 id="blueprint-calculator-heading" className="text-lg font-semibold tracking-tight">
          Calculator
        </h2>
        <Checkbox
          id="calculator-enabled"
          label="Calculator enabled"
          checked={values.calculator.enabled}
          disabled={disabled}
          onChange={(checked) => patch("calculator", { enabled: checked })}
        />
      </section>

      <section aria-labelledby="blueprint-language-heading" className="space-y-3 lg:col-span-2">
        <h2 id="blueprint-language-heading" className="text-lg font-semibold tracking-tight">
          Language
        </h2>
        {languages.length === 0 ? (
          <p role="note" className="text-sm text-muted-foreground">
            No languages are available from the Academic catalog yet.
          </p>
        ) : (
          <ul className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {languages.map((language) => (
              <li key={language.id}>
                <Checkbox
                  id={`language-${language.id}`}
                  label={language.name}
                  checked={values.language.languageIds.includes(language.id)}
                  disabled={disabled}
                  onChange={(checked) => toggleLanguage(language.id, checked)}
                />
              </li>
            ))}
          </ul>
        )}

        <div>
          <label htmlFor="default-language" className="text-sm font-medium">
            Default language
          </label>
          <select
            id="default-language"
            value={values.language.defaultLanguageId}
            disabled={disabled || values.language.languageIds.length === 0}
            onChange={(event) => patch("language", { defaultLanguageId: event.target.value })}
            className={fieldClass}
          >
            <option value="">Select a language</option>
            {languages
              .filter((language) => values.language.languageIds.includes(language.id))
              .map((language) => (
                <option key={language.id} value={language.id}>
                  {language.name}
                </option>
              ))}
          </select>
        </div>
      </section>
    </div>
  );
}
