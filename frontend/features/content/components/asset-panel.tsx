"use client";

/**
 * Version-bound asset panel ({@code specs/12-P3-Content-V1.5.md §15, §42, §43, §105}).
 *
 * <p>Upload, list, preview and remove assets for the selected version. Inserting a reference
 * writes the canonical shortcode {@code [asset: <uuid>]}; a filesystem path is never placed in
 * content and the browser is never given an asset file path. Assets belong to exactly one version,
 * so the panel is disabled for a published (immutable) version.
 */
import { useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { useAssets, useDeleteAsset, useUploadAsset } from "@/features/content/hooks/use-content";
import { assetReference } from "@/features/content/lib/asset-reference";
import { ApiClientError } from "@/lib/errors";

export function AssetPanel({
  versionId,
  published,
  onInsertReference,
}: {
  versionId: string;
  published: boolean;
  onInsertReference?: (shortcode: string) => void;
}) {
  const assets = useAssets(versionId);
  const upload = useUploadAsset(versionId);
  const remove = useDeleteAsset(versionId);
  const fileInput = useRef<HTMLInputElement | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);

  async function handleFile(file: File | undefined) {
    if (!file) {
      return;
    }
    setError(null);
    try {
      await upload.mutateAsync(file);
      setStatus(`${file.name} uploaded.`);
      if (fileInput.current) {
        fileInput.current.value = "";
      }
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "The upload could not be completed.");
    }
  }

  async function handleRemove(assetId: string, fileName: string) {
    setError(null);
    try {
      await remove.mutateAsync(assetId);
      setStatus(`${fileName} removed.`);
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "The asset could not be removed.");
    }
  }

  return (
    <section aria-label="Assets" className="space-y-3">
      <h2 className="text-lg font-semibold">Assets</h2>
      <p className="text-xs text-slate-500">
        References are inserted as <code>[asset: &lt;uuid&gt;]</code>. A published version is immutable, so
        its assets cannot be changed.
      </p>
      {/*
       * Preview gap: the frozen asset contract (§105) exposes list, upload and remove only — no
       * content/download route. Rather than invent an endpoint or a filesystem URL, the panel shows
       * the canonical shortcode, which is what the renderer resolves server-side.
       */}
      <p role="note" className="text-xs text-slate-500">
        Inline preview is not offered: the frozen asset contract has no content route, so assets are
        identified by their canonical shortcode.
      </p>

      <div className="flex flex-wrap items-center gap-2">
        <input
          ref={fileInput}
          aria-label="Upload asset"
          type="file"
          disabled={published}
          onChange={(event) => void handleFile(event.target.files?.[0])}
          className="text-sm"
        />
        {upload.isPending ? (
          <span role="status" className="text-sm text-slate-500">
            Uploading…
          </span>
        ) : null}
      </div>

      {error ? (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      ) : null}
      {status ? (
        <p role="status" className="text-sm text-slate-600">
          {status}
        </p>
      ) : null}

      {assets.isPending ? (
        <p role="status" className="text-sm text-slate-500">
          Loading assets…
        </p>
      ) : null}
      {assets.data && assets.data.length === 0 ? (
        <p className="text-sm text-slate-500">No assets on this version.</p>
      ) : null}

      {assets.data && assets.data.length > 0 ? (
        <ul aria-label="Asset list" className="space-y-2">
          {assets.data.map((asset) => (
            <li key={asset.id} className="rounded border border-slate-200 p-3 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span>
                  <span className="font-medium">{asset.fileName}</span>{" "}
                  <span className="text-slate-500">{asset.fileType}</span>{" "}
                  <span className="text-slate-400">{asset.status}</span>
                </span>
                <span className="flex flex-wrap gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={published}
                    aria-label={`Insert asset ${asset.fileName}`}
                    onClick={() => onInsertReference?.(assetReference(asset.id))}
                  >
                    Insert reference
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    disabled={published}
                    aria-label={`Remove asset ${asset.fileName}`}
                    onClick={() => void handleRemove(asset.id, asset.fileName)}
                    className="text-red-600"
                  >
                    Remove
                  </Button>
                </span>
              </div>
              <p className="mt-1 font-mono text-xs text-slate-500">{assetReference(asset.id)}</p>
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}
