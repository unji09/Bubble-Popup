import { useEffect, useRef, useState, type CSSProperties } from "react";

export type AlertType = "event" | "bad_event" | "deadline" | "stock" | "action";

export interface GameAlert {
  id: number;
  type: AlertType;
  title: string;
  description: string;
  createdAt: number;
  /** 설정하면 상대시간 대신 이 문자열을 표시 */
  timeLabel?: string;
}

function getRelativeTime(createdAt: number): string {
  const diff = Math.floor((Date.now() - createdAt) / 1000);
  if (diff < 5) return "방금 전";
  if (diff < 60) return `${diff}초 전`;
  if (diff < 3600) return `${Math.floor(diff / 60)}분 전`;
  return `${Math.floor(diff / 3600)}시간 전`;
}

interface EventSidebarProps {
  alerts: GameAlert[];
}

const typeIcons: Record<AlertType, string> = {
  event: "celebration",
  bad_event: "warning",
  deadline: "alarm",
  stock: "inventory_2",
  action: "task_alt",
};

const typeIconStyles: Record<AlertType, { chip: string; icon: string }> = {
  event: {
    chip: "bg-cozy-primary/10",
    icon: "text-cozy-primary",
  },
  bad_event: {
    chip: "bg-orange-50",
    icon: "text-orange-500",
  },
  deadline: {
    chip: "bg-accent-rose/15",
    icon: "text-rose-dark",
  },
  stock: {
    chip: "bg-amber-50",
    icon: "text-amber-600",
  },
  action: {
    chip: "bg-primary/15",
    icon: "text-primary-dark",
  },
};

const titleClampStyle: CSSProperties = {
  display: "-webkit-box",
  overflow: "hidden",
  wordBreak: "keep-all",
  overflowWrap: "break-word",
  WebkitBoxOrient: "vertical",
  WebkitLineClamp: 2,
};

const descriptionClampStyle: CSSProperties = {
  display: "-webkit-box",
  overflow: "hidden",
  wordBreak: "keep-all",
  overflowWrap: "break-word",
  WebkitBoxOrient: "vertical",
  WebkitLineClamp: 2,
};

const slideInBounceStyle = `
@keyframes slideInBounce {
  0% { transform: translateX(100%); opacity: 0; }
  50% { transform: translateX(-8px); opacity: 1; }
  70% { transform: translateX(4px); }
  100% { transform: translateX(0); opacity: 1; }
}
.alert-slide-in {
  animation: slideInBounce 0.5s cubic-bezier(0.22, 1, 0.36, 1) forwards;
}
`;

export default function EventSidebar({ alerts }: EventSidebarProps) {
  const [expanded, setExpanded] = useState(false);
  const [, setTick] = useState(0);
  const [newAlertIds, setNewAlertIds] = useState<Set<number>>(new Set());
  const prevAlertCountRef = useRef(alerts.length);
  const latest = alerts[0];

  // 새 알림 감지 → 애니메이션 트리거
  useEffect(() => {
    if (alerts.length > prevAlertCountRef.current) {
      const newIds = new Set(alerts.slice(0, alerts.length - prevAlertCountRef.current).map((a) => a.id));
      setNewAlertIds(newIds);
      const timer = window.setTimeout(() => setNewAlertIds(new Set()), 600);
      prevAlertCountRef.current = alerts.length;
      return () => window.clearTimeout(timer);
    }
    prevAlertCountRef.current = alerts.length;
  }, [alerts]);

  useEffect(() => {
    const timer = window.setInterval(() => setTick((t) => t + 1), 5_000);
    return () => window.clearInterval(timer);
  }, []);

  return (
    <aside className="absolute top-20 right-4 z-10 w-72 pointer-events-none">
      <style>{slideInBounceStyle}</style>
      <div className="glass-panel rounded-2xl shadow-lg pointer-events-auto overflow-hidden">
        <button
          type="button"
          onClick={() => setExpanded((prev) => !prev)}
          className="w-full px-4 py-3 flex items-center justify-between hover:bg-white/40 transition-colors"
        >
          <h2 className="font-bold text-slate-800 text-sm flex items-center gap-2">
            <span className="material-symbols-outlined text-primary text-[18px]">notifications_active</span>
            실시간 알림
          </h2>
          <span className={`material-symbols-outlined text-slate-400 text-[18px] transition-transform duration-200 ${expanded ? "rotate-180" : ""}`}>
            expand_more
          </span>
        </button>

        {latest && !expanded && (
          <div className={`px-4 pb-3 ${newAlertIds.has(latest.id) ? "alert-slide-in" : ""}`}>
            <AlertCard alert={latest} />
          </div>
        )}

        {expanded && (
          <div className="px-4 pb-3 space-y-2 max-h-[400px] overflow-y-auto custom-scrollbar">
            {alerts.map((alert) => (
              <div key={alert.id} className={newAlertIds.has(alert.id) ? "alert-slide-in" : ""}>
                <AlertCard alert={alert} />
              </div>
            ))}
          </div>
        )}
      </div>
    </aside>
  );
}

function AlertCard({ alert }: { alert: GameAlert }) {
  const iconStyle = typeIconStyles[alert.type];

  return (
    <div className="rounded-xl border border-slate-200 bg-white px-3 py-3 shadow-sm">
      <div className="flex items-start gap-2.5">
        <div className={`mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full ${iconStyle.chip}`}>
          <span className={`material-symbols-outlined text-[15px] ${iconStyle.icon}`}>{typeIcons[alert.type]}</span>
        </div>

        <div className="min-w-0 flex-1">
          <div className="flex items-start gap-2">
            <p
              className="min-w-0 flex-1 whitespace-normal text-[12px] font-bold leading-[1.35] text-slate-700"
              style={titleClampStyle}
            >
              {alert.title}
            </p>
            <span className="mt-0.5 shrink-0 text-[10px] leading-none text-slate-400">{alert.timeLabel ?? getRelativeTime(alert.createdAt)}</span>
          </div>

          <p
            className="mt-1 whitespace-normal text-[11px] leading-[1.45] text-slate-500"
            style={descriptionClampStyle}
          >
            {alert.description}
          </p>
        </div>
      </div>
    </div>
  );
}
