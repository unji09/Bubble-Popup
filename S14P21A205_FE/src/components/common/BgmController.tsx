import { useEffect, useRef, useState, type ChangeEvent } from "react";
import { useLocation } from "react-router-dom";

const TRACKS = [
  {
    id: "bgm1",
    label: "시나몬라떼",
    src: "/bgm/Bubblepopup_bgm1.mp3",
    mood: "jazz",
    moodLabel: "재즈 무드",
  },
  {
    id: "bgm2",
    label: "솜사탕",
    src: "/bgm/Bubblepopup_bgm2.mp3",
    mood: "bright",
    moodLabel: "산뜻한 무드",
  },
  {
    id: "bgm3",
    label: "레몬소다",
    src: "/bgm/Bubblepopup_bgm3.mp3",
    mood: "bright",
    moodLabel: "산뜻한 무드",
  },
  {
    id: "bgm4",
    label: "카라멜 브륄레",
    src: "/bgm/Bubblepopup_bgm4.mp3",
    mood: "jazz",
    moodLabel: "재즈 무드",
  },
] as const;

type Track = (typeof TRACKS)[number];
type TrackId = Track["id"];
type TrackMood = Track["mood"];

const DEFAULT_TRACK_ID: TrackId = "bgm1";
const STORAGE_KEYS = {
  trackId: "bubblepopup-bgm-track",
  isPlaying: "bubblepopup-bgm-playing",
  volume: "bubblepopup-bgm-volume",
} as const;
const DEFAULT_VOLUME = 0.35;
const PLAY_ROUTE_PATTERN = /^\/game\/[^/]+\/play\/?$/;

const MOOD_STYLES: Record<
  TrackMood,
  {
    chip: string;
    dot: string;
  }
> = {
  jazz: {
    chip: "border-accent-rose/25 bg-accent-rose/10 text-rose-dark",
    dot: "bg-accent-rose",
  },
  bright: {
    chip: "border-primary/25 bg-primary/12 text-primary-dark",
    dot: "bg-primary-dark",
  },
};

function clampVolume(value: number) {
  return Math.min(1, Math.max(0, value));
}

function isTrackId(value: string | null): value is TrackId {
  return TRACKS.some((track) => track.id === value);
}

function readStoredTrackId() {
  const storedValue = window.localStorage.getItem(STORAGE_KEYS.trackId);
  return isTrackId(storedValue) ? storedValue : DEFAULT_TRACK_ID;
}

function readStoredPlayingState() {
  const storedValue = window.localStorage.getItem(STORAGE_KEYS.isPlaying);
  return storedValue === null ? true : storedValue === "true";
}

function readStoredVolume() {
  const storedValue = window.localStorage.getItem(STORAGE_KEYS.volume);
  const parsedValue = storedValue === null ? DEFAULT_VOLUME : Number(storedValue);

  return Number.isFinite(parsedValue) ? clampVolume(parsedValue) : DEFAULT_VOLUME;
}

export default function BgmController() {
  const location = useLocation();
  const [selectedTrackId, setSelectedTrackId] = useState<TrackId>(() => readStoredTrackId());
  const [isPlaying, setIsPlaying] = useState(() => readStoredPlayingState());
  const [volume, setVolume] = useState(() => readStoredVolume());
  const [isPanelOpen, setIsPanelOpen] = useState(false);
  const [requiresInteraction, setRequiresInteraction] = useState(false);
  const panelRef = useRef<HTMLDivElement | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const loadedTrackIdRef = useRef<TrackId | null>(null);

  const selectedTrack = TRACKS.find((track) => track.id === selectedTrackId) ?? TRACKS[0];
  const sliderProgress = `${Math.round(volume * 100)}%`;
  const selectedMoodStyle = MOOD_STYLES[selectedTrack.mood];
  const isPlayRoute = PLAY_ROUTE_PATTERN.test(location.pathname);

  useEffect(() => {
    const audio = new Audio();
    audio.loop = true;
    audio.preload = "auto";
    audio.volume = DEFAULT_VOLUME;
    audioRef.current = audio;

    return () => {
      audio.pause();
      audio.src = "";
      audioRef.current = null;
    };
  }, []);

  useEffect(() => {
    window.localStorage.setItem(STORAGE_KEYS.trackId, selectedTrackId);
  }, [selectedTrackId]);

  useEffect(() => {
    window.localStorage.setItem(STORAGE_KEYS.isPlaying, String(isPlaying));
  }, [isPlaying]);

  useEffect(() => {
    window.localStorage.setItem(STORAGE_KEYS.volume, String(volume));

    if (audioRef.current) {
      audioRef.current.volume = volume;
    }
  }, [volume]);

  useEffect(() => {
    const audio = audioRef.current;

    if (!audio) {
      return;
    }

    if (loadedTrackIdRef.current !== selectedTrackId) {
      audio.src = selectedTrack.src;
      audio.currentTime = 0;
      loadedTrackIdRef.current = selectedTrackId;
    }

    if (!isPlaying) {
      audio.pause();
      return;
    }

    let cancelled = false;

    void audio.play().then(
      () => {
        if (!cancelled) {
          setRequiresInteraction(false);
        }
      },
      () => {
        if (!cancelled) {
          setRequiresInteraction(true);
        }
      },
    );

    return () => {
      cancelled = true;
    };
  }, [isPlaying, selectedTrackId, selectedTrack.src]);

  useEffect(() => {
    if (!requiresInteraction || !isPlaying) {
      return;
    }

    const resumePlayback = () => {
      const audio = audioRef.current;

      if (!audio) {
        return;
      }

      void audio.play().then(
        () => setRequiresInteraction(false),
        () => setRequiresInteraction(true),
      );
    };

    window.addEventListener("pointerdown", resumePlayback);
    window.addEventListener("keydown", resumePlayback);

    return () => {
      window.removeEventListener("pointerdown", resumePlayback);
      window.removeEventListener("keydown", resumePlayback);
    };
  }, [isPlaying, requiresInteraction]);

  useEffect(() => {
    if (!isPanelOpen) {
      return;
    }

    const handlePointerDown = (event: PointerEvent) => {
      if (panelRef.current?.contains(event.target as Node)) {
        return;
      }

      setIsPanelOpen(false);
    };

    window.addEventListener("pointerdown", handlePointerDown);

    return () => {
      window.removeEventListener("pointerdown", handlePointerDown);
    };
  }, [isPanelOpen]);

  const handleTrackSelect = (trackId: TrackId) => {
    setSelectedTrackId(trackId);
    setIsPlaying(true);
    setRequiresInteraction(false);
  };

  const handlePlaybackToggle = () => {
    setIsPlaying((prev) => {
      const nextValue = !prev;

      if (!nextValue) {
        setRequiresInteraction(false);
      }

      return nextValue;
    });
  };

  const handleVolumeChange = (event: ChangeEvent<HTMLInputElement>) => {
    setVolume(clampVolume(Number(event.target.value)));
  };

  return (
    <div
      ref={panelRef}
      className="fixed bottom-4 right-4 z-[80] flex items-end gap-3 md:bottom-6 md:right-6"
    >
      <style>{`
        @keyframes bgmEqualizer {
          0%, 100% { transform: scaleY(0.45); opacity: 0.5; }
          50% { transform: scaleY(1); opacity: 1; }
        }

        .bgm-volume-slider {
          -webkit-appearance: none;
          appearance: none;
        }

        .bgm-volume-slider::-webkit-slider-thumb {
          -webkit-appearance: none;
          appearance: none;
          width: 18px;
          height: 18px;
          margin-top: -6px;
          border-radius: 9999px;
          border: 2px solid #ffffff;
          background: #8DA98E;
          box-shadow: 0 8px 18px rgba(141, 169, 142, 0.28);
        }

        .bgm-volume-slider::-webkit-slider-runnable-track {
          height: 6px;
          background: transparent;
        }

        .bgm-volume-slider::-moz-range-thumb {
          width: 18px;
          height: 18px;
          border-radius: 9999px;
          border: 2px solid #ffffff;
          background: #8DA98E;
          box-shadow: 0 8px 18px rgba(141, 169, 142, 0.28);
        }

        .bgm-volume-slider::-moz-range-track {
          height: 6px;
          border-radius: 9999px;
          background: transparent;
        }

        .bgm-volume-slider::-moz-range-progress {
          height: 6px;
          border-radius: 9999px;
          background: #A8BFA9;
        }
      `}</style>

      {isPanelOpen ? (
        <div
          className={`relative w-[20rem] max-w-[calc(100vw-2rem)] overflow-hidden rounded-[30px] border shadow-premium ${
            isPlayRoute
              ? "border-slate-200 bg-white/90 shadow-[0_28px_80px_rgba(15,23,42,0.22)]"
              : "border-white/32 bg-white/18 shadow-[0_28px_80px_rgba(255,255,255,0.16),0_24px_56px_rgba(15,23,42,0.1)] backdrop-blur-[26px]"
          }`}
        >
          <div className="absolute inset-x-0 top-0 h-[3px] bg-gradient-to-r from-primary via-primary-dark to-accent-rose" />
          <div
            className={`pointer-events-none absolute inset-0 ${
              isPlayRoute
                ? "bg-white/10"
                : "bg-[radial-gradient(circle_at_top,rgba(255,255,255,0.72),rgba(255,255,255,0.12)_38%,transparent_72%)] opacity-100"
            }`}
          />

          <div className="relative p-5">
            <div className="flex items-start justify-between gap-4">
              <div className="min-w-0">
                <p className={`text-[11px] font-bold tracking-[0.22em] ${
                  isPlayRoute ? "text-slate-500" : "text-slate-400"
                }`}>
                  배경 음악
                </p>
                <h2 className="mt-2 text-[1.9rem] font-black tracking-tight text-slate-900">
                  {selectedTrack.label}
                </h2>
                <div className="mt-3">
                  <span
                    className={`inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full border px-3 py-1 text-[11px] font-semibold ${selectedMoodStyle.chip}`}
                  >
                    <span className={`h-2 w-2 rounded-full ${selectedMoodStyle.dot}`} />
                    {selectedTrack.moodLabel}
                  </span>
                </div>
              </div>

              <button
                type="button"
                onClick={handlePlaybackToggle}
                className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-700 shadow-soft transition-all hover:border-primary/25 hover:bg-primary/5 hover:text-primary-dark"
                aria-label={isPlaying ? "배경 음악 일시정지" : "배경 음악 재생"}
              >
                <span className="material-symbols-outlined text-[22px]">
                  {isPlaying ? "pause" : "play_arrow"}
                </span>
              </button>
            </div>

            {requiresInteraction ? (
              <div className="mt-4 rounded-2xl border border-amber-200 bg-amber-50/85 px-3.5 py-2.5 text-[12px] font-medium leading-relaxed text-amber-800">
                브라우저 자동 재생 제한으로 음악이 바로 시작되지 않았어요. 재생 목록 중 하나를 누르면
                재생돼요.
              </div>
            ) : null}

            <div className="mt-5 px-1">
              <div className="flex items-center justify-between gap-3">
                <div className="flex items-center gap-2 text-slate-700">
                  <span className="material-symbols-outlined text-[18px] text-primary-dark">
                    volume_up
                  </span>
                  <span className="text-sm font-semibold">볼륨</span>
                </div>
                <span className="text-xs font-semibold tabular-nums text-slate-500">
                  {sliderProgress}
                </span>
              </div>

              <div className="relative mt-3 h-5">
                <div className="absolute inset-x-0 top-1/2 h-1.5 -translate-y-1/2 rounded-full bg-slate-200" />
                <div
                  className="absolute left-0 top-1/2 h-1.5 -translate-y-1/2 rounded-full bg-primary"
                  style={{ width: sliderProgress }}
                />
                <input
                  type="range"
                  min="0"
                  max="1"
                  step="0.01"
                  value={volume}
                  onChange={handleVolumeChange}
                  className="bgm-volume-slider relative z-10 h-5 w-full cursor-pointer bg-transparent"
                  aria-label="배경 음악 볼륨 조절"
                />
              </div>
            </div>

            <div className="mt-5 flex items-center justify-between px-1">
              <p className={`text-[11px] font-bold tracking-[0.22em] ${
                isPlayRoute ? "text-slate-500" : "text-slate-400"
              }`}>
                PLAYLIST
              </p>
              <p className={`text-xs font-medium ${isPlayRoute ? "text-slate-500" : "text-slate-400"}`}>
                4곡
              </p>
            </div>

            <div className="mt-3 space-y-2.5">
              {TRACKS.map((track) => {
                const isActive = track.id === selectedTrackId;

                return (
                  <button
                    key={track.id}
                    type="button"
                    onClick={() => handleTrackSelect(track.id)}
                    className={`flex w-full items-center justify-between gap-3 rounded-[24px] border px-4 py-3 text-left transition-all ${
                      isActive
                        ? isPlayRoute
                          ? "border-primary/35 bg-primary/18 shadow-soft"
                          : "border-white/38 bg-white/18 shadow-[0_10px_24px_rgba(15,23,42,0.05)]"
                        : isPlayRoute
                          ? "border-slate-200 bg-white hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-soft"
                          : "border-white/22 bg-white/10 hover:-translate-y-0.5 hover:border-white/42 hover:bg-white/16 hover:shadow-[0_12px_28px_rgba(15,23,42,0.06)]"
                    }`}
                    aria-pressed={isActive}
                  >
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-[1rem] font-bold tracking-tight text-slate-900">
                        {track.label}
                      </p>
                      <p className="mt-1 text-xs font-medium text-slate-500">
                        {isActive ? "지금 재생 중" : "탭해서 변경"}
                      </p>
                    </div>

                    <div className="flex shrink-0 items-center gap-2">
                      {isActive ? (
                        <span className="flex h-3 items-end gap-0.5" aria-hidden="true">
                          {[0, 140, 280].map((delay) => (
                            <span
                              key={delay}
                              className="w-0.5 rounded-full bg-primary-dark"
                              style={{
                                height: "100%",
                                transformOrigin: "bottom",
                                animation: `bgmEqualizer 0.9s ease-in-out ${delay}ms infinite`,
                              }}
                            />
                          ))}
                        </span>
                      ) : null}
                      <span
                        className={`material-symbols-outlined text-[24px] ${
                          isActive ? "text-primary-dark" : "text-slate-300"
                        }`}
                        aria-hidden="true"
                      >
                        {isActive ? "radio_button_checked" : "radio_button_unchecked"}
                      </span>
                    </div>
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      ) : null}

      <button
        type="button"
        onClick={() => setIsPanelOpen((prev) => !prev)}
        className={`flex h-14 w-14 items-center justify-center rounded-full border shadow-lg transition-all hover:scale-105 ${
          isPlaying
            ? "border-white/70 bg-primary-dark text-white shadow-[0_18px_30px_rgba(141,169,142,0.36)]"
            : "border-slate-200 bg-white text-primary-dark shadow-soft"
        }`}
        aria-label="배경 음악 설정 열기"
        aria-expanded={isPanelOpen}
      >
        <span className="material-symbols-outlined text-[26px]">
          {isPlaying ? "music_note" : "music_off"}
        </span>
      </button>
    </div>
  );
}
