import { create } from "zustand";

const STORAGE_KEY = "game_playableFromDay";

function readPersistedDay(): number | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (raw === null) return null;
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

function persistDay(day: number | null) {
  try {
    if (day === null) {
      sessionStorage.removeItem(STORAGE_KEY);
    } else {
      sessionStorage.setItem(STORAGE_KEY, String(day));
    }
  } catch {
    // sessionStorage 접근 실패 무시
  }
}

interface GameState {
  /** join API 응답에서 받은 playableFromDay (이번 시즌 한정) */
  playableFromDay: number | null;

  setPlayableFromDay: (day: number) => void;
  clearGame: () => void;
}

export const useGameStore = create<GameState>((set) => ({
  playableFromDay: readPersistedDay(),

  setPlayableFromDay: (day: number) => {
    persistDay(day);
    set({ playableFromDay: day });
  },
  clearGame: () => {
    persistDay(null);
    set({ playableFromDay: null });
  },
}));
