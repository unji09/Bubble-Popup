import axios from "axios";
import { useState } from "react";
import ModalWrapper from "./ModalWrapper";

interface ApiErrorResponse {
  message?: string;
}

interface ShareModalProps {
  currentStock: number;
  onClose: () => void;
  onSubmit: (quantity: number) => Promise<void> | void;
}

export default function ShareModal({
  currentStock,
  onClose,
  onSubmit,
}: ShareModalProps) {
  const [quantity, setQuantity] = useState<number | "">("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const maxShareQuantity = Math.max(0, currentStock);
  const isOutOfStock = maxShareQuantity <= 0;

  const handleChange = (value: string) => {
    if (value === "") {
      setQuantity("");
      return;
    }

    if (isOutOfStock) {
      setQuantity("");
      return;
    }

    const normalizedValue = Math.max(1, Math.min(maxShareQuantity, Number(value)));
    setQuantity(normalizedValue);
  };

  const handleSubmit = async () => {
    if (isOutOfStock || !quantity || Number(quantity) <= 0 || isSubmitting) {
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);

    try {
      const normalizedQuantity = Math.max(1, Math.min(maxShareQuantity, Number(quantity)));
      await Promise.resolve(onSubmit(normalizedQuantity));
    } catch (error) {
      if (axios.isAxiosError<ApiErrorResponse>(error)) {
        const status = error.response?.status;
        if (status === 400 || status === 409) {
          setSubmitError("앗! 그 사이 손님이 다녀가서 나눔할 재고가 부족해졌어요");
        } else {
          setSubmitError(
            error.response?.data?.message ?? "나눔 실행에 실패했습니다. 잠시 후 다시 시도해주세요.",
          );
        }
      } else {
        setSubmitError("나눔 실행에 실패했습니다. 잠시 후 다시 시도해주세요.");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <ModalWrapper onClose={onClose}>
      <div className="p-8 pb-6 text-center">
        <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-primary/10 text-3xl">
          🎁
        </div>
        <h2 className="mb-2 text-xl font-bold text-slate-800">나눔 이벤트</h2>
        <p className="text-sm leading-relaxed text-slate-500">
          재고를 일부 나누며 손님 유입 효과를 노릴 수 있습니다.
          <br />
          보유 재고 내에서만 수량을 선택할 수 있습니다.
        </p>
      </div>

      <div className="space-y-6 px-8">
        <div className="flex items-center justify-between rounded-xl border border-slate-100 bg-slate-50 p-3">
          <span className="text-sm font-medium text-slate-600">현재 재고</span>
          <span className="text-sm font-bold text-slate-800">{currentStock}개</span>
        </div>

        <div className="space-y-2">
          <label className="ml-1 block text-xs font-bold uppercase tracking-wider text-slate-500">
            나눔 수량
          </label>
          <div className="relative">
            <input
              type="number"
              value={quantity}
              onChange={(event) => handleChange(event.target.value)}
              placeholder={isOutOfStock ? "나눌 재고가 없습니다" : "수량을 입력하세요"}
              min={isOutOfStock ? 0 : 1}
              max={maxShareQuantity}
              disabled={isOutOfStock || isSubmitting}
              className="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-lg font-medium text-slate-800 transition-all placeholder:text-slate-300 focus:border-primary focus:ring-2 focus:ring-primary disabled:cursor-not-allowed disabled:bg-slate-50"
            />
            <span className="absolute right-4 top-1/2 -translate-y-1/2 text-sm font-medium text-slate-400">
              개
            </span>
          </div>
          <div className="mt-1 flex items-center gap-1.5 text-rose-soft">
            <span className="material-symbols-outlined text-[16px]">info</span>
            <span className="text-xs font-medium">1개부터 현재 재고 {maxShareQuantity}개까지 선택할 수 있습니다.</span>
          </div>
        </div>

        {submitError && (
          <div className="rounded-2xl border border-red-200 bg-red-50/80 px-4 py-3 text-sm text-red-600">
            {submitError}
          </div>
        )}
      </div>

      <div className="p-8 pt-6">
        <button
          type="button"
          onClick={() => void handleSubmit()}
          disabled={isOutOfStock || !quantity || Number(quantity) <= 0 || isSubmitting}
          className="flex w-full items-center justify-center gap-2 rounded-xl bg-primary py-4 font-bold text-white shadow-lg shadow-primary/30 transition-all hover:bg-primary-dark active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-40"
        >
          <span className="material-symbols-outlined text-[20px]">volunteer_activism</span>
          <span>{isSubmitting ? "나눔 실행중..." : "나눔 시작하기"}</span>
        </button>
      </div>
    </ModalWrapper>
  );
}
