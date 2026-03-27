import { create } from "zustand";

const DEFAULT_BRAND_NAME = "";

interface BrandState {
  brandName: string;
  setBrandName: (name: string) => void;
  clearBrandName: () => void;
}

export const useBrandStore = create<BrandState>((set) => ({
  brandName: DEFAULT_BRAND_NAME,

  setBrandName: (name: string) => {
    const trimmed = name.trim();
    if (trimmed) {
      set({ brandName: trimmed });
    }
  },

  clearBrandName: () => {
    set({ brandName: DEFAULT_BRAND_NAME });
  },
}));
