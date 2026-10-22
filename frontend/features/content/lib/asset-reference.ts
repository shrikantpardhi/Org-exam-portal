/**
 * Canonical asset reference helpers ({@code specs/12-P3-Content-V1.5.md §15, §43, §105}).
 *
 * <p>Content refers to an asset by the canonical shortcode {@code [asset: <uuid>]}. A filesystem
 * path is never written into content: the browser never receives {@code asset.file_path} at all.
 * The UUID must belong to the same question version; the backend rejects a cross-version
 * reference with {@code ASSET_VERSION_MISMATCH}, so the editor can only insert ids it listed for
 * the selected version.
 */
import type { InsertResult } from "@/features/content/lib/math";

const SHORTCODE_PATTERN = /^\[asset:\s*([0-9a-fA-F-]{36})\s*\]$/;

/** Builds the canonical shortcode for an asset id. */
export function assetReference(assetId: string): string {
  return `[asset: ${assetId}]`;
}

/** Inserts — or replaces the selection with — a canonical asset shortcode. */
export function insertAssetReference(
  value: string,
  selectionStart: number,
  selectionEnd: number,
  assetId: string,
): InsertResult {
  const start = Math.min(Math.max(selectionStart, 0), value.length);
  const end = Math.min(Math.max(selectionEnd, start), value.length);
  const shortcode = assetReference(assetId);
  return {
    value: `${value.slice(0, start)}${shortcode}${value.slice(end)}`,
    selectionStart: start + shortcode.length,
    selectionEnd: start + shortcode.length,
  };
}

/** Extracts the asset ids referenced by canonical shortcodes, in order, without duplicates. */
export function referencedAssetIds(value: string): string[] {
  const ids: string[] = [];
  const pattern = /\[asset:\s*([0-9a-fA-F-]{36})\s*\]/g;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(value)) !== null) {
    const id = match[1];
    if (id && !ids.includes(id)) {
      ids.push(id);
    }
  }
  return ids;
}

/** True when a string is exactly one canonical shortcode (used to reject stray file paths). */
export function isAssetReference(value: string): boolean {
  return SHORTCODE_PATTERN.test(value.trim());
}
