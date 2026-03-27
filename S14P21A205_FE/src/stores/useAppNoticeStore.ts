import { create } from "zustand";

export const AUTH_EXPIRED_NOTICE = "로그인이 만료되었습니다. 다시 로그인해주세요.";
export const SERVER_ISSUE_NOTICE = "서버에 일시적인 문제가 있습니다. 잠시 후 다시 시도해주세요.";

const AUTH_NOTICE_STORAGE_KEY = "bubblepopup:auth-notice";

function canUseSessionStorage() {
  return typeof window !== "undefined" && typeof window.sessionStorage !== "undefined";
}

function readPersistedAuthNotice() {
  if (!canUseSessionStorage()) {
    return null;
  }

  try {
    const value = window.sessionStorage.getItem(AUTH_NOTICE_STORAGE_KEY);
    return typeof value === "string" && value.trim().length > 0 ? value : null;
  } catch {
    return null;
  }
}

function persistAuthNotice(message: string | null) {
  if (!canUseSessionStorage()) {
    return;
  }

  try {
    if (message) {
      window.sessionStorage.setItem(AUTH_NOTICE_STORAGE_KEY, message);
      return;
    }

    window.sessionStorage.removeItem(AUTH_NOTICE_STORAGE_KEY);
  } catch {
    // Ignore storage failures and continue.
  }
}

interface AppNoticeState {
  authNotice: string | null;
  serverNotice: string | null;
  flashNotice: string | null;
  showAuthNotice: (message?: string) => void;
  clearAuthNotice: () => void;
  showServerNotice: (message?: string) => void;
  clearServerNotice: () => void;
  showFlashNotice: (message: string) => void;
  clearFlashNotice: () => void;
}

export const useAppNoticeStore = create<AppNoticeState>((set) => ({
  authNotice: readPersistedAuthNotice(),
  serverNotice: null,
  flashNotice: null,

  showAuthNotice: (message = AUTH_EXPIRED_NOTICE) => {
    persistAuthNotice(message);
    set({ authNotice: message });
  },

  clearAuthNotice: () => {
    persistAuthNotice(null);
    set({ authNotice: null });
  },

  showServerNotice: (message = SERVER_ISSUE_NOTICE) => {
    set({ serverNotice: message });
  },

  clearServerNotice: () => {
    set({ serverNotice: null });
  },

  showFlashNotice: (message) => {
    set({ flashNotice: message });
  },

  clearFlashNotice: () => {
    set({ flashNotice: null });
  },
}));
