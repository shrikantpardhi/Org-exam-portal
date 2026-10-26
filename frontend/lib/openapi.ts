import fs from "node:fs";
import path from "node:path";

/**
 * Build-time flattening of the retained OpenAPI document for the public /docs/api reference
 * ({@code GAP-170}, {@code docs/frontend/docs-site.md} §35–36).
 *
 * <p>This module reads {@code frontend/generated/openapi.json}, the artifact written by the
 * backend's {@code OpenApiDocumentationGenerationTest}. It is imported only from server components
 * (or via {@code import type} from client components), so {@code node:fs} never ships to the
 * browser.
 */

export interface ApiParameter {
  name: string;
  in: string;
  required: boolean;
  description: string;
}

export interface ApiEndpoint {
  method: string;
  path: string;
  summary: string;
  description: string;
  tag: string;
  parameters: ApiParameter[];
  requestBodySchema: string | null;
}

export interface ApiTag {
  name: string;
  description: string;
}

export interface ApiReference {
  tags: ApiTag[];
  endpoints: ApiEndpoint[];
}

interface OpenApiOperation {
  tags?: string[];
  summary?: string;
  description?: string;
  operationId?: string;
  parameters?: Array<{ name: string; in: string; required?: boolean; description?: string }>;
  requestBody?: { content?: Record<string, { schema?: { $ref?: string } }> };
}

interface OpenApiDoc {
  paths?: Record<string, Record<string, OpenApiOperation>>;
  tags?: Array<{ name: string; description?: string }>;
}

// Prefer the fresh backend artifact (produced by the backend verify) and fall back to the
// checked-in snapshot under frontend/generated so a standalone frontend build still works.
const CANDIDATE_FILES = [
  path.join(process.cwd(), "..", "backend", "target", "generated", "openapi.json"),
  path.join(process.cwd(), "generated", "openapi.json"),
];

function requestBodySchema(op: OpenApiOperation): string | null {
  const content = op.requestBody?.content;
  if (!content) return null;
  const media = Object.values(content)[0];
  const ref = media?.schema?.$ref;
  if (ref) return ref.split("/").pop() ?? null;
  return media?.schema ? "object" : null;
}

/** Flatten the OpenAPI document into a browsable, searchable endpoint list (pure, testable). */
export function flattenOpenApi(doc: OpenApiDoc): ApiReference {
  const tags: ApiTag[] = (doc.tags ?? []).map((tag) => ({
    name: tag.name,
    description: tag.description ?? "",
  }));

  const endpoints: ApiEndpoint[] = [];
  for (const [pathName, methods] of Object.entries(doc.paths ?? {})) {
    for (const [method, operation] of Object.entries(methods ?? {})) {
      if (!operation) continue;
      endpoints.push({
        method: method.toUpperCase(),
        path: pathName,
        summary: operation.summary ?? operation.operationId ?? "",
        description: operation.description ?? "",
        tag: operation.tags?.[0] ?? "default",
        parameters: (operation.parameters ?? []).map((p) => ({
          name: p.name,
          in: p.in,
          required: Boolean(p.required),
          description: p.description ?? "",
        })),
        requestBodySchema: requestBodySchema(operation),
      });
    }
  }

  endpoints.sort((a, b) => a.path.localeCompare(b.path) || a.method.localeCompare(b.method));
  return { tags, endpoints };
}

export function loadApiReference(): ApiReference {
  for (const file of CANDIDATE_FILES) {
    if (fs.existsSync(file)) {
      return flattenOpenApi(JSON.parse(fs.readFileSync(file, "utf8")) as OpenApiDoc);
    }
  }
  return { tags: [], endpoints: [] };
}
