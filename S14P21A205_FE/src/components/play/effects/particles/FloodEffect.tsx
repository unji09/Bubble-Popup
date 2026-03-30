import { useRef, useMemo } from "react";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";
import { useEffectLifecycle } from "../useEffectLifecycle";

const BUBBLE_COUNT = 50;

// 카메라 fov=60, z=10 → 화면 상하 약 ±5.8
const SCREEN_BOT = -5.8;
const WATER_H = 12; // 물 평면 높이
// 초기: 물 상단 = 화면 하단에 딱 맞춤 (안 보임)
const START_CENTER_Y = SCREEN_BOT - WATER_H / 2; // -11.8
const RISE_AMOUNT = 10; // 10유닛 올라감 → 최종 상단 y = -5.8+10 = 4.2

interface Props {
  durationMs: number;
}

function createVignetteTexture(): THREE.CanvasTexture {
  const c = document.createElement("canvas");
  c.width = 256;
  c.height = 256;
  const ctx = c.getContext("2d")!;
  const grad = ctx.createRadialGradient(128, 128, 40, 128, 128, 180);
  grad.addColorStop(0, "rgba(10, 20, 60, 0)");
  grad.addColorStop(0.5, "rgba(10, 20, 60, 0.05)");
  grad.addColorStop(0.8, "rgba(10, 20, 60, 0.3)");
  grad.addColorStop(1, "rgba(10, 20, 60, 0.55)");
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, 256, 256);
  const tex = new THREE.CanvasTexture(c);
  tex.needsUpdate = true;
  return tex;
}

export default function FloodEffect({ durationMs }: Props) {
  const opacity = useEffectLifecycle(durationMs, 300, 2500);

  const waterRef = useRef<THREE.Mesh>(null);
  const waterMatRef = useRef<THREE.MeshBasicMaterial>(null);
  const surfaceRef = useRef<THREE.Mesh>(null);
  const surfaceMatRef = useRef<THREE.MeshBasicMaterial>(null);
  const surfaceHlRef = useRef<THREE.Mesh>(null);
  const surfaceHlMatRef = useRef<THREE.MeshBasicMaterial>(null);
  const bubbleRef = useRef<THREE.InstancedMesh>(null);
  const vignetteRef = useRef<THREE.MeshBasicMaterial>(null);
  const dummy = useMemo(() => new THREE.Object3D(), []);
  const startTimeRef = useRef<number | null>(null);
  const surfaceOrigY = useRef<Float32Array | null>(null);
  const surfaceHlOrigY = useRef<Float32Array | null>(null);

  const vignetteTex = useMemo(() => createVignetteTexture(), []);

  // 그라데이션 vertex color가 적용된 물 geometry
  const waterGeo = useMemo(() => {
    const geo = new THREE.PlaneGeometry(30, WATER_H, 192, 16);
    const posAttr = geo.getAttribute("position");
    const colors = new Float32Array(posAttr.count * 3);
    const topColor = new THREE.Color("#5b8fd8");
    const botColor = new THREE.Color("#1a3366");
    const tmp = new THREE.Color();
    for (let i = 0; i < posAttr.count; i++) {
      const localY = posAttr.getY(i);
      const ratio = (localY + WATER_H / 2) / WATER_H; // 0(아래)~1(위)
      tmp.copy(botColor).lerp(topColor, ratio);
      tmp.toArray(colors, i * 3);
    }
    geo.setAttribute("color", new THREE.BufferAttribute(colors, 3));
    return geo;
  }, []);

  const bubbleData = useMemo(() => {
    return Array.from({ length: BUBBLE_COUNT }, () => ({
      x: (Math.random() - 0.5) * 20,
      speed: 0.8 + Math.random() * 1.5,
      offset: Math.random() * 10,
      wobble: 1 + Math.random() * 2,
      size: 0.04 + Math.random() * 0.08,
    }));
  }, []);

  useFrame(({ clock }) => {
    const elapsed = clock.getElapsedTime();
    if (startTimeRef.current === null) startTimeRef.current = elapsed;
    const t = elapsed - startTimeRef.current; // 컴포넌트 마운트 후 경과 시간
    const o = opacity.current;

    // 10초에 걸쳐 올라감 (easeIn: 처음 느리게)
    const p = Math.min(t / 8, 1);
    const rise = p * p * RISE_AMOUNT;
    const centerY = START_CENTER_Y + rise;
    const waterTop = centerY + WATER_H / 2; // 물 상단 y

    // 물 평면: position.y 변경 + 상단 정점 wave
    if (waterRef.current && waterMatRef.current) {
      waterRef.current.position.y = centerY;

      // 상단 정점들을 물결로 변형 (세그먼트 64x1 → 상단 행은 y > 0인 정점들)
      const geo = waterRef.current.geometry as THREE.PlaneGeometry;
      const posAttr = geo.getAttribute("position");
      for (let i = 0; i < posAttr.count; i++) {
        const localY = posAttr.getY(i);
        // 상단 행 정점만 (localY ≈ WATER_H/2)
        if (localY > WATER_H / 2 - 0.5) {
          const x = posAttr.getX(i);
          const wave =
            Math.sin(x * 0.5 + elapsed * 2.0) * 0.6 +
            Math.sin(x * 1.2 + elapsed * 3.0) * 0.3 +
            Math.sin(x * 2.0 + elapsed * 4.5) * 0.12;
          posAttr.setY(i, WATER_H / 2 + wave);
        }
      }
      posAttr.needsUpdate = true;

      // 물이 화면에 보이기 시작하면 opacity 올림
      const visible = Math.max(0, waterTop - SCREEN_BOT);
      waterMatRef.current.opacity = o * Math.min(visible * 0.1, 0.6);
    }

    // 수면 밴드
    if (surfaceRef.current && surfaceMatRef.current) {
      surfaceRef.current.position.y = waterTop - 0.5;
      const geo = surfaceRef.current.geometry as THREE.PlaneGeometry;
      const posAttr = geo.getAttribute("position");
      // 원래 Y 좌표 저장 (최초 1회)
      if (!surfaceOrigY.current) {
        surfaceOrigY.current = new Float32Array(posAttr.count);
        for (let i = 0; i < posAttr.count; i++) {
          surfaceOrigY.current[i] = posAttr.getY(i);
        }
      }
      for (let i = 0; i < posAttr.count; i++) {
        const x = posAttr.getX(i);
        const wave = Math.sin(x * 0.5 + elapsed * 2.0) * 0.7
          + Math.sin(x * 1.2 + elapsed * 3.0) * 0.35
          + Math.sin(x * 2.0 + elapsed * 4.5) * 0.15;
        posAttr.setY(i, surfaceOrigY.current[i] + wave);
      }
      posAttr.needsUpdate = true;
      const visible = Math.max(0, waterTop - SCREEN_BOT);
      surfaceMatRef.current.opacity = o * Math.min(visible * 0.2, 0.9);
    }

    // 수면 하이라이트 — 수면 밴드와 동일한 웨이브
    if (surfaceHlRef.current && surfaceHlMatRef.current) {
      surfaceHlRef.current.position.y = waterTop - 0.1;
      const geo = surfaceHlRef.current.geometry as THREE.PlaneGeometry;
      const posAttr = geo.getAttribute("position");
      if (!surfaceHlOrigY.current) {
        surfaceHlOrigY.current = new Float32Array(posAttr.count);
        for (let i = 0; i < posAttr.count; i++) {
          surfaceHlOrigY.current[i] = posAttr.getY(i);
        }
      }
      for (let i = 0; i < posAttr.count; i++) {
        const x = posAttr.getX(i);
        const wave = Math.sin(x * 0.5 + elapsed * 2.0) * 0.7
          + Math.sin(x * 1.2 + elapsed * 3.0) * 0.35
          + Math.sin(x * 2.0 + elapsed * 4.5) * 0.15;
        posAttr.setY(i, surfaceHlOrigY.current[i] + wave);
      }
      posAttr.needsUpdate = true;
      const visible = Math.max(0, waterTop - SCREEN_BOT);
      surfaceHlMatRef.current.opacity = o * Math.min(visible * 0.15, 0.7);
    }

    // 기포 (아래에서 위로)
    if (bubbleRef.current) {
      const waterDepth = Math.max(1, waterTop - (centerY - WATER_H / 2));
      for (let i = 0; i < BUBBLE_COUNT; i++) {
        const d = bubbleData[i];
        const bubbleY = (centerY - WATER_H / 2) + (d.offset + t * d.speed) % waterDepth;
        const x = d.x + Math.sin(t * d.wobble + i) * 0.5;

        if (bubbleY > waterTop || rise < 0.3) {
          dummy.scale.setScalar(0);
        } else {
          dummy.scale.setScalar(d.size * 14);
        }
        dummy.position.set(x, bubbleY, 0.5);
        dummy.updateMatrix();
        bubbleRef.current.setMatrixAt(i, dummy.matrix);
      }
      bubbleRef.current.instanceMatrix.needsUpdate = true;
      (bubbleRef.current.material as THREE.MeshBasicMaterial).opacity = o * 0.4;
    }

    // 비네트
    if (vignetteRef.current) {
      const visible = Math.max(0, waterTop - SCREEN_BOT);
      vignetteRef.current.opacity = o * Math.min(visible * 0.06, 0.35);
    }
  });

  return (
    <group>
      {/* 물 — 높이 12, 아래가 진한 그라데이션 */}
      <mesh ref={waterRef} geometry={waterGeo} position={[0, START_CENTER_Y, 0]}>
        <meshBasicMaterial
          ref={waterMatRef}
          vertexColors
          transparent
          opacity={0}
          side={THREE.DoubleSide}
          depthWrite={false}
        />
      </mesh>

      {/* 수면 밴드 — 밝은 색으로 물결 강조 */}
      <mesh ref={surfaceRef} position={[0, SCREEN_BOT, 1]}>
        <planeGeometry args={[30, 3.0, 192, 4]} />
        <meshBasicMaterial
          ref={surfaceMatRef}
          color="#b8dcff"
          transparent
          opacity={0}
          side={THREE.DoubleSide}
          depthWrite={false}
        />
      </mesh>

      {/* 수면 하이라이트 — 흰색 거품 라인 */}
      <mesh ref={surfaceHlRef} position={[0, SCREEN_BOT, 1.1]}>
        <planeGeometry args={[30, 2.0, 192, 4]} />
        <meshBasicMaterial
          ref={surfaceHlMatRef}
          color="#ffffff"
          transparent
          opacity={0}
          side={THREE.DoubleSide}
          depthWrite={false}
        />
      </mesh>

      {/* 기포 */}
      <instancedMesh ref={bubbleRef} args={[undefined, undefined, BUBBLE_COUNT]}>
        <sphereGeometry args={[0.04, 8, 8]} />
        <meshBasicMaterial color="#bfdbfe" transparent opacity={0} />
      </instancedMesh>

      {/* 비네트 */}
      <mesh position={[0, 0, 0.5]}>
        <planeGeometry args={[80, 60]} />
        <meshBasicMaterial
          ref={vignetteRef}
          map={vignetteTex}
          transparent
          opacity={0}
          side={THREE.DoubleSide}
          depthWrite={false}
        />
      </mesh>
    </group>
  );
}
