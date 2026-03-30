import { useRef, useMemo } from "react";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";
import { useEffectLifecycle } from "../useEffectLifecycle";

const STAR_COUNT = 60;
const HEART_COUNT = 30;
const SPARKLE_COUNT = 80;

interface Props {
  durationMs: number;
}

/** 별 모양 캔버스 텍스처 */
function createStarTexture(): THREE.CanvasTexture {
  const c = document.createElement("canvas");
  c.width = 64;
  c.height = 64;
  const ctx = c.getContext("2d")!;
  ctx.clearRect(0, 0, 64, 64);

  // 글로우
  const grad = ctx.createRadialGradient(32, 32, 0, 32, 32, 32);
  grad.addColorStop(0, "rgba(255, 230, 100, 0.9)");
  grad.addColorStop(0.3, "rgba(255, 215, 0, 0.5)");
  grad.addColorStop(1, "rgba(255, 215, 0, 0)");
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, 64, 64);

  // 별 모양
  ctx.fillStyle = "#fff8dc";
  ctx.beginPath();
  for (let i = 0; i < 5; i++) {
    const angle = (i * 4 * Math.PI) / 5 - Math.PI / 2;
    const x = 32 + Math.cos(angle) * 14;
    const y = 32 + Math.sin(angle) * 14;
    if (i === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  }
  ctx.closePath();
  ctx.fill();

  const tex = new THREE.CanvasTexture(c);
  tex.needsUpdate = true;
  return tex;
}

/** 하트 모양 캔버스 텍스처 */
function createHeartTexture(): THREE.CanvasTexture {
  const c = document.createElement("canvas");
  c.width = 64;
  c.height = 64;
  const ctx = c.getContext("2d")!;
  ctx.clearRect(0, 0, 64, 64);

  // 글로우
  const grad = ctx.createRadialGradient(32, 34, 0, 32, 34, 30);
  grad.addColorStop(0, "rgba(255, 105, 180, 0.8)");
  grad.addColorStop(0.5, "rgba(244, 114, 182, 0.3)");
  grad.addColorStop(1, "rgba(244, 114, 182, 0)");
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, 64, 64);

  // 하트
  ctx.fillStyle = "#ff69b4";
  ctx.beginPath();
  ctx.moveTo(32, 50);
  ctx.bezierCurveTo(10, 36, 6, 18, 20, 14);
  ctx.bezierCurveTo(28, 10, 32, 18, 32, 22);
  ctx.bezierCurveTo(32, 18, 36, 10, 44, 14);
  ctx.bezierCurveTo(58, 18, 54, 36, 32, 50);
  ctx.fill();

  const tex = new THREE.CanvasTexture(c);
  tex.needsUpdate = true;
  return tex;
}

/**
 * 연예인 등장 이펙트 (화려 버전)
 * - 스포트라이트 2개 (좌/우에서 교차)
 * - 금색 별 + 핑크 하트 (텍스처로 또렷하게)
 * - 작은 스파클 파티클 (반짝이 가루)
 * - 화면 하단에서 위로 화려하게 올라옴
 */
export default function StarBurstEffect({ durationMs }: Props) {
  const opacity = useEffectLifecycle(durationMs);

  const starRef = useRef<THREE.InstancedMesh>(null);
  const heartRef = useRef<THREE.InstancedMesh>(null);
  const sparkleRef = useRef<THREE.InstancedMesh>(null);
  const pivot1Ref = useRef<THREE.Group>(null);
  const pivot2Ref = useRef<THREE.Group>(null);
  const spotlightMat1Ref = useRef<THREE.MeshBasicMaterial>(null);
  const spotlightMat2Ref = useRef<THREE.MeshBasicMaterial>(null);
  const glowRef = useRef<THREE.MeshBasicMaterial>(null);
  const dummy = useMemo(() => new THREE.Object3D(), []);

  const starTex = useMemo(() => createStarTexture(), []);
  const heartTex = useMemo(() => createHeartTexture(), []);

  const starData = useMemo(() => {
    return Array.from({ length: STAR_COUNT }, () => ({
      baseX: (Math.random() - 0.5) * 50,
      speed: 0.5 + Math.random() * 1.0,
      offset: Math.random() * 12,
      twinkleFreq: 3 + Math.random() * 6,
      twinklePhase: Math.random() * Math.PI * 2,
      size: 0.15 + Math.random() * 0.25,
      driftX: (Math.random() - 0.5) * 0.8,
      rotSpeed: (Math.random() - 0.5) * 2,
    }));
  }, []);

  const heartData = useMemo(() => {
    return Array.from({ length: HEART_COUNT }, () => ({
      baseX: (Math.random() - 0.5) * 44,
      speed: 0.3 + Math.random() * 0.7,
      offset: Math.random() * 12,
      wobbleFreq: 1 + Math.random() * 2,
      wobbleAmp: 0.5 + Math.random() * 1.0,
      size: 0.12 + Math.random() * 0.2,
      rotAmp: 0.1 + Math.random() * 0.2,
    }));
  }, []);

  const sparkleData = useMemo(() => {
    return Array.from({ length: SPARKLE_COUNT }, () => ({
      baseX: (Math.random() - 0.5) * 50,
      speed: 0.8 + Math.random() * 1.5,
      offset: Math.random() * 10,
      twinkleFreq: 5 + Math.random() * 10,
      size: 0.02 + Math.random() * 0.04,
      driftX: (Math.random() - 0.5) * 1.2,
    }));
  }, []);

  useFrame(({ clock }) => {
    const t = clock.getElapsedTime();
    const o = opacity.current;

    // 스포트라이트 2개: 상단 고정, 각각 독립적으로 바닥 훑기
    if (pivot1Ref.current && spotlightMat1Ref.current) {
      // 좌측: 느린 스윕 (독립 주기)
      pivot1Ref.current.rotation.z = Math.sin(t * 0.6) * 0.35;
      const pulse = 0.14 + 0.06 * Math.sin(t * 2.5);
      spotlightMat1Ref.current.opacity = o * pulse;
    }
    if (pivot2Ref.current && spotlightMat2Ref.current) {
      // 우측: 다른 속도/위상으로 스윕 (독립 주기)
      pivot2Ref.current.rotation.z = Math.sin(t * 0.45 + 2.5) * 0.35;
      const pulse = 0.12 + 0.05 * Math.sin(t * 2.5 + 1.5);
      spotlightMat2Ref.current.opacity = o * pulse;
    }

    // 금색 별
    if (starRef.current) {
      for (let i = 0; i < STAR_COUNT; i++) {
        const d = starData[i];
        const cycleLen = 12;
        const progress = (d.offset + t * d.speed) % cycleLen;
        const y = -5 + progress * 0.85;
        const x = d.baseX + Math.sin(t * 0.5 + i) * d.driftX;

        const fadeIn = Math.min(progress / 1.5, 1);
        const fadeOut = Math.min((cycleLen - progress) / 2, 1);
        const fade = fadeIn * fadeOut;

        const twinkle = 0.5 + 0.5 * Math.abs(Math.sin(t * d.twinkleFreq + d.twinklePhase));

        dummy.position.set(x, y, 0.1);
        dummy.rotation.set(0, 0, t * d.rotSpeed + i);
        dummy.scale.setScalar(d.size * twinkle * fade);
        dummy.updateMatrix();
        starRef.current.setMatrixAt(i, dummy.matrix);
      }
      starRef.current.instanceMatrix.needsUpdate = true;
      (starRef.current.material as THREE.MeshBasicMaterial).opacity = o * 0.9;
    }

    // 핑크 하트
    if (heartRef.current) {
      for (let i = 0; i < HEART_COUNT; i++) {
        const d = heartData[i];
        const cycleLen = 14;
        const progress = (d.offset + t * d.speed) % cycleLen;
        const y = -5 + progress * 0.7;
        const x = d.baseX + Math.sin(t * d.wobbleFreq + i * 1.3) * d.wobbleAmp;

        const fadeIn = Math.min(progress / 2, 1);
        const fadeOut = Math.min((cycleLen - progress) / 2, 1);
        const fade = fadeIn * fadeOut;

        dummy.position.set(x, y, 0.2);
        dummy.rotation.set(0, 0, Math.sin(t * 0.8 + i) * d.rotAmp);
        dummy.scale.setScalar(d.size * fade);
        dummy.updateMatrix();
        heartRef.current.setMatrixAt(i, dummy.matrix);
      }
      heartRef.current.instanceMatrix.needsUpdate = true;
      (heartRef.current.material as THREE.MeshBasicMaterial).opacity = o * 0.8;
    }

    // 스파클 (반짝이 가루)
    if (sparkleRef.current) {
      for (let i = 0; i < SPARKLE_COUNT; i++) {
        const d = sparkleData[i];
        const cycleLen = 8;
        const progress = (d.offset + t * d.speed) % cycleLen;
        const y = -5 + progress * 1.2;
        const x = d.baseX + Math.sin(t * 1.5 + i * 0.7) * d.driftX;

        const fade = Math.min(progress / 1, 1) * Math.min((cycleLen - progress) / 1.5, 1);
        const twinkle = Math.abs(Math.sin(t * d.twinkleFreq + i * 2.3));

        dummy.position.set(x, y, 0.3);
        dummy.rotation.set(0, 0, t * 3 + i);
        dummy.scale.setScalar(d.size * twinkle * fade * 8);
        dummy.updateMatrix();
        sparkleRef.current.setMatrixAt(i, dummy.matrix);
      }
      sparkleRef.current.instanceMatrix.needsUpdate = true;
      (sparkleRef.current.material as THREE.MeshBasicMaterial).opacity = o * 0.7;
    }

    // 배경 글로우
    if (glowRef.current) {
      glowRef.current.opacity = o * (0.05 + 0.02 * Math.sin(t * 1.5));
    }
  });

  return (
    <group>
      {/* 서치라이트 빔 - 좌측 (상단 고정, 회전으로 스윕) */}
      <group ref={pivot1Ref} position={[-3, 8, -0.5]}>
        {/* cone을 아래로 오프셋해서 pivot(그룹 원점)이 꼭대기가 됨 */}
        <mesh position={[0, -6, 0]}>
          <coneGeometry args={[4, 14, 32, 1, true]} />
          <meshBasicMaterial
            ref={spotlightMat1Ref}
            color="#fff8e7"
            transparent
            opacity={0}
            side={THREE.DoubleSide}
            depthWrite={false}
            blending={THREE.AdditiveBlending}
          />
        </mesh>
      </group>

      {/* 서치라이트 빔 - 우측 (상단 고정, 회전으로 스윕) */}
      <group ref={pivot2Ref} position={[3, 8, -0.5]}>
        <mesh position={[0, -6, 0]}>
          <coneGeometry args={[4, 14, 32, 1, true]} />
          <meshBasicMaterial
            ref={spotlightMat2Ref}
            color="#ffe8f0"
            transparent
            opacity={0}
            side={THREE.DoubleSide}
            depthWrite={false}
            blending={THREE.AdditiveBlending}
          />
        </mesh>
      </group>

      {/* 금색 별 (텍스처) */}
      <instancedMesh ref={starRef} args={[undefined, undefined, STAR_COUNT]}>
        <planeGeometry args={[1, 1]} />
        <meshBasicMaterial
          map={starTex}
          transparent
          opacity={0}
          depthWrite={false}
          blending={THREE.AdditiveBlending}
        />
      </instancedMesh>

      {/* 핑크 하트 (텍스처) */}
      <instancedMesh ref={heartRef} args={[undefined, undefined, HEART_COUNT]}>
        <planeGeometry args={[1, 1]} />
        <meshBasicMaterial
          map={heartTex}
          transparent
          opacity={0}
          depthWrite={false}
          blending={THREE.AdditiveBlending}
        />
      </instancedMesh>

      {/* 스파클 반짝이 가루 */}
      <instancedMesh ref={sparkleRef} args={[undefined, undefined, SPARKLE_COUNT]}>
        <octahedronGeometry args={[0.03, 0]} />
        <meshBasicMaterial
          color="#fffacd"
          transparent
          opacity={0}
          depthWrite={false}
          blending={THREE.AdditiveBlending}
        />
      </instancedMesh>

      {/* 배경 글로우 */}
      <mesh position={[0, -1, -3]}>
        <planeGeometry args={[80, 60]} />
        <meshBasicMaterial
          ref={glowRef}
          color="#f9a8d4"
          transparent
          opacity={0}
          side={THREE.DoubleSide}
          depthWrite={false}
        />
      </mesh>
    </group>
  );
}
