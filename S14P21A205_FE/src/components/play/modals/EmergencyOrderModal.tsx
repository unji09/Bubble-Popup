import axios from "axios";
import { useEffect, useState } from "react";
import PriceSlider from "../../game/PriceSlider";
import ModalWrapper from "./ModalWrapper";
import { applyDiscount } from "../../../utils/dashboardItems";

interface ApiErrorResponse {
  message?: string;
}

export interface EmergencyMenuItem {
  menuId: number;
  name: string;
  ingredientPrice: number;
  ingredientDiscountMultiplier: number;
  emoji: string;
  recommendedPrice: number;
  maxSellingPrice: number;
}

export interface CurrentMenuPricing {
  costPrice: number;
  recommendedPrice: number;
  maxSellingPrice: number;
  sellingPrice: number;
}

interface EmergencyOrderSubmitPayload {
  menuId: number;
  menuName: string;
  quantity: number;
  salePrice: number;
  totalCost: number;
}

interface EmergencyOrderModalProps {
  currentBalance: number;
  menuItems: EmergencyMenuItem[];
  currentMenuId: number | null;
  currentMenuPricing: CurrentMenuPricing | null;
  deliveryTrafficLabel?: string | null;
  estimatedArrivalLabel?: string | null;
  isInitializing?: boolean;
  initializationError?: string | null;
  onClose: () => void;
  onSubmit: (payload: EmergencyOrderSubmitPayload) => Promise<void> | void;
}

const SURCHARGE_RATE = 0.5;

function resolveInitialMenuIndex(menuItems: EmergencyMenuItem[], currentMenuId: number | null) {
  const currentMenuIndex = menuItems.findIndex((menu) => menu.menuId === currentMenuId);
  return currentMenuIndex >= 0 ? currentMenuIndex : 0;
}

function clampPrice(price: number, min: number, max: number) {
  return Math.min(Math.max(price, min), max);
}

function getDefaultSalePrice(
  menu: EmergencyMenuItem,
  currentMenuId: number | null,
  currentMenuPricing: CurrentMenuPricing | null,
) {
  const isCurrentMenu = menu.menuId === currentMenuId;
  const appliedCurrentPricing = isCurrentMenu ? currentMenuPricing : null;
  const minSellingPrice = menu.ingredientPrice;
  const recommendedPrice = menu.recommendedPrice;
  const maxSellingPrice = menu.maxSellingPrice;

  return clampPrice(
    appliedCurrentPricing?.sellingPrice ?? recommendedPrice,
    minSellingPrice,
    maxSellingPrice,
  );
}

function formatWon(amount: number) {
  return `₩${amount.toLocaleString()}`;
}

function formatExpenseWon(amount: number) {
  return `- ${formatWon(amount)}`;
}

function EmergencyQuantityCard({
  quantity,
  onChange,
}: {
  quantity: number;
  onChange: (quantity: number) => void;
}) {
  const [showTooltip, setShowTooltip] = useState(false);

  return (
    <div className="flex h-full flex-col rounded-[1.5rem] border border-transparent bg-white p-6 shadow-soft md:p-7">
      <div className="mb-5 flex items-center justify-between">
        <div>
          <h3 className="text-base font-bold text-slate-900 md:text-lg">수량 설정</h3>
          <p className="mt-1 text-sm text-slate-400">긴급 발주할 수량을 조절합니다.</p>
        </div>
        <div className="relative">
          <button
            type="button"
            onClick={() => setShowTooltip((prev) => !prev)}
            className="flex size-9 items-center justify-center rounded-full border border-slate-200 bg-slate-50 text-slate-400 transition-colors hover:border-primary/20 hover:bg-primary/5 hover:text-primary-dark"
            aria-label="긴급 발주 수량 안내 보기"
          >
            <span className="material-symbols-outlined text-[20px]">inventory_2</span>
          </button>
          {showTooltip && (
            <div className="absolute right-0 top-11 z-10 w-56 rounded-2xl border border-slate-200 bg-white px-3.5 py-3 text-[13px] leading-relaxed text-slate-600 shadow-lg">
              긴급 발주는 50개부터 500개까지 10개 단위로 조절할 수 있습니다.
            </div>
          )}
        </div>
      </div>

      <div className="flex grow flex-col justify-center gap-6">
        <div className="py-1 text-center">
          <p className="mb-2 text-[13px] font-medium text-slate-400">발주 수량</p>
          <p className="text-[2.5rem] font-black tracking-tight text-slate-900 md:text-[2.875rem]">
            {quantity}개
          </p>
        </div>

        <div className="w-full px-1">
          <input
            type="range"
            min={50}
            max={500}
            step={10}
            value={quantity}
            onChange={(event) => onChange(Number(event.target.value))}
            className="h-2 w-full cursor-pointer appearance-none rounded-lg bg-slate-100 accent-primary transition-all hover:accent-primary-dark"
          />
          <div className="mt-2.5 flex justify-between text-xs font-medium text-slate-400">
            <span>50개</span>
            <span>500개</span>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function EmergencyOrderModal({
  currentBalance,
  menuItems,
  currentMenuId,
  currentMenuPricing,
  deliveryTrafficLabel,
  estimatedArrivalLabel,
  isInitializing = false,
  initializationError = null,
  onClose,
  onSubmit,
}: EmergencyOrderModalProps) {
  const [selectedMenuIndex, setSelectedMenuIndex] = useState(() =>
    resolveInitialMenuIndex(menuItems, currentMenuId),
  );
  const [quantity, setQuantity] = useState(120);
  const [showWarningTooltip, setShowWarningTooltip] = useState(false);
  const [price, setPrice] = useState(0);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    setSelectedMenuIndex(resolveInitialMenuIndex(menuItems, currentMenuId));
  }, [menuItems, currentMenuId]);

  const selectedMenu = menuItems[selectedMenuIndex] ?? menuItems[0];

  if (!selectedMenu) {
    return (
      <ModalWrapper onClose={onClose}>
        <div className="p-8 text-center">
          <h2 className="text-lg font-bold text-slate-800">긴급 발주</h2>
          <p className="mt-2 text-sm text-slate-500">
            {isInitializing
              ? "긴급 발주 정보를 불러오는 중입니다."
              : initializationError ?? "발주 가능한 메뉴 정보를 불러오지 못했습니다."}
          </p>
        </div>
      </ModalWrapper>
    );
  }

  const isCurrentMenu = selectedMenu.menuId === currentMenuId;
  const ingredientDiscountMultiplier = selectedMenu.ingredientDiscountMultiplier;
  const hasItemDiscount = ingredientDiscountMultiplier < 1;
  // 판매가 범위는 서버에서 계산된 값 사용 (배율 적용된 원가 기준)
  const originalCostPrice = selectedMenu.ingredientPrice;
  const discountedCostPrice = applyDiscount(
    originalCostPrice,
    ingredientDiscountMultiplier,
  );
  const maxSellingPrice = selectedMenu.maxSellingPrice;
  const minSellingPrice = originalCostPrice;
  const defaultSalePrice = getDefaultSalePrice(
    selectedMenu,
    currentMenuId,
    currentMenuPricing,
  );
  const defaultPriceLabel = isCurrentMenu ? "현재 판매가" : "권장가";
  const materialsCost = discountedCostPrice * quantity;
  const surcharge = Math.round(materialsCost * SURCHARGE_RATE);
  const totalCost = materialsCost + surcharge;
  const remainingBalance = currentBalance - totalCost;
  const canAfford = currentBalance >= totalCost;

  useEffect(() => {
    setPrice(defaultSalePrice);
    setSubmitError(null);
  }, [defaultSalePrice, selectedMenu.menuId]);

  const handleSelectMenu = (index: number) => {
    const nextMenu = menuItems[index];

    if (!nextMenu) {
      return;
    }

    setSelectedMenuIndex(index);
    setPrice(getDefaultSalePrice(nextMenu, currentMenuId, currentMenuPricing));
    setSubmitError(null);
  };

  const handleSubmit = async () => {
    if (!canAfford || isSubmitting) {
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);

    try {
      await Promise.resolve(
        onSubmit({
          menuId: selectedMenu.menuId,
          menuName: selectedMenu.name,
          quantity,
          salePrice: price,
          totalCost,
        }),
      );
    } catch (error) {
      if (axios.isAxiosError<ApiErrorResponse>(error)) {
        const serverMessage = error.response?.data?.message;
        setSubmitError(serverMessage ?? "긴급 발주에 실패했습니다. 잠시 후 다시 시도해주세요.");
      } else {
        setSubmitError("긴급 발주에 실패했습니다. 잠시 후 다시 시도해주세요.");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <ModalWrapper onClose={onClose} panelClassName="w-[min(96vw,1100px)]">
      <div className="border-b border-slate-100 px-6 py-5 pr-12">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-2">
            <span className="text-2xl">🚚</span>
            <div>
              <h2 className="text-lg font-bold text-slate-800">긴급 발주</h2>
              <p className="mt-1 text-sm text-slate-500">
                영업 중 필요한 메뉴를 바로 주문하고 판매가도 함께 설정합니다.
              </p>
            </div>
          </div>

          <div className="relative shrink-0">
            <button
              type="button"
              onClick={() => setShowWarningTooltip((prev) => !prev)}
              className="flex size-8 items-center justify-center rounded-full border border-rose-200 bg-rose-50 text-rose-600 transition-colors hover:bg-rose-100"
              aria-label="긴급 발주 수수료 안내 보기"
            >
              <span className="material-symbols-outlined text-[16px]">info</span>
            </button>
            {showWarningTooltip && (
              <div className="absolute right-0 top-[calc(100%+0.5rem)] z-10 w-64 rounded-2xl border border-rose-200 bg-white px-3.5 py-3 text-xs leading-relaxed text-slate-600 shadow-lg">
                긴급 발주는 재료비 외에 50% 수수료가 추가됩니다. 꼭 필요한 수량만 주문하는 편이 좋습니다.
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="space-y-6 p-6">
        {initializationError && (
          <div className="rounded-2xl border border-amber-200 bg-amber-50/80 px-4 py-3 text-sm text-amber-700">
            {initializationError}
          </div>
        )}

        <div className="grid gap-6 xl:grid-cols-[minmax(0,1.35fr)_minmax(320px,0.9fr)]">
          <div className="space-y-6">
            <section className="space-y-3">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-sm font-bold text-slate-800">메뉴 선택</h3>
                  <p className="mt-1 text-xs text-slate-400">
                    긴급 주문할 메뉴를 골라주세요.
                  </p>
                </div>
                <span className="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-semibold text-slate-500">
                  총 {menuItems.length}개
                </span>
              </div>

              <div className="custom-scrollbar max-h-72 space-y-2 overflow-y-auto rounded-2xl border border-slate-100 bg-white p-2">
                {menuItems.map((menu, index) => {
                  const isSelected = selectedMenuIndex === index;
                  const isCurrentSellingMenu = menu.menuId === currentMenuId;
                  const listHasDiscount = menu.ingredientDiscountMultiplier < 1;
                  const listDiscountedPrice = applyDiscount(
                    menu.ingredientPrice,
                    menu.ingredientDiscountMultiplier,
                  );
                  const isSelectedNewMenu = isSelected && !isCurrentSellingMenu;

                  return (
                    <button
                      key={`${menu.menuId}-${menu.name}`}
                      type="button"
                      onClick={() => handleSelectMenu(index)}
                      className={`flex w-full items-center justify-between rounded-xl border px-3.5 py-3 text-left transition-all ${
                        isSelected
                          ? "border-primary bg-primary/5 shadow-sm ring-1 ring-primary/10"
                          : "border-transparent bg-slate-50 hover:border-slate-200 hover:bg-white"
                      }`}
                    >
                      <div className="flex min-w-0 items-center gap-3">
                        <div
                          className={`flex size-11 shrink-0 items-center justify-center rounded-2xl text-2xl ${
                            isSelected ? "bg-white" : "bg-white/80"
                          }`}
                        >
                          {menu.emoji}
                        </div>
                        <div className="min-w-0">
                          <p
                            className={`truncate text-sm ${
                              isSelected
                                ? "font-bold text-slate-900"
                                : "font-medium text-slate-700"
                            }`}
                          >
                            {menu.name}
                          </p>
                          <div className="mt-0.5 flex items-center gap-1.5 text-[11px]">
                            {listHasDiscount && (
                              <span className="text-rose-300 line-through decoration-2">
                                {formatWon(menu.ingredientPrice)}
                              </span>
                            )}
                            <span
                              className={
                                listHasDiscount
                                  ? "font-semibold text-rose-500"
                                  : "text-slate-400"
                              }
                            >
                              {formatWon(listDiscountedPrice)}
                            </span>
                          </div>
                        </div>
                      </div>

                      <div className="ml-3 flex shrink-0 items-center gap-2">
                        {isCurrentSellingMenu && (
                          <span className="rounded-full bg-primary/15 px-2 py-1 text-[10px] font-bold text-primary-dark">
                            현재 판매 중
                          </span>
                        )}
                        {isSelectedNewMenu && (
                          <span className="rounded-full bg-accent-rose/15 px-2 py-1 text-[10px] font-bold text-rose-dark">
                            새 메뉴
                          </span>
                        )}
                        {isSelected && (
                          <span className="material-symbols-outlined text-[18px] text-primary">
                            check_circle
                          </span>
                        )}
                      </div>
                    </button>
                  );
                })}
              </div>
            </section>

            <div className="grid gap-6 lg:grid-cols-2">
              <EmergencyQuantityCard quantity={quantity} onChange={setQuantity} />
              <PriceSlider
                menuName={selectedMenu.name}
                price={price}
                min={minSellingPrice}
                max={maxSellingPrice}
                step={10}
                originalCostPrice={originalCostPrice}
                discountedCostPrice={discountedCostPrice}
                hasItemDiscount={hasItemDiscount}
                defaultPrice={defaultSalePrice}
                defaultPriceLabel={defaultPriceLabel}
                onChange={setPrice}
              />
            </div>
          </div>

          <div className="space-y-6">
            <section className="space-y-3 rounded-2xl border border-slate-100 bg-white p-5 shadow-soft">
              <div>
                <h3 className="text-sm font-bold text-slate-800">배송 정보</h3>
                <p className="mt-1 text-xs text-slate-400">
                  현재 교통량과 예상 도착 시간을 실시간으로 안내합니다.
                </p>
                <p className="mt-1 text-[11px] leading-relaxed text-slate-400">
                  교통 상황에 따라 예상 도착 시간과 실제 도착 시간은 달라질 수 있습니다.
                </p>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="flex min-h-[144px] flex-col items-center justify-center rounded-2xl border border-slate-100 bg-slate-50/80 p-4 text-center">
                  <div className="mb-3 flex items-center justify-center gap-2">
                    <div className="flex size-8 items-center justify-center rounded-full bg-slate-100">
                      <span className="material-symbols-outlined text-[16px] text-slate-500">
                        traffic
                      </span>
                    </div>
                    <span className="text-xs font-bold uppercase tracking-[0.18em] text-slate-500">
                      현재 교통량
                    </span>
                  </div>
                  <p className="text-[1.4rem] font-black tracking-tight text-slate-700">
                    {deliveryTrafficLabel ?? "연동 예정"}
                  </p>
                </div>

                <div className="flex min-h-[144px] flex-col items-center justify-center rounded-2xl border border-slate-100 bg-slate-50/80 p-4 text-center">
                  <div className="mb-3 flex items-center justify-center gap-2">
                    <div className="flex size-8 items-center justify-center rounded-full bg-primary/10">
                      <span className="material-symbols-outlined text-[16px] text-primary-dark">
                        schedule
                      </span>
                    </div>
                    <span className="shrink-0 whitespace-nowrap text-[11px] font-bold tracking-[0.1em] text-slate-500">
                      예상 도착 시간
                    </span>
                  </div>
                  <p className="whitespace-nowrap text-[1.4rem] font-black tracking-tight text-slate-800">
                    {estimatedArrivalLabel ?? "연동 예정"}
                  </p>
                </div>
              </div>
            </section>

            <section className="rounded-2xl border border-slate-100 bg-slate-50/70 p-4">
              <p className="mb-3 text-xs font-bold uppercase tracking-[0.18em] text-slate-500">
                결제 요약
              </p>
              <div className="space-y-2.5">
                <div className="flex items-center justify-between text-sm text-slate-600">
                  <span>현재 보유금</span>
                  <span className="font-medium text-slate-800">
                    {formatWon(currentBalance)}
                  </span>
                </div>
                <div className="flex items-center justify-between text-sm text-slate-600">
                  <span>적용 판매가</span>
                  <span className="font-semibold text-slate-800">{formatWon(price)}</span>
                </div>
                <div className="flex items-center justify-between text-sm text-slate-600">
                  <span className="text-rose-400">재료비 x 수량</span>
                  <span className="font-semibold text-rose-500">
                    {formatExpenseWon(materialsCost)}
                  </span>
                </div>
                <div className="flex items-center justify-between text-sm text-slate-600">
                  <span className="text-rose-400">
                    긴급 발주 수수료 ({SURCHARGE_RATE * 100}%)
                  </span>
                  <span className="font-semibold text-rose-500">
                    {formatExpenseWon(surcharge)}
                  </span>
                </div>
                <div className="h-px w-full bg-slate-200" />
                <div className="flex items-center justify-between">
                  <span className="text-sm font-bold text-slate-700">총 결제 비용</span>
                  <span className="text-xl font-black text-slate-900">
                    {formatWon(totalCost)}
                  </span>
                </div>
                <div className="h-px w-full bg-slate-200" />
                <div className="flex items-center justify-between">
                  <span className="text-sm font-bold text-slate-700">결제 후 잔액</span>
                  <span
                    className={`text-lg font-bold ${
                      remainingBalance < 0 ? "text-rose-500" : "text-primary-dark"
                    }`}
                  >
                    {formatWon(remainingBalance)}
                  </span>
                </div>
              </div>

              {!canAfford && (
                <p className="mt-3 text-xs font-medium text-rose-500">
                  잔액이 부족해 긴급 발주를 진행할 수 없습니다.
                </p>
              )}

              {submitError && (
                <div className="mt-4 rounded-2xl border border-red-200 bg-red-50/80 px-4 py-3 text-sm text-red-600">
                  {submitError}
                </div>
              )}

              <button
                type="button"
                onClick={() => void handleSubmit()}
                disabled={!canAfford || isSubmitting || isInitializing}
                className="mt-5 flex w-full items-center justify-center gap-2 rounded-2xl bg-primary py-3.5 font-bold text-white shadow-md transition-all hover:bg-primary-dark hover:shadow-lg active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-40"
              >
                <span>
                  {isSubmitting
                    ? "발주 접수중..."
                    : isInitializing
                      ? "정보 불러오는 중..."
                      : "긴급 발주하기"}
                </span>
                {!isSubmitting && !isInitializing && (
                  <span className="material-symbols-outlined text-sm">arrow_forward</span>
                )}
              </button>
            </section>
          </div>
        </div>
      </div>
    </ModalWrapper>
  );
}
