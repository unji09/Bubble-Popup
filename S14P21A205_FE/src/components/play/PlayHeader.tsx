import { useEffect, useRef, useState } from "react";

type CongestionLevel = "very_crowded" | "crowded" | "normal" | "relaxed" | "very_relaxed";

interface PlayHeaderProps {
  location: string;
  storeName: string;
  menuName: string;
  day: number;
  remainingSeconds: number;
  remainingMilliseconds: number;
  congestion: CongestionLevel;
  guests: number;
  stock: number;
  balance: number;
}

const TOTAL_PLAY_SECONDS = 120;
const TOTAL_PLAY_MILLISECONDS = TOTAL_PLAY_SECONDS * 1000;
const BUSINESS_START_HOUR = 10;
const BUSINESS_END_HOUR = 22;
const BUSINESS_DURATION_MINUTES = (BUSINESS_END_HOUR - BUSINESS_START_HOUR) * 60;

const congestionMap: Record<CongestionLevel, { label: string; color: string }> = {
  very_crowded: { label: "매우 혼잡", color: "text-red-500" },
  crowded: { label: "혼잡", color: "text-amber-500" },
  normal: { label: "보통", color: "text-slate-600" },
  relaxed: { label: "여유", color: "text-blue-500" },
  very_relaxed: { label: "매우 여유", color: "text-blue-400" },
};

function clampMilliseconds(milliseconds: number) {
  return Math.min(TOTAL_PLAY_MILLISECONDS, Math.max(0, milliseconds));
}

function formatCountdown(seconds: number) {
  return String(seconds);
}

function getInGameClock(milliseconds: number) {
  const clampedMilliseconds = clampMilliseconds(milliseconds);
  const elapsedMilliseconds = TOTAL_PLAY_MILLISECONDS - clampedMilliseconds;
  const elapsedMinutes = elapsedMilliseconds * (BUSINESS_DURATION_MINUTES / TOTAL_PLAY_MILLISECONDS);
  const totalMinutes = BUSINESS_START_HOUR * 60 + elapsedMinutes;
  const hours24Float = totalMinutes / 60;
  const hour24 = Math.floor(hours24Float) % 24;
  const minuteFloat = totalMinutes % 60;
  const minuteDisplay = Math.floor(minuteFloat);

  return {
    displayTime: `${String(hour24).padStart(2, "0")}:${String(minuteDisplay).padStart(2, "0")}`,
    hourAngle: hours24Float * 30,
    minuteAngle: totalMinutes * 6,
  };
}

export default function PlayHeader({
  location,
  storeName,
  menuName,
  day,
  remainingSeconds,
  remainingMilliseconds,
  congestion,
  guests,
  stock,
  balance,
}: PlayHeaderProps) {
  const congestionInfo = congestionMap[congestion];
  const formattedBalance = `${balance.toLocaleString()}원`;
  const isUrgent = remainingSeconds <= 10;

  return (
    <header className="sticky top-0 z-50 flex flex-wrap items-center justify-between gap-2 border-b border-white/40 px-4 py-2 shadow-sm glass-panel">
      <style>{`
        @keyframes statPulse {
          0% { transform: scale(1.3); opacity: 0.6; }
          50% { transform: scale(1.1); }
          100% { transform: scale(1); opacity: 1; }
        }
        @keyframes menuBounce {
          0% { transform: scale(0.6) rotate(-8deg); opacity: 0; }
          40% { transform: scale(1.15) rotate(3deg); opacity: 1; }
          60% { transform: scale(0.95) rotate(-1deg); }
          80% { transform: scale(1.05) rotate(0deg); }
          100% { transform: scale(1) rotate(0deg); opacity: 1; }
        }
        @keyframes menuGlow {
          0% { box-shadow: 0 0 0 0 rgba(168,191,169,0.5); }
          50% { box-shadow: 0 0 8px 4px rgba(168,191,169,0.3); }
          100% { box-shadow: 0 0 0 0 rgba(168,191,169,0); }
        }
      `}</style>
      {/* 좌측: 매장 정보 */}
      <div className="flex items-center gap-2 text-sm font-medium text-slate-600 shrink-0">
        <div className="flex items-center gap-1">
          <span className="material-symbols-outlined text-[16px] text-primary">location_on</span>
          <span className="font-bold text-slate-800 whitespace-nowrap">{location}</span>
        </div>
        <div className="h-3.5 w-px bg-slate-300" />
        <div className="flex items-center gap-1">
          <span className="material-symbols-outlined text-[16px] text-primary">storefront</span>
          <span className="font-bold text-slate-800 whitespace-nowrap">{storeName}</span>
        </div>
        <div className="h-3.5 w-px bg-slate-300 hidden sm:block" />
        <MenuBadge menuName={menuName} />
      </div>

      {/* 중앙: DAY + 시계 + 타이머 */}
      <div className="flex items-center gap-2.5 rounded-full bg-white/56 px-3 py-1 backdrop-blur-sm shrink-0">
        <div className="rounded-full border border-primary/20 bg-primary/15 px-2.5 py-0.5">
          <span className="text-sm font-extrabold tracking-[0.08em] text-primary-dark whitespace-nowrap">DAY {day}</span>
        </div>
        <AnalogClock remainingMilliseconds={remainingMilliseconds} />
        <div
          className={`flex flex-col items-center justify-center rounded-xl border py-1 text-center w-[72px] ${
            isUrgent ? "border-red-200 bg-red-50/90" : "border-slate-100 bg-white/92"
          }`}
        >
          <span
            className={`text-[9px] font-bold tracking-[0.18em] ${
              isUrgent ? "text-red-400" : "text-slate-400"
            }`}
          >
            영업중
          </span>
          <span
            className={`font-countdown text-lg font-black tabular-nums leading-none ${
              isUrgent ? "text-red-500" : "text-slate-900"
            }`}
          >
            {formatCountdown(remainingSeconds)}
            <span className="text-xs font-bold ml-0.5">초</span>
          </span>
        </div>
      </div>

      {/* 우측: 통계 */}
      <div className="flex items-center gap-3 sm:gap-5 rounded-xl border border-white/50 bg-white/60 px-3 sm:px-5 py-1.5 shadow-sm backdrop-blur-sm shrink-0">
        <StatItem label="유동인구" icon="groups" value={congestionInfo.label} valueColor={congestionInfo.color} />
        <div className="h-6 w-px bg-slate-200" />
        <StatItem label="일일 방문객" icon="person" value={String(guests)} />
        <div className="h-6 w-px bg-slate-200" />
        <StatItem label="재고" icon="inventory_2" value={String(stock)} />
        <div className="h-6 w-px bg-slate-200" />
        <StatItem label="잔액" icon="account_balance_wallet" value={formattedBalance} />
      </div>
    </header>
  );
}

function AnalogClock({
  remainingMilliseconds,
}: {
  remainingMilliseconds: number;
}) {
  const { displayTime, hourAngle, minuteAngle } = getInGameClock(remainingMilliseconds);
  const rimGradientId = "playClockRimGradient";
  const faceGradientId = "playClockFaceGradient";
  const highlightGradientId = "playClockHighlightGradient";
  const shadowFilterId = "playClockShadow";

  return (
    <div
      aria-label={`현재 인게임 시각 ${displayTime}`}
      className="relative size-[56px] shrink-0"
    >
      <svg viewBox="0 0 120 120" className="size-full">
        <defs>
          <linearGradient id={rimGradientId} x1="18" y1="18" x2="102" y2="102" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="#d3e0d4" />
            <stop offset="55%" stopColor="#A8BFA9" />
            <stop offset="100%" stopColor="#8DA98E" />
          </linearGradient>
          <radialGradient id={faceGradientId} cx="42%" cy="32%">
            <stop offset="0%" stopColor="#fffef9" />
            <stop offset="100%" stopColor="#f6f0df" />
          </radialGradient>
          <linearGradient id={highlightGradientId} x1="40" y1="22" x2="82" y2="68" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="rgba(255,255,255,0.85)" />
            <stop offset="100%" stopColor="rgba(255,255,255,0)" />
          </linearGradient>
          <filter id={shadowFilterId} x="-20%" y="-20%" width="140%" height="140%">
            <feDropShadow dx="0" dy="8" stdDeviation="8" floodColor="rgba(15,23,42,0.14)" />
          </filter>
        </defs>

        <circle
          cx="60"
          cy="60"
          r="56"
          fill={`url(#${rimGradientId})`}
          stroke="#8DA98E"
          strokeWidth="2"
          filter={`url(#${shadowFilterId})`}
        />
        <circle cx="60" cy="60" r="48.5" fill="#ffffff" opacity="0.72" />
        <circle cx="60" cy="60" r="45.5" fill={`url(#${faceGradientId})`} stroke="#f0e4c7" strokeWidth="2" />
        <ellipse cx="48" cy="40" rx="19" ry="11" fill={`url(#${highlightGradientId})`} opacity="0.95" />

        {Array.from({ length: 12 }).map((_, index) => {
          const isMajorTick = index % 3 === 0;

          return (
            <line
              key={index}
              x1="60"
              y1={isMajorTick ? "23" : "26"}
              x2="60"
              y2={isMajorTick ? "12.5" : "18"}
              stroke={isMajorTick ? "#8DA98E" : "#a7b5a7"}
              strokeWidth={isMajorTick ? "3.6" : "2.2"}
              strokeLinecap="round"
              transform={`rotate(${index * 30} 60 60)`}
            />
          );
        })}

        <g
          style={{
            transform: `rotate(${hourAngle}deg)`,
            transformOrigin: "60px 60px",
            transition: "transform 90ms linear",
            willChange: "transform",
          }}
        >
          <line
            x1="60"
            y1="65"
            x2="60"
            y2="37"
            stroke="#445445"
            strokeWidth="6"
            strokeLinecap="round"
          />
        </g>

        <g
          style={{
            transform: `rotate(${minuteAngle}deg)`,
            transformOrigin: "60px 60px",
            transition: "transform 90ms linear",
            willChange: "transform",
          }}
        >
          <line
            x1="60"
            y1="68"
            x2="60"
            y2="24"
            stroke="#8DA98E"
            strokeWidth="4.2"
            strokeLinecap="round"
          />
        </g>

        <circle cx="60" cy="60" r="8" fill="#A8BFA9" stroke="#ffffff" strokeWidth="3" />
        <circle cx="60" cy="60" r="3.2" fill="#6f856f" />
      </svg>
    </div>
  );
}

function StatItem({
  label,
  icon,
  value,
  valueColor,
}: {
  label: string;
  icon: string;
  value: string;
  valueColor?: string;
}) {
  const [animKey, setAnimKey] = useState(0);
  const prevValueRef = useRef(value);

  useEffect(() => {
    if (prevValueRef.current !== value) {
      setAnimKey((k) => k + 1);
      prevValueRef.current = value;
    }
  }, [value]);

  return (
    <div className="flex min-w-[3.75rem] flex-col items-center relative">
      <span className="text-[10px] font-bold tracking-[0.14em] text-slate-400">{label}</span>
      <div className="flex items-center gap-1 whitespace-nowrap">
        <span className="material-symbols-outlined text-[14px] text-slate-400">{icon}</span>
        <span
          key={animKey}
          className={`text-sm font-bold ${valueColor || "text-slate-800"}`}
          style={animKey > 0 ? { animation: "statPulse 0.4s ease-out" } : undefined}
        >
          {value}
        </span>
      </div>
    </div>
  );
}

function MenuBadge({ menuName }: { menuName: string }) {
  const [animKey, setAnimKey] = useState(0);
  const prevRef = useRef(menuName);

  useEffect(() => {
    if (prevRef.current !== menuName && menuName) {
      prevRef.current = menuName;
      setAnimKey((k) => k + 1);
    }
  }, [menuName]);

  return (
    <div className="hidden sm:flex items-center gap-1 relative">
      <span className="material-symbols-outlined text-[16px] text-primary">restaurant_menu</span>
      <span
        key={animKey}
        className="font-bold text-slate-800 whitespace-nowrap rounded-md px-1"
        style={animKey > 0 ? {
          animation: "menuBounce 0.6s ease-out, menuGlow 1.5s ease-out",
        } : undefined}
      >
        {menuName}
      </span>
    </div>
  );
}
