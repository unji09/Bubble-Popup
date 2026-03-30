import { useRef, useMemo } from "react";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";
import { useEffectLifecycle } from "../useEffectLifecycle";

const FOG_COUNT = 30;
const VIRUS_COUNT = 30;
const SPORE_COUNT = 50;

interface Props {
  durationMs: number;
}

/** 바이러스 모양 텍스처 (원형 몸체 + 돌기) */
function createVirusTexture(): THREE.CanvasTexture {
  const c = document.createElement("canvas");
  c.width = 64;
  c.height = 64;
  const ctx = c.getContext("2d")!;
  ctx.clearRect(0, 0, 64, 64);

  // 글로우
  const grad = ctx.createRadialGradient(32, 32, 0, 32, 32, 28);
  grad.addColorStop(0, "rgba(34, 197, 94, 0.6)");
  grad.addColorStop(0.5, "rgba(34, 197, 94, 0.3)");
  grad.addColorStop(1, "rgba(34, 197, 94, 0)");
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, 64, 64);

  // 몸체
  ctx.fillStyle = "#22c55e";
  ctx.beginPath();
  ctx.arc(32, 32, 12, 0, Math.PI * 2);
  ctx.fill();

  // 돌기 (스파이크)
  ctx.strokeStyle = "#16a34a";
  ctx.lineWidth = 2;
  for (let i = 0; i < 10; i++) {
    const angle = (i / 10) * Math.PI * 2;
    const innerR = 12;
    const outerR = 18 + Math.random() * 4;
    ctx.beginPath();
    ctx.moveTo(32 + Math.cos(angle) * innerR, 32 + Math.sin(angle) * innerR);
    ctx.lineTo(32 + Math.cos(angle) * outerR, 32 + Math.sin(angle) * outerR);
    ctx.stroke();
    // 돌기 끝 동그라미
    ctx.fillStyle = "#4ade80";
    ctx.beginPath();
    ctx.arc(32 + Math.cos(angle) * outerR, 32 + Math.sin(angle) * outerR, 2.5, 0, Math.PI * 2);
    ctx.fill();
  }

  const tex = new THREE.CanvasTexture(c);
  tex.needsUpdate = true;
  return tex;
}

export default function VirusFogEffect({ durationMs }: Props) {
  const opacity = useEffectLifecycle(durationMs, 800, 2000);

  const fogRef = useRef<THREE.InstancedMesh>(null);
  const virusRef = useRef<THREE.InstancedMesh>(null);
  const sporeRef = useRef<THREE.InstancedMesh>(null);
  const tintRef = useRef<THREE.MeshBasicMaterial>(null);
  const dummy = useMemo(() => new THREE.Object3D(), []);

  const virusTex = useMemo(() => createVirusTexture(), []);

  const fogData = useMemo(() => {
    return Array.from({ length: FOG_COUNT }, () => ({
      x: (Math.random() - 0.5) * 26,
      y: (Math.random() - 0.5) * 16,
      z: -2 + Math.random() * 2,
      driftX: 0.2 + Math.random() * 0.5,
      driftY: 0.1 + Math.random() * 0.3,
      scale: 2.0 + Math.random() * 3,
      phase: Math.random() * Math.PI * 2,
    }));
  }, []);

  const virusData = useMemo(() => {
    return Array.from({ length: VIRUS_COUNT }, () => ({
      x: (Math.random() - 0.5) * 20,
      y: (Math.random() - 0.5) * 12,
      z: -3 + Math.random() * 8,
      driftX: 0.2 + Math.random() * 0.6,
      driftY: 0.15 + Math.random() * 0.4,
      rotSpeed: 0.5 + Math.random() * 2,
      size: 0.35 + Math.random() * 0.5,
      phase: Math.random() * Math.PI * 2,
    }));
  }, []);

  // 미세 포자 (작은 녹색 입자가 떠다님)
  const sporeData = useMemo(() => {
    return Array.from({ length: SPORE_COUNT }, () => ({
      x: (Math.random() - 0.5) * 24,
      y: (Math.random() - 0.5) * 14,
      z: (Math.random() - 0.5) * 4,
      driftX: (Math.random() - 0.5) * 0.8,
      driftY: (Math.random() - 0.5) * 0.6,
      twinkleFreq: 2 + Math.random() * 6,
      size: 0.025 + Math.random() * 0.05,
      phase: Math.random() * Math.PI * 2,
    }));
  }, []);

  useFrame(({ clock }) => {
    const t = clock.getElapsedTime();
    const o = opacity.current;

    // 독안개 (보라/녹색 큰 구체)
    if (fogRef.current) {
      for (let i = 0; i < FOG_COUNT; i++) {
        const d = fogData[i];
        dummy.position.set(
          d.x + Math.sin(t * d.driftX + d.phase) * 3,
          d.y + Math.cos(t * d.driftY + d.phase) * 2,
          d.z,
        );
        // 안개가 부드럽게 커졌다 작아졌다
        const breathe = d.scale * (1 + Math.sin(t * 0.5 + d.phase) * 0.15);
        dummy.scale.setScalar(breathe);
        dummy.updateMatrix();
        fogRef.current.setMatrixAt(i, dummy.matrix);
      }
      fogRef.current.instanceMatrix.needsUpdate = true;
      (fogRef.current.material as THREE.MeshBasicMaterial).opacity = o * 0.1;
    }

    // 바이러스 (텍스처 — 화면 전체에 균등 분포, 느리게 떠다님)
    if (virusRef.current) {
      for (let i = 0; i < VIRUS_COUNT; i++) {
        const d = virusData[i];
        dummy.position.set(
          d.x + Math.sin(t * d.driftX + d.phase) * 2.5,
          d.y + Math.cos(t * d.driftY + d.phase) * 1.5,
          d.z,
        );
        dummy.rotation.set(0, 0, t * d.rotSpeed);
        dummy.scale.setScalar(d.size);
        dummy.updateMatrix();
        virusRef.current.setMatrixAt(i, dummy.matrix);
      }
      virusRef.current.instanceMatrix.needsUpdate = true;
      (virusRef.current.material as THREE.MeshBasicMaterial).opacity = o * 0.65;
    }

    // 미세 포자
    if (sporeRef.current) {
      for (let i = 0; i < SPORE_COUNT; i++) {
        const d = sporeData[i];
        const twinkle = 0.5 + 0.5 * Math.abs(Math.sin(t * d.twinkleFreq + d.phase));
        dummy.position.set(
          d.x + Math.sin(t * d.driftX + i * 0.5) * 2,
          d.y + Math.cos(t * d.driftY + i * 0.3) * 1.5,
          d.z,
        );
        dummy.scale.setScalar(d.size * twinkle * 10);
        dummy.updateMatrix();
        sporeRef.current.setMatrixAt(i, dummy.matrix);
      }
      sporeRef.current.instanceMatrix.needsUpdate = true;
      (sporeRef.current.material as THREE.MeshBasicMaterial).opacity = o * 0.7;
    }

    // 전체 녹색 틴트
    if (tintRef.current) {
      tintRef.current.opacity = o * (0.06 + Math.sin(t * 1.5) * 0.02);
    }
  });

  return (
    <group>
      {/* 독안개 */}
      <instancedMesh ref={fogRef} args={[undefined, undefined, FOG_COUNT]}>
        <sphereGeometry args={[1.2, 12, 12]} />
        <meshBasicMaterial color="#7e3aab" transparent opacity={0} depthWrite={false} />
      </instancedMesh>

      {/* 바이러스 (텍스처) */}
      <instancedMesh ref={virusRef} args={[undefined, undefined, VIRUS_COUNT]}>
        <planeGeometry args={[1, 1]} />
        <meshBasicMaterial
          map={virusTex}
          transparent
          opacity={0}
          side={THREE.DoubleSide}
          depthWrite={false}
        />
      </instancedMesh>

      {/* 미세 포자 */}
      <instancedMesh ref={sporeRef} args={[undefined, undefined, SPORE_COUNT]}>
        <sphereGeometry args={[0.02, 6, 6]} />
        <meshBasicMaterial color="#4ade80" transparent opacity={0} />
      </instancedMesh>

      {/* 녹색 틴트 */}
      <mesh position={[0, 0, -2]}>
        <planeGeometry args={[80, 60]} />
        <meshBasicMaterial ref={tintRef} color="#166534" transparent opacity={0} side={THREE.DoubleSide} />
      </mesh>
    </group>
  );
}
