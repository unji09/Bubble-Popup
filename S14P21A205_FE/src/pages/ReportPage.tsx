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

function getNetProfit(report: GameDayReportResponse) {
  return (report.revenue ?? 0) - (report.totalCost ?? 0);
}

function getIsBankrupt(report: GameDayReportResponse) {
  return Boolean(report.isBankrupt);
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

    setLoading(true);
    setError(null);

    Promise.allSettled([getDayReport(day), getAllDayReports(day)])
      .then(([todayResult, allResult]) => {
        if (cancelled) {
          return;
        }

        const reports = allResult.status === "fulfilled" ? allResult.value : [];
        setAllReports(reports);

        if (todayResult.status === "fulfilled") {
          setReport(todayResult.value);
          return;
        }

        const fallback =
          reports.find((item) => item.day === day) ?? reports[reports.length - 1] ?? null;

        setReport(fallback);
        if (!fallback) {
          setError("리포트를 불러오지 못했습니다.");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [day]);

  const handleBankruptExit = () => {
    navigate("/", {
      replace: true,
      state: { showBankruptWarning: true },
    });
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-[#FDFDFB] flex flex-col items-center justify-center">
        <AppHeader />
        <p className="pt-24 text-slate-500">리포트를 불러오는 중...</p>
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
  const disposal = isStockDisposalDay(report.day);
  const reputation = getReputation(report);
  const reputationChange = (report.change_capture_rate ?? 0) * 5;
  const stockSubtext = isBankrupt
    ? "파산으로 영업 종료"
    : disposal
      ? "폐기 대상"
      : "다음 날 이월";

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
                  Day {report.day} 영업 종료, 파산 상태입니다.
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

          {report.consecutiveDeficitDays > 0 && (
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
                {isBankrupt
                  ? `파산했습니다: ${report.consecutiveDeficitDays}일 연속 적자 발생`
                  : `${report.consecutiveDeficitDays}일 연속 적자 중입니다. 3일 연속이면 파산합니다.`}
              </h3>
            </div>
          )}

          {isBankrupt && (
            <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-rose-700">
              <p className="text-sm font-semibold">더 이상 다음 날 영업은 진행할 수 없습니다.</p>
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
            />
          </div>
        </div>
      </main>
    </div>
  );
}
