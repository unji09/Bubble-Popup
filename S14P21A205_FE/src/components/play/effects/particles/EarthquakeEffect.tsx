import { useRef, useMemo } from "react";
import { useFrame, useThree } from "@react-three/fiber";
import * as THREE from "three";
import { useEffectLifecycle } from "../useEffectLifecycle";

const DEBRIS_COUNT = 120;
const DUST_COUNT = 70;
const CRACK_COUNT = 30;

interface Props {
  durationMs: number;
}

/** 균열 텍스처 */
function createCrackTexture(): THREE.CanvasTexture {
  const c = document.createElement("canvas");
  c.width = 64;
  c.height = 64;
  const ctx = c.getContext("2d")!;
  ctx.clearRect(0, 0, 64, 64);

  ctx.strokeStyle = "#5c4033";
  ctx.lineWidth = 2;
  // 메인 균열
  ctx.beginPath();
  ctx.moveTo(32, 4);
  ctx.lineTo(28, 18);
  ctx.lineTo(35, 32);
  ctx.lineTo(30, 48);
  ctx.lineTo(34, 60);
  ctx.stroke();
  // 가지
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(28, 18);
  ctx.lineTo(18, 24);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(35, 32);
  ctx.lineTo(48, 36);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(30, 48);
  ctx.lineTo(20, 52);
  ctx.stroke();

  const tex = new THREE.CanvasTexture(c);
  tex.needsUpdate = true;
  return tex;
}

export default function EarthquakeEffect({ durationMs }: Props) {
  const opacity = useEffectLifecycle(durationMs);
  const { camera } = useThree();

  const debrisRef = useRef<THREE.InstancedMesh>(null);
  const dustRef = useRef<THREE.InstancedMesh>(null);
  const crackRef = useRef<THREE.InstancedMesh>(null);
  const flashRef = useRef<THREE.MeshBasicMaterial>(null);
  const tintRef = useRef<THREE.MeshBasicMaterial>(null);
  const dummy = useMemo(() => new THREE.Object3D(), []);
  const originalCamPos = useRef(camera.position.clone());

  const crackTex = useMemo(() => createCrackTexture(), []);

  const debrisData = useMemo(() => {
    return Array.from({ length: DEBRIS_COUNT }, () => ({
      x: (Math.random() - 0.5) * 24,
      y: -5 + Math.random() * 2,
      z: (Math.random() - 0.5) * 6,
      vx: (Math.random() - 0.5) * 3,
      vy: 2 + Math.random() * 5,
      rotSpeed: 1 + Math.random() * 6,
      size: 0.05 + Math.random() * 0.12,
      // 색상 변형: 밝은/어두운 돌 조각
      colorShift: Math.random(),
    }));
  }, []);

  const dustData = useMemo(() => {
    return Array.from({ length: DUST_COUNT }, () => ({
      x: (Math.random() - 0.5) * 26,
      speed: 0.3 + Math.random() * 1,
      scale: 1.5 + Math.random() * 3.5,
      offset: Math.random() * 12,
      driftX: (Math.random() - 0.5) * 0.5,
    }));
  }, []);

  // 균열 파티클 (바닥에 갈라진 느낌)
  const crackData = useMemo(() => {
    return Array.from({ length: CRACK_COUNT }, () => ({
      x: (Math.random() - 0.5) * 20,
      y: -6 + Math.random() * 3,
      size: 0.3 + Math.random() * 0.6,
      rot: Math.random() * Math.PI * 2,
      delay: Math.random() * 2,
    }));
  }, []);

  useFrame(({ clock }) => {
    const t = clock.getElapsedTime();
    const o = opacity.current;

    // 카메라 흔들림 (더 강하게)
    const shakeIntensity = o * 0.25;
    const shakeFreq = 15;
    camera.position.x = originalCamPos.current.x + Math.sin(t * shakeFreq) * shakeIntensity;
    camera.position.y = originalCamPos.current.y + Math.cos(t * shakeFreq * 1.3) * shakeIntensity;

    // 잔해 (튀어오름)
    if (debrisRef.current) {
      for (let i = 0; i < DEBRIS_COUNT; i++) {
        const d = debrisData[i];
        const lt = t % 3.5;
        const gravity = 4;
        const y = d.y + d.vy * lt - 0.5 * gravity * lt * lt;
        const x = d.x + d.vx * lt;

        if (y < d.y - 1) {
          dummy.scale.setScalar(0);
        } else {
          dummy.position.set(x, y, d.z);
          dummy.rotation.set(t * d.rotSpeed, t * d.rotSpeed * 0.5, t * d.rotSpeed * 0.3);
          dummy.scale.setScalar(d.size * 1.5);
        }
        dummy.updateMatrix();
        debrisRef.current.setMatrixAt(i, dummy.matrix);
      }
      debrisRef.current.instanceMatrix.needsUpdate = true;
      (debrisRef.current.material as THREE.MeshBasicMaterial).opacity = o * 0.85;
    }

    // 먼지 구름
    if (dustRef.current) {
      for (let i = 0; i < DUST_COUNT; i++) {
        const d = dustData[i];
        const y = -5 + ((d.offset + t * d.speed) % 8);
        const x = d.x + Math.sin(t * d.driftX + i) * 1.5;
        dummy.position.set(x, y, -1);
        dummy.scale.setScalar(d.scale);
        dummy.updateMatrix();
        dustRef.current.setMatrixAt(i, dummy.matrix);
      }
      dustRef.current.instanceMatrix.needsUpdate = true;
      (dustRef.current.material as THREE.MeshBasicMaterial).opacity = o * 0.15;
    }

    // 균열 (시간차로 나타남)
    if (crackRef.current) {
      for (let i = 0; i < CRACK_COUNT; i++) {
        const d = crackData[i];
        const show = t > d.delay ? 1 : 0;
        dummy.position.set(d.x, d.y, 0.05);
        dummy.rotation.set(0, 0, d.rot);
        dummy.scale.setScalar(d.size * show);
        dummy.updateMatrix();
        crackRef.current.setMatrixAt(i, dummy.matrix);
      }
      crackRef.current.instanceMatrix.needsUpdate = true;
      (crackRef.current.material as THREE.MeshBasicMaterial).opacity = o * 0.6;
    }

    // 화면 흔들림 플래시
    if (flashRef.current) {
      const flash = Math.random() > 0.92 ? 0.08 : 0;
      flashRef.current.opacity = o * flash;
    }

    // 갈색 틴트
    if (tintRef.current) {
      tintRef.current.opacity = o * (0.06 + Math.sin(t * 4) * 0.02);
    }
  });

  return (
    <group>
      {/* 돌 잔해 */}
      <instancedMesh ref={debrisRef} args={[undefined, undefined, DEBRIS_COUNT]}>
        <boxGeometry args={[0.12, 0.12, 0.12]} />
        <meshBasicMaterial color="#5c4033" transparent opacity={0} />
      </instancedMesh>

      {/* 먼지 구름 */}
      <instancedMesh ref={dustRef} args={[undefined, undefined, DUST_COUNT]}>
        <sphereGeometry args={[1.2, 8, 8]} />
        <meshBasicMaterial color="#8b7355" transparent opacity={0} />
      </instancedMesh>

      {/* 균열 */}
      <instancedMesh ref={crackRef} args={[undefined, undefined, CRACK_COUNT]}>
        <planeGeometry args={[1, 1]} />
        <meshBasicMaterial
          map={crackTex}
          transparent
          opacity={0}
          side={THREE.DoubleSide}
          depthWrite={false}
        />
      </instancedMesh>

      {/* 플래시 */}
      <mesh position={[0, 0, -2]}>
        <planeGeometry args={[80, 60]} />
        <meshBasicMaterial ref={flashRef} color="#ffffff" transparent opacity={0} side={THREE.DoubleSide} />
      </mesh>

      {/* 갈색 틴트 */}
      <mesh position={[0, 0, -2.5]}>
        <planeGeometry args={[80, 60]} />
        <meshBasicMaterial ref={tintRef} color="#5c4033" transparent opacity={0} side={THREE.DoubleSide} />
      </mesh>
    </group>
  );
}
