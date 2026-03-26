export interface ErrorPageState {
  status: number;
  code?: string | null;
  message?: string | null;
  path?: string | null;
  timestamp?: string | null;
  returnTo?: string | null;
}

interface StoredErrorPageState extends ErrorPageState {
  storedAt: number;
}

const ERROR_PAGE_STATE_STORAGE_KEY = "bubblebubble:error-page-state";
const ERROR_PAGE_STATE_TTL_MS = 5 * 60 * 1000;

function canUseSessionStorage() {
  return typeof window !== "undefined" && typeof window.sessionStorage !== "undefined";
}

function normalizeOptionalString(value: unknown) {
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : null;
}

export function saveErrorPageState(state: ErrorPageState) {
  if (!canUseSessionStorage()) {
    return;
  }

  const nextState: StoredErrorPageState = {
    ...state,
    code: normalizeOptionalString(state.code),
    message: normalizeOptionalString(state.message),
    path: normalizeOptionalString(state.path),
    timestamp: normalizeOptionalString(state.timestamp),
    returnTo: normalizeOptionalString(state.returnTo),
    storedAt: Date.now(),
  };

  try {
    window.sessionStorage.setItem(ERROR_PAGE_STATE_STORAGE_KEY, JSON.stringify(nextState));
  } catch {
    // Ignore storage failures and continue with redirect.
  }
}

export function clearErrorPageState() {
  if (!canUseSessionStorage()) {
    return;
  }

  try {
    window.sessionStorage.removeItem(ERROR_PAGE_STATE_STORAGE_KEY);
  } catch {
    // Ignore storage failures.
  }
}

export function consumeErrorPageState(): ErrorPageState | null {
  if (!canUseSessionStorage()) {
    return null;
  }

  try {
    const raw = window.sessionStorage.getItem(ERROR_PAGE_STATE_STORAGE_KEY);
    window.sessionStorage.removeItem(ERROR_PAGE_STATE_STORAGE_KEY);

    if (!raw) {
      return null;
    }

    const parsed = JSON.parse(raw) as Partial<StoredErrorPageState>;
    if (typeof parsed.storedAt !== "number" || Date.now() - parsed.storedAt > ERROR_PAGE_STATE_TTL_MS) {
      return null;
    }

    if (typeof parsed.status !== "number") {
      return null;
    }

    return {
      status: parsed.status,
      code: normalizeOptionalString(parsed.code),
      message: normalizeOptionalString(parsed.message),
      path: normalizeOptionalString(parsed.path),
      timestamp: normalizeOptionalString(parsed.timestamp),
      returnTo: normalizeOptionalString(parsed.returnTo),
    };
  } catch {
    return null;
  }
}

export function redirectToErrorPage(targetPath: string, state: ErrorPageState) {
  saveErrorPageState(state);

  if (typeof window === "undefined") {
    return;
  }

  if (window.location.pathname === targetPath) {
    window.location.reload();
    return;
  }

  window.location.replace(targetPath);
}
