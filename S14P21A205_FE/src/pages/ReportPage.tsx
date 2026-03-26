import { useEffect, useState } from "react";
import { useNavigate, useOutletContext, useParams } from "react-router-dom";
import type { GameGuardContext } from "../router/GameGuard";
import { getAllDayReports, getDayReport, type GameDayReportResponse } from "../api/game";
import AppHeader from "../components/common/AppHeader";
import Badge from "../components/common/Badge";
import Button from "../components/common/Button";
import CountdownTimer from "../components/common/CountdownTimer";
import StatCard from "../components/common/StatCard";
import ProfitChart from "../components/report/ProfitChart";
import WeatherCard from "../components/report/WeatherCard";
import useBrandName from "../hooks/useBrandName";
import { useGameStore } from "../stores/useGameStore";

function getNetProfit(report: GameDayReportResponse) {
  return (report.revenue ?? 0) - (report.totalCost ?? 0);
}

function getIsBankrupt(report: GameDayReportResponse) {
  return Boolean(report.isBankrupt);
}

type BankruptcyReason = "consecutive_deficit" | "rent_unpaid" | null;

function getBankruptcyReason(report: GameDayReportResponse): BankruptcyReason {
  if (!getIsBankrupt(report)) {
    return null;
  }

  return report.consecutiveDeficitDays >= 3 ? "consecutive_deficit" : "rent_unpaid";
}

function getReputation(report: GameDayReportResponse) {
  return Math.min((report.capture_rate ?? 0) * 5, 5);
}

function buildChartData(reports: GameDayReportResponse[], currentDay: number) {
  const result = reports.map((report) => ({
    day: report.day,
    value: getNetProfit(report),
    isCurrent: report.day === currentDay,
    isFuture: false,
  }));

  if (currentDay < 7) {
    result.push({
      day: currentDay + 1,
      value: 0,
      isCurrent: false,
      isFuture: true,
    });
  }

  return result;
}

function isStockDisposalDay(day: number) {
  return day % 2 === 0 || day === 7;
}

export default function ReportPage() {
  const { day: dayParam } = useParams<{ day: string }>();
  const navigate = useNavigate();
  const guardContext = useOutletContext<GameGuardContext>();
  const day = Number(dayParam) || 1;
  const { brandName } = useBrandName();

  const [report, setReport] = useState<GameDayReportResponse | null>(null);
  const [allReports, setAllReports] = useState<GameDayReportResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const timers: ReturnType<typeof setTimeout>[] = [];

    const fetchReport = async (): Promise<boolean> => {
      try {
        const [todayResult, allResult] = await Promise.allSettled([
          getDayReport(day),
          getAllDayReports(day),
        ]);

        if (cancelled) return false;

        const reports = allResult.status === "fulfilled" ? allResult.value : [];
        setAllReports(reports);

        if (todayResult.status === "fulfilled") {
          setReport(todayResult.value);
          setError(null);
          return true;
        }

        const fallback =
          reports.find((item) => item.day === day) ?? reports[reports.length - 1] ?? null;

        setReport(fallback);
        if (fallback) {
          setError(null);
          return true;
        }

        return false;
      } catch {
        return false;
      }
    };

    setLoading(true);
    setError(null);

    // 최소 2초 스켈레톤 + BE 리포트 생성이 늦을 수 있으므로 실패 시 3초 간격 재시도 (최대 4회)
    const MIN_LOADING_MS = 2000;
    const loadStart = Date.now();

    const loadWithRetry = async () => {
      const success = await fetchReport();
      if (cancelled) return;

      if (success) {
        const elapsed = Date.now() - loadStart;
        const remaining = Math.max(0, MIN_LOADING_MS - elapsed);
        const timer = setTimeout(() => {
          if (!cancelled) setLoading(false);
        }, remaining);
        timers.push(timer);
        return;
      }

      let retryCount = 0;
      const scheduleRetry = () => {
        if (cancelled || retryCount >= 4) {
          if (!cancelled) {
            setError("리포트를 불러오지 못했습니다. 새로고침을 시도해주세요.");
            setLoading(false);
          }
          return;
        }
        retryCount++;
        const timer = setTimeout(async () => {
          if (cancelled) return;
          const ok = await fetchReport();
          if (cancelled) return;
          if (ok) {
            setLoading(false);
          } else {
            scheduleRetry();
          }
        }, 3000);
        timers.push(timer);
      };

      scheduleRetry();
    };

    void loadWithRetry();

    return () => {
      cancelled = true;
      for (const t of timers) clearTimeout(t);
    };
  }, [day]);

  const handleBankruptExit = () => {
    if (report?.seasonId != null) {
      useGameStore.getState().setBankruptNoticeSeasonNumber(report.seasonId);
    }

    navigate("/", { replace: true });
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-[#FDFDFB] text-slate-900 font-display flex flex-col">
        <AppHeader />
        <main className="flex-1 flex justify-center px-4 py-8 pt-24 sm:px-10 animate-pulse">
          <div className="w-full max-w-[1024px] space-y-6">
            {/* 타이틀 스켈레톤 */}
            <div className="flex flex-col gap-2 border-b border-slate-200 pb-6">
              <div className="flex items-center gap-2">
                <div className="h-5 w-14 rounded-full bg-slate-200" />
                <div className="h-5 w-16 rounded-full bg-slate-200" />
                <div className="h-5 w-14 rounded-full bg-slate-200" />
              </div>
              <div className="h-8 w-40 rounded bg-slate-200" />
              <div className="h-4 w-56 rounded bg-slate-100" />
            </div>
            {/* 타이머 스켈레톤 */}
            <div className="flex items-center gap-3">
              <div className="h-9 w-28 rounded-full bg-slate-100" />
              <div className="h-4 w-48 rounded bg-slate-100" />
            </div>
            {/* 통계 카드 스켈레톤 */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              {[1, 2, 3, 4].map((i) => (
                <div key={i} className="rounded-2xl border border-slate-100 bg-white p-5 shadow-soft">
                  <div className="h-3 w-12 rounded bg-slate-200 mb-3" />
                  <div className="h-6 w-20 rounded bg-slate-200" />
                  <div className="h-3 w-16 rounded bg-slate-100 mt-2" />
                </div>
              ))}
            </div>
            {/* 차트 스켈레톤 */}
            <div className="rounded-2xl border border-slate-100 bg-white p-6 shadow-soft">
              <div className="h-4 w-32 rounded bg-slate-200 mb-4" />
              <div className="h-48 w-full rounded bg-slate-50" />
            </div>
          </div>
        </main>
      </div>
    );
  }

  if (error || !report) {
    return (
      <div className="min-h-screen bg-[#FDFDFB] flex flex-col items-center justify-center">
        <AppHeader />
        <p className="pt-24 text-red-500">{error ?? "데이터를 불러올 수 없습니다."}</p>
      </div>
    );
  }

  const chartData = buildChartData(allReports, report.day);
  const todayProfit = getNetProfit(report);
  const isBankrupt = getIsBankrupt(report);
  const bankruptcyReason = getBankruptcyReason(report);
  const disposal = isStockDisposalDay(report.day);
  const reputation = getReputation(report);
  const reputationChange = (report.change_capture_rate ?? 0) * 5;
  const stockSubtext = bankruptcyReason === "rent_unpaid"
    ? "임대료 미납으로 영업 종료"
    : bankruptcyReason === "consecutive_deficit"
      ? "3일 연속 적자로 영업 종료"
      : disposal
        ? "폐기 대상"
        : "다음 날 이월";
  const bankruptStatusMessage = bankruptcyReason === "rent_unpaid"
    ? `Day ${report.day} 영업 종료, 임대료를 내지 못해 바로 파산했습니다.`
    : `Day ${report.day} 영업 종료, 파산 상태입니다.`;
  const showBankruptcyWarning = report.consecutiveDeficitDays > 0 || isBankrupt;
  const warningMessage = isBankrupt
    ? bankruptcyReason === "rent_unpaid"
      ? "임대료를 내지 못해 즉시 파산했습니다."
      : `파산했습니다: ${report.consecutiveDeficitDays}일 연속 적자 발생`
    : `${report.consecutiveDeficitDays}일 연속 적자 중입니다. 3일 연속이면 파산합니다.`;
  const bankruptInfoMessage = bankruptcyReason === "rent_unpaid"
    ? "임대료를 내지 못해 더 이상 다음 날 영업은 진행할 수 없습니다."
    : "3일 연속 적자로 더 이상 다음 날 영업은 진행할 수 없습니다.";
  const weatherDisabledMessage = bankruptcyReason === "rent_unpaid"
    ? "임대료를 내지 못해 매장이 바로 폐업되었습니다."
    : "3일 연속 적자로 매장이 폐업되었습니다.";

  const formatCurrency = (value: number) => {
    const absolute = Math.abs(value).toLocaleString();
    return value < 0 ? `-${absolute}원` : `${absolute}원`;
  };

  return (
    <div className="min-h-screen bg-[#FDFDFB] text-slate-900 font-display flex flex-col">
      <AppHeader />

      <main className="flex-1 flex justify-center px-4 py-8 pt-24 sm:px-10">
        <div className="flex w-full max-w-[1024px] flex-col gap-8">
          <div className="flex flex-col items-start justify-between gap-4 border-b border-slate-200 pb-6 sm:flex-row sm:items-end">
            <div className="flex flex-col gap-2">
              <div className="flex items-center gap-2">
                <Badge variant="gray" size="sm">시즌 {report.seasonId}</Badge>
                <Badge variant="green" size="sm">{report.locationName}</Badge>
                <Badge variant="gold" size="sm">{report.menuName}</Badge>
              </div>
              <h1 className="text-4xl font-black leading-tight tracking-tight">
                {brandName}
              </h1>
              {isBankrupt ? (
                <p className="text-base font-medium text-rose-dark">
                  {bankruptStatusMessage}
                </p>
              ) : (
                <p className="text-base text-slate-500">
                  Day {report.day} 운영 결과를 확인하세요.
                </p>
              )}
            </div>

            <div className="flex flex-col items-end gap-3">
              <div className="flex flex-col items-end gap-1.5">
                <CountdownTimer
                  endTimestampMs={guardContext.phaseEndTimestamp}
                  label={isBankrupt ? "로비 이동까지 남은 시간" : "다음 날 이동까지 남은 시간"}
                  onComplete={isBankrupt ? handleBankruptExit : undefined}
                  variant="pill"
                />
                <span className="pr-1 text-xs font-medium text-slate-400">
                  {isBankrupt ? "시간이 끝나면 로비로 이동합니다." : "시간이 끝나면 다음 날로 이동합니다."}
                </span>
              </div>

              {isBankrupt && (
                <Button variant="danger" onClick={handleBankruptExit}>
                  나가기
                </Button>
              )}
            </div>
          </div>

          <div className="flex items-center gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4 text-slate-700">
            <span className="material-symbols-outlined text-2xl text-slate-500">receipt_long</span>
            <h3 className="text-base font-bold tracking-tight">
              Day {report.day} 영업 마감 후 일일 임대료가 정산되었습니다
            </h3>
          </div>

          {showBankruptcyWarning && (
            <div
              className={`flex items-center gap-3 rounded-xl p-4 ${
                isBankrupt
                  ? "border border-rose-dark bg-rose-soft text-white"
                  : "border border-red-100 bg-red-50 text-red-700"
              }`}
            >
              <span
                className={`material-symbols-outlined text-2xl ${
                  isBankrupt ? "text-white" : "text-red-500"
                }`}
              >
                warning
              </span>
              <h3 className="text-lg font-bold tracking-tight">
                {warningMessage}
              </h3>
            </div>
          )}

          {isBankrupt && (
            <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-rose-700">
              <p className="text-sm font-semibold">{bankruptInfoMessage}</p>
              <p className="mt-1 text-sm">리포트를 확인한 뒤 로비로 이동해 주세요.</p>
            </div>
          )}

          {report.isNextDayOrderDay && !isBankrupt && (
            <div className="flex items-center gap-3 rounded-xl border border-primary/30 bg-primary/10 p-4 text-primary-dark">
              <span className="material-symbols-outlined text-2xl text-primary">local_shipping</span>
              <h3 className="text-base font-bold tracking-tight">
                내일은 발주일입니다. 재고를 확인하세요.
              </h3>
            </div>
          )}

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard
              label="매출"
              value={formatCurrency(report.revenue)}
              icon="payments"
              iconBg="bg-green-100"
              iconColor="text-green-600"
            />
            <StatCard
              label="지출"
              value={formatCurrency(report.totalCost)}
              icon="shopping_cart_checkout"
              iconBg="bg-red-100"
              iconColor="text-red-600"
            />
            <StatCard
              label="순이익"
              value={formatCurrency(todayProfit)}
              icon={todayProfit >= 0 ? "savings" : "money_off"}
              iconBg={todayProfit >= 0 ? "bg-primary/20" : "bg-rose-100"}
              iconColor={todayProfit >= 0 ? "text-primary-dark" : "text-rose-dark"}
              highlight={todayProfit < 0}
            />
            <StatCard
              label="방문객 수"
              value={`${report.visitors}명`}
              icon="groups"
              iconBg="bg-slate-100"
              iconColor="text-slate-600"
            />
            <StatCard
              label="평판"
              value={reputation.toFixed(1)}
              change={{
                value: `${reputationChange >= 0 ? "+" : ""}${reputationChange.toFixed(1)}`,
                positive: reputationChange >= 0,
              }}
              icon="star"
              iconBg="bg-yellow-100"
              iconColor="text-yellow-600"
            />
            <StatCard
              label="판매 수량"
              value={`${report.salesCount}개`}
              subtext={report.menuName}
              icon="shopping_bag"
              iconBg="bg-blue-100"
              iconColor="text-blue-600"
            />
            <StatCard
              label="남은 재고"
              value={`${report.stockRemaining}개`}
              subtext={stockSubtext}
              icon="inventory_2"
              iconBg="bg-purple-100"
              iconColor="text-purple-600"
            />
            <StatCard
              label="폐기 재고"
              value={`${report.stockDisposedCount}개`}
              subtext={report.stockDisposedCount > 50 ? "대규모 폐기 발생" : "폐기 발생"}
              icon="delete"
              iconBg="bg-slate-100"
              iconColor="text-slate-500"
              highlight={report.stockDisposedCount > 50}
            />
          </div>

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-4">
            <ProfitChart data={chartData} isBankrupt={isBankrupt} />
            <WeatherCard
              condition={report.tomorrowWeather?.condition ?? null}
              disabled={isBankrupt}
              disabledMessage={weatherDisabledMessage}
            />
          </div>
        </div>
      </main>
    </div>
  );
}
