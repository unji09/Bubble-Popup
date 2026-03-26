import Badge from "../common/Badge";
import { formatCurrency } from "./utils";

export interface RankingRowEntry {
  rank: number | null;
  nickname: string;
  storeName: string;
  locationName: string;
  menuName: string;
  roi: number;
  totalRevenue: number;
  rewardPoints: number;
  isBankrupt: boolean;
  isMe?: boolean;
}

interface RankingRowProps {
  entry: RankingRowEntry;
  animationDelay?: number;
}

export default function RankingRow({ entry, animationDelay }: RankingRowProps) {
  return (
    <div
      className={`flex items-center rounded-2xl p-4 shadow-soft border transition-shadow hover:shadow-md group relative overflow-hidden animate-slide-in-left ${
        entry.isMe && entry.isBankrupt
          ? "bg-rose-50/60 border-rose-300"
          : entry.isMe
            ? "bg-primary/5 border-primary"
            : entry.isBankrupt
              ? "bg-slate-50 border-slate-100 opacity-60"
              : "bg-white border-slate-50"
      }`}
      style={animationDelay ? { animationDelay: `${animationDelay}ms` } : undefined}
    >
      {entry.isMe && (
        <div className={`absolute left-0 top-0 bottom-0 w-1.5 ${entry.isBankrupt ? "bg-rose-400" : "bg-primary"}`} />
      )}

      {/* Rank */}
      <div className="w-12 flex justify-center mr-4 ml-2">
        <span className={`text-xs font-bold px-2.5 py-1 rounded-full ${
          entry.isMe && entry.isBankrupt
            ? "bg-rose-400 text-white"
            : entry.isMe
              ? "bg-primary text-white"
              : "bg-slate-100 text-slate-500 group-hover:bg-primary group-hover:text-white transition-colors"
        }`}>
          {entry.rank ?? "-"}
        </span>
      </div>

      {/* Avatar */}
      <div className={`size-12 rounded-full bg-slate-100 overflow-hidden mr-4 flex-shrink-0 flex items-center justify-center text-xl ${
        entry.isMe
          ? entry.isBankrupt
            ? "border-2 border-white shadow-sm ring-2 ring-rose-300/40"
            : "border-2 border-white shadow-sm ring-2 ring-primary/30"
          : ""
      }`}>
        <span className="material-symbols-outlined text-2xl text-slate-400">person</span>
      </div>

      {/* Info */}
      <div className="flex flex-col md:flex-row md:items-center flex-1 gap-1 md:gap-4 overflow-hidden">
        <div className="flex flex-col min-w-[140px]">
          <span className="font-bold text-slate-900 text-lg truncate flex items-center gap-1.5">
            {entry.nickname}
            {entry.isBankrupt && <Badge variant="rose" size="sm">파산</Badge>}
          </span>
          {entry.isMe && (
            <span className={`text-xs font-bold ${entry.isBankrupt ? "text-rose-400" : "text-primary"}`}>
              {entry.isBankrupt ? "나 (파산)" : "나 (Player)"}
            </span>
          )}
        </div>
        <div className="flex flex-col text-sm text-slate-500">
          <span className="flex items-center gap-1 font-medium text-slate-700 truncate">
            <span className="material-symbols-outlined text-base">store</span>
            {entry.storeName}
          </span>
          <span className="flex items-center gap-1 text-xs truncate">
            <span className="material-symbols-outlined text-sm">location_on</span>
            {entry.locationName} · {entry.menuName}
          </span>
        </div>
      </div>

      {/* Stats */}
      <div className="flex items-center gap-2 md:gap-6 ml-2">
        <div className="hidden sm:flex flex-col items-end justify-center w-24">
          <span className="text-xs text-slate-400 font-medium mb-0.5">ROI</span>
          <span className={`font-bold ${entry.roi < 0 ? "text-rose-500" : "text-slate-700"}`}>
            {entry.roi.toFixed(1)}%
          </span>
        </div>
        <div className="flex flex-col items-end w-[140px]">
          <span className="font-mono font-bold text-primary text-lg">{formatCurrency(entry.totalRevenue)}</span>
        </div>
        <div className="w-16 flex justify-end">
          <span className="bg-primary/20 text-primary-dark text-xs font-bold px-2.5 py-1 rounded-full">
            {entry.rewardPoints}P
          </span>
        </div>
      </div>
    </div>
  );
}
