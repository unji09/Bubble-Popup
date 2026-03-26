import { useEffect, useState } from "react";

type WeatherCondition = "SUNNY" | "RAIN" | "SNOW" | "HEATWAVE" | "COLDWAVE" | "FOG";

interface WeatherInfo {
  label: string;
  bg: string;
  borderColor: string;
}

const WEATHER_MAP: Record<WeatherCondition, WeatherInfo> = {
  SUNNY: {
    label: "맑음",
    bg: "from-amber-50 via-yellow-50 to-orange-50",
    borderColor: "border-amber-200/60",
  },
  RAIN: {
    label: "비",
    bg: "from-slate-100 via-blue-100 to-indigo-100",
    borderColor: "border-blue-200/60",
  },
  SNOW: {
    label: "눈",
    bg: "from-sky-50 via-blue-50 to-indigo-50",
    borderColor: "border-sky-200/60",
  },
  HEATWAVE: {
    label: "폭염",
    bg: "from-red-50 via-orange-50 to-amber-50",
    borderColor: "border-orange-200/60",
  },
  COLDWAVE: {
    label: "한파",
    bg: "from-indigo-100 via-blue-100 to-purple-100",
    borderColor: "border-indigo-200/60",
  },
  FOG: {
    label: "안개",
    bg: "from-gray-100 via-slate-100 to-gray-50",
    borderColor: "border-gray-200/60",
  },
};

/* ═══════════════════════════════════════════
   Weather scenes — MAXIMUM edition
   ═══════════════════════════════════════════ */

function SunnyScene() {
  return (
    <div className="absolute inset-0 flex items-center justify-center pointer-events-none overflow-hidden">
      {/* Background radial warmth */}
      <div className="absolute w-72 h-72 rounded-full bg-gradient-radial from-amber-200/20 via-yellow-100/10 to-transparent wc-breathe" />
      {/* Outer rotating rays — thick */}
      <div className="wc-spin-slow absolute w-56 h-56 opacity-50">
        {Array.from({ length: 16 }).map((_, i) => (
          <div key={i} className="absolute left-1/2 top-0 -ml-[1.5px] w-[3px] h-full origin-center"
            style={{ transform: `rotate(${i * 22.5}deg)` }}>
            <div className="w-full h-10 bg-gradient-to-b from-amber-300/70 via-yellow-300/30 to-transparent rounded-full" />
          </div>
        ))}
      </div>
      {/* Mid counter-rotating rays — thin */}
      <div className="wc-spin-slow-reverse absolute w-40 h-40 opacity-40">
        {Array.from({ length: 12 }).map((_, i) => (
          <div key={i} className="absolute left-1/2 top-0 -ml-px w-[2px] h-full origin-center"
            style={{ transform: `rotate(${i * 30 + 15}deg)` }}>
            <div className="w-full h-7 bg-gradient-to-b from-yellow-400/60 to-transparent rounded-full" />
          </div>
        ))}
      </div>
      {/* Inner fast rotating rays */}
      <div className="wc-spin-fast absolute w-28 h-28 opacity-30">
        {Array.from({ length: 8 }).map((_, i) => (
          <div key={i} className="absolute left-1/2 top-0 -ml-px w-[1px] h-full origin-center"
            style={{ transform: `rotate(${i * 45}deg)` }}>
            <div className="w-full h-5 bg-gradient-to-b from-orange-400/80 to-transparent rounded-full" />
          </div>
        ))}
      </div>
      {/* Multi-layer glow */}
      <div className="absolute w-36 h-36 rounded-full bg-yellow-200/15 wc-glow-pulse" />
      <div className="absolute w-24 h-24 rounded-full bg-amber-200/20 wc-glow-pulse" style={{ animationDelay: "0.4s" }} />
      <div className="absolute w-16 h-16 rounded-full bg-yellow-300/25 wc-glow-pulse" style={{ animationDelay: "0.8s" }} />
      {/* Sun body — layered */}
      <div className="absolute w-14 h-14 rounded-full bg-gradient-to-br from-yellow-300 via-amber-300 to-orange-400 shadow-xl shadow-amber-300/50" />
      <div className="absolute w-11 h-11 rounded-full bg-gradient-to-br from-yellow-200 to-amber-200 opacity-80" />
      <div className="absolute w-7 h-7 rounded-full bg-yellow-100/60" />
      {/* Lens flare streaks */}
      <div className="absolute w-32 h-[1px] bg-gradient-to-r from-transparent via-amber-300/40 to-transparent wc-flare" />
      <div className="absolute w-[1px] h-32 bg-gradient-to-b from-transparent via-amber-300/40 to-transparent wc-flare" style={{ animationDelay: "1.5s" }} />
      {/* Floating sparkles — lots */}
      {[
        { t: "12%", l: "15%", d: "0s" }, { t: "18%", l: "78%", d: "0.8s" },
        { t: "30%", l: "10%", d: "1.6s" }, { t: "25%", l: "88%", d: "0.4s" },
        { t: "55%", l: "12%", d: "2.0s" }, { t: "68%", l: "82%", d: "1.2s" },
        { t: "72%", l: "22%", d: "0.6s" }, { t: "78%", l: "75%", d: "2.4s" },
        { t: "42%", l: "90%", d: "1.0s" }, { t: "85%", l: "50%", d: "1.8s" },
      ].map((s, i) => (
        <div key={i} className={`absolute wc-sparkle text-amber-400/60 ${i % 3 === 0 ? "text-xs" : "text-[10px]"}`}
          style={{ top: s.t, left: s.l, animationDelay: s.d }}>✦</div>
      ))}
      {/* Floating orbs */}
      {[
        { t: "20%", l: "30%", d: "0s", sz: 4 }, { t: "60%", l: "70%", d: "1s", sz: 3 },
        { t: "40%", l: "20%", d: "2s", sz: 5 }, { t: "75%", l: "60%", d: "0.5s", sz: 3 },
      ].map((o, i) => (
        <div key={`o${i}`} className="absolute rounded-full bg-yellow-300/30 wc-float"
          style={{ top: o.t, left: o.l, width: o.sz, height: o.sz, animationDelay: o.d }} />
      ))}
    </div>
  );
}

function RainScene() {
  const backDrops = Array.from({ length: 18 }, (_, i) => ({
    l: `${2 + i * 5.5}%`, d: `${(i * 0.11) % 0.8}s`, h: 10 + (i % 3) * 3, dur: `${0.85 + (i % 4) * 0.1}s`, w: 1, opacity: 0.2,
  }));
  const midDrops = Array.from({ length: 20 }, (_, i) => ({
    l: `${1 + i * 5}%`, d: `${(i * 0.14) % 0.7}s`, h: 14 + (i % 4) * 4, dur: `${0.55 + (i % 3) * 0.1}s`, w: 1.5, opacity: 0.45,
  }));
  const frontDrops = Array.from({ length: 14 }, (_, i) => ({
    l: `${4 + i * 7}%`, d: `${(i * 0.09) % 0.5}s`, h: 22 + (i % 3) * 6, dur: `${0.4 + (i % 3) * 0.07}s`, w: 2.5, opacity: 0.7,
  }));
  const allDrops = [
    ...backDrops.map((d) => ({ ...d, layer: "back" as const })),
    ...midDrops.map((d) => ({ ...d, layer: "mid" as const })),
    ...frontDrops.map((d) => ({ ...d, layer: "front" as const })),
  ];

  return (
    <div className="absolute inset-0 overflow-hidden pointer-events-none">
      {/* Rain cloud — layered, puffy, realistic */}
      <div className="absolute -top-4 -left-4 -right-4">
        {/* Back cloud layer — soft, wide */}
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[110%] h-14 bg-slate-400/25 rounded-[50%] blur-md" />
        {/* Main cloud body — overlapping circles for puffy shape */}
        <div className="relative flex justify-center items-end">
          <div className="w-16 h-10 bg-gradient-to-b from-slate-300/55 to-slate-400/30 rounded-full -mr-3 mt-2 blur-[1px]" />
          <div className="w-22 h-14 bg-gradient-to-b from-slate-300/65 to-slate-400/40 rounded-full -mr-4 mt-1" />
          <div className="w-20 h-16 bg-gradient-to-b from-slate-300/70 to-slate-500/45 rounded-full -mr-5 -mt-1" />
          <div className="w-28 h-20 bg-gradient-to-b from-slate-300/80 to-slate-500/55 rounded-full -mt-3 z-10" />
          <div className="w-24 h-18 bg-gradient-to-b from-slate-300/75 to-slate-500/50 rounded-full -ml-5 -mt-2 z-[9]" />
          <div className="w-20 h-14 bg-gradient-to-b from-slate-300/65 to-slate-400/40 rounded-full -ml-4 mt-1" />
          <div className="w-14 h-10 bg-gradient-to-b from-slate-300/50 to-slate-400/25 rounded-full -ml-3 mt-2 blur-[1px]" />
        </div>
        {/* Cloud bottom — flat dark underbelly */}
        <div className="absolute bottom-0 left-1/2 -translate-x-1/2 w-[85%] h-6 bg-gradient-to-b from-slate-500/35 to-slate-500/10 rounded-b-lg blur-[2px]" />
        {/* Subtle inner highlight */}
        <div className="absolute top-1 left-1/2 -translate-x-1/2 w-16 h-6 bg-white/8 rounded-full blur-sm" />
      </div>

      {/* Lightning flash (subtle) */}
      <div className="absolute top-14 left-1/2 -translate-x-1/2 w-[1px] h-8 bg-gradient-to-b from-blue-200/60 to-transparent wc-lightning" />

      {/* Rain drops — 3 depth layers */}
      {allDrops.map((d, i) => (
        <div key={i} className="wc-rain absolute"
          style={{
            left: d.l,
            top: d.layer === "back" ? 12 : d.layer === "mid" ? 16 : 20,
            animationDelay: d.d,
            animationDuration: d.dur,
            opacity: d.opacity,
          }}>
          <div className="rounded-full bg-gradient-to-b from-blue-400 to-blue-200/10"
            style={{ width: d.w, height: d.h }} />
        </div>
      ))}

      {/* Splash rings — more */}
      {[8, 18, 30, 42, 54, 66, 78, 90].map((l, i) => (
        <div key={i} className="wc-splash absolute bottom-2"
          style={{ left: `${l}%`, animationDelay: `${i * 0.15}s` }}>
          <div className="w-4 h-1.5 rounded-full border border-blue-300/40" />
          <div className="absolute -top-1.5 -left-0.5 w-1 h-1 rounded-full bg-blue-300/30 wc-splash-dot"
            style={{ animationDelay: `${i * 0.15 + 0.08}s` }} />
          <div className="absolute -top-2 left-1 w-0.5 h-0.5 rounded-full bg-blue-300/25 wc-splash-dot"
            style={{ animationDelay: `${i * 0.15 + 0.12}s` }} />
          <div className="absolute -top-1.5 right-0 w-1 h-1 rounded-full bg-blue-300/30 wc-splash-dot"
            style={{ animationDelay: `${i * 0.15 + 0.16}s` }} />
        </div>
      ))}

      {/* Puddle reflections */}
      <div className="absolute bottom-0 left-0 right-0 h-6 bg-gradient-to-t from-blue-200/25 to-transparent" />
      {[10, 35, 60, 82].map((l, i) => (
        <div key={`p${i}`} className="absolute bottom-0 rounded-full blur-sm bg-blue-300/12 wc-puddle"
          style={{ left: `${l}%`, width: 14 + (i % 2) * 8, height: 2, animationDelay: `${i * 0.6}s` }} />
      ))}

      {/* Mist atmosphere */}
      <div className="absolute bottom-0 left-0 right-0 h-16 bg-gradient-to-t from-slate-300/15 to-transparent" />
      <div className="absolute bottom-4 left-0 w-[200%] h-6 wc-mist opacity-10"
        style={{ background: "linear-gradient(90deg, transparent, rgba(148,163,184,0.4), transparent)" }} />
    </div>
  );
}

function SnowScene() {
  // 3 depth layers — bold, visible snowfall
  const backFlakes = Array.from({ length: 14 }, (_, i) => ({
    l: `${(i * 7.1) % 97}%`, d: `${(i * 0.6) % 5}s`, dur: `${5.5 + (i % 3)}s`,
    sz: 7 + (i % 2) * 3, char: i % 3 === 0 ? "•" : "❆", opacity: 0.7,
  }));
  const midFlakes = Array.from({ length: 16 }, (_, i) => ({
    l: `${(i * 6.2) % 96}%`, d: `${(i * 0.45) % 4.5}s`, dur: `${4 + (i % 4) * 0.7}s`,
    sz: 11 + (i % 3) * 3, char: i % 2 === 0 ? "❄" : "❆", opacity: 1,
  }));
  const frontFlakes = Array.from({ length: 10 }, (_, i) => ({
    l: `${(i * 10) % 95}%`, d: `${(i * 0.4) % 3.5}s`, dur: `${3 + (i % 3) * 0.6}s`,
    sz: 16 + (i % 4) * 4, char: i % 2 === 0 ? "❄" : "❆", opacity: 1,
  }));

  return (
    <div className="absolute inset-0 overflow-hidden pointer-events-none">
      {/* Cloud bank — 5 clouds */}
      <div className="absolute -top-6 -left-6 -right-6">
        <div className="flex justify-center items-end">
          <div className="w-24 h-12 bg-gradient-to-b from-slate-200/60 to-sky-200/20 rounded-full -mr-6 blur-[2px]" />
          <div className="w-36 h-18 bg-gradient-to-b from-white/75 to-slate-200/30 rounded-full -mr-8 -mt-1 blur-[1px]" />
          <div className="w-48 h-22 bg-gradient-to-b from-white/85 to-sky-100/40 rounded-full -mt-4 z-10" />
          <div className="w-36 h-18 bg-gradient-to-b from-white/75 to-slate-200/30 rounded-full -ml-8 -mt-1 blur-[1px]" />
          <div className="w-24 h-12 bg-gradient-to-b from-slate-200/60 to-sky-200/20 rounded-full -ml-6 blur-[2px]" />
        </div>
      </div>

      {/* Back layer — small, slow */}
      {backFlakes.map((f, i) => (
        <div key={`b${i}`} className="wc-snow-slow absolute -top-1"
          style={{ left: f.l, fontSize: f.sz, opacity: f.opacity, animationDelay: f.d, animationDuration: f.dur, color: "rgba(147,197,253,0.8)" }}>
          {f.char}
        </div>
      ))}
      {/* Mid layer */}
      {midFlakes.map((f, i) => (
        <div key={`m${i}`} className="wc-snow absolute -top-2"
          style={{ left: f.l, fontSize: f.sz, opacity: f.opacity, animationDelay: f.d, animationDuration: f.dur, color: "rgba(96,165,250,0.9)" }}>
          {f.char}
        </div>
      ))}
      {/* Front layer — big, fast, vivid */}
      {frontFlakes.map((f, i) => (
        <div key={`f${i}`} className="wc-snow-front absolute -top-3"
          style={{ left: f.l, fontSize: f.sz, opacity: f.opacity, animationDelay: f.d, animationDuration: f.dur, color: "rgba(59,130,246,1)" }}>
          {f.char}
        </div>
      ))}

      {/* Sparkle particles */}
      {Array.from({ length: 12 }).map((_, i) => (
        <div key={`sp${i}`} className="absolute wc-sparkle"
          style={{
            top: `${8 + (i * 7.5) % 80}%`,
            left: `${5 + (i * 8) % 88}%`,
            fontSize: 6 + (i % 3) * 3,
            animationDelay: `${(i * 0.4) % 3}s`,
            color: i % 2 === 0 ? "rgba(96,165,250,0.85)" : "rgba(147,197,253,0.75)",
          }}>✦</div>
      ))}

      {/* Ground snow drifts */}
      <div className="absolute bottom-0 left-0 right-0">
        <div className="h-14 bg-gradient-to-t from-white/85 via-white/45 to-transparent" />
        <div className="absolute bottom-0 left-[2%] w-[30%] h-8 bg-white/70 rounded-t-full blur-[1px]" />
        <div className="absolute bottom-0 left-[25%] w-[32%] h-10 bg-white/65 rounded-t-full blur-[1px]" />
        <div className="absolute bottom-0 right-[3%] w-[28%] h-9 bg-white/60 rounded-t-full blur-[1px]" />
        {/* Ground sparkles */}
        {Array.from({ length: 8 }).map((_, i) => (
          <div key={`gs${i}`} className="absolute bottom-1 wc-sparkle text-[7px]"
            style={{
              left: `${5 + i * 12}%`,
              animationDelay: `${(i * 0.5) % 3}s`,
              color: "rgba(147,197,253,0.9)",
            }}>✦</div>
        ))}
      </div>

      {/* Atmosphere + mist */}
      <div className="absolute inset-0 bg-gradient-to-b from-sky-200/12 via-transparent to-blue-200/15" />
      <div className="absolute bottom-4 left-0 w-[250%] h-8 wc-mist opacity-20"
        style={{ background: "linear-gradient(90deg, transparent, rgba(147,197,253,0.4), rgba(186,230,253,0.25), transparent)" }} />
    </div>
  );
}

function HeatwaveScene() {
  return (
    <div className="absolute inset-0 overflow-hidden pointer-events-none">
      {/* Full background heat pulse */}
      <div className="absolute inset-0 wc-heat-bg"
        style={{ background: "radial-gradient(ellipse at 70% 20%, rgba(251,146,60,0.15), transparent 60%)" }} />
      <div className="absolute inset-0 wc-heat-bg"
        style={{ background: "radial-gradient(ellipse at 30% 80%, rgba(239,68,68,0.08), transparent 50%)", animationDelay: "1.5s" }} />

      {/* Heat shimmer waves — 8 layers with varied patterns */}
      {Array.from({ length: 8 }).map((_, i) => (
        <div key={i}
          className="wc-heat-wave absolute left-0 right-0 h-full"
          style={{
            opacity: 0.15 + (i % 3) * 0.08,
            background: `repeating-linear-gradient(${i % 2 === 0 ? 0 : 2}deg, transparent, transparent ${14 + i * 2}px, rgba(251,146,60,${0.08 + (i % 4) * 0.04}) ${16 + i * 2}px, transparent ${18 + i * 2}px)`,
            animationDelay: `${i * 0.35}s`,
            animationDuration: `${2 + (i % 4) * 0.4}s`,
          }}
        />
      ))}

      {/* Blazing sun with massive corona */}
      <div className="absolute top-2 right-4">
        {/* Corona — 5 expanding rings */}
        <div className="absolute -inset-10 rounded-full bg-red-300/5 wc-glow-pulse scale-[3]" style={{ animationDelay: "0.9s" }} />
        <div className="absolute -inset-8 rounded-full bg-orange-300/8 wc-glow-pulse scale-[2.5]" style={{ animationDelay: "0.6s" }} />
        <div className="absolute -inset-6 rounded-full bg-orange-300/12 wc-glow-pulse scale-[2]" style={{ animationDelay: "0.3s" }} />
        <div className="absolute -inset-4 rounded-full bg-orange-400/15 wc-glow-pulse scale-150" />
        <div className="absolute -inset-2 rounded-full bg-orange-400/20 wc-glow-pulse" style={{ animationDelay: "0.5s" }} />
        {/* Sun body — fiery gradient */}
        <div className="w-16 h-16 rounded-full bg-gradient-to-br from-yellow-200 via-orange-400 to-red-600 shadow-2xl shadow-orange-500/60" />
        <div className="absolute inset-1 rounded-full bg-gradient-to-br from-yellow-100/70 via-orange-200/30 to-transparent" />
        <div className="absolute inset-3 rounded-full bg-yellow-100/30" />
        {/* Rotating ray ring */}
        <div className="absolute -inset-5 wc-spin-heat">
          {Array.from({ length: 12 }).map((_, i) => (
            <div key={i} className="absolute left-1/2 top-0 -ml-px w-[2px] h-full origin-center"
              style={{ transform: `rotate(${i * 30}deg)` }}>
              <div className="w-full h-3 bg-gradient-to-b from-orange-400/50 to-transparent rounded-full" />
            </div>
          ))}
        </div>
      </div>

      {/* Rising heat blobs — 16 total, varied sizes */}
      {Array.from({ length: 16 }).map((_, i) => (
        <div key={i} className="wc-heat-rise absolute bottom-0"
          style={{
            left: `${3 + i * 6}%`,
            animationDelay: `${(i * 0.35) % 3.5}s`,
            animationDuration: `${1.8 + (i % 4) * 0.4}s`,
          }}>
          <div className="rounded-full"
            style={{
              width: 5 + (i % 4) * 4,
              height: 5 + (i % 4) * 4,
              background: `radial-gradient(circle, rgba(${i % 2 === 0 ? "251,146,60" : "239,68,68"},${0.15 + (i % 3) * 0.1}) 0%, transparent 70%)`,
            }} />
        </div>
      ))}

      {/* Ember/spark particles floating up */}
      {Array.from({ length: 8 }).map((_, i) => (
        <div key={`e${i}`} className="wc-ember absolute bottom-0"
          style={{
            left: `${10 + i * 11}%`,
            animationDelay: `${(i * 0.5) % 3}s`,
            animationDuration: `${3 + (i % 3)}s`,
          }}>
          <div className="w-1.5 h-1.5 rounded-full"
            style={{ background: i % 2 === 0 ? "rgba(251,191,36,0.7)" : "rgba(251,146,60,0.6)" }} />
        </div>
      ))}

      {/* Ground heat distortion — triple layer */}
      <div className="absolute bottom-0 left-0 right-0 h-16 bg-gradient-to-t from-orange-200/40 to-transparent wc-heat-ground" />
      <div className="absolute bottom-0 left-0 right-0 h-10 bg-gradient-to-t from-red-200/20 to-transparent wc-heat-ground" style={{ animationDelay: "0.7s" }} />
      <div className="absolute bottom-0 left-0 right-0 h-6 bg-gradient-to-t from-yellow-200/15 to-transparent wc-heat-ground" style={{ animationDelay: "1.4s" }} />

      {/* Heat sparkles scattered */}
      {[
        { t: "25%", l: "12%", d: "0s" }, { t: "35%", l: "45%", d: "0.8s" },
        { t: "50%", l: "28%", d: "1.6s" }, { t: "42%", l: "70%", d: "0.4s" },
        { t: "60%", l: "55%", d: "2.0s" }, { t: "30%", l: "82%", d: "1.2s" },
        { t: "70%", l: "18%", d: "2.4s" }, { t: "55%", l: "88%", d: "0.6s" },
      ].map((s, i) => (
        <div key={`hs${i}`} className={`absolute wc-sparkle ${i % 2 === 0 ? "text-orange-400/50" : "text-yellow-400/40"} text-[10px]`}
          style={{ top: s.t, left: s.l, animationDelay: s.d }}>✦</div>
      ))}
    </div>
  );
}

function ColdwaveScene() {
  /* 한파 = "얼어붙은 유리창"
     카드 자체가 꽁꽁 언 창문. 성에, 고드름, 찬바람, 입김 */
  return (
    <div className="absolute inset-0 overflow-hidden pointer-events-none">
      {/* 차갑고 어두운 배경 — 흐린 겨울 새벽 */}
      <div className="absolute inset-0"
        style={{ background: "linear-gradient(180deg, rgba(30,41,59,0.15) 0%, rgba(51,65,85,0.08) 40%, rgba(100,116,139,0.05) 100%)" }} />

      {/* 성에 — 카드 테두리를 따라 퍼지는 서리 (유리창에 낀 성에) */}
      {/* 상단 — 가장 두꺼운 성에 */}
      <div className="absolute top-0 left-0 right-0 h-24 bg-gradient-to-b from-white/40 via-blue-100/20 to-transparent" />
      <div className="absolute top-0 left-0 right-0 h-12 bg-gradient-to-b from-white/25 to-transparent" style={{ filter: "blur(1px)" }} />
      {/* 하단 성에 */}
      <div className="absolute bottom-0 left-0 right-0 h-20 bg-gradient-to-t from-white/35 via-blue-100/18 to-transparent" />
      {/* 좌우 성에 */}
      <div className="absolute top-0 bottom-0 left-0 w-16 bg-gradient-to-r from-white/30 via-blue-100/15 to-transparent" />
      <div className="absolute top-0 bottom-0 right-0 w-16 bg-gradient-to-l from-white/30 via-blue-100/15 to-transparent" />

      {/* 코너 성에 — 유리창 코너에 두껍게 낀 성에 */}
      <div className="absolute top-0 left-0 w-32 h-32 bg-gradient-to-br from-white/50 via-blue-100/25 to-transparent rounded-br-full" />
      <div className="absolute top-0 right-0 w-32 h-32 bg-gradient-to-bl from-white/50 via-blue-100/25 to-transparent rounded-bl-full" />
      <div className="absolute bottom-0 left-0 w-28 h-28 bg-gradient-to-tr from-white/40 via-blue-100/20 to-transparent rounded-tr-full" />
      <div className="absolute bottom-0 right-0 w-28 h-28 bg-gradient-to-tl from-white/40 via-blue-100/20 to-transparent rounded-tl-full" />

      {/* 성에 결정 패턴 — 서리꽃 (fern frost) */}
      {[
        { x: "0%", y: "0%", w: "40%", r: 30, o: "left" },
        { x: "0%", y: "0%", w: "35%", r: 55, o: "left" },
        { x: "0%", y: "0%", w: "25%", r: 12, o: "left" },
        { x: "100%", y: "0%", w: "38%", r: 148, o: "right" },
        { x: "100%", y: "0%", w: "30%", r: 125, o: "right" },
        { x: "0%", y: "100%", w: "30%", r: -25, o: "left" },
        { x: "0%", y: "100%", w: "22%", r: -50, o: "left" },
        { x: "100%", y: "100%", w: "35%", r: -152, o: "right" },
        { x: "100%", y: "100%", w: "28%", r: -130, o: "right" },
        { x: "50%", y: "0%", w: "20%", r: 85, o: "left" },
      ].map((c, i) => (
        <div key={`fr${i}`} className="absolute wc-frost-crack"
          style={{
            top: c.y, left: c.x, width: c.w, height: 1,
            transform: `rotate(${c.r}deg)`, transformOrigin: c.o,
            background: `linear-gradient(90deg, rgba(255,255,255,${0.4 + (i % 3) * 0.1}), rgba(186,230,253,${0.2 + (i % 2) * 0.1}), transparent)`,
            animationDelay: `${i * 0.4}s`,
          }} />
      ))}

      {/* 고드름 — 상단에서 매달림 */}
      {[
        { l: "6%", w: 7, h: 32, d: "0s" },
        { l: "14%", w: 10, h: 46, d: "0.3s" },
        { l: "22%", w: 6, h: 26, d: "0.8s" },
        { l: "30%", w: 9, h: 40, d: "0.5s" },
        { l: "40%", w: 12, h: 52, d: "0.2s" },
        { l: "49%", w: 7, h: 30, d: "1s" },
        { l: "58%", w: 10, h: 44, d: "0.4s" },
        { l: "66%", w: 6, h: 28, d: "0.9s" },
        { l: "75%", w: 9, h: 38, d: "0.6s" },
        { l: "84%", w: 12, h: 48, d: "0.1s" },
        { l: "93%", w: 7, h: 30, d: "0.7s" },
      ].map((ic, i) => (
        <div key={`ic${i}`} className="absolute top-0" style={{ left: ic.l }}>
          {/* 고드름 몸체 — 위는 넓고 아래로 뾰족 */}
          <div style={{
              width: ic.w, height: ic.h,
              clipPath: "polygon(0% 0%, 100% 0%, 55% 100%, 45% 100%)",
              background: "linear-gradient(to bottom, rgba(255,255,255,0.85), rgba(186,230,253,0.7), rgba(147,197,253,0.45))",
              filter: "drop-shadow(0 1px 3px rgba(147,197,253,0.5))",
            }} />
          {/* 고드름 끝 물방울 — 녹는 느낌 */}
          <div className="wc-icicle-drip absolute -bottom-1.5 left-1/2 -translate-x-1/2 w-2 h-2 rounded-full bg-cyan-300/60"
            style={{ animationDelay: ic.d }} />
        </div>
      ))}

      {/* 찬바람 줄기 — 수평으로 지나감 */}
      {Array.from({ length: 8 }).map((_, i) => (
        <div key={`wn${i}`} className="wc-cold-streak absolute"
          style={{
            top: `${15 + (i * 10) % 70}%`,
            right: 0,
            width: `${50 + (i % 3) * 15}%`,
            height: 1 + (i % 2) * 0.5,
            animationDelay: `${(i * 0.3) % 2.5}s`,
            animationDuration: `${2 + (i % 3) * 0.5}s`,
            background: `linear-gradient(90deg, transparent, rgba(148,163,184,${0.15 + (i % 3) * 0.08}), rgba(203,213,225,0.08), transparent)`,
          }}
        />
      ))}

      {/* 입김 — 중앙 하단에서 피어오르는 하얀 김 */}
      {[
        { b: "18%", l: "30%", w: 40, h: 16, d: "0s", dur: "4s" },
        { b: "22%", l: "50%", w: 32, h: 12, d: "1.5s", dur: "3.5s" },
        { b: "15%", l: "42%", w: 50, h: 18, d: "3s", dur: "4.5s" },
        { b: "25%", l: "55%", w: 28, h: 10, d: "2s", dur: "3.8s" },
      ].map((br, i) => (
        <div key={`br${i}`} className="absolute wc-breath-rise rounded-full blur-sm"
          style={{
            bottom: br.b, left: br.l,
            width: br.w, height: br.h,
            background: "radial-gradient(ellipse, rgba(255,255,255,0.2), rgba(203,213,225,0.08), transparent)",
            animationDelay: br.d, animationDuration: br.dur,
          }} />
      ))}

      {/* 유리창 성에 질감 — 미세한 노이즈 느낌 반짝이 */}
      {Array.from({ length: 16 }).map((_, i) => (
        <div key={`sp${i}`} className="absolute wc-sparkle"
          style={{
            top: `${3 + (i * 6.2) % 90}%`,
            left: `${3 + (i * 6.4) % 92}%`,
            fontSize: 4 + (i % 3) * 2,
            animationDelay: `${(i * 0.35) % 3.5}s`,
            color: i % 3 === 0 ? "rgba(255,255,255,0.6)" : i % 3 === 1 ? "rgba(186,230,253,0.5)" : "rgba(147,197,253,0.4)",
          }}>
          {i % 2 === 0 ? "✦" : "·"}
        </div>
      ))}

      {/* 얼어붙은 바닥 — 빙판 */}
      <div className="absolute bottom-0 left-0 right-0">
        <div className="h-6 bg-gradient-to-t from-slate-300/20 via-blue-100/10 to-transparent" />
        {/* 빙판 반사 */}
        <div className="absolute bottom-0 left-0 right-0 h-2 bg-gradient-to-r from-transparent via-white/15 to-transparent" />
      </div>

      {/* 전체 차가운 톤 오버레이 */}
      <div className="absolute inset-0 bg-gradient-to-b from-blue-100/5 via-transparent to-slate-200/8" />
    </div>
  );
}

function FogScene() {
  const layers = [
    { t: "8%", d: "0s", dur: "12s", h: 18, o: 0.35 },
    { t: "20%", d: "5s", dur: "15s", h: 24, o: 0.28 },
    { t: "32%", d: "2s", dur: "10s", h: 30, o: 0.32 },
    { t: "46%", d: "7s", dur: "14s", h: 22, o: 0.25 },
    { t: "58%", d: "1s", dur: "11s", h: 28, o: 0.3 },
    { t: "70%", d: "4s", dur: "13s", h: 34, o: 0.28 },
    { t: "82%", d: "8s", dur: "12s", h: 20, o: 0.22 },
  ];

  return (
    <div className="absolute inset-0 overflow-hidden pointer-events-none">
      {/* Fog bands — 7 layers */}
      {layers.map((l, i) => (
        <div key={i} className={`wc-fog${i % 2 === 0 ? "" : "-reverse"} absolute left-0 w-[280%]`}
          style={{
            top: l.t, height: l.h, opacity: l.o,
            animationDelay: l.d, animationDuration: l.dur,
            background: "linear-gradient(90deg, transparent 0%, rgba(148,163,184,0.3) 15%, rgba(203,213,225,0.45) 35%, rgba(148,163,184,0.4) 55%, rgba(203,213,225,0.35) 75%, transparent 100%)",
            borderRadius: "50%",
          }}
        />
      ))}

      {/* Floating mist particles */}
      {[
        { t: "15%", l: "15%", d: "0s", sz: 3 }, { t: "30%", l: "55%", d: "1s", sz: 2 },
        { t: "45%", l: "25%", d: "2s", sz: 3 }, { t: "55%", l: "75%", d: "0.5s", sz: 2 },
        { t: "65%", l: "40%", d: "3s", sz: 4 }, { t: "75%", l: "85%", d: "1.5s", sz: 2 },
        { t: "80%", l: "20%", d: "2.5s", sz: 3 }, { t: "35%", l: "90%", d: "3.5s", sz: 2 },
      ].map((p, i) => (
        <div key={i} className="wc-fog-dot absolute rounded-full bg-slate-300/30"
          style={{ top: p.t, left: p.l, width: p.sz, height: p.sz, animationDelay: p.d }} />
      ))}

      {/* Overall haze gradient */}
      <div className="absolute inset-0 bg-gradient-to-b from-slate-200/10 via-slate-300/15 to-slate-200/10" />

      {/* Mysterious glow */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-24 h-24 rounded-full bg-slate-200/10 wc-glow-pulse" />
    </div>
  );
}

function FinaleScene() {
  return (
    <div className="absolute inset-0 overflow-hidden pointer-events-none">
      {/* Confetti particles — more! */}
      {Array.from({ length: 28 }).map((_, i) => {
        const colors = [
          "bg-amber-400", "bg-rose-400", "bg-blue-400",
          "bg-emerald-400", "bg-purple-400", "bg-pink-400",
          "bg-cyan-400", "bg-yellow-300",
        ];
        const color = colors[i % colors.length];
        const left = `${2 + (i * 3.5) % 94}%`;
        const delay = `${(i * 0.22) % 3.5}s`;
        const dur = `${2 + (i % 5) * 0.4}s`;
        const size = 3 + (i % 4) * 2;
        const isCircle = i % 4 === 0;
        const isLong = i % 4 === 2;
        return (
          <div key={i}
            className={`wc-confetti absolute -top-2 ${color} ${isCircle ? "rounded-full" : "rounded-sm"}`}
            style={{
              left,
              width: isCircle ? size : isLong ? size * 0.4 : size * 0.7,
              height: isCircle ? size : isLong ? size * 2 : size * 1.2,
              animationDelay: delay,
              animationDuration: dur,
              opacity: 0.75,
            }}
          />
        );
      })}

      {/* Center trophy glow — multi layer */}
      <div className="absolute inset-0 flex items-center justify-center">
        <div className="absolute w-32 h-32 rounded-full bg-amber-200/10 wc-glow-pulse" />
        <div className="absolute w-24 h-24 rounded-full bg-amber-200/15 wc-glow-pulse" style={{ animationDelay: "0.3s" }} />
        <div className="absolute w-16 h-16 rounded-full bg-amber-300/20 wc-glow-pulse" style={{ animationDelay: "0.6s" }} />
        <span className="relative text-6xl wc-trophy-bounce select-none drop-shadow-lg">🏆</span>
      </div>

      {/* Sparkles — lots around trophy */}
      {[
        { t: "15%", l: "20%", d: "0s" }, { t: "22%", l: "75%", d: "0.4s" },
        { t: "35%", l: "12%", d: "0.8s" }, { t: "30%", l: "85%", d: "1.2s" },
        { t: "55%", l: "15%", d: "1.6s" }, { t: "60%", l: "82%", d: "2.0s" },
        { t: "70%", l: "25%", d: "0.6s" }, { t: "72%", l: "72%", d: "1.0s" },
        { t: "45%", l: "8%", d: "2.4s" }, { t: "48%", l: "90%", d: "1.4s" },
      ].map((s, i) => (
        <div key={i} className={`absolute wc-sparkle ${i % 2 === 0 ? "text-amber-400/70" : "text-yellow-300/60"} ${i % 3 === 0 ? "text-xs" : "text-[10px]"}`}
          style={{ top: s.t, left: s.l, animationDelay: s.d }}>✦</div>
      ))}
    </div>
  );
}

/* ── Bankrupt Scene: 파산 — 무너진 가게 ── */
function BankruptScene() {
  return (
    <div className="absolute inset-0 flex items-center justify-center pointer-events-none overflow-hidden">
      {/* 빨간 경고등 — 전체 펄스 */}
      <div className="absolute inset-0 wc-bankrupt-pulse" style={{
        background: "radial-gradient(ellipse at center, rgba(239,68,68,0.12) 0%, transparent 70%)",
      }} />

      {/* 어두운 비네팅 */}
      <div className="absolute inset-0" style={{
        background: "radial-gradient(ellipse at center, transparent 30%, rgba(30,30,30,0.15) 100%)",
      }} />

      {/* 금이 간 균열 — 중앙에서 뻗어나감 */}
      {[
        { x1: "50%", y1: "50%", angle: -35, len: 90, w: 2.5, d: "0s" },
        { x1: "50%", y1: "50%", angle: 40, len: 110, w: 2, d: "0.15s" },
        { x1: "50%", y1: "50%", angle: -75, len: 70, w: 2, d: "0.3s" },
        { x1: "50%", y1: "50%", angle: 15, len: 85, w: 1.5, d: "0.4s" },
        { x1: "50%", y1: "50%", angle: -120, len: 95, w: 2, d: "0.2s" },
        { x1: "50%", y1: "50%", angle: 70, len: 75, w: 1.5, d: "0.5s" },
        { x1: "50%", y1: "50%", angle: 160, len: 80, w: 2, d: "0.1s" },
        { x1: "50%", y1: "50%", angle: -160, len: 65, w: 1.5, d: "0.35s" },
      ].map((cr, i) => (
        <div key={`cr${i}`} className="absolute wc-shatter-crack" style={{
          left: cr.x1, top: cr.y1, width: cr.len, height: cr.w,
          transformOrigin: "0% 50%",
          transform: `rotate(${cr.angle}deg)`,
          background: `linear-gradient(90deg, rgba(239,68,68,0.6), rgba(239,68,68,0.25), transparent)`,
          animationDelay: cr.d,
        }} />
      ))}

      {/* 중앙 — 깨진 돼지저금통 */}
      <div className="relative wc-bankrupt-shake">
        <span className="text-7xl" style={{
          filter: "drop-shadow(0 4px 12px rgba(239,68,68,0.3))",
        }}>💸</span>
      </div>

      {/* 중앙에서 사방으로 날아가는 지폐/동전 — 돈이 빨려나가는 느낌 */}
      {[
        { angle: 0, dist: 140, d: "0s", sz: 24, e: "💵" },
        { angle: 45, dist: 130, d: "0.2s", sz: 20, e: "💴" },
        { angle: 90, dist: 120, d: "0.4s", sz: 22, e: "🪙" },
        { angle: 135, dist: 135, d: "0.15s", sz: 18, e: "💵" },
        { angle: 180, dist: 125, d: "0.5s", sz: 24, e: "💴" },
        { angle: 225, dist: 140, d: "0.1s", sz: 20, e: "🪙" },
        { angle: 270, dist: 115, d: "0.35s", sz: 22, e: "💵" },
        { angle: 315, dist: 130, d: "0.25s", sz: 18, e: "💴" },
        { angle: 20, dist: 145, d: "0.6s", sz: 16, e: "🪙" },
        { angle: 110, dist: 120, d: "0.45s", sz: 20, e: "💵" },
        { angle: 200, dist: 135, d: "0.55s", sz: 16, e: "🪙" },
        { angle: 290, dist: 125, d: "0.3s", sz: 18, e: "💴" },
      ].map((b, i) => (
        <div key={`fly${i}`} className="absolute wc-money-fly" style={{
          left: "50%", top: "50%",
          fontSize: b.sz,
          marginLeft: -b.sz / 2, marginTop: -b.sz / 2,
          // CSS 변수로 각도와 거리 전달
          ["--fly-x" as string]: `${Math.cos(b.angle * Math.PI / 180) * b.dist}px`,
          ["--fly-y" as string]: `${Math.sin(b.angle * Math.PI / 180) * b.dist}px`,
          ["--fly-r" as string]: `${(b.angle % 2 === 0 ? 1 : -1) * (180 + (i % 3) * 90)}deg`,
          animationDelay: b.d,
          animationDuration: `${2 + (i % 4) * 0.3}s`,
        }}>{b.e}</div>
      ))}

      {/* 소용돌이 흡입 링 — 돈이 빨려나가는 바람 */}
      {[0, 1, 2].map((i) => (
        <div key={`vortex${i}`} className="absolute wc-vortex-ring rounded-full" style={{
          left: "50%", top: "50%",
          width: 40 + i * 50, height: 40 + i * 50,
          marginLeft: -(20 + i * 25), marginTop: -(20 + i * 25),
          border: `1.5px solid rgba(239,68,68,${0.25 - i * 0.06})`,
          animationDelay: `${i * 0.4}s`,
        }} />
      ))}

      {/* 하단 — 무너진 잔해 느낌 */}
      <div className="absolute bottom-0 left-0 right-0 h-16">
        <div className="absolute bottom-0 left-0 right-0 h-10 bg-gradient-to-t from-rose-100/60 via-rose-50/30 to-transparent" />
        {/* 바닥에 쌓인 동전/지폐 */}
        {["🪙","💵","🪙","💴","🪙","💵","🪙"].map((e, i) => (
          <span key={`floor${i}`} className="absolute bottom-1 wc-floor-item" style={{
            left: `${8 + i * 13}%`,
            fontSize: 10 + (i % 3) * 3,
            opacity: 0.4 + (i % 3) * 0.15,
            transform: `rotate(${-20 + i * 12}deg)`,
            animationDelay: `${i * 0.3}s`,
          }}>{e}</span>
        ))}
      </div>

      {/* 빨간 도장 — 파산 각인 */}
      <div className="absolute wc-stamp-slam" style={{
        top: "15%", right: "8%",
        border: "3px solid rgba(220,38,38,0.5)",
        borderRadius: 4,
        padding: "2px 8px",
        transform: "rotate(-12deg)",
        color: "rgba(220,38,38,0.5)",
        fontSize: 14,
        fontWeight: 900,
        letterSpacing: 4,
      }}>CLOSED</div>
    </div>
  );
}

const SCENE_MAP: Record<WeatherCondition, () => React.JSX.Element> = {
  SUNNY: SunnyScene,
  RAIN: RainScene,
  SNOW: SnowScene,
  HEATWAVE: HeatwaveScene,
  COLDWAVE: ColdwaveScene,
  FOG: FogScene,
};

/* ═══════════════════════
   Main component
   ═══════════════════════ */

interface WeatherCardProps {
  condition: string | null;
  disabled?: boolean;
  disabledMessage?: string;
}

export default function WeatherCard({ condition, disabled = false, disabledMessage }: WeatherCardProps) {
  const [mounted, setMounted] = useState(false);
  useEffect(() => {
    const raf = requestAnimationFrame(() => setMounted(true));
    return () => cancelAnimationFrame(raf);
  }, []);

  const key = condition as WeatherCondition | null;
  const weather = key && key in WEATHER_MAP ? WEATHER_MAP[key] : null;
  const Scene = weather && key ? SCENE_MAP[key] : null;
  const noWeather = !condition && !disabled;

  const bgGradient = disabled
    ? "from-rose-50 via-slate-50 to-slate-100"
    : noWeather
      ? "from-amber-50 via-orange-50 to-rose-50"
      : weather
        ? weather.bg
        : "from-blue-50 to-indigo-50";
  const border = disabled
    ? "border-rose-200/60"
    : noWeather
      ? "border-amber-200/60"
      : weather
        ? weather.borderColor
        : "border-blue-100/50";

  return (
    <>
      <style>{`
        /* ── Shared ── */
        @keyframes wc-breathe { 0%,100%{transform:scale(1);opacity:.2} 50%{transform:scale(1.15);opacity:.35} }
        @keyframes wc-float { 0%,100%{transform:translateY(0);opacity:.3} 50%{transform:translateY(-8px);opacity:.7} }
        .wc-breathe { animation:wc-breathe 5s ease-in-out infinite }
        .wc-float { animation:wc-float 3s ease-in-out infinite }

        /* ── Sun ── */
        @keyframes wc-spin { from{transform:rotate(0)} to{transform:rotate(360deg)} }
        @keyframes wc-spin-r { from{transform:rotate(0)} to{transform:rotate(-360deg)} }
        @keyframes wc-glow { 0%,100%{transform:scale(1);opacity:.3} 50%{transform:scale(1.35);opacity:.55} }
        @keyframes wc-twinkle { 0%,100%{opacity:.15;transform:scale(.6)} 50%{opacity:1;transform:scale(1.4)} }
        @keyframes wc-flare { 0%,100%{opacity:.2;transform:scale(.8)} 50%{opacity:.6;transform:scale(1.1)} }
        .wc-spin-slow { animation:wc-spin 25s linear infinite }
        .wc-spin-slow-reverse { animation:wc-spin-r 18s linear infinite }
        .wc-spin-fast { animation:wc-spin 10s linear infinite }
        .wc-glow-pulse { animation:wc-glow 3s ease-in-out infinite }
        .wc-sparkle { animation:wc-twinkle 2s ease-in-out infinite }
        .wc-flare { animation:wc-flare 4s ease-in-out infinite }

        /* ── Rain ── */
        @keyframes wc-fall { 0%{transform:translateY(-12px);opacity:0} 6%{opacity:1} 88%{opacity:.7} 100%{transform:translateY(155px);opacity:0} }
        @keyframes wc-splat { 0%{transform:scaleX(1) scaleY(1);opacity:.5} 50%{transform:scaleX(2) scaleY(.3);opacity:.4} 100%{transform:scaleX(3) scaleY(0);opacity:0} }
        @keyframes wc-splat-dot { 0%{transform:translateY(0);opacity:.4} 100%{transform:translateY(-8px);opacity:0} }
        @keyframes wc-puddle-ripple { 0%,100%{opacity:.1;transform:scaleX(1)} 50%{opacity:.22;transform:scaleX(1.2)} }
        @keyframes wc-lightning { 0%,92%,100%{opacity:0} 94%{opacity:.8} 96%{opacity:0} 98%{opacity:.4} }
        @keyframes wc-mist-drift { 0%{transform:translateX(-50%)} 100%{transform:translateX(0)} }
        .wc-rain { animation:wc-fall .65s linear infinite }
        .wc-splash { animation:wc-splat .5s ease-out infinite }
        .wc-splash-dot { animation:wc-splat-dot .5s ease-out infinite }
        .wc-puddle { animation:wc-puddle-ripple 2s ease-in-out infinite }
        .wc-lightning { animation:wc-lightning 4s ease-in-out infinite }
        .wc-mist { animation:wc-mist-drift 8s linear infinite }

        /* ── Snow ── */
        @keyframes wc-drift {
          0%{transform:translateY(-8px) translateX(0);opacity:0}
          8%{opacity:.85}
          20%{transform:translateY(28px) translateX(10px)}
          40%{transform:translateY(60px) translateX(-7px)}
          60%{transform:translateY(95px) translateX(8px)}
          80%{transform:translateY(130px) translateX(-5px)}
          100%{transform:translateY(170px) translateX(3px);opacity:0}
        }
        @keyframes wc-drift-slow {
          0%{transform:translateY(-4px) translateX(0);opacity:0}
          10%{opacity:.6}
          30%{transform:translateY(40px) translateX(6px)}
          60%{transform:translateY(100px) translateX(-4px)}
          100%{transform:translateY(175px) translateX(2px);opacity:0}
        }
        @keyframes wc-drift-front {
          0%{transform:translateY(-10px) translateX(0);opacity:0}
          5%{opacity:.9}
          15%{transform:translateY(20px) translateX(12px)}
          35%{transform:translateY(55px) translateX(-10px)}
          55%{transform:translateY(90px) translateX(9px)}
          75%{transform:translateY(125px) translateX(-6px)}
          100%{transform:translateY(170px) translateX(4px);opacity:0}
        }
        @keyframes wc-snow-atmos { 0%,100%{opacity:.3} 50%{opacity:.6} }
        @keyframes wc-snow-pillar { 0%,100%{opacity:.15;height:55%} 50%{opacity:.35;height:65%} }
        .wc-snow { animation:wc-drift 5s ease-in-out infinite }
        .wc-snow-slow { animation:wc-drift-slow 7s ease-in-out infinite }
        .wc-snow-front { animation:wc-drift-front var(--dur, 3.5s) ease-in-out infinite }
        .wc-snow-atmos { animation:wc-snow-atmos 5s ease-in-out infinite }
        .wc-spin-snow { animation:wc-spin 30s linear infinite }
        .wc-snow-pillar { animation:wc-snow-pillar 4s ease-in-out infinite }

        /* ── Heatwave ── */
        @keyframes wc-shimmer { 0%{transform:translateY(0) scaleX(1)} 50%{transform:translateY(-4px) scaleX(1.02)} 100%{transform:translateY(0) scaleX(1)} }
        @keyframes wc-rise { 0%{transform:translateY(0);opacity:.4} 100%{transform:translateY(-130px);opacity:0} }
        @keyframes wc-ground-pulse { 0%,100%{opacity:.2} 50%{opacity:.45} }
        @keyframes wc-heat-bg-pulse { 0%,100%{opacity:.3} 50%{opacity:.7} }
        @keyframes wc-ember-rise {
          0%{transform:translateY(0) translateX(0);opacity:.6}
          25%{transform:translateY(-35px) translateX(5px);opacity:.8}
          50%{transform:translateY(-70px) translateX(-4px);opacity:.6}
          75%{transform:translateY(-105px) translateX(6px);opacity:.3}
          100%{transform:translateY(-140px) translateX(-2px);opacity:0}
        }
        .wc-heat-wave { animation:wc-shimmer 3s ease-in-out infinite }
        .wc-heat-rise { animation:wc-rise 2.5s ease-out infinite }
        .wc-heat-ground { animation:wc-ground-pulse 2s ease-in-out infinite }
        .wc-spin-heat { animation:wc-spin 12s linear infinite }
        .wc-heat-bg { animation:wc-heat-bg-pulse 3s ease-in-out infinite }
        .wc-ember { animation:wc-ember-rise var(--dur, 3s) ease-out infinite }

        /* ── Coldwave ── */
        @keyframes wc-streak { 0%{transform:translateX(100%);opacity:0} 30%{opacity:1} 70%{opacity:1} 100%{transform:translateX(-120%);opacity:0} }
        @keyframes wc-ice { 0%,100%{opacity:.15;transform:scale(.7) rotate(0)} 50%{opacity:.75;transform:scale(1.3) rotate(90deg)} }
        @keyframes wc-breath { 0%,100%{transform:scale(1);opacity:.15} 50%{transform:scale(1.5);opacity:.25} }
        @keyframes wc-cold-bg-pulse { 0%,100%{opacity:.4} 50%{opacity:.8} }
        @keyframes wc-blizzard-back-fall {
          0%{transform:translateY(-6px) translateX(0);opacity:0}
          8%{opacity:.5}
          30%{transform:translateY(35px) translateX(20px)}
          60%{transform:translateY(85px) translateX(35px)}
          100%{transform:translateY(170px) translateX(50px);opacity:0}
        }
        @keyframes wc-blizzard-mid-fall {
          0%{transform:translateY(-8px) translateX(0);opacity:0}
          6%{opacity:.7}
          25%{transform:translateY(30px) translateX(18px)}
          50%{transform:translateY(70px) translateX(30px)}
          75%{transform:translateY(115px) translateX(40px)}
          100%{transform:translateY(170px) translateX(55px);opacity:0}
        }
        @keyframes wc-blizzard-front-fall {
          0%{transform:translateY(-10px) translateX(0);opacity:0}
          5%{opacity:.85}
          20%{transform:translateY(25px) translateX(22px)}
          45%{transform:translateY(65px) translateX(38px)}
          70%{transform:translateY(110px) translateX(48px)}
          100%{transform:translateY(170px) translateX(60px);opacity:0}
        }
        @keyframes wc-frost-crack-appear { 0%{opacity:0;width:0} 50%{opacity:.5} 100%{opacity:.3;width:100%} }
        @keyframes wc-spin-cold { from{transform:rotate(0)} to{transform:rotate(360deg)} }
        .wc-cold-streak { animation:wc-streak 3s ease-in-out infinite }
        .wc-crystal { animation:wc-ice 3s ease-in-out infinite }
        .wc-breath { animation:wc-breath 4s ease-in-out infinite }
        .wc-cold-bg { animation:wc-cold-bg-pulse 4s ease-in-out infinite }
        .wc-blizzard-back { animation:wc-blizzard-back-fall var(--dur, 2.5s) linear infinite }
        .wc-blizzard-mid { animation:wc-blizzard-mid-fall var(--dur, 1.8s) linear infinite }
        .wc-blizzard-front { animation:wc-blizzard-front-fall var(--dur, 1.2s) linear infinite }
        @keyframes wc-aurora { 0%{opacity:.3;transform:translateX(-5%)} 50%{opacity:.7;transform:translateX(5%)} 100%{opacity:.3;transform:translateX(-5%)} }
        @keyframes wc-ice-magic-rise {
          0%{transform:translateY(0) scale(1);opacity:0}
          10%{opacity:.8}
          40%{transform:translateY(-40px) scale(1.2);opacity:.9}
          70%{transform:translateY(-85px) scale(0.9);opacity:.5}
          100%{transform:translateY(-130px) scale(0.5);opacity:0}
        }
        .wc-ice-magic { animation:wc-ice-magic-rise 3.5s ease-out infinite }
        @keyframes wc-icicle-drip {
          0%,70%{opacity:0;transform:translateY(0) translateX(-50%) scale(1)}
          75%{opacity:.5;transform:translateY(0) translateX(-50%) scale(1)}
          100%{opacity:0;transform:translateY(12px) translateX(-50%) scale(0.3)}
        }
        @keyframes wc-breath-rise {
          0%{opacity:0;transform:translateY(0) scale(0.8)}
          20%{opacity:.25}
          50%{opacity:.2;transform:translateY(-15px) scale(1.2)}
          100%{opacity:0;transform:translateY(-35px) scale(1.5)}
        }
        .wc-icicle-drip { animation:wc-icicle-drip 3s ease-in infinite }
        .wc-breath-rise { animation:wc-breath-rise 4s ease-out infinite }
        .wc-frost-crack { animation:wc-frost-crack-appear 3s ease-out infinite }
        .wc-spin-cold { animation:wc-spin-cold 20s linear infinite }
        .wc-aurora { animation:wc-aurora 8s ease-in-out infinite }

        /* ── Fog ── */
        @keyframes wc-fog-move { 0%{transform:translateX(-60%)} 100%{transform:translateX(0%)} }
        @keyframes wc-fog-move-r { 0%{transform:translateX(0%)} 100%{transform:translateX(-60%)} }
        @keyframes wc-fog-float { 0%,100%{transform:translateY(0);opacity:.2} 50%{transform:translateY(-8px);opacity:.5} }
        .wc-fog { animation:wc-fog-move 12s ease-in-out infinite alternate }
        .wc-fog-reverse { animation:wc-fog-move-r 12s ease-in-out infinite alternate }
        .wc-fog-dot { animation:wc-fog-float 4s ease-in-out infinite }

        /* ── Finale ── */
        @keyframes wc-confetti-fall {
          0%{transform:translateY(-8px) rotate(0deg);opacity:0}
          8%{opacity:.8}
          50%{transform:translateY(80px) rotate(180deg);opacity:.7}
          100%{transform:translateY(170px) rotate(360deg);opacity:0}
        }
        @keyframes wc-trophy-bounce {
          0%,100%{transform:translateY(0) scale(1)}
          50%{transform:translateY(-10px) scale(1.08)}
        }
        .wc-confetti { animation:wc-confetti-fall 3s ease-in-out infinite }
        .wc-trophy-bounce { animation:wc-trophy-bounce 2s ease-in-out infinite }

        /* ── Bankrupt ── */
        @keyframes wc-bankrupt-pulse { 0%,100%{opacity:.3} 50%{opacity:1} }
        @keyframes wc-shatter-crack { 0%{opacity:0;transform:rotate(var(--r,0deg)) scaleX(0)} 40%{opacity:.8} 100%{opacity:.4;transform:rotate(var(--r,0deg)) scaleX(1)} }
        @keyframes wc-bankrupt-shake { 0%,100%{transform:translate(0,0)} 10%{transform:translate(-2px,1px)} 20%{transform:translate(2px,-1px)} 30%{transform:translate(-1px,2px)} 40%{transform:translate(1px,-1px)} 50%{transform:translate(0,0)} }
        @keyframes wc-money-fly {
          0% { transform:translate(0,0) rotate(0deg) scale(1); opacity:.9 }
          20% { opacity:1 }
          100% { transform:translate(var(--fly-x),var(--fly-y)) rotate(var(--fly-r)) scale(.3); opacity:0 }
        }
        @keyframes wc-vortex-ring {
          0% { transform:scale(.3); opacity:.5 }
          100% { transform:scale(1.8); opacity:0 }
        }
        @keyframes wc-stamp-slam { 0%{transform:rotate(-12deg) scale(3);opacity:0} 50%{transform:rotate(-12deg) scale(0.95);opacity:.6} 70%{transform:rotate(-12deg) scale(1.05);opacity:.5} 100%{transform:rotate(-12deg) scale(1);opacity:.5} }
        @keyframes wc-floor-wobble { 0%,100%{transform:translateY(0)} 50%{transform:translateY(-2px)} }
        .wc-bankrupt-pulse { animation:wc-bankrupt-pulse 2s ease-in-out infinite }
        .wc-shatter-crack { animation:wc-shatter-crack 0.8s ease-out forwards }
        .wc-bankrupt-shake { animation:wc-bankrupt-shake 3s ease-in-out infinite }
        .wc-money-fly { animation:wc-money-fly 2.2s ease-out infinite }
        .wc-vortex-ring { animation:wc-vortex-ring 2s ease-out infinite }
        .wc-stamp-slam { animation:wc-stamp-slam 1s ease-out forwards }
        .wc-floor-item { animation:wc-floor-wobble 2s ease-in-out infinite }
      `}</style>

      <div
        className={`lg:col-span-2 rounded-xl shadow-soft border flex flex-col overflow-hidden transition-all duration-700 ${
          disabled
            ? `bg-gradient-to-br ${bgGradient} ${border}`
            : `bg-gradient-to-br ${bgGradient} ${border}`
        } ${mounted ? "opacity-100 translate-y-0" : "opacity-0 translate-y-2"}`}
      >
        {/* Header */}
        <div className="px-6 pt-6 pb-2 relative z-10">
          <h3 className="text-lg font-bold text-slate-800 mb-1 flex items-center gap-2">
            <span className={`material-symbols-outlined ${noWeather ? "text-amber-500" : disabled ? "text-rose-400" : "text-blue-500"}`}>
              {noWeather ? "flag" : disabled ? "store" : "calendar_month"}
            </span>
            {noWeather ? "시즌 종료" : disabled ? "영업 종료" : "내일 날씨"}
          </h3>
          <p className="text-sm text-slate-500">
            {noWeather
              ? "마지막 영업일이 끝났습니다."
              : disabled
                ? (disabledMessage ?? "파산으로 매장이 폐업되었습니다.")
                : "내일의 날씨 예보입니다."}
          </p>
        </div>

        {/* Scene area */}
        <div className="relative flex-1 min-h-[160px]">
          {Scene && !disabled && !noWeather && <Scene />}
          {noWeather && <FinaleScene />}
          {disabled && !noWeather && <BankruptScene />}
        </div>

        {/* Label */}
        <div className="px-6 pb-5 pt-2 text-center relative z-10">
          {noWeather ? (
            <>
              <p className="text-2xl font-black tracking-tight text-slate-800">수고하셨습니다!</p>
              <p className="text-sm text-slate-500 mt-1">결과를 확인해보세요</p>
            </>
          ) : (
            <>
              <p className={`text-2xl font-black tracking-tight ${disabled ? "text-rose-400/80" : "text-slate-800"}`}>
                {disabled ? "폐업" : weather ? weather.label : "--"}
              </p>
              {disabled && (
                <p className="text-sm text-slate-400 mt-1">더 이상 영업할 수 없습니다</p>
              )}
            </>
          )}
        </div>
      </div>
    </>
  );
}
