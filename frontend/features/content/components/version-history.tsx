"use client";

/**
 * Version history panel ({@code specs/12-P3-Content-V1.5.md §90, §101, §110}).
 *
 * <p>Every row shows the facts the server actually returned: version number, created and published
 * dates, review state, published state, marks, negative marks and difficulty. Review and published
 * state come from the server row, never from local form state, so the screen cannot claim a version
 * is approved before the backend said so. Selecting an already-published version offers the
 * "create a new version" path instead of editing it in place, because published versions are
 * immutable.
 */
import { Button } from "@/components/ui/button";
import type { ReviewStatus, VersionListItem } from "@/features/content/schemas/content";

const REVIEW_LABEL: Record<ReviewStatus, string> = {
  DRAFT: "Draft",
  IN_REVIEW: "In review",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  PUBLISHED: "Published",
};

export function VersionHistory({
  versions,
  selectedVersionId,
  onSelect,
  onCreateNewVersion,
  canCreateVersion = true,
  pending = false,
  error,
}: {
  versions: VersionListItem[];
  selectedVersionId?: string;
  onSelect: (versionId: string) => void;
  onCreateNewVersion: (baseVersionId: string) => void;
  canCreateVersion?: boolean;
  pending?: boolean;
  error?: string | null;
}) {
  if (pending) {
    return (
      <section aria-label="Version history">
        <h2 className="text-lg font-semibold">Version history</h2>
        <p role="status" className="text-sm text-muted-foreground">
          Loading version history…
        </p>
      </section>
    );
  }

  if (error) {
    return (
      <section aria-label="Version history">
        <h2 className="text-lg font-semibold">Version history</h2>
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      </section>
    );
  }

  return (
    <section aria-label="Version history" className="space-y-2">
      <h2 className="text-lg font-semibold">Version history</h2>

      {versions.length === 0 ? <p className="text-sm text-muted-foreground">No versions yet.</p> : null}

      {versions.length > 0 ? (
        <div className="overflow-x-auto" tabIndex={0}>
<table aria-label="Versions" className="w-full min-w-[640px] text-sm">
          <thead>
            <tr className="text-left">
              <th scope="col">Version</th>
              <th scope="col">Published</th>
              <th scope="col">Review state</th>
              <th scope="col">Published state</th>
              <th scope="col">Marks</th>
              <th scope="col">Negative marks</th>
              <th scope="col">Difficulty</th>
              <th scope="col">Actions</th>
            </tr>
          </thead>
          <tbody>
            {versions.map((version) => {
              const selected = version.id === selectedVersionId;
              return (
                <tr
                  key={version.id}
                  aria-current={selected ? "true" : undefined}
                  className={selected ? "bg-muted" : undefined}
                >
                  <td>v{version.version}</td>
                  <td>{version.publishedOn ?? "Not published"}</td>
                  <td>{REVIEW_LABEL[version.reviewStatus]}</td>
                  <td>{version.published ? "Published" : "Unpublished"}</td>
                  <td>{version.marks ?? "—"}</td>
                  <td>{version.negativeMarks ?? "—"}</td>
                  <td>{version.difficulty ?? "—"}</td>
                  <td className="space-x-2">
                    <Button variant="outline" size="sm" aria-label={`Select version ${version.version}`} onClick={() => onSelect(version.id)}>
                      Select
                    </Button>
                    {version.published ? (
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={!canCreateVersion}
                        aria-label={`Create a new version from version ${version.version}`}
                        onClick={() => onCreateNewVersion(version.id)}
                      >
                        Create a new version
                      </Button>
                    ) : null}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
</div>
      ) : null}

      {selectedVersionId ? <SelectedVersionNotice versions={versions} selectedVersionId={selectedVersionId} /> : null}

      <p role="note" className="text-xs text-muted-foreground">
        {/* Frozen-contract gap: §110 asks for a created date, but GET /questions/{id}/versions
            returns only VersionListItem (id, version, reviewStatus, published, marks,
            negativeMarks, difficulty, publishedOn). No created timestamp is exposed for a version,
            so the column states that rather than inventing a value. */}
        The version list response has no <code>createdOn</code> field, so a version&apos;s created
        date is not shown here; only the published date is available.
      </p>
    </section>
  );
}

/**
 * Explains why a published version cannot be edited in place ({@code §101, §110}).
 *
 * <p>Published versions are immutable, so the only correct next action is a new version.
 */
function SelectedVersionNotice({
  versions,
  selectedVersionId,
}: {
  versions: VersionListItem[];
  selectedVersionId: string;
}) {
  const selected = versions.find((version) => version.id === selectedVersionId);
  if (!selected) {
    return null;
  }
  if (!selected.published) {
    return (
      <p role="note" className="text-sm text-muted-foreground">
        Editing version {selected.version} in place.
      </p>
    );
  }
  return (
    <p role="note" className="text-sm text-amber-700">
      Version {selected.version} is published and therefore immutable. Editing it is not offered —
      create a new version to change the content.
    </p>
  );
}
