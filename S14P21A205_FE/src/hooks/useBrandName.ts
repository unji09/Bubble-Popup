import { useBrandStore } from "../stores/useBrandStore";

/** 스토어 외부에서 브랜드명 설정 (GameGuard, API 응답 등) */
export function setStoredBrandName(nextBrandName: string) {
  const trimmed = nextBrandName.trim();
  if (trimmed) {
    useBrandStore.getState().setBrandName(trimmed);
  }
  return trimmed || useBrandStore.getState().brandName;
}

/** 스토어 외부에서 브랜드명 초기화 */
export function clearStoredBrandName() {
  useBrandStore.getState().clearBrandName();
  return useBrandStore.getState().brandName;
}

/** 현재 브랜드명 조회 (비-React 컨텍스트용) */
export function getStoredBrandName() {
  return useBrandStore.getState().brandName;
}

/** React 컴포넌트용 hook */
export default function useBrandName() {
  const brandName = useBrandStore((s) => s.brandName);
  const setBrandName = useBrandStore((s) => s.setBrandName);
  const clearBrandName = useBrandStore((s) => s.clearBrandName);

  return {
    brandName,
    setBrandName: (nextBrandName: string) => {
      const trimmed = nextBrandName.trim();
      if (trimmed) setBrandName(trimmed);
      return trimmed || brandName;
    },
    clearBrandName: () => {
      clearBrandName();
      return useBrandStore.getState().brandName;
    },
  };
}
