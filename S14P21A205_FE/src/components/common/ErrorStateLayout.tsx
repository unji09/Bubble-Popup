import type { ReactNode } from "react";
import FloatingBubbles from "./FloatingBubbles";
import GuestHeader from "./GuestHeader";

type ErrorAction = {
  label: string;
  onClick: () => void;
  variant?: "primary" | "secondary";
};

interface ErrorStateLayoutProps {
  code: string;
  title: string;
  description: string;
  badge?: string;
  primaryAction: ErrorAction;
  secondaryAction?: ErrorAction;
  footer?: ReactNode;
}

function actionClassName(variant: ErrorAction["variant"] = "primary") {
  if (variant === "secondary") {
    return "border border-[#D7DBD1] bg-white text-slate-700 hover:bg-[#F8F8F5]";
  }

  return "bg-primary text-white hover:bg-primary-dark shadow-sm";
}

export default function ErrorStateLayout({
  code,
  title,
  description,
  badge,
  primaryAction,
  secondaryAction,
  footer,
}: ErrorStateLayoutProps) {
  return (
    <div className="relative min-h-screen overflow-hidden bg-[#FDFDFB] text-slate-900 font-display">
      <FloatingBubbles />
      <GuestHeader />

      <main className="relative z-10 flex min-h-screen items-center justify-center px-4 pb-12 pt-28">
        <section className="glass-panel w-full max-w-[560px] rounded-[32px] px-8 py-10 text-center shadow-[0_24px_60px_rgba(61,80,64,0.12)] md:px-12">
          {badge ? (
            <div className="mb-5 inline-flex rounded-full border border-[#D7DBD1] bg-white/80 px-4 py-1.5 text-xs font-semibold uppercase tracking-[0.24em] text-[#6E7C69]">
              {badge}
            </div>
          ) : null}

          <div className="mb-4 text-[72px] font-black leading-none tracking-[-0.06em] text-[#9AA894] md:text-[88px]">
            {code}
          </div>

          <h1 className="mb-3 text-3xl font-black tracking-tight text-[#314235] md:text-4xl">{title}</h1>
          <p className="mx-auto max-w-[420px] break-keep text-sm leading-7 text-slate-600 md:text-base">{description}</p>

          <div className="mt-8 flex flex-col gap-3 sm:flex-row sm:justify-center">
            <button
              type="button"
              onClick={primaryAction.onClick}
              className={`min-w-[160px] rounded-full px-6 py-3 text-sm font-bold transition-colors ${actionClassName(primaryAction.variant)}`}
            >
              {primaryAction.label}
            </button>

            {secondaryAction ? (
              <button
                type="button"
                onClick={secondaryAction.onClick}
                className={`min-w-[160px] rounded-full px-6 py-3 text-sm font-bold transition-colors ${actionClassName(
                  secondaryAction.variant ?? "secondary",
                )}`}
              >
                {secondaryAction.label}
              </button>
            ) : null}
          </div>

          {footer ? <div className="mt-6 text-sm text-slate-500">{footer}</div> : null}
        </section>
      </main>
    </div>
  );
}
