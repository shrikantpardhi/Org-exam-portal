import { ExamRunner } from "@/features/delivery/components/exam-runner";

/**
 * Runtime examination route ({@code specs/14-P5-Delivery-V1.5.md §75}).
 *
 * <p>The attempt id is the only client input. All runtime state is read from the server, so a
 * reload recovers the attempt without rebuilding the snapshot.
 */
export default async function ExamPage({ params }: { params: Promise<{ attemptId: string }> }) {
  const { attemptId } = await params;
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-6 py-16">
      <h1 className="text-2xl font-semibold tracking-tight">Examination</h1>
      <ExamRunner attemptId={attemptId} />
    </main>
  );
}
