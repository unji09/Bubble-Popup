/**
 * 게임 시간 정책 상수
 * BE의 GameTimePolicy.java와 동일하게 유지할 것
 *
 * 시즌 타임라인:
 * [지역선택 1분] → [1일차: 영업준비 40초 + 영업중 2분 + 리포트 20초] × 7일
 * → [시즌요약 3분] → [다음시즌대기 5분] → 종료
 */

// --- 구간별 시간 (초) ---

/** 지역 선택 구간 */
export const LOCATION_SELECTION_SECONDS = 60; // 1분

/** 영업 준비 구간 */
export const PREP_SECONDS = 40;

/** 영업중 구간 */
export const BUSINESS_SECONDS = 120; // 2분

/** 일일 리포트 구간 */
export const REPORT_SECONDS = 20;

/** 하루 전체 = 영업준비 + 영업중 + 리포트 */
export const DAY_SECONDS = PREP_SECONDS + BUSINESS_SECONDS + REPORT_SECONDS; // 180초 = 3분

/** 시즌 요약 구간 */
export const SEASON_SUMMARY_SECONDS = 180; // 3분

/** 다음 시즌 대기 구간 */
export const NEXT_SEASON_WAIT_SECONDS = 300; // 5분

/** 총 일수 */
export const TOTAL_DAYS = 7;

// --- 영업중 세부 ---

/** 인게임 영업 시작 시간 */
export const BUSINESS_OPEN_HOUR = 10;

/** 인게임 영업 종료 시간 */
export const BUSINESS_CLOSE_HOUR = 22;

/** 인게임 영업 시간 (분) = 12시간 = 720분 */
export const GAME_BUSINESS_MINUTES = (BUSINESS_CLOSE_HOUR - BUSINESS_OPEN_HOUR) * 60;

/** 틱 간격 (초) = 서버가 상태를 계산하는 주기 */
export const TICK_INTERVAL_SECONDS = 10;

/** 총 틱 수 = 영업중시간 / 틱간격 */
export const TOTAL_TICK_COUNT = BUSINESS_SECONDS / TICK_INTERVAL_SECONDS; // 12

// --- 시즌 페이즈 ---

export type SeasonPhase =
  | "LOCATION_SELECTION"
  | "DAY_PREPARING"
  | "DAY_BUSINESS"
  | "DAY_REPORT"
  | "SEASON_SUMMARY"
  | "NEXT_SEASON_WAITING"
  | "CLOSED";

// --- 유틸 함수 ---

/** 시즌 전체 플레이 가능 시간 (지역선택 + 7일) */
export function playableDurationSeconds(totalDays = TOTAL_DAYS): number {
  return LOCATION_SELECTION_SECONDS + DAY_SECONDS * totalDays;
}

/** 시즌 전체 사이클 시간 (플레이 + 요약 + 대기) */
export function seasonCycleDurationSeconds(totalDays = TOTAL_DAYS): number {
  return playableDurationSeconds(totalDays) + SEASON_SUMMARY_SECONDS + NEXT_SEASON_WAIT_SECONDS;
}

/** 영업중 경과 초 → 인게임 시각 문자열 (HH:MM) */
export function elapsedToGameTime(elapsedBusinessSeconds: number): string {
  const bounded = Math.max(0, Math.min(elapsedBusinessSeconds, BUSINESS_SECONDS));
  const gameMinutes = Math.floor((bounded * GAME_BUSINESS_MINUTES) / BUSINESS_SECONDS);
  const hour = BUSINESS_OPEN_HOUR + Math.floor(gameMinutes / 60);
  const minute = gameMinutes % 60;
  return `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
}

/** 페이즈에 따라 이동해야 할 페이지 경로 반환 */
export function phaseToRoute(phase: SeasonPhase, day: number | null): string | null {
  switch (phase) {
    case "LOCATION_SELECTION":
      return "/game/setup/location";
    case "DAY_PREPARING":
      return day ? `/game/${day}/prep` : null;
    case "DAY_BUSINESS":
      return day ? `/game/${day}/play` : null;
    case "DAY_REPORT":
      return day ? `/game/${day}/report` : null;
    case "SEASON_SUMMARY":
    case "NEXT_SEASON_WAITING":
      return "/ranking";
    case "CLOSED":
      return "/";
    default:
      return null;
  }
}
