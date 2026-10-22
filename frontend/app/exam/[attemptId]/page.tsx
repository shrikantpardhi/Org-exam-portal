import { ExamRuntime } from "@/features/delivery/components/exam-runtime";

/**
 * Runtime examination route ({@code specs/14-P5-Delivery-V1.5.md §73, §75}).
 *
 * <p>The attempt id is the only client input. Every runtime fact — questions, responses, timer and
 * capabilities — is read from the server, so a reload recovers the attempt instead of rebuilding a
 * snapshot, and nothing about the examination is decided by this route.
 */
export default async function ExamPage({ params }: { params: Promise<{ attemptId: string }> }) {
  const { attemptId } = await params;
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-4 py-8">
      <h1 className="text-2xl font-semibold tracking-tight">Examination</h1>
      <ExamRuntime attemptId={attemptId} />
    </main>
  );
}
