import { useEffect, useState } from "react";

interface PriceSliderProps {
  menuName: string;
  price: number;
  min: number;
  max: number;
  step: number;
  originalCostPrice: number;
  discountedCostPrice: number;
  hasItemDiscount: boolean;
  defaultPrice: number;
  defaultPriceLabel: string;
  onChange: (price: number) => void;
}

function clampPrice(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

export default function PriceSlider({
  menuName,
  price,
  min,
  max,
  step,
  originalCostPrice,
  discountedCostPrice,
  hasItemDiscount,
  defaultPrice,
  defaultPriceLabel,
  onChange,
}: PriceSliderProps) {
  const [showTooltip, setShowTooltip] = useState(false);
  const [inputValue, setInputValue] = useState(String(price));
  const [isFocused, setIsFocused] = useState(false);
  const normalizedStep = Number.isFinite(step) && step > 0 ? step : 1;
  const sliderMaxIndex = Math.max(0, Math.ceil((max - min) / normalizedStep));

  const toPriceFromSliderIndex = (index: number) => {
    if (sliderMaxIndex === 0) {
      return min;
    }

    if (index >= sliderMaxIndex) {
      return max;
    }

    return Math.min(max, min + index * normalizedStep);
  };

  const toSliderIndexFromPrice = (value: number) => {
    const clampedValue = clampPrice(value, min, max);

    if (sliderMaxIndex === 0) {
      return 0;
    }

    if (clampedValue >= max) {
      return sliderMaxIndex;
    }

    return Math.max(0, Math.min(sliderMaxIndex, Math.round((clampedValue - min) / normalizedStep)));
  };

  const normalizePrice = (value: number) => toPriceFromSliderIndex(toSliderIndexFromPrice(value));
  const normalizedPrice = normalizePrice(price);
  const normalizedDefaultPrice = normalizePrice(defaultPrice);
  const sliderValue = toSliderIndexFromPrice(normalizedPrice);

  useEffect(() => {
    if (!isFocused) {
      setInputValue(String(normalizedPrice));
    }
  }, [normalizedPrice, isFocused]);

  useEffect(() => {
    if (price !== normalizedPrice) {
      onChange(normalizedPrice);
    }
  }, [price, normalizedPrice, onChange]);

  const margin = normalizedPrice - discountedCostPrice;
  const isProfit = margin > 0;

  return (
    <div className="flex h-full flex-col rounded-[1.5rem] border border-transparent bg-white p-6 shadow-soft md:p-7">
      <div className="mb-5 flex items-center justify-between">
        <div>
          <h3 className="text-base font-bold text-slate-900 md:text-lg">판매가 설정</h3>
          <p className="mt-1 text-sm text-slate-400">{menuName} 판매가를 조절합니다.</p>
        </div>
        <div className="relative">
          <button
            type="button"
            onClick={() => setShowTooltip((prev) => !prev)}
            className="flex size-9 items-center justify-center rounded-full border border-slate-200 bg-slate-50 text-slate-400 transition-colors hover:border-primary/20 hover:bg-primary/5 hover:text-primary-dark"
            aria-label="가격 설정 범위 보기"
          >
            <span className="material-symbols-outlined text-[20px]">payments</span>
          </button>
          {showTooltip && (
            <div className="absolute right-0 top-11 z-10 w-52 rounded-2xl border border-slate-200 bg-white px-3.5 py-3 text-[13px] leading-relaxed text-slate-600 shadow-lg">
              최소 판매가부터 권장가의 2배까지 설정할 수 있습니다.
            </div>
          )}
        </div>
      </div>

      <div className="flex grow flex-col">
        <div className="flex grow flex-col items-center justify-center py-1 text-center">
          <p className="mb-2 text-[13px] font-medium text-slate-400">판매 가격</p>
          <div className="flex items-center justify-center gap-1">
            <span className="text-[2.5rem] font-black tracking-tight text-slate-900 md:text-[2.875rem]">₩</span>
            <input
              type="text"
              inputMode="numeric"
              value={isFocused ? inputValue : normalizedPrice.toLocaleString()}
              onChange={(event) => {
                setInputValue(event.target.value.replace(/[^0-9]/g, ""));
              }}
              onFocus={() => {
                setIsFocused(true);
                setInputValue(String(normalizedPrice));
              }}
              onBlur={() => {
                setIsFocused(false);
                const value = Number(inputValue);

                if (!Number.isNaN(value) && inputValue !== "") {
                  onChange(normalizePrice(value));
                } else {
                  setInputValue(String(normalizedPrice));
                }
              }}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  event.currentTarget.blur();
                }
              }}
              className="w-40 border-b-2 border-slate-200 bg-transparent text-center text-[2.5rem] font-black tracking-tight text-slate-900 outline-none transition-colors focus:border-primary md:text-[2.875rem]"
            />
          </div>
          <p className="mt-2.5 text-[13px] font-semibold text-primary-dark">
            {defaultPriceLabel} ₩{normalizedDefaultPrice.toLocaleString()}
          </p>
          <p className="mt-1.5 text-xs font-medium text-slate-400">
            ₩{min.toLocaleString()} ~ ₩{max.toLocaleString()}
          </p>

          <div className="mt-4 w-full px-1">
            <input
              type="range"
              min={0}
              max={sliderMaxIndex}
              step={1}
              value={sliderValue}
              onChange={(event) => onChange(toPriceFromSliderIndex(Number(event.target.value)))}
              className="h-2 w-full cursor-pointer appearance-none rounded-lg bg-slate-100 accent-primary transition-all hover:accent-primary-dark"
            />
            <div className="mt-2 flex justify-between text-xs font-medium text-slate-400">
              <span>₩{min.toLocaleString()}</span>
              <span>₩{max.toLocaleString()}</span>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3 pt-3">
          <div className="rounded-2xl bg-slate-50 p-3 text-center">
            <p className="mb-1 text-xs font-medium text-slate-400">원가</p>
            {hasItemDiscount ? (
              <div className="space-y-1">
                <p className="text-xs font-bold text-rose-300 line-through decoration-2">
                  ₩{originalCostPrice.toLocaleString()}
                </p>
                <p className="text-base font-bold text-rose-500 md:text-lg">
                  ₩{discountedCostPrice.toLocaleString()}
                </p>
              </div>
            ) : (
              <p className="text-base font-bold text-slate-700 md:text-lg">
                ₩{originalCostPrice.toLocaleString()}
              </p>
            )}
          </div>
          <div
            className={`rounded-2xl border p-3 text-center ${
              isProfit ? "border-primary/10 bg-primary/5" : "border-red-100 bg-red-50"
            }`}
          >
            <p className={`mb-1 text-xs font-medium ${isProfit ? "text-primary-dark" : "text-red-400"}`}>
              마진
            </p>
            <p className={`text-base font-bold md:text-lg ${isProfit ? "text-primary" : "text-red-500"}`}>
              {isProfit ? "+" : ""}₩{margin.toLocaleString()}
            </p>
            {hasItemDiscount && (
              <p className={`mt-1 text-[11px] font-medium ${isProfit ? "text-primary-dark/70" : "text-red-400"}`}>
                할인 원가 기준
              </p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
