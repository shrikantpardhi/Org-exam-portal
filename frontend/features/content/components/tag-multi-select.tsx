"use client";

/**
 * Tag multi-select ({@code specs/12-P3-Content-V1.5.md §109}).
 *
 * <p>Tags are chosen from the Academic Tag masters and saved as {@code tagIds} on the draft-level
 * question update. Choosing a tag can never create one: an unknown term is simply reported as not
 * found, because tag creation has its own frozen admin endpoint the authoring screen must not
 * pretend to own.
 */
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { useTags } from "@/features/academic/hooks/use-academic";

export function TagMultiSelect({
  selected,
  onChange,
  disabled = false,
}: {
  selected: string[];
  onChange: (next: string[]) => void;
  disabled?: boolean;
}) {
  const [search, setSearch] = useState("");
  const tags = useTags(search.trim().length > 0 ? { search: search.trim() } : {});
  const available = (tags.data ?? []).filter((tag) => !selected.includes(tag.id));

  function add(tagId: string) {
    if (!selected.includes(tagId)) {
      onChange([...selected, tagId]);
    }
  }

  function remove(tagId: string) {
    onChange(selected.filter((id) => id !== tagId));
  }

  return (
    <section aria-label="Tags" className="space-y-2">
      <h2 className="text-lg font-semibold">Tags</h2>
      <p className="text-xs text-muted-foreground">
        Tags come from the Academic Tag masters. Selecting one only associates it; a tag is never
        created from this screen.
      </p>

      {selected.length === 0 ? (
        <p className="text-sm text-muted-foreground">No tags selected.</p>
      ) : (
        <ul aria-label="Selected tags" className="flex flex-wrap gap-2">
          {selected.map((tagId) => {
            const known = (tags.data ?? []).find((tag) => tag.id === tagId);
            // While the catalog is loading the id is all we have; once it resolves the label wins.
            const label = known?.name ?? tagId;
            return (
              <li key={tagId} className="flex items-center gap-1 rounded-full bg-muted px-3 py-1 text-sm">
                <span>{label}</span>
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={disabled}
                  aria-label={`Remove tag ${label}`}
                  onClick={() => remove(tagId)}
                >
                  Remove
                </Button>
              </li>
            );
          })}
        </ul>
      )}

      <label className="block space-y-1 text-sm">
        <span className="font-medium">Find a tag</span>
        <input
          aria-label="Search tags"
          value={search}
          disabled={disabled}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Search tags"
          className="w-full rounded-md border border-input px-3 py-2 text-sm"
        />
      </label>

      {tags.isPending ? (
        <p role="status" className="text-sm text-muted-foreground">
          Loading tags…
        </p>
      ) : null}

      {tags.data && available.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          {search.trim().length > 0
            ? "No matching tag. A new tag must be created in Academic masters, not here."
            : "All available tags are already selected."}
        </p>
      ) : null}

      {available.length > 0 ? (
        <ul aria-label="Available tags" className="flex flex-wrap gap-2">
          {available.map((tag) => (
            <li key={tag.id}>
              <Button variant="outline" size="sm" disabled={disabled} onClick={() => add(tag.id)}>
                {tag.name}
              </Button>
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}
