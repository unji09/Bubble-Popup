import type { TodayEventScheduleItem } from "../../../api/game";

export type EventEffectType =
  | "TYPHOON"
  | "FLOOD"
  | "FIRE"
  | "EARTHQUAKE"
  | "GOVERNMENT_SUBSIDY"
  | "CELEBRITY_APPEARANCE"
  | "SUBSTITUTE_HOLIDAY"
  | "INFECTIOUS_DISEASE"
  | "POLICY_CHANGE"
  | "FESTIVAL"
  | "PRICE_DOWN"
  | "PRICE_UP";

export interface SfxEntry {
  src: string;
  delayMs?: number;
  volume?: number;
}

export type SoundSrc = string | SfxEntry[] | null;

export const EFFECT_CONFIG: Record<
  EventEffectType,
  { durationMs: number; soundSrc: SoundSrc }
> = {
  TYPHOON: { durationMs: 15000, soundSrc: null },
  FLOOD: {
    durationMs: 15000,
    soundSrc: [
      { src: "/sfx/flood.mp3", delayMs: 0 },
      { src: "/sfx/flood.mp3", delayMs: 5000 },
      { src: "/sfx/flood.mp3", delayMs: 10000 },
    ],
  },
  FIRE: { durationMs: 15000, soundSrc: "/sfx/fire.mp3" },
  EARTHQUAKE: { durationMs: 15000, soundSrc: "/sfx/earthquake.mp3" },
  GOVERNMENT_SUBSIDY: { durationMs: 15000, soundSrc: "/sfx/subsidy.mp3" },
  CELEBRITY_APPEARANCE: {
    durationMs: 15000,
    soundSrc: [
      { src: "/sfx/celebrity.mp3", volume: 0.15, delayMs: 0 },
      { src: "/sfx/celebrity.mp3", volume: 0.15, delayMs: 4000 },
      { src: "/sfx/celebrity.mp3", volume: 0.15, delayMs: 8000 },
      { src: "/sfx/celebrity.mp3", volume: 0.15, delayMs: 12000 },
    ],
  },
  SUBSTITUTE_HOLIDAY: { durationMs: 15000, soundSrc: null }, // ConfettiEffect에서 burst 타이밍에 맞춰 재생
  INFECTIOUS_DISEASE: { durationMs: 15000, soundSrc: [{ src: "/sfx/epidemic.mp3", volume: 1.0 }] },
  POLICY_CHANGE: {
    durationMs: 15000,
    soundSrc: [
      { src: "/sfx/subsidy.mp3", delayMs: 0, volume: 0.8 },
      { src: "/sfx/stamp.mp3", delayMs: 1500 },
    ],
  },
  FESTIVAL: { durationMs: 15000, soundSrc: null },
  PRICE_DOWN: { durationMs: 7000, soundSrc: "/sfx/price-down.mp3" },
  PRICE_UP: { durationMs: 7000, soundSrc: [{ src: "/sfx/price-up.mp3", volume: 0.3 }] },
};

/** eventCategory → 이펙트 타입 매핑 */
const CATEGORY_MAP: Record<string, EventEffectType> = {
  TYPHOON: "TYPHOON",
  FLOOD: "FLOOD",
  FIRE: "FIRE",
  EARTHQUAKE: "EARTHQUAKE",
  GOVERNMENT_SUBSIDY: "GOVERNMENT_SUBSIDY",
  CELEBRITY_APPEARANCE: "CELEBRITY_APPEARANCE",
  SUBSTITUTE_HOLIDAY: "SUBSTITUTE_HOLIDAY",
  INFECTIOUS_DISEASE: "INFECTIOUS_DISEASE",
  POLICY_CHANGE: "POLICY_CHANGE",
  FESTIVAL: "FESTIVAL",
};

// _PRICE_UP / _PRICE_DOWN 카테고리 일괄 매핑
const PRICE_UP_CATEGORIES = [
  "BREAD_PRICE_UP", "MALA_SKEWER_PRICE_UP", "JELLY_PRICE_UP",
  "TTEOKBOKKI_PRICE_UP", "HAMBURGER_PRICE_UP", "ICE_CREAM_PRICE_UP",
  "DAKGANGJEONG_PRICE_UP", "TACO_PRICE_UP", "HOTDOG_PRICE_UP",
  "BUBBLE_TEA_PRICE_UP",
];
const PRICE_DOWN_CATEGORIES = [
  "BREAD_PRICE_DOWN", "MALA_SKEWER_PRICE_DOWN", "JELLY_PRICE_DOWN",
  "TTEOKBOKKI_PRICE_DOWN", "HAMBURGER_PRICE_DOWN", "ICE_CREAM_PRICE_DOWN",
  "DAKGANGJEONG_PRICE_DOWN", "TACO_PRICE_DOWN", "HOTDOG_PRICE_DOWN",
  "BUBBLE_TEA_PRICE_DOWN",
];
for (const c of PRICE_UP_CATEGORIES) CATEGORY_MAP[c] = "PRICE_UP";
for (const c of PRICE_DOWN_CATEGORIES) CATEGORY_MAP[c] = "PRICE_DOWN";

/** 이벤트 데이터를 분석하여 해당하는 3D 이펙트 타입을 반환한다. */
export function classifyEventEffect(
  event: TodayEventScheduleItem,
): EventEffectType | null {
  // 1차: eventCategory로 정확 매칭
  const category = event.eventCategory;
  if (category && CATEGORY_MAP[category]) {
    return CATEGORY_MAP[category];
  }

  // 2차: 이름 기반 fallback
  const name = event.eventName ?? event.newsTitle ?? event.type ?? "";

  if (/태풍|typhoon/iu.test(name)) return "TYPHOON";
  if (/침수|홍수|flood/iu.test(name)) return "FLOOD";
  if (/화재|fire/iu.test(name)) return "FIRE";
  if (/지진|earthquake/iu.test(name)) return "EARTHQUAKE";
  if (/정부\s*지원금|지원금|subsidy/iu.test(name)) return "GOVERNMENT_SUBSIDY";
  if (/연예인|유명인|celebrity/iu.test(name)) return "CELEBRITY_APPEARANCE";
  if (/대체\s*공휴일|공휴일|holiday/iu.test(name)) return "SUBSTITUTE_HOLIDAY";
  if (/감염병|전염병|infectious|epidemic|virus/iu.test(name)) return "INFECTIOUS_DISEASE";
  if (/정책\s*변경|정부\s*방침|규제|policy/iu.test(name)) return "POLICY_CHANGE";
  if (/축제|행사|콘서트|전시|공연|페스티벌|플리마켓|야구|경기|concert|festival/iu.test(name)) return "FESTIVAL";
  if (/price\s*down|가격\s*하락|원가\s*하락|원재료.*하락/iu.test(name)) return "PRICE_DOWN";
  if (/price\s*up|가격\s*상승|원가\s*상승|원재료.*상승/iu.test(name)) return "PRICE_UP";

  return null;
}
