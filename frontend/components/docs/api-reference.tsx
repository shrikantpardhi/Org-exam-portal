"use client";

import { useMemo, useState } from "react";

import type { ApiEndpoint, ApiTag } from "@/lib/openapi";

const METHOD_STYLE: Record<string, string> = {
 GET: "bg-emerald-100 text-emerald-800",
 POST: "bg-blue-100 text-blue-800",
 PUT: "bg-amber-100 text-amber-800",
 PATCH: "bg-amber-100 text-amber-800",
 DELETE: "bg-red-100 text-red-800",
};

/**
 * Browsable + searchable rendering of the build-time generated API reference ({@code GAP-170}).
 * Endpoints are grouped by tag and expandable to show parameters and request bodies.
 */
export function ApiReference({ endpoints, tags }: { endpoints: ApiEndpoint[]; tags: ApiTag[] }) {
 const [query, setQuery] = useState("");
  const q = query.trim().toLowerCase();

  const filtered = useMemo(() => {
    if (!q) return endpoints;
    return endpoints.filter((endpoint) =>
 [endpoint.method, endpoint.path, endpoint.summary, endpoint.description, endpoint.tag]
 .join(" ")
        .toLowerCase()
        .includes(q),
    );
  }, [endpoints, q]);

  const grouped = useMemo(() => {
    const order = new Map(tags.map((tag, index) => [tag.name, index]));
    const groups = new Map<string, ApiEndpoint[]>();
    for (const endpoint of filtered) {
      const bucket = groups.get(endpoint.tag) ?? [];
      bucket.push(endpoint);
      groups.set(endpoint.tag, bucket);
    }
    return [...groups.entries()].sort((a, b) => {
      const ao = order.get(a[0]);
      const bo = order.get(b[0]);
      if (ao !== undefined && bo !== undefined) return ao - bo;
      if (ao !== undefined) return -1;
      if (bo !== undefined) return 1;
      return a[0].localeCompare(b[0]);
    });
  }, [filtered, tags]);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-3">
        <input
 type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
 placeholder="Filter endpoints…"
 aria-label="Filter API endpoints"
 className="w-full max-w-sm rounded-md border border-input px-3 py-2 text-sm"
        />
        <span className="text-sm text-muted-foreground">
 {filtered.length} endpoint{filtered.length === 1 ? "" : "s"}
        </span>
      </div>

      {grouped.length === 0 ? (
        <p className="text-sm text-muted-foreground">No endpoints match your query.</p>
      ) : null}

      {grouped.map(([tag, list]) => {
        const tagMeta = tags.find((t) => t.name === tag);
        return (
          <section key={tag} className="space-y-2">
            <h2 className="text-xl font-semibold text-foreground">{tag}</h2>
            {tagMeta?.description ? (
              <p className="text-sm text-muted-foreground">{tagMeta.description}</p>
            ) : null}
            <div className="divide-y divide-border rounded-lg border border-border">
              {list.map((endpoint) => (
                <details key={endpoint.method + " " + endpoint.path}>
                  <summary className="flex cursor-pointer flex-wrap items-center gap-3 px-4 py-3 text-sm">
                    <span
 className={
 "rounded px-2 py-0.5 font-mono text-xs font-semibold " +
 (METHOD_STYLE[endpoint.method] ?? "bg-muted text-foreground")
                      }
                    >
                      {endpoint.method}
                    </span>
                    <code className="font-mono text-foreground">{endpoint.path}</code>
                    {endpoint.summary ? (
                      <span className="text-muted-foreground">{endpoint.summary}</span>
                    ) : null}
                  </summary>
                  <div className="space-y-3 px-4 pb-4 text-sm">
                    {endpoint.description ? (
                      <p className="text-muted-foreground">{endpoint.description}</p>
                    ) : null}
                    {endpoint.parameters.length > 0 ? (
                      <div>
                        <p className="mb-1 font-medium text-foreground">Parameters</p>
                        <ul className="space-y-1 text-muted-foreground">
                          {endpoint.parameters.map((param) => (
                            <li key={param.in + " " + param.name}>
                              <code className="font-mono">{param.name}</code> ({param.in})
 {param.required ? " — required" : ""}
 {param.description ? " — " + param.description : ""}
                            </li>
                          ))}
                        </ul>
                      </div>
                    ) : null}
                    {endpoint.requestBodySchema ? (
                      <p className="text-muted-foreground">
                        Request body: <code className="font-mono">{endpoint.requestBodySchema}</code>
                      </p>
                    ) : null}
                  </div>
                </details>
              ))}
            </div>
          </section>
        );
      })}
    </div>
  );
}
