import { useEffect, useState } from "react";
import type { FormEvent } from "react";

interface DistrictInfo {
  name: string;
  rent: string;
  congestion: string;
  grade: string;
  tags: string[];
  description: string;
}

interface DistrictDetailPanelProps {
  district: DistrictInfo;
  interiorCost: string | null;
  discountedRent?: string | null;
  rentDiscountLabel?: string | null;
  isSubmitting?: boolean;
  submitError?: string | null;
  onComplete: (brandName: string) => void | Promise<void>;
  onClose: () => void;
}

const BRAND_NAME_MAX_LENGTH = 10;

function getCongestionStyle(congestion: string): { label: string; text: string } {
  // 유동인구 순위 형식 ("유동인구 1위" 등)
  const rankMatch = congestion.match(/(\d+)위/);
  if (rankMatch) {
    const rank = Number(rankMatch[1]);
    if (rank <= 2) return { label: congestion, text: "text-rose-500" };
    if (rank <= 5) return { label: congestion, text: "text-amber-600" };
    return { label: congestion, text: "text-sky-600" };
  }
  // 레거시 fallback
  const toneMap: Record<string, { label: string; text: string }> = {
    "매우 혼잡": { label: "매우 혼잡", text: "text-rose-500" },
    혼잡: { label: "혼잡", text: "text-amber-600" },
    보통: { label: "보통", text: "text-primary-dark" },
    여유: { label: "여유", text: "text-sky-600" },
    "매우 여유": { label: "매우 여유", text: "text-slate-500" },
  };
  return toneMap[congestion] ?? { label: congestion, text: "text-primary-dark" };
}

const gradeTone: Record<string, string> = {
  S: "border-accent-rose bg-accent-rose text-white",
  A: "border-amber-200 bg-amber-100 text-amber-800",
  B: "border-sky-300 bg-sky-100 text-sky-800",
};

export default function DistrictDetailPanel({
  district,
  interiorCost,
  discountedRent,
  rentDiscountLabel,
  isSubmitting = false,
  submitError,
  onComplete,
  onClose,
}: DistrictDetailPanelProps) {
  const [brandName, setBrandName] = useState("");
  const [visible, setVisible] = useState(false);
  const congestionMeta = getCongestionStyle(district.congestion);
  const grade = district.grade.charAt(0);
  const hasRentDiscount =
    Boolean(discountedRent) && discountedRent !== district.rent && Boolean(rentDiscountLabel);

  useEffect(() => {
    const frame = window.requestAnimationFrame(() => setVisible(true));

    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !isSubmitting) {
        onClose();
      }
    };

    window.addEventListener("keydown", handleEscape);

    return () => {
      window.cancelAnimationFrame(frame);
      window.removeEventListener("keydown", handleEscape);
    };
  }, [isSubmitting, onClose]);

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmedBrandName = brandName.trim().slice(0, BRAND_NAME_MAX_LENGTH);

    if (!trimmedBrandName || isSubmitting) {
      return;
    }

    void onComplete(trimmedBrandName);
  };

  return (
    <div className="fixed inset-0 z-[90] overflow-y-auto px-4 py-6" role="dialog" aria-modal="true">
      <div
        className={`fixed inset-0 bg-slate-900/38 backdrop-blur-sm transition-opacity duration-200 ${
          visible ? "opacity-100" : "opacity-0"
        }`}
        onClick={() => {
          if (!isSubmitting) {
            onClose();
          }
        }}
      />

      <div className="relative z-10 flex min-h-full items-start justify-center sm:items-center">
        <div
          className={`custom-scrollbar relative max-h-[calc(100vh-3rem)] w-full max-w-2xl overflow-y-auto rounded-[30px] border border-white/70 bg-white shadow-premium transition-all duration-300 ease-out ${
            visible ? "translate-y-0 scale-100 opacity-100" : "translate-y-4 scale-95 opacity-0"
          }`}
        >
          <div className="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-primary via-primary-dark to-accent-rose" />

          <button
            type="button"
            onClick={onClose}
            className="absolute right-5 top-5 z-30 flex h-11 w-11 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 shadow-soft transition-colors hover:border-slate-300 hover:text-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
            aria-label="닫기"
            disabled={isSubmitting}
          >
            <span className="material-symbols-outlined text-[22px]">close</span>
          </button>

          <div className="border-b border-slate-100 bg-[#F8FBF8] px-6 pb-6 pt-7 sm:px-7">
            <div className="pr-12">
              <p className="text-[11px] font-bold uppercase tracking-[0.24em] text-slate-400">
                Selected District
              </p>
              <div className="mt-2 flex items-center gap-3">
                <h2 className="text-3xl font-black tracking-tight text-slate-900">
                  {district.name}
                </h2>
                <span
                  className={`rounded-full border px-4 py-1.5 text-sm font-extrabold ${
                    gradeTone[grade] ?? gradeTone.B
                  }`}
                >
                  {district.grade}
                </span>
              </div>
            </div>
          </div>

          <div className="p-6 sm:p-7">
            <p className="text-sm leading-relaxed text-slate-500">{district.description}</p>

            <div className="mt-5 flex flex-wrap gap-2">
              {district.tags.map((tag) => (
                <span
                  key={tag}
                  className="rounded-full border border-primary/15 bg-primary/10 px-3 py-1 text-xs font-semibold text-primary-dark"
                >
                  #{tag.replaceAll("_", " ")}
                </span>
              ))}
            </div>

            <div className="mt-6 grid gap-3 sm:grid-cols-3">
              <div className="rounded-2xl border border-slate-200 bg-slate-50/90 p-4">
                <span className="mb-1 block text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                  일일 임대료
                </span>
                {hasRentDiscount ? (
                  <div className="flex flex-col gap-1">
                    <span className="font-mono text-sm font-semibold text-rose-300 line-through decoration-2">
                      {district.rent}
                    </span>
                    <span className="font-mono text-lg font-bold text-rose-500">
                      {discountedRent}
                    </span>
                    <span className="text-[11px] font-semibold text-rose-400">
                      {rentDiscountLabel}
                    </span>
                  </div>
                ) : (
                  <span className="font-mono text-lg font-bold text-slate-900">
                    {district.rent}
                  </span>
                )}
              </div>

              <div className="rounded-2xl border border-slate-200 bg-slate-50/90 p-4">
                <span className="mb-1 block text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                  유동인구 순위
                </span>
                <span className={`text-lg font-bold ${congestionMeta.text}`}>
                  {congestionMeta.label}
                </span>
              </div>

              <div className="rounded-2xl border border-slate-200 bg-slate-50/90 p-4">
                <span className="mb-1 block text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                  인테리어 비용
                </span>
                <span className="font-mono text-lg font-bold text-slate-900">
                  {interiorCost ?? "-"}
                </span>
              </div>
            </div>

            <p className="mt-4 text-xs text-slate-400">
              인테리어 비용은 선택 지역의 기본 임대료를 기준으로 계산됩니다.
            </p>

            <form className="mt-6" onSubmit={handleSubmit}>
              <label className="mb-2 block text-sm font-semibold text-slate-600">
                팝업 브랜드명
              </label>
              <input
                type="text"
                value={brandName}
                onChange={(event) =>
                  setBrandName(event.target.value.slice(0, BRAND_NAME_MAX_LENGTH))
                }
                maxLength={BRAND_NAME_MAX_LENGTH}
                className="h-12 w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 text-base font-medium text-slate-800 transition-all placeholder:text-slate-300 focus:border-primary focus:ring-2 focus:ring-primary/15"
                placeholder="브랜드 이름을 입력해주세요"
                autoFocus
                disabled={isSubmitting}
              />
              <p className="mt-2 text-right text-xs text-slate-400">
                {brandName.length}/{BRAND_NAME_MAX_LENGTH}자
              </p>

              {submitError && (
                <p className="mt-3 text-sm text-rose-500">{submitError}</p>
              )}

              <button
                type="submit"
                disabled={!brandName.trim() || isSubmitting}
                className="mt-5 flex h-12 w-full items-center justify-center gap-2 rounded-2xl bg-primary font-bold text-slate-900 shadow-lg shadow-primary/20 transition-all hover:bg-primary-dark hover:text-white active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-40"
              >
                <span>{isSubmitting ? "시즌 참여 중..." : `${district.name}에서 시작하기`}</span>
                {!isSubmitting && (
                  <span className="material-symbols-outlined text-lg">arrow_forward</span>
                )}
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
}
