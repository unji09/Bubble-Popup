import { useRef, useMemo, useEffect } from "react";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";
import { useEffectLifecycle } from "../useEffectLifecycle";

const BURST_COUNT = 5;
const RAYS_PER_BURST = 80;
const TOTAL_RAYS = BURST_COUNT * RAYS_PER_BURST;
const TRAIL_COUNT = BURST_COUNT;
const SPARKLE_PER_BURST = 25;
const TOTAL_SPARKLES = BURST_COUNT * SPARKLE_PER_BURST;

const BURST_PALETTES = [
  ["#ff4040", "#ff8855", "#ffcc66", "#ffffff"],
  ["#6699ff", "#99ccff", "#ccddff", "#ffffff"],
  ["#ffdd22", "#ffee66", "#ffffaa", "#ffffff"],
  ["#ff77bb", "#ff99dd", "#ffbbee", "#ffffff"],
  ["#ffbb33", "#ffdd55", "#ffeeaa", "#ffffff"],
];

const BURST_CYCLE = 6.0;
const GRAVITY = 1.8; // 강한 중력 → 곡선 궤적
const FESTIVAL_SFX_SRC = "/sfx/festival.mp3";
const FESTIVAL_SFX_VOLUME = 0.5;
const FESTIVAL_SFX_SKIP = 0.8;
const AUDIO_POOL_SIZE = BURST_COUNT;

interface Props {
  durationMs: number;
}

/** 줄기 텍스처 — 밝은 중심선 + 글로우 */
function createRayTexture(): THREE.CanvasTexture {
  const c = document.createElement("canvas");
  c.width = 16;
  c.height = 128;
  const ctx = c.getContext("2d")!;
  ctx.clearRect(0, 0, 16, 128);

  // 세로 그라데이션 (머리 밝고 꼬리 사라짐)
  const vGrad = ctx.createLinearGradient(8, 0, 8, 128);
  vGrad.addColorStop(0, "rgba(255, 255, 255, 1)");
  vGrad.addColorStop(0.05, "rgba(255, 255, 240, 1)");
  vGrad.addColorStop(0.15, "rgba(255, 240, 200, 0.85)");
  vGrad.addColorStop(0.4, "rgba(255, 210, 140, 0.5)");
  vGrad.addColorStop(0.7, "rgba(255, 160, 80, 0.2)");
  vGrad.addColorStop(1, "rgba(255, 100, 40, 0)");
  ctx.fillStyle = vGrad;
  ctx.fillRect(0, 0, 16, 128);

  // 가로: 중심이 밝은 가느다란 코어
  ctx.globalCompositeOperation = "destination-in";
  const hGrad = ctx.createLinearGradient(0, 0, 16, 0);
  hGrad.addColorStop(0, "rgba(0,0,0,0)");
  hGrad.addColorStop(0.2, "rgba(0,0,0,0.3)");
  hGrad.addColorStop(0.4, "rgba(0,0,0,0.8)");
  hGrad.addColorStop(0.5, "rgba(0,0,0,1)");
  hGrad.addColorStop(0.6, "rgba(0,0,0,0.8)");
  hGrad.addColorStop(0.8, "rgba(0,0,0,0.3)");
  hGrad.addColorStop(1, "rgba(0,0,0,0)");
  ctx.fillStyle = hGrad;
  ctx.fillRect(0, 0, 16, 128);
  ctx.globalCompositeOperation = "source-over";

  const tex = new THREE.CanvasTexture(c);
  tex.needsUpdate = true;
  return tex;
}

/** 중심 글로우 텍스처 */
function createGlowTexture(): THREE.CanvasTexture {
  const c = document.createElement("canvas");
  c.width = 64;
  c.height = 64;
  const ctx = c.getContext("2d")!;
  ctx.clearRect(0, 0, 64, 64);
  const grad = ctx.createRadialGradient(32, 32, 0, 32, 32, 32);
  grad.addColorStop(0, "rgba(255, 255, 255, 1)");
  grad.addColorStop(0.2, "rgba(255, 250, 220, 0.8)");
  grad.addColorStop(0.5, "rgba(255, 200, 120, 0.3)");
  grad.addColorStop(1, "rgba(255, 100, 50, 0)");
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, 64, 64);
  const tex = new THREE.CanvasTexture(c);
  tex.needsUpdate = true;
  return tex;
}

/** 상승 꼬리 텍스처 */
function createTrailTexture(): THREE.CanvasTexture {
  const c = document.createElement("canvas");
  c.width = 8;
  c.height = 64;
  const ctx = c.getContext("2d")!;
  ctx.clearRect(0, 0, 8, 64);
  const grad = ctx.createLinearGradient(4, 0, 4, 64);
  grad.addColorStop(0, "rgba(255, 255, 255, 1)");
  grad.addColorStop(0.15, "rgba(255, 230, 150, 0.7)");
  grad.addColorStop(0.5, "rgba(255, 180, 80, 0.3)");
  grad.addColorStop(1, "rgba(255, 120, 40, 0)");
  ctx.fillStyle = grad;
  ctx.fillRect(1, 0, 6, 64);
  const tex = new THREE.CanvasTexture(c);
  tex.needsUpdate = true;
  return tex;
}

export default function FireworkEffect({ durationMs }: Props) {
  const opacity = useEffectLifecycle(durationMs);

  const rayRef = useRef<THREE.InstancedMesh>(null);
  const trailRef = useRef<THREE.InstancedMesh>(null);
  const glowRef = useRef<THREE.InstancedMesh>(null);
  const sparkleRef = useRef<THREE.InstancedMesh>(null);
  const dummy = useMemo(() => new THREE.Object3D(), []);
  const startTimeRef = useRef<number | null>(null);
  // 각 burst의 마지막 폭발 사이클 번호를 추적 (중복 재생 방지)
  const lastBurstCycleRef = useRef<number[]>(Array(BURST_COUNT).fill(-1));
  const audioPoolRef = useRef<HTMLAudioElement[]>([]);
  const audioPoolIdx = useRef(0);

  useEffect(() => {
    const pool: HTMLAudioElement[] = [];
    for (let i = 0; i < AUDIO_POOL_SIZE; i++) {
      const audio = new Audio(FESTIVAL_SFX_SRC);
      audio.volume = FESTIVAL_SFX_VOLUME;
      audio.preload = "auto";
      pool.push(audio);
    }
    audioPoolRef.current = pool;

    return () => {
      for (const a of pool) {
        a.pause();
        a.src = "";
      }
    };
  }, []);

  const rayTex = useMemo(() => createRayTexture(), []);
  const glowTex = useMemo(() => createGlowTexture(), []);
  const trailTex = useMemo(() => createTrailTexture(), []);

  const burstData = useMemo(() => {
    return Array.from({ length: BURST_COUNT }, () => {
      const rays = Array.from({ length: RAYS_PER_BURST }, (_, r) => {
        const baseAngle = (r / RAYS_PER_BURST) * Math.PI * 2;
        const angle = baseAngle + (Math.random() - 0.5) * 0.12;
        return {
          dirX: Math.cos(angle),
          dirY: Math.sin(angle),
          // 속도 변화로 불규칙한 길이
          speed: 2.5 + Math.random() * 2.0,
          fadeSpeed: 0.5 + Math.random() * 0.5,
        };
      });

      const sparkles = Array.from({ length: SPARKLE_PER_BURST }, () => {
        const angle = Math.random() * Math.PI * 2;
        return {
          dirX: Math.cos(angle),
          dirY: Math.sin(angle),
          speed: 0.3 + Math.random() * 1.0,
          twinkle: 4 + Math.random() * 8,
          size: 0.02 + Math.random() * 0.04,
        };
      });

      return {
        centerX: (Math.random() - 0.5) * 14,
        centerY: 3 + Math.random() * 3,
        delay: Math.random() * 4,
        rays,
        sparkles,
      };
    });
  }, []);

  const rayColorArray = useMemo(() => {
    const arr = new Float32Array(TOTAL_RAYS * 3);
    for (let b = 0; b < BURST_COUNT; b++) {
      const palette = BURST_PALETTES[b % BURST_PALETTES.length];
      for (let r = 0; r < RAYS_PER_BURST; r++) {
        const c = new THREE.Color(palette[Math.floor(Math.random() * palette.length)]);
        c.offsetHSL((Math.random() - 0.5) * 0.05, (Math.random() - 0.5) * 0.1, (Math.random() - 0.5) * 0.1);
        c.toArray(arr, (b * RAYS_PER_BURST + r) * 3);
      }
    }
    return arr;
  }, []);

  const sparkleColorArray = useMemo(() => {
    const arr = new Float32Array(TOTAL_SPARKLES * 3);
    for (let b = 0; b < BURST_COUNT; b++) {
      const palette = BURST_PALETTES[b % BURST_PALETTES.length];
      for (let s = 0; s < SPARKLE_PER_BURST; s++) {
        const c = new THREE.Color(palette[0]);
        c.lerp(new THREE.Color("#ffffff"), 0.3 + Math.random() * 0.4);
        c.toArray(arr, (b * SPARKLE_PER_BURST + s) * 3);
      }
    }
    return arr;
  }, []);

  useFrame(({ clock }) => {
    const elapsed = clock.getElapsedTime();
    if (startTimeRef.current === null) startTimeRef.current = elapsed;
    const t = elapsed - startTimeRef.current;
    const o = opacity.current;

    // 폭죽 폭발 시점에 효과음 재생
    for (let b = 0; b < BURST_COUNT; b++) {
      const burst = burstData[b];
      const rawBt = t - burst.delay;
      if (rawBt < 0) continue;
      const cycle = Math.floor(rawBt / BURST_CYCLE);
      const bt = rawBt % BURST_CYCLE;
      if (bt < 0.15 && cycle !== lastBurstCycleRef.current[b]) {
        lastBurstCycleRef.current[b] = cycle;
        const pool = audioPoolRef.current;
        if (pool.length > 0) {
          const audio = pool[audioPoolIdx.current % pool.length];
          audioPoolIdx.current++;
          audio.currentTime = FESTIVAL_SFX_SKIP;
          audio.play().catch(() => {});
        }
      }
    }

    // 폭죽 줄기 — 포물선 궤적
    if (rayRef.current) {
      let idx = 0;
      for (let b = 0; b < BURST_COUNT; b++) {
        const burst = burstData[b];
        const rawBt = t - burst.delay;
        const bt = rawBt >= 0 ? rawBt % BURST_CYCLE : -1;
        const et = bt - 0.5; // 상승 후 폭발

        for (let r = 0; r < RAYS_PER_BURST; r++) {
          const ray = burst.rays[r];

          if (et < 0 || et > 2.0) {
            dummy.scale.set(0, 0, 0);
          } else {
            // 포물선: vx, vy 초기속도 → 중력으로 아래 휘어짐
            const vx = ray.dirX * ray.speed;
            const vy = ray.dirY * ray.speed;

            // 현재 위치 (포물선)
            const px = burst.centerX + vx * et;
            const py = burst.centerY + vy * et - 0.5 * GRAVITY * et * et;

            // 현재 속도 방향 (접선) → 줄기 방향
            const curVx = vx;
            const curVy = vy - GRAVITY * et;
            const angle = Math.atan2(curVy, curVx);

            // 페이드 (더 천천히 사라짐)
            const fade = Math.max(0, 1 - et * et / (4.0 * ray.fadeSpeed));
            // 줄기 길이: 속도에 비례, 점점 짧아짐
            const speed = Math.sqrt(curVx * curVx + curVy * curVy);
            const stretch = Math.min(speed * 0.25, 1.2) * Math.max(0.3, 1 - et * 0.3);

            dummy.position.set(px, py, 0);
            dummy.rotation.set(0, 0, angle - Math.PI / 2);
            dummy.scale.set(fade * 0.45, fade * stretch * 1.3, 1);
          }

          dummy.updateMatrix();
          rayRef.current.setMatrixAt(idx, dummy.matrix);
          idx++;
        }
      }
      rayRef.current.instanceMatrix.needsUpdate = true;
      (rayRef.current.material as THREE.MeshBasicMaterial).opacity = o;
    }

    // 중심 글로우
    if (glowRef.current) {
      for (let b = 0; b < BURST_COUNT; b++) {
        const burst = burstData[b];
        const rawBt = t - burst.delay;
        const bt = rawBt >= 0 ? rawBt % BURST_CYCLE : -1;
        const et = bt - 0.5;

        if (et < 0 || et > 1.0) {
          dummy.scale.set(0, 0, 0);
        } else {
          const flash = et < 0.1
            ? et / 0.1
            : Math.max(0, 1 - (et - 0.1) / 0.9);
          dummy.position.set(burst.centerX, burst.centerY, 0.1);
          dummy.rotation.set(0, 0, 0);
          dummy.scale.setScalar(flash * 3.0);
        }

        dummy.updateMatrix();
        glowRef.current.setMatrixAt(b, dummy.matrix);
      }
      glowRef.current.instanceMatrix.needsUpdate = true;
      (glowRef.current.material as THREE.MeshBasicMaterial).opacity = o * 0.8;
    }

    // 스파클
    if (sparkleRef.current) {
      let idx = 0;
      for (let b = 0; b < BURST_COUNT; b++) {
        const burst = burstData[b];
        const rawBt = t - burst.delay;
        const bt = rawBt >= 0 ? rawBt % BURST_CYCLE : -1;
        const et = bt - 0.5;

        for (let s = 0; s < SPARKLE_PER_BURST; s++) {
          const sp = burst.sparkles[s];

          if (et < 0.1 || et > 1.8) {
            dummy.scale.set(0, 0, 0);
          } else {
            const px = burst.centerX + sp.dirX * sp.speed * et;
            const py = burst.centerY + sp.dirY * sp.speed * et - 0.5 * GRAVITY * et * et;
            const fade = Math.max(0, 1 - et / 1.8);
            const twinkle = 0.3 + 0.7 * Math.abs(Math.sin(t * sp.twinkle + s * 2.1));

            dummy.position.set(px, py, 0.2);
            dummy.rotation.set(0, 0, t * 3 + s);
            dummy.scale.setScalar(sp.size * fade * twinkle * 12);
          }

          dummy.updateMatrix();
          sparkleRef.current.setMatrixAt(idx, dummy.matrix);
          idx++;
        }
      }
      sparkleRef.current.instanceMatrix.needsUpdate = true;
      (sparkleRef.current.material as THREE.MeshBasicMaterial).opacity = o * 0.6;
    }

    // 상승 꼬리
    if (trailRef.current) {
      for (let b = 0; b < TRAIL_COUNT; b++) {
        const burst = burstData[b];
        const rawBt = t - burst.delay;
        const bt = rawBt >= 0 ? rawBt % BURST_CYCLE : -1;

        if (bt < 0 || bt > 0.5) {
          dummy.scale.set(0, 0, 0);
        } else {
          const p = bt / 0.5;
          const startY = -6;
          const y = startY + (burst.centerY - startY) * p;
          const wobble = Math.sin(bt * 50) * 0.04;
          dummy.position.set(burst.centerX + wobble, y, 0);
          dummy.rotation.set(0, 0, 0);
          const f = p < 0.8 ? 1 : (1 - p) / 0.2;
          dummy.scale.set(f * 0.06, f * 0.4, 1);
        }

        dummy.updateMatrix();
        trailRef.current.setMatrixAt(b, dummy.matrix);
      }
      trailRef.current.instanceMatrix.needsUpdate = true;
      (trailRef.current.material as THREE.MeshBasicMaterial).opacity = o * 0.9;
    }
  });

  return (
    <group>
      {/* 폭죽 줄기 */}
      <instancedMesh ref={rayRef} args={[undefined, undefined, TOTAL_RAYS]}>
        <planeGeometry args={[0.15, 0.8]} />
        <meshBasicMaterial
          map={rayTex}
          transparent
          opacity={0}
          vertexColors
          depthWrite={false}
          blending={THREE.AdditiveBlending}
        />
        <instancedBufferAttribute attach="geometry-attributes-color" args={[rayColorArray, 3]} />
      </instancedMesh>

      {/* 중심 글로우 */}
      <instancedMesh ref={glowRef} args={[undefined, undefined, BURST_COUNT]}>
        <planeGeometry args={[1, 1]} />
        <meshBasicMaterial
          map={glowTex}
          transparent
          opacity={0}
          depthWrite={false}
          blending={THREE.AdditiveBlending}
        />
      </instancedMesh>

      {/* 스파클 */}
      <instancedMesh ref={sparkleRef} args={[undefined, undefined, TOTAL_SPARKLES]}>
        <octahedronGeometry args={[0.02, 0]} />
        <meshBasicMaterial
          color="#fffacd"
          transparent
          opacity={0}
          vertexColors
          depthWrite={false}
          blending={THREE.AdditiveBlending}
        />
        <instancedBufferAttribute attach="geometry-attributes-color" args={[sparkleColorArray, 3]} />
      </instancedMesh>

      {/* 상승 꼬리 */}
      <instancedMesh ref={trailRef} args={[undefined, undefined, TRAIL_COUNT]}>
        <planeGeometry args={[0.05, 0.5]} />
        <meshBasicMaterial
          map={trailTex}
          color="#ffddaa"
          transparent
          opacity={0}
          depthWrite={false}
          blending={THREE.AdditiveBlending}
        />
      </instancedMesh>
    </group>
  );
}
