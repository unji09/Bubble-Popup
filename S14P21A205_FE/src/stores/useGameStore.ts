import { create } from "zustand";

const PLAYABLE_FROM_DAY_STORAGE_KEY = "game_playableFromDay";
const BANKRUPT_NOTICE_SEASON_STORAGE_KEY = "game_bankruptNoticeSeasonNumber";

function readPersistedDay(): number | null {
  try {
    const raw = sessionStorage.getItem(PLAYABLE_FROM_DAY_STORAGE_KEY);
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
      sessionStorage.removeItem(PLAYABLE_FROM_DAY_STORAGE_KEY);
    } else {
      sessionStorage.setItem(PLAYABLE_FROM_DAY_STORAGE_KEY, String(day));
    }
  } catch {
    // sessionStorage 접근 실패 무시
  }
}

function readPersistedBankruptNoticeSeasonNumber(): number | null {
  try {
    const raw = localStorage.getItem(BANKRUPT_NOTICE_SEASON_STORAGE_KEY);
    if (raw === null) return null;
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

function persistBankruptNoticeSeasonNumber(seasonNumber: number | null) {
  try {
    if (seasonNumber === null) {
      localStorage.removeItem(BANKRUPT_NOTICE_SEASON_STORAGE_KEY);
    } else {
      localStorage.setItem(BANKRUPT_NOTICE_SEASON_STORAGE_KEY, String(seasonNumber));
    }
  } catch {
    // localStorage 접근 실패 무시
  }
}

interface GameState {
  /** join API 응답에서 받은 playableFromDay (이번 시즌 한정) */
  playableFromDay: number | null;
  bankruptNoticeSeasonNumber: number | null;

  setPlayableFromDay: (day: number) => void;
  setBankruptNoticeSeasonNumber: (seasonNumber: number) => void;
  clearBankruptNotice: () => void;
  clearGame: () => void;
}

export const useGameStore = create<GameState>((set) => ({
  playableFromDay: readPersistedDay(),
  bankruptNoticeSeasonNumber: readPersistedBankruptNoticeSeasonNumber(),

  setPlayableFromDay: (day: number) => {
    persistDay(day);
    set({ playableFromDay: day });
  },
  setBankruptNoticeSeasonNumber: (seasonNumber: number) => {
    persistBankruptNoticeSeasonNumber(seasonNumber);
    set({ bankruptNoticeSeasonNumber: seasonNumber });
  },
  clearBankruptNotice: () => {
    persistBankruptNoticeSeasonNumber(null);
    set({ bankruptNoticeSeasonNumber: null });
  },
  clearGame: () => {
    persistDay(null);
    set({ playableFromDay: null });
  },
}));
