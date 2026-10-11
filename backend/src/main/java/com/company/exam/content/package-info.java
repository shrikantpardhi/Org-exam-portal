/**
 * Content module boundary.
 *
 * <p>Owns Question, QuestionVersion, QuestionTemplate, QuestionOption, Asset, Explanation, Hint,
 * PreviousExamHistory, and QuestionTag. Responsible for authoring, versioning, taxonomy mapping,
 * review, approval, publication, and content metadata.
 *
 * <p>Renderable content uses the single contract {@code MARKDOWN_LATEX_V1}
 * ({@code docs/decisions/ADR-0008-markdown-latex-content-boundary.md}). P0 establishes this
 * boundary only; the module is implemented in P3.
 *
 * <p>See {@code specs/06-Architecture-Freeze-V1.5.md §33}.
 */
package com.company.exam.content;
