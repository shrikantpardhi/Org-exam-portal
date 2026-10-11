"use client";

import Link from "next/link";

import { useAssessments } from "@/features/assessment/hooks/use-assessment";
import type { ApiClientError } from "@/lib/errors";

/** Assessment list ({@code specs/13-P4-Assessment-V1.5.md §100, §114}). */
export function AssessmentList() {
  const assessments = useAssessments();

  return (
    <section className="space-y-4">
      {assessments.isPending ? <p role="status">Loading assessments…</p> : null}
      {assessments.isError ? (
        <p role="alert">{(assessments.error as ApiClientError).message}</p>
      ) : null}
      {assessments.data && assessments.data.length === 0 ? (
        <p className="text-sm text-slate-500">No assessments yet.</p>
      ) : null}

      {assessments.data && assessments.data.length > 0 ? (
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-slate-200">
              <th className="py-2">Code</th>
              <th className="py-2">Name</th>
              <th className="py-2">Status</th>
            </tr>
          </thead>
          <tbody>
            {assessments.data.map((assessment) => (
              <tr key={assessment.id} className="border-b border-slate-100">
                <td className="py-2 font-mono text-xs">{assessment.code}</td>
                <td className="py-2">
                  <Link href={`/assessments/${assessment.id}`} className="underline">
                    {assessment.name}
                  </Link>
                </td>
                <td className="py-2">{assessment.status}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
    </section>
  );
}
