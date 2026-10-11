import { NextResponse } from "next/server";

/**
 * Health endpoint for the frontend container.
 *
 * Used by the container health check so that "container started" is not confused with
 * "service ready".
 */
export function GET() {
  return NextResponse.json({ status: "UP" });
}
