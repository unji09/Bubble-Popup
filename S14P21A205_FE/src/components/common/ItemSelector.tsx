import { useState } from "react";

interface ShopItem {
  id: number;
  name: string;
  group: string;
  desc: string;
  discount: string;
  price: number;
  owned?: boolean;
}

interface ItemGroup {
  group: string;
  label: string;
  icon: string;
  items: ShopItem[];
}

interface ItemSelectorProps {
  groups: ItemGroup[];
  selectedIds: number[];
  onToggle: (id: number) => void;
  availablePoints?: number | null;
  disabled?: boolean;
  disabledMessage?: string;
  isLoading?: boolean;
  emptyMessage?: string;
  hideTooltip?: boolean;
}

export default function ItemSelector({
  groups,
  selectedIds,
  onToggle,
  availablePoints = null,
  disabled = false,
  disabledMessage = "",
  isLoading = false,
  emptyMessage = "선택 가능한 아이템이 없습니다.",
  hideTooltip = false,
}: ItemSelectorProps) {
  const [showTooltip, setShowTooltip] = useState(false);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-2 px-1">
        <h3 className="text-lg font-bold">아이템 선택</h3>
        {!hideTooltip && (
          <div className="relative">
            <button
              type="button"
              onClick={() => setShowTooltip((prev) => !prev)}
              className="flex size-7 items-center justify-center rounded-full border border-slate-200 bg-slate-50 text-slate-400 transition-colors hover:border-primary/20 hover:bg-primary/5 hover:text-primary-dark"
              aria-label="아이템 선택 안내 보기"
            >
              <span className="material-symbols-outlined text-[16px]">info</span>
            </button>
            {showTooltip && (
              <div className="absolute left-0 top-9 z-10 w-72 rounded-2xl border border-slate-200 bg-white px-3.5 py-3 text-[13px] leading-relaxed text-slate-600 shadow-lg">
                카테고리별로 한 가지씩, 최대 두 가지 아이템만 적용할 수 있습니다. 이번 단계에서는
                선택 상태만 저장되고 실제 구매는 진행되지 않습니다.
              </div>
            )}
          </div>
        )}
      </div>

      {disabled && disabledMessage && (
        <div className="rounded-[20px] border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 shadow-soft">
          {disabledMessage}
        </div>
      )}

      {isLoading ? (
        <div className="rounded-[20px] bg-white p-5 shadow-soft">
          <div className="h-5 w-36 animate-pulse rounded-full bg-slate-100" />
          <div className="mt-4 grid grid-cols-2 gap-2.5">
            {Array.from({ length: 4 }).map((_, index) => (
              <div
                key={index}
                className="h-24 animate-pulse rounded-xl border border-slate-100 bg-slate-50"
              />
            ))}
          </div>
        </div>
      ) : groups.length === 0 ? (
        <div className="rounded-[20px] bg-white px-5 py-8 text-center text-sm text-slate-400 shadow-soft">
          {emptyMessage}
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          {groups.map((group) => {
            const selectedInGroup = group.items.find((item) => selectedIds.includes(item.id));

            return (
              <div key={group.group} className="rounded-[20px] bg-white p-5 shadow-soft">
                <div className="mb-3 flex items-center gap-2">
                  <span className="text-lg">{group.icon}</span>
                  <span className="text-sm font-bold text-slate-700">{group.label}</span>
                  {selectedInGroup && (
                    <span className="ml-auto rounded-full bg-primary/10 px-2 py-0.5 text-xs font-bold text-primary">
                      적용 중
                    </span>
                  )}
                </div>

                <div className="grid grid-cols-2 gap-2.5">
                  {group.items.map((item) => {
                    const isSelected = selectedIds.includes(item.id);
                    const otherGroupCount = selectedIds.filter((selectedId) => {
                      return !group.items.some((groupItem) => groupItem.id === selectedId);
                    }).length;
                    const cannotAfford =
                      !isSelected &&
                      availablePoints !== null &&
                      item.price > availablePoints + (selectedInGroup?.price ?? 0);
                    const isDisabled =
                      disabled ||
                      cannotAfford ||
                      (!isSelected &&
                        !selectedInGroup &&
                        otherGroupCount >= 2);

                    return (
                      <button
                        key={item.id}
                        type="button"
                        disabled={isDisabled}
                        onClick={() => !isDisabled && onToggle(item.id)}
                        className={`relative rounded-xl p-4 text-left transition-all ${
                          isDisabled
                            ? "cursor-not-allowed border border-slate-100 bg-slate-50 opacity-35"
                            : isSelected
                              ? "cursor-pointer border-2 border-primary bg-primary/5 shadow-sm"
                              : "cursor-pointer border border-slate-100 bg-slate-50 hover:border-slate-200 hover:shadow-sm"
                        }`}
                      >
                        {isSelected && (
                          <div className="absolute -right-1.5 -top-1.5 rounded-full bg-primary p-0.5 text-white shadow-sm">
                            <span className="material-symbols-outlined block text-[14px] font-bold">
                              check
                            </span>
                          </div>
                        )}

                        <div className="mb-1.5 flex items-center justify-between gap-2">
                          <div className="flex items-center gap-1.5">
                            <span
                              className={`text-sm font-bold ${
                                isSelected ? "text-primary-dark" : "text-slate-500"
                              }`}
                            >
                              {item.discount}
                            </span>
                            {item.owned && (
                              <span className="rounded-full bg-emerald-50 px-1.5 py-0.5 text-[10px] font-bold text-emerald-600">
                                보유
                              </span>
                            )}
                          </div>
                          <span
                            className={`rounded-full px-2 py-0.5 text-[11px] font-bold ${
                              isSelected
                                ? "bg-primary text-white"
                                : "bg-slate-200/70 text-slate-500"
                            }`}
                          >
                            {item.price}P
                          </span>
                        </div>

                        <p
                          className={`text-xs leading-relaxed ${
                            isSelected ? "text-slate-600" : "text-slate-400"
                          }`}
                        >
                          {item.desc}
                        </p>
                      </button>
                    );
                  })}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
