interface ErrorPageDetailsProps {
  code?: string | null;
  message?: string | null;
  path?: string | null;
}

export default function ErrorPageDetails({ code, message, path }: ErrorPageDetailsProps) {
  if (!code && !message && !path) {
    return null;
  }

  return (
    <div className="space-y-2">
      {message ? (
        <p className="mx-auto max-w-[420px] break-keep text-sm leading-6 text-slate-500">{message}</p>
      ) : null}
      {code || path ? (
        <p className="font-mono text-[11px] uppercase tracking-[0.18em] text-slate-400">
          {[code, path].filter(Boolean).join(" | ")}
        </p>
      ) : null}
    </div>
  );
}
