import { useState, useEffect } from "react";
import { useOutletContext, useParams, useNavigate } from "react-router-dom";
import type { GameGuardContext } from "../router/GameGuard";
import AppHeader from "../components/common/AppHeader";
import StatCard from "../components/common/StatCard";
import Badge from "../components/common/Badge";
import CountdownTimer from "../components/common/CountdownTimer";
import ProfitChart from "../components/report/ProfitChart";
import WeatherCard from "../components/report/WeatherCard";
import BankruptModal from "../components/report/BankruptModal";
import { getDayReport, getAllDayReports, type GameDayReportResponse } from "../api/game";
import useBrandName from "../hooks/useBrandName";

function getNetProfit(r: GameDayReportResponse) {
  return (r.revenue ?? 0) - (r.totalCost ?? 0);
}

function getIsBankrupt(r: GameDayReportResponse) {
  return (r.consecutiveDeficitDays ?? 0) >= 3;
}

function getReputation(r: GameDayReportResponse) {
  return Math.min((r.capture_rate ?? 0) * 5, 5);
}

function buildChartData(reports: GameDayReportResponse[], currentDay: number) {
  const result = reports.map((r) => ({
    day: r.day,
    value: getNetProfit(r),
    isCurrent: r.day === currentDay,
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

/** 짝수일(2,4,6) 또는 마지막날(7)이면 재고가 전량 폐기 */
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
        if (cancelled) return;
        const reports = allResult.status === "fulfilled" ? allResult.value : [];
        setAllReports(reports);
        if (todayResult.status === "fulfilled") {
          setReport(todayResult.value);
        } else {
          const fallback = reports.find((r) => r.day === day) ?? reports[reports.length - 1] ?? null;
          setReport(fallback);
          if (!fallback) setError("리포트를 불러오지 못했습니다.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => { cancelled = true; };
  }, [day]);

  const [showBankruptModal, setShowBankruptModal] = useState(false);

  if (loading) {
    return (
      <div className="min-h-screen bg-[#FDFDFB] flex flex-col items-center justify-center">
        <AppHeader />
        <p className="text-slate-500 pt-24">리포트를 불러오는 중...</p>
      </div>
    );
  }

  if (error || !report) {
    return (
      <div className="min-h-screen bg-[#FDFDFB] flex flex-col items-center justify-center">
        <AppHeader />
        <p className="text-red-500 pt-24">{error ?? "데이터를 불러올 수 없습니다."}</p>
      </div>
    );
  }

  const chartData = buildChartData(allReports, report.day);
  const todayProfit = getNetProfit(report);
  const isBankrupt = getIsBankrupt(report);
  const disposal = isStockDisposalDay(report.day);
  const reputation = getReputation(report);
  const reputationChange = (report.change_capture_rate ?? 0) * 5;

  const fmt = (v: number) => v < 0 ? `-₩${Math.abs(v).toLocaleString()}` : `₩${v.toLocaleString()}`;

  const stockSubtext = isBankrupt
    ? "압류됨"
    : disposal
      ? "전량 폐기"
      : "다음날 이월";

  return (
    <div className="min-h-screen bg-[#FDFDFB] text-slate-900 font-display flex flex-col">
      <AppHeader />

      {showBankruptModal && (
        <BankruptModal
          onClose={() => {
            setShowBankruptModal(false);
            navigate("/", { state: { showBankruptWarning: true } });
          }}
          isLastDay={report.day === 7}
        />
      )}

      <main className={`flex-1 flex justify-center py-8 px-4 sm:px-10 pt-24 ${showBankruptModal ? "blur-[2px] pointer-events-none" : ""}`}>
        <div className="flex flex-col max-w-[1024px] w-full gap-8">
          {/* Header */}
          <div className="flex flex-col sm:flex-row justify-between items-start sm:items-end gap-4 border-b border-slate-200 pb-6">
            <div className="flex flex-col gap-2">
              <div className="flex items-center gap-2">
                <Badge variant="gray" size="sm">시즌 {report.seasonId}</Badge>
                <Badge variant="green" size="sm">{report.locationName}</Badge>
                <Badge variant="gold" size="sm">{report.menuName}</Badge>
              </div>
              <h1 className="text-4xl font-black leading-tight tracking-tight">
                {brandName}
              </h1>
              {isBankrupt
                ? <p className="text-rose-dark text-base font-medium">Day {report.day} 영업 종료 — 파산 상태</p>
                : <p className="text-slate-500 text-base">Day {report.day} 운영 결과를 확인하세요.</p>}
            </div>
            <div className="flex flex-col items-end gap-1.5">
              <CountdownTimer
                endTimestampMs={guardContext.phaseEndTimestamp}
                label={isBankrupt ? "결과 확인" : "다음날 이동"}
                onComplete={() => isBankrupt ? setShowBankruptModal(true) : undefined}
                variant="pill"
              />
              <span className="text-xs text-slate-400 font-medium pr-1">
                {isBankrupt ? "결과 화면으로 이동" : "다음날로 자동 이동"}
              </span>
            </div>
          </div>

          {/* Warning: 연속 적자 */}
          {report.consecutiveDeficitDays > 0 && (
            <div className={`rounded-xl p-4 flex items-center gap-3 ${
              isBankrupt
                ? "bg-rose-soft border border-rose-dark text-white"
                : "bg-red-50 border border-red-100 text-red-700"
            }`}>
              <span className={`material-symbols-outlined text-2xl ${isBankrupt ? "text-white" : "text-red-500"}`}>warning</span>
              <h3 className="tracking-tight text-lg font-bold">
                {isBankrupt
                  ? `파산했습니다: ${report.consecutiveDeficitDays}일 연속 적자 발생`
                  : `${report.consecutiveDeficitDays}일 연속 적자 중 (3일 연속 시 파산)`}
              </h3>
            </div>
          )}

          {/* 발주일 안내 */}
          {report.isNextDayOrderDay && (
            <div className="rounded-xl p-4 flex items-center gap-3 bg-primary/10 border border-primary/30 text-primary-dark">
              <span className="material-symbols-outlined text-2xl text-primary">local_shipping</span>
              <h3 className="tracking-tight text-base font-bold">내일은 발주일입니다! 재고를 확인하세요.</h3>
            </div>
          )}

          {/* Stats */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard label="매출" value={fmt(report.revenue)} icon="payments" iconBg="bg-green-100" iconColor="text-green-600" />
            <StatCard label="지출" value={fmt(report.totalCost)} icon="shopping_cart_checkout" iconBg="bg-red-100" iconColor="text-red-600" />
            <StatCard
              label="순이익"
              value={fmt(todayProfit)}
              icon={todayProfit >= 0 ? "savings" : "money_off"}
              iconBg={todayProfit >= 0 ? "bg-primary/20" : "bg-rose-100"}
              iconColor={todayProfit >= 0 ? "text-primary-dark" : "text-rose-dark"}
              highlight={todayProfit < 0}
            />
            <StatCard label="방문객 수" value={`${report.visitors}명`} icon="groups" iconBg="bg-slate-100" iconColor="text-slate-600" />
            <StatCard label="평판" value={reputation.toFixed(1)}
              change={{ value: `${reputationChange >= 0 ? "+" : ""}${reputationChange.toFixed(1)}`, positive: reputationChange >= 0 }}
              icon="star" iconBg="bg-yellow-100" iconColor="text-yellow-600" />
            <StatCard label="판매 수량" value={`${report.salesCount}개`} subtext={report.menuName} icon="shopping_bag" iconBg="bg-blue-100" iconColor="text-blue-600" />
            <StatCard label="남은 재고" value={`${report.stockRemaining}개`} subtext={stockSubtext}
              icon="inventory_2" iconBg="bg-purple-100" iconColor="text-purple-600" />
            <StatCard label="폐기된 재고" value={`${report.stockDisposedCount}개`}
              subtext={report.stockDisposedCount > 50 ? "대규모 손실 발생" : "손실 발생"}
              icon="delete" iconBg="bg-slate-100" iconColor="text-slate-500"
              highlight={report.stockDisposedCount > 50} />
          </div>

          {/* Chart + Weather */}
          <div className="grid grid-cols-1 lg:grid-cols-4 gap-4">
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
