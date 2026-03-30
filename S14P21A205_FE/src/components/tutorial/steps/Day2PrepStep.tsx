import { useState } from "react";
import GuideOverlay from "../GuideOverlay";
import { MOCK_AI_NEWS_ITEMS, MOCK_NEWS_RANKINGS_DAY2, MOCK_STOCK_TIMELINE } from "../mockData";
import CozyNewspaper from "../../game/CozyNewspaper";

type Tab = "news" | "stock";

export default function Day2PrepStep() {
  const [tab, setTab] = useState<Tab>("news");
  const [expandedNewsId, setExpandedNewsId] = useState<number | null>(MOCK_AI_NEWS_ITEMS[0]?.id ?? null);

  return (
    <GuideOverlay
      title="2일차 영업 준비"
      description={
        <span>
          2일차부터는 AI가 전날 데이터를 분석해 <strong>뉴스</strong>를 만들어줘요.
          뉴스를 읽고 전략을 세워보세요!
        </span>
      }
    >
      <div className="flex flex-col gap-6">
        {/* Page Header */}
        <div className="flex flex-col gap-5">
          <div className="flex flex-col gap-2.5">
            <div className="flex items-center gap-2 text-slate-400 text-sm font-medium">
              <span className="material-symbols-outlined text-[1.25rem]">calendar_today</span>
              <span>DAY 2</span>
            </div>
            <h2 className="text-slate-900 text-3xl md:text-[2rem] font-black leading-tight tracking-tight">
              {tab === "news" ? "버블 뉴스" : "재고 관리"}
            </h2>
          </div>

          {/* Tabs */}
          <div className="border-b border-slate-100">
            <div className="flex items-center gap-6">
              <button
                onClick={() => setTab("news")}
                className={`pb-2.5 text-[15px] transition-colors ${
                  tab === "news"
                    ? "border-b-2 border-slate-900 text-slate-900 font-bold"
                    : "text-slate-400 hover:text-slate-600 font-medium"
                }`}
              >
                버블 뉴스
              </button>
              <button
                onClick={() => setTab("stock")}
                className={`pb-2.5 text-[15px] transition-colors ${
                  tab === "stock"
                    ? "border-b-2 border-slate-900 text-slate-900 font-bold"
                    : "text-slate-400 hover:text-slate-600 font-medium"
                }`}
              >
                재고 관리
              </button>
            </div>
          </div>
        </div>

        {/* 비발주일 안내 */}
        <div className="flex items-center gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4">
          <span className="material-symbols-outlined text-2xl text-amber-500">info</span>
          <div>
            <p className="text-sm font-bold text-amber-800">오늘은 정규 발주일이 아니에요</p>
            <p className="text-xs text-amber-600 mt-0.5">어제 남은 재고가 이월됩니다. 메뉴와 가격만 변경할 수 있어요.</p>
          </div>
        </div>

        {/* Tab: 버블 뉴스 */}
        {tab === "news" ? (
          <div className="flex flex-col gap-4">
            <div className="flex items-center gap-3 rounded-xl border border-primary/20 bg-primary/5 p-3">
              <span className="material-symbols-outlined text-primary">auto_awesome</span>
              <p className="text-sm text-slate-600">
                <strong className="text-primary">AI 뉴스</strong> — 전날 데이터를 바탕으로 AI가 자동 생성한 기사예요
              </p>
            </div>

            <CozyNewspaper
              items={MOCK_AI_NEWS_ITEMS}
              expandedId={expandedNewsId}
              onToggle={(id) => setExpandedNewsId(expandedNewsId === id ? null : id)}
              day={2}
              rankings={MOCK_NEWS_RANKINGS_DAY2}
            />
          </div>
        ) : (
          /* Tab: 재고 관리 */
          <div className="flex flex-col gap-6">
            {/* 재고 관리 규칙 */}
            <div className="rounded-2xl bg-white border border-slate-100 shadow-soft p-6">
              <h3 className="text-lg font-bold text-slate-800 mb-2 flex items-center gap-2">
                <span className="material-symbols-outlined text-primary text-xl">inventory_2</span>
                재고 관리 규칙
              </h3>
              <p className="text-sm text-slate-500 mb-5">
                재고 흐름을 이해하면 수익을 극대화할 수 있어요!
              </p>

              {/* 타임라인 차트 */}
              <div className="overflow-x-auto mb-6">
                <div className="min-w-[540px]">
                  <div className="grid grid-cols-7 gap-1.5 mb-2">
                    {MOCK_STOCK_TIMELINE.map((day) => (
                      <div
                        key={day.day}
                        className={`text-center text-xs font-bold py-2 rounded-lg ${
                          day.isOrderDay ? "bg-primary/10 text-primary" : "bg-slate-50 text-slate-400"
                        }`}
                      >
                        DAY {day.day} {day.isOrderDay && "📦"}
                      </div>
                    ))}
                  </div>

                  <div className="grid grid-cols-7 gap-1.5 h-40 items-end">
                    {MOCK_STOCK_TIMELINE.map((day) => {
                      const max = 300;
                      return (
                        <div key={day.day} className="flex flex-col items-center justify-end h-full gap-0.5">
                          {day.ordered > 0 && (
                            <div
                              className="w-full bg-primary/20 rounded-t transition-all"
                              style={{ height: `${(day.ordered / max) * 100}%` }}
                            />
                          )}
                          <div
                            className="w-full bg-primary rounded-t transition-all"
                            style={{ height: `${(day.sold / max) * 100}%` }}
                          />
                          {day.wasted > 0 && (
                            <div
                              className="w-full bg-rose-400 rounded transition-all"
                              style={{ height: `${Math.max((day.wasted / max) * 100, 4)}%` }}
                            />
                          )}
                          <div
                            className="w-full bg-amber-300 rounded-b transition-all"
                            style={{ height: `${(day.remaining / max) * 100}%` }}
                          />
                        </div>
                      );
                    })}
                  </div>

                  <div className="grid grid-cols-7 gap-1.5 mt-1.5">
                    {MOCK_STOCK_TIMELINE.map((day) => (
                      <div key={day.day} className="text-center text-[10px] text-slate-400">
                        {day.ordered > 0 && <span className="block">발주 {day.ordered}</span>}
                        <span className="block">판매 {day.sold}</span>
                        <span className="block">잔여 {day.remaining}</span>
                      </div>
                    ))}
                  </div>

                  <div className="flex items-center justify-center gap-4 mt-4">
                    {[
                      { color: "bg-primary/20", label: "발주" },
                      { color: "bg-primary", label: "판매" },
                      { color: "bg-rose-400", label: "폐기" },
                      { color: "bg-amber-300", label: "잔여" },
                    ].map((l) => (
                      <div key={l.label} className="flex items-center gap-1.5">
                        <div className={`w-3 h-3 rounded ${l.color}`} />
                        <span className="text-xs text-slate-500 font-medium">{l.label}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>

              {/* 규칙 요약 카드 */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div className="flex items-start gap-3 p-4 rounded-xl bg-primary/5 border border-primary/10">
                  <span className="material-symbols-outlined text-primary text-xl mt-0.5">local_shipping</span>
                  <div>
                    <p className="text-sm font-bold text-slate-700">발주일 (DAY 1, 3, 5, 7)</p>
                    <p className="text-xs text-slate-500 mt-1">새로 발주하면 이전 재고는 폐기돼요. 필요한 만큼만 발주하세요!</p>
                  </div>
                </div>
                <div className="flex items-start gap-3 p-4 rounded-xl bg-slate-50 border border-slate-100">
                  <span className="material-symbols-outlined text-slate-500 text-xl mt-0.5">sync</span>
                  <div>
                    <p className="text-sm font-bold text-slate-700">비발주일 (DAY 2, 4, 6)</p>
                    <p className="text-xs text-slate-500 mt-1">어제 팔고 남은 재고가 그대로 이월돼요. 메뉴와 가격만 변경 가능!</p>
                  </div>
                </div>
                <div className="flex items-start gap-3 p-4 rounded-xl bg-rose-50 border border-rose-100">
                  <span className="material-symbols-outlined text-rose-500 text-xl mt-0.5">swap_horiz</span>
                  <div>
                    <p className="text-sm font-bold text-slate-700">메뉴 변경 시</p>
                    <p className="text-xs text-slate-500 mt-1">이전 메뉴의 재고가 전량 폐기돼요. 신중하게 결정하세요!</p>
                  </div>
                </div>
                <div className="flex items-start gap-3 p-4 rounded-xl bg-amber-50 border border-amber-100">
                  <span className="material-symbols-outlined text-amber-600 text-xl mt-0.5">inventory_2</span>
                  <div>
                    <p className="text-sm font-bold text-slate-700">재고가 0이면?</p>
                    <p className="text-xs text-slate-500 mt-1">손님이 와도 팔 수 없어요! 재고 관리가 수익의 핵심이에요.</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </GuideOverlay>
  );
}
