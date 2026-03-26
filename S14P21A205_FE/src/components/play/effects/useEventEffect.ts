import { create } from "zustand";
import type { EventEffectType } from "./effects";
import { EFFECT_CONFIG } from "./effects";

interface EventEffectState {
  activeEffect: EventEffectType | null;
  triggerEffect: (type: EventEffectType) => void;
  clearEffect: () => void;
}

let timerId: ReturnType<typeof setTimeout> | null = null;

export const useEventEffectStore = create<EventEffectState>((set) => ({
  activeEffect: null,

  triggerEffect: (type) => {
    if (timerId) clearTimeout(timerId);
    set({ activeEffect: type });
    const duration = EFFECT_CONFIG[type].durationMs;
    timerId = setTimeout(() => {
      set({ activeEffect: null });
      timerId = null;
    }, duration);
  },

  clearEffect: () => {
    if (timerId) {
      clearTimeout(timerId);
      timerId = null;
    }
    set({ activeEffect: null });
  },
}));
