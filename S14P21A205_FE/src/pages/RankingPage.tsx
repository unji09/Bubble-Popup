import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import AppHeader from "../components/common/AppHeader";
import Badge from "../components/common/Badge";
import Button from "../components/common/Button";
import Podium from "../components/ranking/Podium";
import RankingList from "../components/ranking/RankingList";
import RankingRow from "../components/ranking/RankingRow";
import { getCurrentSeasonFinalRankings, type CurrentSeasonFinalRankingsResponse } from "../api/game";
import { useUserStore } from "../stores/useUserStore";

export default function RankingPage() {
  const navigate = useNavigate();
  const [data, setData] = useState<CurrentSeasonFinalRankingsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getCurrentSeasonFinalRankings()
      .then((res) => {
        console.log("[RankingPage] API response:", res);
        console.log("[RankingPage] rankings:", res.rankings);
        console.log("[RankingPage] myRankings:", res.myRankings);
        setData(res);
      })
      .catch(() => setError("랭킹 정보를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="min-h-screen bg-[#FDFDFB] flex items-center justify-center">
        <p className="text-slate-500 font-medium">랭킹을 불러오는 중...</p>
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="min-h-screen bg-[#FDFDFB] flex flex-col items-center justify-center gap-4">
        <p className="text-slate-500 font-medium">{error ?? "데이터가 없습니다."}</p>
        <Button variant="outline" onClick={() => navigate("/")}>로비로 돌아가기</Button>
      </div>
    );
  }

  // 내 userId 판별: myRankings + rankings 양쪽에서 수집
  const myUserIds = new Set(data.myRankings.map((m) => m.userId));
  // myRankings에 항목이 없어도 rankings에 내 매장이 있을 수 있음 (파산 매장이 rankings에 포함된 경우)
  // → nickname으로 추가 판별
  const myNickname = useUserStore.getState().nickname;
  if (myNickname) {
    for (const r of data.rankings) {
      if (r.nickname === myNickname) myUserIds.add(r.userId);
    }
  }

  const podiumEntries = data.rankings
    .filter((r) => r.rank != null && r.rank <= 3)
    .map((r) => ({ ...r, rank: r.rank as number, isMe: myUserIds.has(r.userId) }));

  const listEntries = data.rankings
    .filter((r) => r.rank != null && r.rank >= 4)
    .map((r) => ({ ...r, isMe: myUserIds.has(r.userId) }));

  // 파산 매장 (rank가 null인 항목) — rankings에 있지만 podium/list에 걸리지 않는 것들
  const bankruptEntries = data.rankings
    .filter((r) => r.rank === null && r.isBankrupt)
    .map((r) => ({ ...r, isMe: myUserIds.has(r.userId) }));

  // 10위 밖인 나의 기록 (myRankings에만 있는 것)
  const myOutsideEntries = data.myRankings.filter(
    (m) => m.rank === null || m.rank > 10
  );

  return (
    <div className="min-h-screen bg-[#FDFDFB] text-slate-900 font-display flex flex-col">
      <AppHeader />

      <main className="flex-1 flex flex-col items-center py-8 px-4 sm:px-6 pt-24">
        <div className="w-full max-w-[1100px] flex flex-col gap-8">
          {/* Top buttons */}
          <div className="flex flex-col sm:flex-row justify-between gap-4 w-full">
            <Button variant="outline" onClick={() => navigate("/")}>
              <span className="material-symbols-outlined text-xl">arrow_back</span>
              로비로 돌아가기
            </Button>
            <Button variant="primary" onClick={() => navigate("/mypage")}>
              <span className="material-symbols-outlined text-xl">history</span>
              시즌 통산 기록 확인하기
            </Button>
          </div>

          {/* Title */}
          <div className="flex flex-col items-center text-center gap-2 mt-4 animate-fade-up">
            <Badge variant="gray" size="md">시즌 {data.seasonId}</Badge>
            <h1 className="text-4xl font-black leading-tight tracking-tight">시즌 랭킹</h1>
            <p className="text-slate-500 font-medium">이번 시즌 최고의 팝업스토어 마스터를 확인하세요.</p>
          </div>

          {/* Podium (1~3위) */}
          <Podium entries={podiumEntries} />

          {/* Full ranking list (4위~) */}
          <RankingList entries={listEntries} />

          {/* 파산 매장 */}
          {bankruptEntries.length > 0 && (
            <div className="flex flex-col gap-4">
              <h2 className="text-lg font-bold text-slate-500 flex items-center gap-2">
                <span className="material-symbols-outlined text-rose-400">dangerous</span>
                파산 매장
              </h2>
              {bankruptEntries.map((entry, idx) => (
                <RankingRow key={`bankrupt-${idx}`} entry={{ ...entry, rank: entry.rank }} animationDelay={600 + idx * 100} />
              ))}
            </div>
          )}

          {/* 나의 기록 (10위 밖) */}
          {myOutsideEntries.length > 0 && (
            <div className="flex flex-col gap-4 pb-12">
              <h2 className="text-lg font-bold text-slate-700 flex items-center gap-2">
                <span className="material-symbols-outlined text-primary">person</span>
                나의 기록
              </h2>
              {myOutsideEntries.map((entry, idx) => (
                <RankingRow key={`my-${idx}`} entry={{ ...entry, rank: entry.rank, isMe: true }} animationDelay={800 + idx * 100} />
              ))}
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
