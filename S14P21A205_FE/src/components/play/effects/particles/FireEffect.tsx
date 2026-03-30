import { useRef, useMemo } from "react";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";
import { useEffectLifecycle } from "../useEffectLifecycle";

const EMBER_COUNT = 250;
const SMOKE_COUNT = 40;
const ASH_COUNT = 60;

interface Props {
  durationMs: number;
}

export default function FireEffect({ durationMs }: Props) {
  const opacity = useEffectLifecycle(durationMs);

  const emberRef = useRef<THREE.InstancedMesh>(null);
  const smokeRef = useRef<THREE.InstancedMesh>(null);
  const ashRef = useRef<THREE.InstancedMesh>(null);
  const glowRef = useRef<THREE.MeshBasicMaterial>(null);
  const dummy = useMemo(() => new THREE.Object3D(), []);

  const emberData = useMemo(() => {
    return Array.from({ length: EMBER_COUNT }, () => ({
      x: (Math.random() - 0.5) * 50,
      speed: 1.5 + Math.random() * 4,
      wobbleFreq: 1 + Math.random() * 3,
      wobbleAmp: 0.4 + Math.random() * 0.8,
      offset: Math.random() * 18,
      size: 0.03 + Math.random() * 0.06,
      z: (Math.random() - 0.5) * 3,
    }));
  }, []);

  const smokeData = useMemo(() => {
    return Array.from({ length: SMOKE_COUNT }, () => ({
      x: (Math.random() - 0.5) * 46,
      speed: 0.5 + Math.random() * 1.2,
      scale: 0.8 + Math.random() * 2,
      offset: Math.random() * 14,
    }));
  }, []);

  // 재(ash) 파티클: 느리게 내려오며 흔들림
  const ashData = useMemo(() => {
    return Array.from({ length: ASH_COUNT }, () => ({
      x: (Math.random() - 0.5) * 50,
      speed: 0.3 + Math.random() * 0.8,
      offset: Math.random() * 16,
      swayFreq: 0.5 + Math.random() * 1.5,
      swayAmp: 0.8 + Math.random() * 2.0,
      rotSpeed: 0.5 + Math.random() * 2,
      size: 0.04 + Math.random() * 0.08,
      z: (Math.random() - 0.5) * 2,
    }));
  }, []);

  useFrame(({ clock }) => {
    const t = clock.getElapsedTime();
    const o = opacity.current;

    // 불씨 (위로 올라감)
    if (emberRef.current) {
      for (let i = 0; i < EMBER_COUNT; i++) {
        const d = emberData[i];
        const y = ((d.offset + t * d.speed) % 18) - 9;
        const x = d.x + Math.sin(t * d.wobbleFreq + i) * d.wobbleAmp;
        dummy.position.set(x, y, d.z);
        dummy.scale.setScalar(d.size / 0.04 * (0.6 + Math.sin(t * 5 + i) * 0.3));
        dummy.updateMatrix();
        emberRef.current.setMatrixAt(i, dummy.matrix);
      }
      emberRef.current.instanceMatrix.needsUpdate = true;
      (emberRef.current.material as THREE.MeshBasicMaterial).opacity = o * 0.85;
    }

    // 연기
    if (smokeRef.current) {
      for (let i = 0; i < SMOKE_COUNT; i++) {
        const d = smokeData[i];
        const y = ((d.offset + t * d.speed) % 16) - 8;
        const progress = (y + 8) / 16;
        const scale = d.scale * (1 + progress * 2.5);
        dummy.position.set(d.x, y, -1);
        dummy.scale.setScalar(scale);
        dummy.updateMatrix();
        smokeRef.current.setMatrixAt(i, dummy.matrix);
      }
      smokeRef.current.instanceMatrix.needsUpdate = true;
      (smokeRef.current.material as THREE.MeshBasicMaterial).opacity = o * 0.22;
    }

    // 재 (위에서 천천히 내려옴 + 흔들림)
    if (ashRef.current) {
      for (let i = 0; i < ASH_COUNT; i++) {
        const d = ashData[i];
        const rawY = (d.offset + t * d.speed) % 16;
        const y = 8 - rawY; // 위에서 아래로
        const x = d.x + Math.sin(t * d.swayFreq + i * 0.9) * d.swayAmp;

        dummy.position.set(x, y, d.z + 0.5);
        dummy.rotation.set(
          t * d.rotSpeed * 0.5,
          t * d.rotSpeed,
          Math.sin(t * d.rotSpeed * 0.3 + i) * 0.5,
        );
        dummy.scale.setScalar(d.size * 8);
        dummy.updateMatrix();
        ashRef.current.setMatrixAt(i, dummy.matrix);
      }
      ashRef.current.instanceMatrix.needsUpdate = true;
      (ashRef.current.material as THREE.MeshBasicMaterial).opacity = o * 0.5;
    }

    if (glowRef.current) {
      glowRef.current.opacity = o * (0.18 + Math.sin(t * 3) * 0.08);
    }
  });

  return (
    <group>
      {/* 불씨 */}
      <instancedMesh ref={emberRef} args={[undefined, undefined, EMBER_COUNT]}>
        <sphereGeometry args={[0.05, 6, 6]} />
        <meshBasicMaterial color="#ff4400" transparent opacity={0} />
      </instancedMesh>

      {/* 연기 */}
      <instancedMesh ref={smokeRef} args={[undefined, undefined, SMOKE_COUNT]}>
        <sphereGeometry args={[0.6, 8, 8]} />
        <meshBasicMaterial color="#3a3a3a" transparent opacity={0} />
      </instancedMesh>

      {/* 재 (회색 조각이 느리게 내려옴) */}
      <instancedMesh ref={ashRef} args={[undefined, undefined, ASH_COUNT]}>
        <planeGeometry args={[0.06, 0.04]} />
        <meshBasicMaterial color="#666666" transparent opacity={0} side={THREE.DoubleSide} />
      </instancedMesh>

      {/* 불빛 글로우 */}
      <mesh position={[0, 0, -2]}>
        <planeGeometry args={[80, 60]} />
        <meshBasicMaterial ref={glowRef} color="#cc2200" transparent opacity={0} side={THREE.DoubleSide} />
      </mesh>
    </group>
  );
}
