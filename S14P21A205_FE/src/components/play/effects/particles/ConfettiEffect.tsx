import { useRef, useMemo, useEffect } from "react";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";
import { useEffectLifecycle } from "../useEffectLifecycle";

const CONFETTI_COUNT = 400;
const COLORS = ["#f43f5e", "#3b82f6", "#22c55e", "#eab308", "#a855f7", "#ec4899", "#06b6d4", "#f97316"];
const GRAVITY = 1.8;
const BURST_COUNT = 10; // 하나씩 랜덤하게 터지는 느낌
const HOLIDAY_SFX_SRC = "/sfx/holiday.mp3";
const HOLIDAY_SFX_VOLUME = 0.5;
const AUDIO_POOL_SIZE = 4;

interface Props {
  durationMs: number;
}

export default function ConfettiEffect({ durationMs }: Props) {
  const opacity = useEffectLifecycle(durationMs);
  const meshRef = useRef<THREE.InstancedMesh>(null);
  const dummy = useMemo(() => new THREE.Object3D(), []);
  const startTimeRef = useRef<number | null>(null);
  const firedBurstsRef = useRef<boolean[]>(Array(BURST_COUNT).fill(false));
  const audioPoolRef = useRef<HTMLAudioElement[]>([]);
  const audioPoolIdx = useRef(0);

  useEffect(() => {
    const pool: HTMLAudioElement[] = [];
    for (let i = 0; i < AUDIO_POOL_SIZE; i++) {
      const audio = new Audio(HOLIDAY_SFX_SRC);
      audio.volume = HOLIDAY_SFX_VOLUME;
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

  // 각 burst의 발사 시간을 랜덤하게 설정 (하나씩 터지는 느낌)
  const burstTimes = useMemo(() => {
    const times: number[] = [];
    for (let b = 0; b < BURST_COUNT; b++) {
      // 0~10초 사이에 완전 랜덤
      times.push(Math.random() * 10);
    }
    return times;
  }, []);

  // 각 burst 그룹의 중심점 (모여있다가 터지는 위치)
  const burstCenters = useMemo(() => {
    return Array.from({ length: BURST_COUNT }, () => ({
      x: (Math.random() - 0.5) * 14,
      y: 2 + Math.random() * 4,
      z: (Math.random() - 0.5) * 0.3,
    }));
  }, []);

  const confettiData = useMemo(() => {
    return Array.from({ length: CONFETTI_COUNT }, (_, i) => {
      const burstGroup = i % BURST_COUNT;
      // 폭발 방향 (전방향으로 퍼짐)
      const angle = Math.random() * Math.PI * 2;
      const speed = 3 + Math.random() * 5;
      return {
        burstGroup,
        // 중심에서 살짝 흩어진 초기 위치 (모여있는 느낌)
        offsetX: (Math.random() - 0.5) * 0.3,
        offsetY: (Math.random() - 0.5) * 0.3,
        offsetZ: (Math.random() - 0.5) * 0.3,
        vx: Math.cos(angle) * speed * (0.8 + Math.random() * 0.4),
        vy: Math.sin(angle) * speed * 0.5 + speed * 0.5, // 위쪽 편향
        rotX: 1 + Math.random() * 3,
        rotY: 1 + Math.random() * 4,
        rotZ: 0.5 + Math.random() * 2,
        wobbleFreqX: 0.5 + Math.random() * 2,
        wobbleAmpX: 0.3 + Math.random() * 0.8,
        flipFreq: 2 + Math.random() * 5,
        size: 0.5 + Math.random() * 0.7,
        drag: 0.3 + Math.random() * 0.5,
      };
    });
  }, []);

  const colorArray = useMemo(() => {
    const arr = new Float32Array(CONFETTI_COUNT * 3);
    for (let i = 0; i < CONFETTI_COUNT; i++) {
      const c = new THREE.Color(COLORS[Math.floor(Math.random() * COLORS.length)]);
      c.toArray(arr, i * 3);
    }
    return arr;
  }, []);

  useFrame(({ clock }) => {
    const elapsed = clock.getElapsedTime();
    if (startTimeRef.current === null) startTimeRef.current = elapsed;
    const t = elapsed - startTimeRef.current;
    const o = opacity.current;

    // burst 타이밍에 맞춰 효과음 재생
    for (let b = 0; b < BURST_COUNT; b++) {
      if (!firedBurstsRef.current[b] && t >= burstTimes[b]) {
        firedBurstsRef.current[b] = true;
        const pool = audioPoolRef.current;
        if (pool.length > 0) {
          const audio = pool[audioPoolIdx.current % pool.length];
          audioPoolIdx.current++;
          audio.currentTime = 0;
          audio.play().catch(() => {});
        }
      }
    }

    if (meshRef.current) {
      for (let i = 0; i < CONFETTI_COUNT; i++) {
        const d = confettiData[i];
        const burstTime = burstTimes[d.burstGroup];
        const bt = t - burstTime;

        const center = burstCenters[d.burstGroup];

        if (bt < 0 || bt > 8) {
          dummy.scale.set(0, 0, 0);
        } else {
          const dragFactor = Math.exp(-d.drag * bt);
          const x = center.x + d.offsetX + (d.vx / d.drag) * (1 - dragFactor)
            + Math.sin(bt * d.wobbleFreqX + i * 0.7) * d.wobbleAmpX;
          const y = center.y + d.offsetY + (d.vy / d.drag) * (1 - dragFactor)
            - 0.5 * GRAVITY * bt * bt;
          const z = center.z + d.offsetZ;

          const fadeIn = Math.min(bt * 10, 1);
          const fadeOut = Math.max(0, 1 - (bt - 5) / 3);
          const fade = fadeIn * fadeOut;

          dummy.position.set(x, y, z);
          dummy.rotation.set(
            bt * d.rotX + Math.sin(bt * d.flipFreq) * 1.5,
            bt * d.rotY,
            bt * d.rotZ + Math.cos(bt * d.flipFreq * 0.7) * 0.8,
          );
          dummy.scale.setScalar(d.size * fade);
        }

        dummy.updateMatrix();
        meshRef.current.setMatrixAt(i, dummy.matrix);
      }
      meshRef.current.instanceMatrix.needsUpdate = true;
      (meshRef.current.material as THREE.MeshBasicMaterial).opacity = o * 0.88;
    }
  });

  return (
    <instancedMesh ref={meshRef} args={[undefined, undefined, CONFETTI_COUNT]}>
      <boxGeometry args={[0.08, 0.05, 0.008]} />
      <meshBasicMaterial transparent opacity={0} vertexColors side={THREE.DoubleSide} />
      <instancedBufferAttribute attach="geometry-attributes-color" args={[colorArray, 3]} />
    </instancedMesh>
  );
}
