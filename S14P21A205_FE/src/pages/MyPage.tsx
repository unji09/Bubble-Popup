import { useEffect, useState } from "react";
import AppHeader from "../components/common/AppHeader";
import Badge from "../components/common/Badge";
import NicknameEditModal from "../components/mypage/NicknameEditModal";
import ProfileSummaryCard from "../components/mypage/ProfileSummaryCard";
import SeasonHistoryCard from "../components/mypage/SeasonHistoryCard";
import SeasonHistoryEmptyState from "../components/mypage/SeasonHistoryEmptyState";
import SeasonSortDropdown from "../components/mypage/SeasonSortDropdown";
import { getUserRecords, type UserRecord } from "../api/user";
import { useUserStore } from "../stores/useUserStore";
import { matchesHangulSearch } from "../utils/hangulSearch";

type RankVariant = "gold" | "gray" | "rose";
type SeasonStatus = "default" | "bankrupt" | "comeback";
type SeasonSortOption = "latest" | "oldest" | "revenue" | "rank";

interface SeasonHistoryItem {
  id: string;
  season: number;
  rank: string;
  rankValue: number | null;
  rankVariant: RankVariant;
  status: SeasonStatus;
  location: string;
  storeName: string;
  revenue: string;
  rewardPoints: string;
}

function toSeasonHistoryItem(record: UserRecord): SeasonHistoryItem {
  const isBankrupt = record.rank === null;
  const rankVariant: RankVariant = record.rank === 1 ? "gold" : isBankrupt ? "rose" : "gray";

  return {
    id: `season-${record.seasonNumber}-${record.rank ?? "bankrupt"}`,
    season: record.seasonNumber,
    rank: isBankrupt ? "파산" : record.rank === null ? "-" : `${record.rank}위`,
    rankValue: isBankrupt || record.rank === null ? null : record.rank,
    rankVariant,
    status: isBankrupt ? "bankrupt" : "default",
    location: record.location,
    storeName: record.popupName,
    revenue: `₩${record.profit.toLocaleString()}`,
    rewardPoints: `${record.rewardPoint.toLocaleString()}P`,
  };
}

const latestSeasonStatusPriority: Record<SeasonStatus, number> = {
  comeback: 0,
  default: 1,
  bankrupt: 2,
};

const oldestSeasonStatusPriority: Record<SeasonStatus, number> = {
  bankrupt: 0,
  default: 1,
  comeback: 2,
};

const seasonSortOptions: { value: SeasonSortOption; label: string }[] = [
  { value: "latest", label: "최신순" },
  { value: "oldest", label: "오래된순" },
  { value: "revenue", label: "수익순" },
  { value: "rank", label: "순위순" },
];

const seasonSortLabels: Record<SeasonSortOption, string> = Object.fromEntries(
  seasonSortOptions.map((option) => [option.value, option.label]),
) as Record<SeasonSortOption, string>;

function parseRevenue(value: string) {
  return Number(value.replace(/[^\d]/g, "")) || 0;
}

export default function MyPage() {
  const nickname = useUserStore((s) => s.nickname) ?? "버블킹";
  const email = useUserStore((s) => s.email) ?? "";
  const updateNickname = useUserStore((s) => s.updateNickname);

  const [seasonHistory, setSeasonHistory] = useState<SeasonHistoryItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isNicknameModalOpen, setIsNicknameModalOpen] = useState(false);
  const [draftNickname, setDraftNickname] = useState(nickname);
  const [nicknameError, setNicknameError] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [sortOption, setSortOption] = useState<SeasonSortOption>("latest");
  const [sortAnimationCycle, setSortAnimationCycle] = useState(0);

  useEffect(() => {
    getUserRecords()
      .then((res) => setSeasonHistory(res.data.records.map(toSeasonHistoryItem)))
      .catch(() => {})
      .finally(() => setIsLoading(false));
  }, []);

  const uniqueSeasonCount = new Set(seasonHistory.map((record) => record.season)).size;
  const rankedSeasons = seasonHistory.filter(
    (season): season is SeasonHistoryItem & { rankValue: number } => season.rankValue !== null,
  );
  const bestRankLabel =
    rankedSeasons.length > 0
      ? `${Math.min(...rankedSeasons.map((season) => season.rankValue))}위`
      : "-";
  const normalizedSearchQuery = searchQuery.trim().toLowerCase();
  const filteredSeasonHistory = [...seasonHistory]
    .filter((record) => {
      if (!normalizedSearchQuery) {
        return true;
      }

      return [record.location, record.storeName].some((value) =>
        matchesHangulSearch(value, normalizedSearchQuery),
      );
    })
    .sort((recordA, recordB) => {
      if (sortOption === "latest") {
        if (recordA.season !== recordB.season) {
          return recordB.season - recordA.season;
        }

        return latestSeasonStatusPriority[recordA.status] - latestSeasonStatusPriority[recordB.status];
      }

      if (sortOption === "oldest") {
        if (recordA.season !== recordB.season) {
          return recordA.season - recordB.season;
        }

        return oldestSeasonStatusPriority[recordA.status] - oldestSeasonStatusPriority[recordB.status];
      }

      if (sortOption === "revenue") {
        const revenueDiff = parseRevenue(recordB.revenue) - parseRevenue(recordA.revenue);

        if (revenueDiff !== 0) {
          return revenueDiff;
        }
      }

      if (sortOption === "rank") {
        const rankA = recordA.rankValue ?? Number.POSITIVE_INFINITY;
        const rankB = recordB.rankValue ?? Number.POSITIVE_INFINITY;
        const rankDiff = rankA - rankB;

        if (rankDiff !== 0) {
          return rankDiff;
        }
      }

      if (recordA.season !== recordB.season) {
        return recordB.season - recordA.season;
      }

      return latestSeasonStatusPriority[recordA.status] - latestSeasonStatusPriority[recordB.status];
    });
  const summaryBadges = [
    `참여 시즌 ${uniqueSeasonCount}회`,
    `최고 순위 ${bestRankLabel}`,
  ];

  const handleSortChange = (nextSortOption: SeasonSortOption) => {
    if (nextSortOption === sortOption) {
      return;
    }

    setSortOption(nextSortOption);
    setSortAnimationCycle((prev) => prev + 1);
  };

  const openNicknameModal = () => {
    setDraftNickname(nickname);
    setNicknameError("");
    setIsNicknameModalOpen(true);
  };

  const closeNicknameModal = () => {
    setDraftNickname(nickname);
    setNicknameError("");
    setIsNicknameModalOpen(false);
  };

  const saveNickname = async () => {
    const nextNickname = draftNickname.trim();

    if (!nextNickname) {
      setNicknameError("닉네임을 입력해 주세요.");
      return;
    }

    try {
      await updateNickname(nextNickname);
      setNicknameError("");
      setIsNicknameModalOpen(false);
    } catch {
      setNicknameError("닉네임 변경에 실패했어요. 다시 시도해 주세요.");
    }
  };

  return (
    <div className="flex min-h-screen flex-col bg-[#FDFDFB] font-display text-slate-900">
      <AppHeader />

      <main className="flex-grow w-full max-w-6xl mx-auto px-4 py-10 pt-24 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 gap-8 lg:grid-cols-[320px_minmax(0,1fr)] lg:items-start">
          <section className="lg:sticky lg:top-24">
            <ProfileSummaryCard
              nickname={nickname}
              email={email}
              summaryBadges={summaryBadges}
              onEditNickname={openNicknameModal}
            />
          </section>

          <section className="space-y-6">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
              <div className="space-y-2">
                <p className="text-sm font-semibold uppercase tracking-[0.24em] text-primary-dark/80">
                  History
                </p>
                <div>
                  <h2 className="text-3xl font-bold tracking-tight text-slate-900">
                    시즌 기록
                  </h2>
                  <p className="mt-2 text-sm leading-6 text-slate-500">
                    위치나 가게 이름으로 검색하고, 원하는 기준으로 정렬해서 기록을 볼 수 있어요.
                  </p>
                </div>
              </div>

              <Badge variant="gray" size="md">
                총 {uniqueSeasonCount}개 시즌
              </Badge>
            </div>

            <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_180px]">
              <label className="flex items-center gap-3 rounded-2xl border border-slate-200 bg-white px-4 py-3 shadow-soft">
                <span className="material-symbols-outlined text-slate-400">search</span>
                <input
                  value={searchQuery}
                  onChange={(event) => setSearchQuery(event.target.value)}
                  className="w-full border-none bg-transparent text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none"
                  placeholder="위치 또는 가게 이름 검색"
                />
              </label>

              <SeasonSortDropdown
                value={sortOption}
                onChange={(value) => handleSortChange(value as SeasonSortOption)}
                options={seasonSortOptions}
              />
            </div>

            {isLoading ? (
              <div className="flex justify-center py-16">
                <p className="text-slate-400 font-medium">기록을 불러오는 중...</p>
              </div>
            ) : seasonHistory.length === 0 ? (
              <SeasonHistoryEmptyState nickname={nickname} />
            ) : filteredSeasonHistory.length > 0 ? (
              <div className="space-y-4">
                <p className="text-sm text-slate-500">
                  검색 결과 {filteredSeasonHistory.length}건 · {seasonSortLabels[sortOption]}
                </p>
                {filteredSeasonHistory.map((season, index) => (
                  <div
                    key={`${sortAnimationCycle}-${season.id}`}
                    className={sortAnimationCycle > 0 ? "history-reorder-enter" : undefined}
                    style={
                      sortAnimationCycle > 0
                        ? { animationDelay: `${Math.min(index, 5) * 35}ms` }
                        : undefined
                    }
                  >
                    <SeasonHistoryCard
                      season={season.season}
                      location={season.location}
                      storeName={season.storeName}
                      revenue={season.revenue}
                      rewardPoints={season.rewardPoints}
                      rank={season.rank}
                      rankVariant={season.rankVariant}
                      status={season.status}
                    />
                  </div>
                ))}
              </div>
            ) : (
              <div className="rounded-[28px] border border-dashed border-slate-300 bg-white/80 p-8 shadow-soft sm:p-10">
                <div className="flex size-14 items-center justify-center rounded-2xl bg-slate-100 text-slate-500">
                  <span className="material-symbols-outlined text-3xl">search_off</span>
                </div>
                <div className="mt-6 space-y-3">
                  <h3 className="text-2xl font-bold tracking-tight text-slate-900">
                    검색 결과가 없어요
                  </h3>
                  <p className="text-sm leading-7 text-slate-500">
                    위치나 가게 이름을 다시 확인해 보세요.
                  </p>
                </div>
              </div>
            )}
          </section>
        </div>
      </main>

      <footer className="mt-12 border-t border-gray-100 bg-white py-8">
        <div className="mx-auto max-w-6xl px-4 text-center text-xs text-gray-400">
          <p>© 2026 BubbleBubble. All rights reserved.</p>
          <div className="mt-2 space-x-4">
            <a className="hover:underline" href="#">
              이용약관
            </a>
            <a className="hover:underline" href="#">
              개인정보처리방침
            </a>
          </div>
        </div>
      </footer>

      <NicknameEditModal
        isOpen={isNicknameModalOpen}
        nickname={draftNickname}
        error={nicknameError}
        onChange={(value) => {
          setDraftNickname(value);
          if (nicknameError) {
            setNicknameError("");
          }
        }}
        onClose={closeNicknameModal}
        onSave={saveNickname}
      />
    </div>
  );
}
