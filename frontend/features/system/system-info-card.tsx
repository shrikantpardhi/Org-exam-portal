"use client";

import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { useSystemInfo } from "@/hooks/use-system-info";

/**
 * Demonstrates the frontend to backend boundary: the browser calls the API through the Axios
 * client and renders the normalized result. It also establishes the loading, error, and success
 * states that later screens reuse (specs/09-P0-Foundation-V1.5.md §105, §128).
 */
export function SystemInfoCard() {
  const { data, error, isPending, isError, refetch } = useSystemInfo();

  return (
    <Card>
      <CardHeader>
        <CardTitle>Backend connectivity</CardTitle>
        <CardDescription>
          Non-business application information read from the platform API.
        </CardDescription>
      </CardHeader>

      <CardContent>
        {isPending && (
          <p role="status" aria-live="polite" className="text-sm text-muted-foreground">
            Loading application information…
          </p>
        )}

        {isError && (
          <div role="alert" className="space-y-1 text-sm text-destructive">
            <p className="font-medium">{error.message}</p>
            {error.requestId ? (
              <p className="text-muted-foreground">Reference: {error.requestId}</p>
            ) : null}
          </div>
        )}

        {data && (
          <dl className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-2">
            <dt className="text-muted-foreground">Application</dt>
            <dd className="font-medium">{data.application}</dd>
            <dt className="text-muted-foreground">Version</dt>
            <dd className="font-medium">{data.version}</dd>
            <dt className="text-muted-foreground">Environment</dt>
            <dd className="font-medium">{data.environment}</dd>
            <dt className="text-muted-foreground">Server time</dt>
            <dd className="font-medium">{data.timestamp}</dd>
            <dt className="text-muted-foreground">Request id</dt>
            <dd className="font-mono text-xs">{data.requestId ?? "—"}</dd>
          </dl>
        )}
      </CardContent>

      <CardFooter>
        <Button variant="outline" size="sm" onClick={() => void refetch()}>
          Refresh
        </Button>
      </CardFooter>
    </Card>
  );
}
