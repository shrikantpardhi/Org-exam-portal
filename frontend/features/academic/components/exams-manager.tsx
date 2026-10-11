"use client";

import { MasterForm } from "@/features/academic/components/master-form";
import { useArchiveExam, useCreateExam, useExams } from "@/features/academic/hooks/use-academic";
import { examFormSchema } from "@/features/academic/schemas/academic";
import { useAuth } from "@/lib/auth/auth-context";
import type { ApiClientError } from "@/lib/errors";

/**
 * Exam master-data management ({@code specs/11-P2-Academic-V1.5.md §60, §97, §98}).
 *
 * <p>Create and archive actions are shown only when the caller has the permission. This is a UX aid;
 * the backend rechecks every permission.
 */
export function ExamsManager() {
  const { can } = useAuth();
  const exams = useExams();
  const createExam = useCreateExam();
  const archiveExam = useArchiveExam();
  const canArchive = can("EXAM_ARCHIVE");

  return (
    <section className="space-y-4">
      <h2 className="text-lg font-semibold">Exams</h2>

      {can("EXAM_CREATE") ? (
        <MasterForm
          schema={examFormSchema}
          fields={[
            { name: "code", label: "Code" },
            { name: "name", label: "Name" },
            { name: "description", label: "Description" },
          ]}
          submitLabel="Create exam"
          onSubmit={async (values) => {
            await createExam.mutateAsync(values);
          }}
        />
      ) : null}

      {exams.isPending ? (
        <p role="status" className="text-sm text-slate-500">
          Loading exams…
        </p>
      ) : null}
      {exams.isError ? (
        <p role="alert" className="text-sm text-red-600">
          {(exams.error as ApiClientError).message}
        </p>
      ) : null}
      {exams.data && exams.data.length === 0 ? (
        <p className="text-sm text-slate-500">No exams yet.</p>
      ) : null}

      {exams.data && exams.data.length > 0 ? (
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-slate-200">
              <th className="py-2">Code</th>
              <th className="py-2">Name</th>
              <th className="py-2">Status</th>
              {canArchive ? <th className="py-2">Actions</th> : null}
            </tr>
          </thead>
          <tbody>
            {exams.data.map((exam) => (
              <tr key={exam.id} className="border-b border-slate-100">
                <td className="py-2 font-mono text-xs">{exam.code}</td>
                <td className="py-2">{exam.name}</td>
                <td className="py-2">{exam.status}</td>
                {canArchive ? (
                  <td className="py-2">
                    <button
                      type="button"
                      className="text-red-600 underline"
                      onClick={() => void archiveExam.mutateAsync(exam.id)}
                    >
                      Archive
                    </button>
                  </td>
                ) : null}
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
    </section>
  );
}
