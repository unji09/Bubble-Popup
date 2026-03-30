import { type ReactNode } from "react";

interface GuideOverlayProps {
  title: string;
  description: string | ReactNode;
  children: ReactNode;
  position?: "top" | "bottom";
}

export default function GuideOverlay({ title, description, children, position = "top" }: GuideOverlayProps) {
  const bubble = (
    <div className="flex items-start gap-3 p-5 bg-primary/5 border border-primary/20 rounded-2xl mb-4">
      {/* 캐릭터 아이콘 */}
      <div className="flex items-center justify-center w-10 h-10 rounded-full bg-primary/20 shrink-0">
        <span className="material-symbols-outlined text-primary text-xl">smart_toy</span>
      </div>
      <div className="flex-1 min-w-0">
        <h3 className="text-base font-bold text-slate-800 mb-1">{title}</h3>
        <div className="text-sm text-slate-600 leading-relaxed">{description}</div>
      </div>
    </div>
  );

  return (
    <div className="flex flex-col h-full">
      {position === "top" && bubble}
      <div className="flex-1 min-h-0 overflow-auto">{children}</div>
      {position === "bottom" && bubble}
    </div>
  );
}
