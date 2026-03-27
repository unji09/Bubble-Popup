import { create } from "zustand";
import type { EventEffectType } from "./effects";
import type { SoundSrc } from "./effects";
import { EFFECT_CONFIG } from "./effects";

interface EventEffectState {
  activeEffect: EventEffectType | null;
  triggerEffect: (type: EventEffectType) => void;
  /** Unity 로딩 중일 때 이펙트를 대기시킴 — 종료 시각만 기록하고 화면에 표시하지 않음 */
  deferEffect: (type: EventEffectType) => void;
  /** Unity 로딩 완료 시 호출 — 대기 중인 이펙트가 있으면 남은 시간만큼 재생 */
  activateDeferred: () => void;
  clearEffect: () => void;
}

let timerId: ReturnType<typeof setTimeout> | null = null;
let activeAudios: HTMLAudioElement[] = [];
let delayTimerIds: ReturnType<typeof setTimeout>[] = [];

// 이펙트 종료 시각 추적 (탭 복귀 시 복원용)
let effectEndTimestamp: number | null = null;
let currentEffectType: EventEffectType | null = null;

// 대기 중인(deferred) 이펙트
let deferredEndTimestamp: number | null = null;
let deferredEffectType: EventEffectType | null = null;

// 브라우저 자동재생 정책 우회: 첫 사용자 인터랙션에서 오디오 언락
let audioUnlocked = false;
function unlockAudio() {
  if (audioUnlocked) return;
  const silent = new Audio();
  silent.volume = 0;
  silent.play().then(() => {
    silent.pause();
    audioUnlocked = true;
    removeUnlockListeners();
  }).catch(() => {});
}
const unlockEvents = ["click", "touchstart", "keydown", "mousedown", "pointerdown"] as const;
function removeUnlockListeners() {
  for (const evt of unlockEvents) document.removeEventListener(evt, unlockAudio);
}
for (const evt of unlockEvents) document.addEventListener(evt, unlockAudio, { once: true });

function stopAllSfx() {
  for (const id of delayTimerIds) clearTimeout(id);
  delayTimerIds = [];
  for (const audio of activeAudios) {
    audio.pause();
    audio.src = "";
  }
  activeAudios = [];
}

function playAudio(src: string, volume = 0.5) {
  const audio = new Audio(src);
  audio.volume = volume;
  audio.play().catch(() => {
    const retry = () => {
      audio.play().catch(() => {});
      document.removeEventListener("click", retry);
      document.removeEventListener("touchstart", retry);
    };
    document.addEventListener("click", retry, { once: true });
    document.addEventListener("touchstart", retry, { once: true });
  });
  activeAudios.push(audio);
}

function playSfx(soundSrc: SoundSrc) {
  stopAllSfx();
  if (!soundSrc) return;
  if (typeof soundSrc === "string") {
    playAudio(soundSrc);
    return;
  }
  for (const entry of soundSrc) {
    const delay = entry.delayMs ?? 0;
    if (delay === 0) {
      playAudio(entry.src, entry.volume);
    } else {
      const id = setTimeout(() => playAudio(entry.src, entry.volume), delay);
      delayTimerIds.push(id);
    }
  }
}

function startEffect(set: (s: Partial<EventEffectState>) => void, type: EventEffectType, durationMs: number) {
  if (timerId) clearTimeout(timerId);

  const { soundSrc } = EFFECT_CONFIG[type];
  effectEndTimestamp = Date.now() + durationMs;
  currentEffectType = type;

  // deferred 상태 초기화
  deferredEndTimestamp = null;
  deferredEffectType = null;

  set({ activeEffect: type });
  playSfx(soundSrc);

  timerId = setTimeout(() => {
    effectEndTimestamp = null;
    currentEffectType = null;
    set({ activeEffect: null });
    stopAllSfx();
    timerId = null;
  }, durationMs);
}

export const useEventEffectStore = create<EventEffectState>((set) => ({
  activeEffect: null,

  triggerEffect: (type) => {
    const { durationMs } = EFFECT_CONFIG[type];
    startEffect(set, type, durationMs);
  },

  deferEffect: (type) => {
    // 화면에 표시하지 않고 종료 시각만 기록
    const { durationMs } = EFFECT_CONFIG[type];
    deferredEndTimestamp = Date.now() + durationMs;
    deferredEffectType = type;
  },

  activateDeferred: () => {
    if (deferredEndTimestamp == null || deferredEffectType == null) return;

    const remaining = deferredEndTimestamp - Date.now();
    deferredEndTimestamp = null;
    const type = deferredEffectType;
    deferredEffectType = null;

    if (remaining <= 0) return;

    startEffect(set, type, remaining);
  },

  clearEffect: () => {
    if (timerId) {
      clearTimeout(timerId);
      timerId = null;
    }
    effectEndTimestamp = null;
    currentEffectType = null;
    deferredEndTimestamp = null;
    deferredEffectType = null;
    stopAllSfx();
    set({ activeEffect: null });
  },
}));

// 탭 복귀 시: 이펙트 시간이 아직 남아있으면 다시 활성화
document.addEventListener("visibilitychange", () => {
  if (document.hidden) return;
  if (effectEndTimestamp == null || currentEffectType == null) return;

  const remaining = effectEndTimestamp - Date.now();
  if (remaining <= 0) {
    // 이미 끝남
    effectEndTimestamp = null;
    currentEffectType = null;
    return;
  }

  // activeEffect가 null이면 (백그라운드에서 setTimeout이 이미 실행됨) 복원
  const state = useEventEffectStore.getState();
  if (state.activeEffect == null) {
    if (timerId) clearTimeout(timerId);

    const type = currentEffectType;
    const { soundSrc } = EFFECT_CONFIG[type];
    useEventEffectStore.setState({ activeEffect: type });
    playSfx(soundSrc);

    timerId = setTimeout(() => {
      effectEndTimestamp = null;
      currentEffectType = null;
      useEventEffectStore.setState({ activeEffect: null });
      stopAllSfx();
      timerId = null;
    }, remaining);
  }
});
