import { useRef, useMemo } from "react";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";
import { useEffectLifecycle } from "../useEffectLifecycle";

const CONFETTI_COUNT = 160;
const COLORS = ["#f43f5e", "#3b82f6", "#22c55e", "#eab308", "#a855f7", "#ec4899", "#06b6d4", "#f97316"];

interface Props {
  durationMs: number;
}

export default function ConfettiEffect({ durationMs }: Props) {
  const opacity = useEffectLifecycle(durationMs);

  const meshRef = useRef<THREE.InstancedMesh>(null);
  const dummy = useMemo(() => new THREE.Object3D(), []);

  const confettiData = useMemo(() => {
    const data: {
      x: number; z: number; speed: number; offset: number;
      rotX: number; rotY: number; rotZ: number;
      wobbleFreqX: number; wobbleAmpX: number;
      wobbleFreqZ: number; wobbleAmpZ: number;
      size: number; flipFreq: number;
    }[] = [];
    for (let i = 0; i < CONFETTI_COUNT; i++) {
      data.push({
        x: (Math.random() - 0.5) * 26,
        z: (Math.random() - 0.5) * 3,
        speed: 0.8 + Math.random() * 2,
        offset: Math.random() * 20,
        rotX: 1 + Math.random() * 3,
        rotY: 1 + Math.random() * 4,
        rotZ: 0.5 + Math.random() * 2,
        wobbleFreqX: 0.5 + Math.random() * 2,
        wobbleAmpX: 0.8 + Math.random() * 2.5,
        wobbleFreqZ: 0.3 + Math.random() * 1.5,
        wobbleAmpZ: 0.3 + Math.random() * 0.8,
        size: 0.8 + Math.random() * 1.2,
        flipFreq: 2 + Math.random() * 5,
      });
    }
    return data;
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
    const t = clock.getElapsedTime();
    const o = opacity.current;

    if (meshRef.current) {
      for (let i = 0; i < CONFETTI_COUNT; i++) {
        const d = confettiData[i];
        // 낙하: 위에서 아래로 + 감속 느낌 (sin으로 속도 변화)
        const rawProgress = (d.offset + t * d.speed) % 20;
        const y = 10 - rawProgress;

        // 좌우 흔들림 (공기 저항 느낌)
        const x = d.x + Math.sin(t * d.wobbleFreqX + i * 0.7) * d.wobbleAmpX;
        // 앞뒤 흔들림
        const z = d.z + Math.sin(t * d.wobbleFreqZ + i * 1.3) * d.wobbleAmpZ;

        dummy.position.set(x, y, z);
        // 3축 회전 + 펄럭임 (flipFreq로 종이 뒤집히는 느낌)
        dummy.rotation.set(
          t * d.rotX + Math.sin(t * d.flipFreq) * 1.5,
          t * d.rotY,
          t * d.rotZ + Math.cos(t * d.flipFreq * 0.7) * 0.8,
        );
        dummy.scale.setScalar(d.size);
        dummy.updateMatrix();
        meshRef.current.setMatrixAt(i, dummy.matrix);
      }
      meshRef.current.instanceMatrix.needsUpdate = true;
      (meshRef.current.material as THREE.MeshBasicMaterial).opacity = o * 0.88;
    }
  });

  return (
    <instancedMesh ref={meshRef} args={[undefined, undefined, CONFETTI_COUNT]}>
      <boxGeometry args={[0.12, 0.07, 0.01]} />
      <meshBasicMaterial transparent opacity={0} vertexColors side={THREE.DoubleSide} />
      <instancedBufferAttribute attach="geometry-attributes-color" args={[colorArray, 3]} />
    </instancedMesh>
  );
}
