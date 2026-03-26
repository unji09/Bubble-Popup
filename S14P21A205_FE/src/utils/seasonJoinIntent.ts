import { SEASON_JOIN_INTENT_STORAGE_KEY } from "../constants";

export function hasSeasonJoinIntent() {
  try {
    return sessionStorage.getItem(SEASON_JOIN_INTENT_STORAGE_KEY) === "true";
  } catch {
    return false;
  }
}

export function setSeasonJoinIntent() {
  try {
    sessionStorage.setItem(SEASON_JOIN_INTENT_STORAGE_KEY, "true");
  } catch {
    // Ignore storage access failures and continue navigation.
  }
}

export function clearSeasonJoinIntent() {
  try {
    sessionStorage.removeItem(SEASON_JOIN_INTENT_STORAGE_KEY);
  } catch {
    // Ignore storage access failures and continue navigation.
  }
}
