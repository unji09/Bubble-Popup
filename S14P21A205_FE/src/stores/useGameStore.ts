import { create } from "zustand";

const PLAYABLE_FROM_DAY_STORAGE_KEY = "game_playableFromDay";
const CURRENT_LOCATION_NAME_STORAGE_KEY = "game_currentLocationName";
const BANKRUPT_NOTICE_SEASON_STORAGE_KEY = "game_bankruptNoticeSeasonNumber";
const BANKRUPT_REPORT_DAY_STORAGE_KEY = "game_bankruptReportDay";

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

function readPersistedCurrentLocationName(): string | null {
  try {
    const raw = sessionStorage.getItem(CURRENT_LOCATION_NAME_STORAGE_KEY)?.trim();
    return raw || null;
  } catch {
    return null;
  }
}

function persistCurrentLocationName(locationName: string | null) {
  try {
    if (!locationName || !locationName.trim()) {
      sessionStorage.removeItem(CURRENT_LOCATION_NAME_STORAGE_KEY);
    } else {
      sessionStorage.setItem(CURRENT_LOCATION_NAME_STORAGE_KEY, locationName.trim());
    }
  } catch {
    // sessionStorage access failures can be ignored here.
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

function readPersistedBankruptReportDay(): number | null {
  try {
    const raw = sessionStorage.getItem(BANKRUPT_REPORT_DAY_STORAGE_KEY);
    if (raw === null) return null;
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

function persistBankruptReportDay(day: number | null) {
  try {
    if (day === null) {
      sessionStorage.removeItem(BANKRUPT_REPORT_DAY_STORAGE_KEY);
    } else {
      sessionStorage.setItem(BANKRUPT_REPORT_DAY_STORAGE_KEY, String(day));
    }
  } catch {
    // sessionStorage access failures can be ignored here.
  }
}

interface GameState {
  /** join API 응답에서 받은 playableFromDay (이번 시즌 한정) */
  playableFromDay: number | null;
  currentLocationName: string | null;
  bankruptNoticeSeasonNumber: number | null;
  bankruptReportDay: number | null;

  setPlayableFromDay: (day: number) => void;
  setCurrentLocationName: (locationName: string | null) => void;
  setBankruptNoticeSeasonNumber: (seasonNumber: number) => void;
  setBankruptReportDay: (day: number) => void;
  clearBankruptReportDay: () => void;
  clearBankruptNotice: () => void;
  clearGame: () => void;
}

export const useGameStore = create<GameState>((set) => ({
  playableFromDay: readPersistedDay(),
  currentLocationName: readPersistedCurrentLocationName(),
  bankruptNoticeSeasonNumber: readPersistedBankruptNoticeSeasonNumber(),
  bankruptReportDay: readPersistedBankruptReportDay(),

  setPlayableFromDay: (day: number) => {
    persistDay(day);
    set({ playableFromDay: day });
  },
  setCurrentLocationName: (locationName: string | null) => {
    const normalizedLocationName = locationName?.trim() ? locationName.trim() : null;
    persistCurrentLocationName(normalizedLocationName);
    set({ currentLocationName: normalizedLocationName });
  },
  setBankruptNoticeSeasonNumber: (seasonNumber: number) => {
    persistBankruptNoticeSeasonNumber(seasonNumber);
    set({ bankruptNoticeSeasonNumber: seasonNumber });
  },
  setBankruptReportDay: (day: number) => {
    persistBankruptReportDay(day);
    set({ bankruptReportDay: day });
  },
  clearBankruptReportDay: () => {
    persistBankruptReportDay(null);
    set({ bankruptReportDay: null });
  },
  clearBankruptNotice: () => {
    persistBankruptNoticeSeasonNumber(null);
    set({ bankruptNoticeSeasonNumber: null });
  },
  clearGame: () => {
    persistDay(null);
    persistCurrentLocationName(null);
    persistBankruptReportDay(null);
    set({
      playableFromDay: null,
      currentLocationName: null,
      bankruptReportDay: null,
    });
  },
}));
