import { useEffect, useState } from "react";
import GuideOverlay from "../GuideOverlay";
import { MOCK_NEWS_ITEMS, MOCK_NEWS_RANKINGS_DAY1 } from "../mockData";
import { useTutorialDataContext } from "../TutorialDataContext";
import { useTutorialStore } from "../../../stores/useTutorialStore";
import CozyNewspaper from "../../game/CozyNewspaper";
import MenuSelector from "../../game/MenuSelector";
import PriceSlider from "../../game/PriceSlider";
import QuantityCounter from "../../game/QuantityCounter";
import bubbleNewsImage from "../../../assets/Bubblenewsimg.png";

const MENU_EMOJI: Record<string, string> = {
  "빵": "🍞", "마라꼬치": "🍢", "젤리": "🍬", "떡볶이": "🍽️", "햄버거": "🍔",
  "아이스크림": "🍨", "닭강정": "🍗", "타코": "🌮", "핫도그": "🌭", "버블티": "🧋",
};

type Tab = "prep" | "news";

export default function PrepStep() {
  const { menus } = useTutorialDataContext();
  const { updateGameState } = useTutorialStore();
  const [tab, setTab] = useState<Tab>("news");
  const [selectedMenu, setSelectedMenu] = useState<number | null>(null);
  const [price, setPrice] = useState(0);
  const [quantity, setQuantity] = useState(120);
  const [expandedNewsId, setExpandedNewsId] = useState<number | null>(MOCK_NEWS_ITEMS[0]?.id ?? null);

  const selectedMenuData = menus.find((m) => m.id === selectedMenu);

  // 메뉴 선택 시 가격 초기화
  useEffect(() => {
    if (selectedMenuData) {
      setPrice(selectedMenuData.recommendedPrice);
    }
  }, [selectedMenu]);

  const menuSelectorMenus = menus.map((m) => ({
    id: m.id,
    emoji: MENU_EMOJI[m.name] ?? "🍽️",
    name: m.name,
  }));

  // Day 1 rankings with image injected
  const rankings = MOCK_NEWS_RANKINGS_DAY1.map((section) =>
    section.imageAlt
      ? { ...section, imageSrc: bubbleNewsImage }
      : section,
  );

  const totalCost = selectedMenuData
    ? selectedMenuData.originPrice * quantity
    : 0;

  return (
    <GuideOverlay
      title="영업 준비"
      description={
        <span>
          매일 영업 전에 <strong>뉴스</strong>를 확인하고, <strong>메뉴·가격·수량</strong>을 설정해요.
          실제 영업 준비 화면을 직접 체험해보세요!
        </span>
      }
    >
      <div className="flex flex-col gap-6">
        {/* Page Header */}
        <div className="flex flex-col gap-5">
          <div className="flex flex-col gap-2.5">
            <div className="flex items-center gap-2 text-slate-400 text-sm font-medium">
              <span className="material-symbols-outlined text-[1.25rem]">calendar_today</span>
              <span>DAY 1</span>
            </div>
            <h2 className="text-slate-900 text-3xl md:text-[2rem] font-black leading-tight tracking-tight">
              {tab === "prep" ? "영업 준비" : "버블 뉴스"}
            </h2>
          </div>

          {/* Tabs */}
          <div className="border-b border-slate-100">
            <div className="flex items-center gap-6">
              <button
                onClick={() => setTab("prep")}
                className={`pb-2.5 text-[15px] transition-colors ${
                  tab === "prep"
                    ? "border-b-2 border-slate-900 text-slate-900 font-bold"
                    : "text-slate-400 hover:text-slate-600 font-medium"
                }`}
              >
                영업 준비
              </button>
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
            </div>
          </div>
        </div>

        {/* Tab: 영업 준비 */}
        {tab === "prep" ? (
          <div className="flex flex-col gap-6">
            {/* 메뉴 선택 */}
            <MenuSelector
              menus={menuSelectorMenus}
              selectedId={selectedMenu}
              onSelect={(id) => {
                setSelectedMenu(id);
                updateGameState({ selectedMenuId: id });
              }}
            />

            {/* 가격 + 수량 */}
            {selectedMenuData && (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <PriceSlider
                  menuName={selectedMenuData.name}
                  price={price}
                  min={selectedMenuData.originPrice}
                  max={selectedMenuData.maxSellingPrice}
                  step={10}
                  originalCostPrice={selectedMenuData.originPrice}
                  discountedCostPrice={selectedMenuData.originPrice}
                  hasItemDiscount={false}
                  defaultPrice={selectedMenuData.recommendedPrice}
                  defaultPriceLabel="권장가"
                  onChange={(v) => {
                    setPrice(v);
                    updateGameState({ menuPrice: v });
                  }}
                />
                <div className="flex flex-col gap-5">
                  <QuantityCounter
                    quantity={quantity}
                    min={50}
                    max={500}
                    step={10}
                    onChange={(v) => {
                      setQuantity(v);
                      updateGameState({ orderQuantity: v });
                    }}
                  />
                  <div className="flex flex-col gap-3.5">
                    <div className="flex items-center justify-between px-4 py-2">
                      <span className="text-sm text-slate-500 font-medium">총 예상 비용</span>
                      <span className="text-[1.75rem] font-black text-slate-900 tracking-tight">
                        ₩{totalCost.toLocaleString()}
                      </span>
                    </div>
                    <button
                      type="button"
                      onClick={() => setTab("news")}
                      className="w-full font-bold text-base py-4 px-6 rounded-2xl shadow-lg transition-all flex items-center justify-center gap-2 bg-primary hover:bg-primary-dark text-slate-900 hover:text-white shadow-primary/20 group"
                    >
                      <span>발주신청하기</span>
                      <span className="material-symbols-outlined transition-transform group-hover:translate-x-1">
                        arrow_forward
                      </span>
                    </button>
                  </div>
                </div>
              </div>
            )}

          </div>
        ) : (
          /* Tab: 버블 뉴스 */
          <CozyNewspaper
            items={MOCK_NEWS_ITEMS}
            expandedId={expandedNewsId}
            onToggle={(id) => setExpandedNewsId(expandedNewsId === id ? null : id)}
            day={1}
            rankings={rankings}
          />
        )}

        {/* 안내 */}
        <div className="p-4 bg-slate-50 rounded-xl space-y-2 text-sm text-slate-600">
          <p>💡 가격의 비싸고 싸고 기준은 <strong>그 메뉴의 평균 판매가</strong>예요</p>
          <p>💡 뉴스 내용이 <strong>매출 순위, 일일 방문객, 임대료</strong>에 영향을 줄 수 있어요</p>
          <p>📦 정규 발주는 <strong>DAY 1, 3, 5, 7</strong>에 가능하고, 발주 시 이전 재고는 폐기돼요</p>
        </div>
      </div>
    </GuideOverlay>
  );
}
