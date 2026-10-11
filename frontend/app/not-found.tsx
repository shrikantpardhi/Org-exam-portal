import Link from "next/link";

export default function NotFound() {
  return (
    <main className="mx-auto flex w-full max-w-xl flex-col items-start gap-4 px-6 py-24">
      <h1 className="text-xl font-semibold">Page not found</h1>
      <p className="text-sm text-slate-600">
        The page you requested does not exist.
      </p>
      <Link className="text-sm font-medium underline" href="/">
        Return to the home page
      </Link>
    </main>
  );
}
