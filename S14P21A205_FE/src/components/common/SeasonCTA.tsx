import { Link } from "react-router-dom";
import CountdownTimer from "./CountdownTimer";

type SeasonTone = "active" | "waiting";

interface SeasonCTAProps {
  title: string;
  badgeText: string;
  timerLabel: string;
  ctaLabel: string;
  ctaTo?: string;
  endTimestampMs?: number;
  tone?: SeasonTone;
  disabled?: boolean;
  onCountdownComplete?: () => void;
  onCtaClick?: () => void;
}

export default function SeasonCTA({
  title,
  badgeText,
  timerLabel,
  ctaLabel,
  ctaTo,
  endTimestampMs,
  tone = "active",
  disabled = false,
  onCountdownComplete,
  onCtaClick,
}: SeasonCTAProps) {
  const isWaitingTone = tone === "waiting";
  const isButtonDisabled = disabled || !ctaTo;

  return (
    <div className="relative flex min-h-[400px] w-full flex-col justify-between overflow-hidden rounded-[24px] bg-white p-8 shadow-soft">
      <div className="relative z-10 space-y-6">
        <div className="mb-4 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-[28px] text-primary">
              bubble_chart
            </span>
            <span className="text-sm font-bold uppercase tracking-wider text-slate-400">
              Season
            </span>
          </div>
          <div
            className={`rounded-full px-4 py-1.5 text-xs font-bold uppercase tracking-wider ${
              isWaitingTone
                ? "bg-slate-100 text-slate-500"
                : "bg-primary/10 text-primary-dark"
            }`}
          >
            {badgeText}
          </div>
        </div>

        <h2 className="text-2xl font-bold md:text-3xl">{title}</h2>

        <div className="mt-4 flex flex-col">
          <span className="mb-1 text-sm font-medium text-gray-500">{timerLabel}</span>
          {typeof endTimestampMs === "number" ? (
            <CountdownTimer
              key={`season-${endTimestampMs}`}
              endTimestampMs={endTimestampMs}
              label={timerLabel}
              onComplete={onCountdownComplete}
              variant="display"
              showIcon={false}
            />
          ) : (
            <div className="text-[56px] font-bold leading-none tracking-tight text-slate-300">
              --
            </div>
          )}
        </div>
      </div>

      <div className="relative z-10 mt-8 w-full">
        {!isButtonDisabled ? (
          <Link
            to={ctaTo}
            onClick={onCtaClick}
            className={`group flex h-16 w-full items-center justify-center gap-2 rounded-2xl text-lg font-bold text-white shadow-md transition-all hover:-translate-y-0.5 hover:shadow-lg ${
              isWaitingTone
                ? "bg-slate-600 hover:bg-slate-700"
                : "bg-primary hover:bg-primary-dark"
            }`}
          >
            {ctaLabel}
            <span className="material-symbols-outlined text-[20px] font-bold transition-transform group-hover:translate-x-1">
              arrow_forward
            </span>
          </Link>
        ) : (
          <div className="flex h-16 w-full items-center justify-center rounded-2xl bg-slate-100 text-lg font-bold text-slate-400">
            {ctaLabel}
          </div>
        )}
      </div>

      <div className="pointer-events-none absolute -bottom-10 -right-10 h-64 w-64 rounded-full bg-gradient-to-br from-primary/10 to-transparent opacity-50" />
    </div>
  );
}
