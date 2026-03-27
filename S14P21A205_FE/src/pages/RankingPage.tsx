import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getCurrentSeasonFinalRankings, getSeasonTime, type CurrentSeasonFinalRankingsResponse } from "../api/game";
import AppHeader from "../components/common/AppHeader";
import Badge from "../components/common/Badge";
import Button from "../components/common/Button";
import RankingEmptyState from "../components/ranking/RankingEmptyState";
import Podium from "../components/ranking/Podium";
import RankingList from "../components/ranking/RankingList";
import RankingRow from "../components/ranking/RankingRow";
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

  // --- 서버 시간 동기화 ---
  // 3.1 화면 진입 시 sync (상태 확인용)
  useEffect(() => {
    getSeasonTime().catch(() => {});
  }, []);

  // 3.5 탭 복귀 시 sync
  useEffect(() => {
    const handler = () => {
      if (document.visibilityState === "visible") {
        getSeasonTime().catch(() => {});
      }
    };
    document.addEventListener("visibilitychange", handler);
    return () => document.removeEventListener("visibilitychange", handler);
  }, []);
  // --- 서버 시간 동기화 끝 ---

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
        <Button variant="outline" onClick={() => navigate("/")}>
          로비로 돌아가기
        </Button>
      </div>
    );
  }

  const myUserIds = new Set(data.myRankings.map((entry) => entry.userId));
  const myNickname = useUserStore.getState().nickname;

  if (myNickname) {
    for (const ranking of data.rankings) {
      if (ranking.nickname === myNickname) {
        myUserIds.add(ranking.userId);
      }
    }
  }

  const podiumEntries = data.rankings
    .filter((ranking) => ranking.rank != null && ranking.rank <= 3)
    .map((ranking) => ({
      ...ranking,
      rank: ranking.rank as number,
      isMe: myUserIds.has(ranking.userId),
    }));

  const listEntries = data.rankings
    .filter((ranking) => ranking.rank != null && ranking.rank >= 4)
    .map((ranking) => ({ ...ranking, isMe: myUserIds.has(ranking.userId) }));

  const bankruptEntries = data.rankings
    .filter((ranking) => ranking.rank === null && ranking.isBankrupt)
    .map((ranking) => ({ ...ranking, isMe: myUserIds.has(ranking.userId) }));

  const myOutsideEntries = data.myRankings.filter(
    (ranking) => ranking.rank === null || ranking.rank > 10,
  );
  const isEmptyRankingSeason =
    data.rankings.length === 0 && data.myRankings.length === 0;

  return (
    <div className="min-h-screen bg-[#FDFDFB] text-slate-900 font-display flex flex-col">
      <AppHeader />

      <main className="flex-1 flex flex-col items-center py-8 px-4 pt-24 sm:px-6">
        <div className="w-full max-w-[1100px] flex flex-col gap-8">
          <div className="flex w-full flex-col justify-between gap-4 sm:flex-row">
            <Button variant="outline" onClick={() => navigate("/")}>
              <span className="material-symbols-outlined text-xl">arrow_back</span>
              로비로 돌아가기
            </Button>
            <Button variant="primary" onClick={() => navigate("/mypage")}>
              <span className="material-symbols-outlined text-xl">history</span>
              시즌 통산 기록 확인하기
            </Button>
          </div>

          <div className="mt-4 flex flex-col items-center gap-2 text-center animate-fade-up">
            <Badge variant="gray" size="md">시즌 {data.seasonId}</Badge>
            <h1 className="text-4xl font-black leading-tight tracking-tight">시즌 랭킹</h1>
            <p className="text-slate-500 font-medium">
              이번 시즌 최고의 팝업스토어 마스터를 확인하세요.
            </p>
          </div>

          {isEmptyRankingSeason ? (
            <RankingEmptyState />
          ) : (
            <>
              <Podium entries={podiumEntries} />
              <RankingList entries={listEntries} />

              {bankruptEntries.length > 0 && (
                <div className="flex flex-col gap-4">
                  <h2 className="flex items-center gap-2 text-lg font-bold text-slate-500">
                    <span className="material-symbols-outlined text-rose-400">dangerous</span>
                    파산 매장
                  </h2>
                  {bankruptEntries.map((entry, index) => (
                    <RankingRow
                      key={`bankrupt-${index}`}
                      entry={{ ...entry, rank: entry.rank }}
                      animationDelay={600 + index * 100}
                    />
                  ))}
                </div>
              )}

              {myOutsideEntries.length > 0 && (
                <div className="flex flex-col gap-4 pb-12">
                  <h2 className="flex items-center gap-2 text-lg font-bold text-slate-700">
                    <span className="material-symbols-outlined text-primary">person</span>
                    나의 기록
                  </h2>
                  {myOutsideEntries.map((entry, index) => (
                    <RankingRow
                      key={`my-${index}`}
                      entry={{ ...entry, rank: entry.rank, isMe: true }}
                      animationDelay={800 + index * 100}
                    />
                  ))}
                </div>
              )}
            </>
          )}
        </div>
      </main>
    </div>
  );
}
