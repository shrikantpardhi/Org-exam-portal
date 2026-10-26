"use client";

import { useQuestions } from "@/features/content/hooks/use-content";
import { useAuth } from "@/lib/auth/auth-context";
import type { ApiClientError } from "@/lib/errors";
import Link from "next/link";
import { useState } from "react";

/**
 * Question list ({@code specs/12-P3-Content-V1.5.md §113, §114, §115}).
 *
 * <p>Filters, search, and permission-aware create action. Full content is not shown in rows.
 */
export function QuestionList() {
  const { can } = useAuth();
  const [search, setSearch] = useState("");
  const questions = useQuestions({ search: search || undefined });

  return (
    <section className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <input
          aria-label="Search questions"
          placeholder="Search title or content"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          className="w-full max-w-sm rounded-md border border-input px-3 py-2 text-sm"
        />
        {can("QUESTION_CREATE") ? (
          <Link href="/questions/new" className="rounded-md bg-primary px-3 py-2 text-sm text-white">
            New question
          </Link>
        ) : null}
      </div>

      {questions.isPending ? (
        <p role="status" className="text-sm text-muted-foreground">
          Loading questions…
        </p>
      ) : null}
      {questions.isError ? (
        <p role="alert" className="text-sm text-red-600">
          {(questions.error as ApiClientError).message}
        </p>
      ) : null}
      {questions.data && questions.data.length === 0 ? (
        <p className="text-sm text-muted-foreground">No questions yet.</p>
      ) : null}

      {questions.data && questions.data.length > 0 ? (
        <div className="overflow-x-auto" role="region" aria-label="Scrollable table" tabIndex={0}>
<table className="w-full min-w-[640px] text-left text-sm">
          <thead>
            <tr className="border-b border-border">
              <th className="py-2">Question</th>
              <th className="py-2">Difficulty</th>
              <th className="py-2">Version</th>
              <th className="py-2">Review</th>
              <th className="py-2">Published</th>
            </tr>
          </thead>
          <tbody>
            {questions.data.map((question) => (
              <tr key={question.id} className="border-b border-border">
                <td className="py-2">
                  <Link href={`/questions/${question.id}`} className="underline">
                    {question.id.slice(0, 8)}…
                  </Link>
                </td>
                <td className="py-2">{question.difficulty ?? "—"}</td>
                <td className="py-2">{question.latestVersion ?? "—"}</td>
                <td className="py-2">{question.latestReviewStatus ?? "—"}</td>
                <td className="py-2">{question.published ? "Yes" : "No"}</td>
              </tr>
            ))}
          </tbody>
        </table>
</div>
      ) : null}
    </section>
  );
}
