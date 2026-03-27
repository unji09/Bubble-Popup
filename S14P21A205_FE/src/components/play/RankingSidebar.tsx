import { useMemo, useState } from "react";
import { formatMoneyUnit } from "../../utils/formatMoneyUnit";

export interface RankEntry {
  id: string;
  name: string;
  storeName: string;
  revenue: number;
  roi: number;
  isMe?: boolean;
}

interface RankingSidebarProps {
  rankings: RankEntry[];
}

const medalMap: Record<number, string> = {
  1: "🥇",
  2: "🥈",
  3: "🥉",
};

export default function RankingSidebar({ rankings }: RankingSidebarProps) {
  const [expanded, setExpanded] = useState(true);
  const [showTooltip, setShowTooltip] = useState(false);

  const sortedRankings = useMemo(
    () => [...rankings].sort((left, right) => right.roi - left.roi),
    [rankings],
  );

  return (
    <aside className="absolute top-20 left-4 z-10 w-72 pointer-events-none">
      <div className="glass-panel rounded-2xl shadow-lg pointer-events-auto overflow-visible">
        <div className="flex items-start justify-between gap-3 px-4 py-3">
          <div className="min-w-0">
            <div className="flex items-center gap-1.5">
              <div className="relative">
                <button
                  type="button"
                  onClick={() => setShowTooltip((prev) => !prev)}
                  className="flex items-center gap-2 text-sm font-bold text-slate-800"
                  aria-label="ROI 기준 안내 보기"
                >
                  <span className="inline-flex items-center justify-center size-6 rounded-full bg-primary/10 hover:bg-primary/20 transition-colors">
                    <span className="material-symbols-outlined text-[16px] text-primary">leaderboard</span>
                  </span>
                  실시간 랭킹
                </button>
                {showTooltip && (
                  <div className="absolute left-0 top-8 z-20 w-56 rounded-2xl border border-slate-200 bg-white px-3.5 py-3 text-[12px] leading-relaxed text-slate-600 shadow-lg">
                    실제 순위는 ROI 기준으로 산정됩니다.
                    <br />
                    ROI는 투자 대비 수익률로, 수익을 투자금으로 나눈 값입니다.
                  </div>
                )}
              </div>
            </div>
          </div>

          <button
            type="button"
            onClick={() => setExpanded((prev) => !prev)}
            className="shrink-0 text-slate-400 transition-colors hover:text-slate-600"
            aria-label={expanded ? "실시간 랭킹 접기" : "실시간 랭킹 펼치기"}
          >
            <span
              className={`material-symbols-outlined text-[18px] transition-transform duration-200 ${
                expanded ? "rotate-180" : ""
              }`}
            >
              expand_more
            </span>
          </button>
        </div>

        {expanded && (
          <div className="space-y-1 px-3 pb-3">
            {sortedRankings.map((entry, index) => {
              const displayRank = index + 1;

              return (
                <div
                  key={entry.id}
                  className={`flex items-center gap-2.5 rounded-lg px-3 py-2.5 transition-colors ${
                    entry.isMe ? "bg-primary/8 ring-1 ring-primary/10" : "hover:bg-white/50"
                  }`}
                >
                  <div className="w-7 shrink-0 text-center flex flex-col items-center">
                    {medalMap[displayRank] ? (
                      <span className="text-base leading-none">{medalMap[displayRank]}</span>
                    ) : (
                      <span className="text-[12px] font-bold text-slate-400">{displayRank}위</span>
                    )}
                  </div>

                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-1">
                      <p
                        className={`truncate text-[12px] font-bold ${
                          entry.isMe ? "text-primary-dark" : "text-slate-700"
                        }`}
                      >
                        {entry.name}
                      </p>
                      {entry.isMe && (
                        <span className="rounded-full bg-primary/10 px-1.5 py-0.5 text-[9px] font-bold text-primary">
                          YOU
                        </span>
                      )}
                    </div>
                    <p className="truncate text-[10px] text-slate-400">{entry.storeName}</p>
                  </div>

                  <div className="w-[4.75rem] shrink-0 text-right leading-tight">
                    <div className="flex items-baseline justify-end gap-1 whitespace-nowrap">
                      <span className="text-[8px] font-bold tracking-[0.1em] text-slate-400">ROI</span>
                      <span
                        className={`font-countdown text-[13px] font-bold tabular-nums ${
                          entry.roi < 0
                            ? "text-rose-500"
                            : entry.isMe
                              ? "text-primary-dark"
                              : "text-slate-700"
                        }`}
                      >
                        {entry.roi.toFixed(1)}%
                      </span>
                    </div>
                    <div className="mt-0.5 flex items-center justify-end gap-1 whitespace-nowrap text-[9px]">
                      <span className="font-bold tracking-[0.08em] text-slate-400">수익</span>
                      <span className="font-countdown font-semibold text-slate-500">
                        {formatMoneyUnit(entry.revenue)}
                      </span>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </aside>
  );
}
