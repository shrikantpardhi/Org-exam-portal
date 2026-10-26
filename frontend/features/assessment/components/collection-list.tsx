"use client";

/**
 * Collection UI ({@code specs/13-P4-Assessment-V1.5.md §110}).
 *
 * <p>List, create, get, update, add assessment, remove assessment, and archive. Membership is managed
 * through CollectionAssessment, so adding or removing is a server round-trip rather than local state.
 * Archiving uses the frozen status update — the contract defines no DELETE for a collection (GAP-050).
 */
import { useState } from "react";
import Link from "next/link";

import { Button } from "@/components/ui/button";
import {
  useAddAssessmentToCollection,
  useAssessments,
  useCollection,
  useCollections,
  useCreateCollection,
  useRemoveAssessmentFromCollection,
  useUpdateCollection,
} from "@/features/assessment/hooks/use-assessment";
import type { Collection } from "@/features/assessment/schemas/assessment-builder";
import { ApiClientError } from "@/lib/errors";

const fieldClass =
  "w-full rounded-md border border-input px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-900";

const statuses = ["ACTIVE", "INACTIVE", "ARCHIVED"] as const;

export function CollectionList() {
  const collections = useCollections();
  const createCollection = useCreateCollection();
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    try {
      await createCollection.mutateAsync({
        code,
        name,
        ...(description.trim().length > 0 ? { description } : {}),
      });
      setCode("");
      setName("");
      setDescription("");
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "The collection was rejected.");
    }
  }

  return (
    <div className="space-y-8">
      <form onSubmit={submit} className="space-y-3" aria-labelledby="collection-create-heading">
        <h2 id="collection-create-heading" className="text-lg font-semibold tracking-tight">
          Create a collection
        </h2>
        <div className="grid gap-3 sm:grid-cols-3">
          <div>
            <label htmlFor="collection-code" className="text-sm font-medium">
              Code
            </label>
            <input
              id="collection-code"
              value={code}
              onChange={(event) => setCode(event.target.value)}
              required
              className={fieldClass}
            />
          </div>
          <div>
            <label htmlFor="collection-name" className="text-sm font-medium">
              Name
            </label>
            <input
              id="collection-name"
              value={name}
              onChange={(event) => setName(event.target.value)}
              required
              className={fieldClass}
            />
          </div>
          <div>
            <label htmlFor="collection-description" className="text-sm font-medium">
              Description
            </label>
            <input
              id="collection-description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              className={fieldClass}
            />
          </div>
        </div>
        <Button type="submit" disabled={createCollection.isPending}>
          Create collection
        </Button>
      </form>

      {collections.isPending ? <p role="status">Loading collections…</p> : null}
      {collections.isError ? (
        <p role="alert" className="text-sm text-red-600">
          Collections could not be loaded.
        </p>
      ) : null}
      {collections.data && collections.data.length === 0 ? (
        <p role="note" className="text-sm text-muted-foreground">
          No collections yet.
        </p>
      ) : null}

      <ul className="space-y-3">
        {(collections.data ?? []).map((collection) => (
          <li key={collection.id} className="rounded border border-border p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <Link href={`/collections/${collection.id}`} className="font-medium underline">
                  {collection.name}
                </Link>
                <p className="font-mono text-xs text-muted-foreground">{collection.code}</p>
              </div>
              <span className="text-sm text-muted-foreground">{collection.status}</span>
            </div>
            <p className="mt-2 text-sm text-muted-foreground">
              {collection.assessmentIds.length} assessment
              {collection.assessmentIds.length === 1 ? "" : "s"}
            </p>
          </li>
        ))}
      </ul>

      {error ? (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      ) : null}
    </div>
  );
}

export function CollectionDetail({ collectionId }: { collectionId: string }) {
  const collection = useCollection(collectionId);
  const assessments = useAssessments();
  const addAssessment = useAddAssessmentToCollection(collectionId);
  const removeAssessment = useRemoveAssessmentFromCollection(collectionId);
  const updateCollection = useUpdateCollection(collectionId);

  const [assessmentId, setAssessmentId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const membership: Collection | undefined = collection.data;
  const memberIds = membership?.assessmentIds ?? [];
  const nameFor = (id: string): string =>
    assessments.data?.find((assessment) => assessment.id === id)?.name ?? id;

  async function add(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setMessage(null);
    if (assessmentId.length === 0) {
      setError("Choose an assessment to add.");
      return;
    }
    try {
      await addAssessment.mutateAsync(assessmentId);
      setAssessmentId("");
      setMessage("Assessment added to the collection.");
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "The assessment could not be added.");
    }
  }

  async function remove(id: string) {
    setError(null);
    setMessage(null);
    try {
      await removeAssessment.mutateAsync(id);
      setMessage("Assessment removed from the collection.");
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "The assessment could not be removed.");
    }
  }

  async function archive() {
    setError(null);
    setMessage(null);
    try {
      await updateCollection.mutateAsync({ status: "ARCHIVED" });
      setMessage("Collection archived.");
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "The collection could not be archived.");
    }
  }

  if (collection.isPending) {
    return <p role="status">Loading collection…</p>;
  }

  if (collection.isError || !membership) {
    return (
      <p role="alert" className="text-sm text-red-600">
        The collection could not be loaded.
      </p>
    );
  }

  return (
    <div className="space-y-6">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{membership.name}</h1>
        <p className="font-mono text-xs text-muted-foreground">{membership.code}</p>
        {membership.description ? <p className="text-sm text-muted-foreground">{membership.description}</p> : null}
        <p className="text-sm text-muted-foreground">Status: {membership.status}</p>
      </header>

      <form onSubmit={add} className="flex flex-wrap items-end gap-3">
        <div className="min-w-56">
          <label htmlFor="collection-add-assessment" className="text-sm font-medium">
            Add assessment
          </label>
          <select
            id="collection-add-assessment"
            value={assessmentId}
            onChange={(event) => setAssessmentId(event.target.value)}
            className={fieldClass}
          >
            <option value="">Choose an assessment</option>
            {(assessments.data ?? [])
              .filter((assessment) => !memberIds.includes(assessment.id))
              .map((assessment) => (
                <option key={assessment.id} value={assessment.id}>
                  {assessment.name}
                </option>
              ))}
          </select>
        </div>
        <Button type="submit" disabled={addAssessment.isPending}>
          Add
        </Button>
      </form>

      <section aria-labelledby="collection-members-heading" className="space-y-2">
        <h2 id="collection-members-heading" className="text-lg font-semibold tracking-tight">
          Members
        </h2>
        {memberIds.length === 0 ? (
          <p role="note" className="text-sm text-muted-foreground">
            This collection has no assessments yet.
          </p>
        ) : (
          <ul className="space-y-2">
            {memberIds.map((id) => (
              <li key={id} className="flex items-center justify-between gap-3 rounded border border-border p-3 text-sm">
                <span>{nameFor(id)}</span>
                <Button variant="ghost" size="sm" aria-label={`Remove ${nameFor(id)}`} onClick={() => void remove(id)}>
                  Remove
                </Button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <div className="flex flex-wrap gap-3">
        <Button variant="outline" onClick={() => void archive()} disabled={updateCollection.isPending}>
          Archive collection
        </Button>
      </div>

      {message ? (
        <p role="status" className="text-sm text-green-700">
          {message}
        </p>
      ) : null}
      {error ? (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      ) : null}
    </div>
  );
}

/** Archived status selector, kept separate so the list screen stays a list. */
export function CollectionStatusSelect({
  value,
  onChange,
}: {
  value: string;
  onChange: (status: string) => void;
}) {
  return (
    <label className="flex items-center gap-2 text-sm">
      Status
      <select value={value} onChange={(event) => onChange(event.target.value)} className={fieldClass}>
        {statuses.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    </label>
  );
}
