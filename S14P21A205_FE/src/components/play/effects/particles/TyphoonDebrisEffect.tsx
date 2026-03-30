import { useRef, useMemo } from "react";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";
import { useEffectLifecycle } from "../useEffectLifecycle";

const LEAF_COUNT = 35;
const BRANCH_COUNT = 15;
const TRASH_COUNT = 12;
const DUST_COUNT = 50;

interface Props {
  durationMs: number;
}

/** 나뭇잎 텍스처 */
function createLeafTexture(baseColor: string, veinColor: string): THREE.CanvasTexture {
  const c = document.createElement("canvas");
  c.width = 128;
  c.height = 128;
  const ctx = c.getContext("2d")!;
  ctx.clearRect(0, 0, 128, 128);

  const leafPath = () => {
    ctx.beginPath();
    ctx.moveTo(64, 4);
    ctx.bezierCurveTo(78, 10, 88, 18, 96, 28);
    ctx.bezierCurveTo(92, 30, 98, 36, 104, 40);
    ctx.bezierCurveTo(100, 44, 106, 50, 108, 56);
    ctx.bezierCurveTo(102, 58, 104, 64, 100, 72);
    ctx.bezierCurveTo(96, 76, 92, 82, 86, 88);
    ctx.bezierCurveTo(80, 94, 74, 100, 68, 108);
    ctx.bezierCurveTo(66, 116, 64, 122, 64, 124);
    ctx.bezierCurveTo(64, 122, 62, 116, 60, 108);
    ctx.bezierCurveTo(54, 100, 48, 94, 42, 88);
    ctx.bezierCurveTo(36, 82, 32, 76, 28, 72);
    ctx.bezierCurveTo(24, 64, 26, 58, 20, 56);
    ctx.bezierCurveTo(22, 50, 28, 44, 24, 40);
    ctx.bezierCurveTo(30, 36, 36, 30, 32, 28);
    ctx.bezierCurveTo(40, 18, 50, 10, 64, 4);
    ctx.closePath();
  };

  ctx.save();
  leafPath();
  ctx.clip();

  const r = parseInt(baseColor.slice(1, 3), 16) || 74;
  const g = parseInt(baseColor.slice(3, 5), 16) || 222;
  const b = parseInt(baseColor.slice(5, 7), 16) || 128;
  const grad = ctx.createRadialGradient(64, 55, 8, 64, 55, 60);
  grad.addColorStop(0, `rgba(${Math.min(r + 40, 255)}, ${Math.min(g + 30, 255)}, ${Math.min(b + 20, 255)}, 1)`);
  grad.addColorStop(0.6, baseColor);
  grad.addColorStop(1, `rgba(${Math.max(r - 30, 0)}, ${Math.max(g - 30, 0)}, ${Math.max(b - 20, 0)}, 0.85)`);
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, 128, 128);

  ctx.strokeStyle = veinColor;
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(64, 10);
  ctx.quadraticCurveTo(63, 60, 64, 118);
  ctx.stroke();

  ctx.lineWidth = 1.2;
  for (let i = 0; i < 6; i++) {
    const y = 22 + i * 15;
    const spread = 16 + i * 2;
    ctx.beginPath();
    ctx.moveTo(64, y);
    ctx.quadraticCurveTo(64 - spread * 0.5, y + 2, 64 - spread, y + 10);
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(64, y);
    ctx.quadraticCurveTo(64 + spread * 0.5, y + 2, 64 + spread, y + 10);
    ctx.stroke();
  }

  ctx.restore();

  const tex = new THREE.CanvasTexture(c);
  tex.needsUpdate = true;
  return tex;
}

/** 나뭇가지 텍스처 */
function createBranchTexture(): THREE.CanvasTexture {
  const c = document.createElement("canvas");
  c.width = 128;
  c.height = 32;
  const ctx = c.getContext("2d")!;
  ctx.clearRect(0, 0, 128, 32);

  // 메인 가지
  ctx.strokeStyle = "#5c3a1e";
  ctx.lineWidth = 6;
  ctx.lineCap = "round";
  ctx.beginPath();
  ctx.moveTo(4, 16);
  ctx.quadraticCurveTo(40, 12, 80, 18);
  ctx.quadraticCurveTo(100, 20, 124, 14);
  ctx.stroke();

  // 작은 곁가지
  ctx.lineWidth = 2.5;
  ctx.strokeStyle = "#7a5030";
  ctx.beginPath();
  ctx.moveTo(35, 14);
  ctx.lineTo(28, 4);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(65, 17);
  ctx.lineTo(72, 28);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(95, 18);
  ctx.lineTo(88, 6);
  ctx.stroke();

  const tex = new THREE.CanvasTexture(c);
  tex.needsUpdate = true;
  return tex;
}

/** 종이/쓰레기 텍스처 */
function createTrashTexture(): THREE.CanvasTexture {
  const c = document.createElement("canvas");
  c.width = 64;
  c.height = 64;
  const ctx = c.getContext("2d")!;
  ctx.clearRect(0, 0, 64, 64);

  // 구겨진 종이/비닐
  ctx.fillStyle = "#e8e0d0";
  ctx.beginPath();
  ctx.moveTo(8, 12);
  ctx.lineTo(28, 6);
  ctx.lineTo(52, 10);
  ctx.lineTo(58, 30);
  ctx.lineTo(54, 52);
  ctx.lineTo(32, 58);
  ctx.lineTo(10, 50);
  ctx.lineTo(4, 28);
  ctx.closePath();
  ctx.fill();

  // 구김 선
  ctx.strokeStyle = "#c8bfb0";
  ctx.lineWidth = 0.8;
  ctx.beginPath();
  ctx.moveTo(12, 20);
  ctx.lineTo(50, 35);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(25, 10);
  ctx.lineTo(35, 52);
  ctx.stroke();

  const tex = new THREE.CanvasTexture(c);
  tex.needsUpdate = true;
  return tex;
}

interface OrbitData {
  radius: number;
  angle: number;
  orbitSpeed: number;
  y: number;
  yWobbleFreq: number;
  yWobbleAmp: number;
  radiusWobbleFreq: number;
  radiusWobbleAmp: number;
  rotSpeed: number;
  flipSpeed: number;
  size: number;
  zOffset: number;
}

function makeOrbitData(count: number, sizeMin: number, sizeMax: number): OrbitData[] {
  return Array.from({ length: count }, () => ({
    radius: 1.5 + Math.random() * 8,
    angle: Math.random() * Math.PI * 2,
    orbitSpeed: 1.2 + Math.random() * 2.0,
    y: (Math.random() - 0.5) * 10,
    yWobbleFreq: 0.5 + Math.random() * 1.5,
    yWobbleAmp: 0.5 + Math.random() * 1.5,
    radiusWobbleFreq: 0.3 + Math.random() * 0.8,
    radiusWobbleAmp: 0.5 + Math.random() * 2.0,
    rotSpeed: 2 + Math.random() * 6,
    flipSpeed: 3 + Math.random() * 5,
    size: sizeMin + Math.random() * (sizeMax - sizeMin),
    zOffset: (Math.random() - 0.5) * 3,
  }));
}

function updateOrbit(
  ref: THREE.InstancedMesh,
  data: OrbitData[],
  count: number,
  dummy: THREE.Object3D,
  t: number,
  o: number,
  opacityMul: number,
) {
  for (let i = 0; i < count; i++) {
    const d = data[i];
    const currentAngle = d.angle + t * d.orbitSpeed * (3 / (d.radius + 1));
    const currentRadius = d.radius + Math.sin(t * d.radiusWobbleFreq + i) * d.radiusWobbleAmp;

    const x = Math.cos(currentAngle) * currentRadius;
    const z = Math.sin(currentAngle) * currentRadius * 0.4 + d.zOffset;
    const y = d.y + Math.sin(t * d.yWobbleFreq + i * 0.7) * d.yWobbleAmp;

    dummy.position.set(x, y, z);
    dummy.rotation.set(
      Math.sin(t * d.flipSpeed + i * 0.7) * 0.8,
      currentAngle + t * d.rotSpeed * 0.3,
      Math.sin(t * d.rotSpeed * 0.3 + i * 0.5) * 1,
    );
    dummy.scale.setScalar(d.size);
    dummy.updateMatrix();
    ref.setMatrixAt(i, dummy.matrix);
  }
  ref.instanceMatrix.needsUpdate = true;
  (ref.material as THREE.MeshBasicMaterial).opacity = o * opacityMul;
}

export default function TyphoonDebrisEffect({ durationMs }: Props) {
  const opacity = useEffectLifecycle(durationMs);

  const leafRef = useRef<THREE.InstancedMesh>(null);
  const branchRef = useRef<THREE.InstancedMesh>(null);
  const trashRef = useRef<THREE.InstancedMesh>(null);
  const dustRef = useRef<THREE.InstancedMesh>(null);
  const tintRef = useRef<THREE.MeshBasicMaterial>(null);
  const dummy = useMemo(() => new THREE.Object3D(), []);

  const leafTex = useMemo(() => createLeafTexture("#4ade80", "#166534"), []);
  const branchTex = useMemo(() => createBranchTexture(), []);
  const trashTex = useMemo(() => createTrashTexture(), []);

  const leafData = useMemo(() => makeOrbitData(LEAF_COUNT, 0.3, 0.6), []);
  const branchData = useMemo(() => makeOrbitData(BRANCH_COUNT, 0.4, 0.8), []);
  const trashData = useMemo(() => makeOrbitData(TRASH_COUNT, 0.2, 0.5), []);
  const dustData = useMemo(() => makeOrbitData(DUST_COUNT, 0.05, 0.15), []);

  useFrame(({ clock }) => {
    const t = clock.getElapsedTime();
    const o = opacity.current;

    if (leafRef.current) updateOrbit(leafRef.current, leafData, LEAF_COUNT, dummy, t, o, 0.85);
    if (branchRef.current) updateOrbit(branchRef.current, branchData, BRANCH_COUNT, dummy, t, o, 0.8);
    if (trashRef.current) updateOrbit(trashRef.current, trashData, TRASH_COUNT, dummy, t, o, 0.75);

    // 먼지: 더 빠르고 작게
    if (dustRef.current) {
      for (let i = 0; i < DUST_COUNT; i++) {
        const d = dustData[i];
        const currentAngle = d.angle + t * d.orbitSpeed * 1.5 * (3 / (d.radius + 1));
        const currentRadius = d.radius + Math.sin(t * d.radiusWobbleFreq + i) * d.radiusWobbleAmp;
        const x = Math.cos(currentAngle) * currentRadius;
        const z = Math.sin(currentAngle) * currentRadius * 0.3 + d.zOffset;
        const y = d.y + Math.sin(t * d.yWobbleFreq + i * 0.7) * d.yWobbleAmp;
        dummy.position.set(x, y, z);
        dummy.scale.setScalar(d.size);
        dummy.updateMatrix();
        dustRef.current.setMatrixAt(i, dummy.matrix);
      }
      dustRef.current.instanceMatrix.needsUpdate = true;
      (dustRef.current.material as THREE.MeshBasicMaterial).opacity = o * 0.4;
    }

    if (tintRef.current) {
      tintRef.current.opacity = o * (0.08 + Math.sin(t * 3) * 0.03);
    }
  });

  return (
    <group>
      {/* 나뭇잎 */}
      <instancedMesh ref={leafRef} args={[undefined, undefined, LEAF_COUNT]}>
        <planeGeometry args={[0.7, 0.7]} />
        <meshBasicMaterial
          map={leafTex}
          transparent
          opacity={0}
          side={THREE.DoubleSide}
          alphaTest={0.1}
        />
      </instancedMesh>

      {/* 나뭇가지 */}
      <instancedMesh ref={branchRef} args={[undefined, undefined, BRANCH_COUNT]}>
        <planeGeometry args={[1.0, 0.25]} />
        <meshBasicMaterial
          map={branchTex}
          transparent
          opacity={0}
          side={THREE.DoubleSide}
          alphaTest={0.1}
        />
      </instancedMesh>

      {/* 종이/쓰레기 */}
      <instancedMesh ref={trashRef} args={[undefined, undefined, TRASH_COUNT]}>
        <planeGeometry args={[0.4, 0.4]} />
        <meshBasicMaterial
          map={trashTex}
          transparent
          opacity={0}
          side={THREE.DoubleSide}
          alphaTest={0.1}
        />
      </instancedMesh>

      {/* 먼지 입자 */}
      <instancedMesh ref={dustRef} args={[undefined, undefined, DUST_COUNT]}>
        <sphereGeometry args={[0.04, 6, 6]} />
        <meshBasicMaterial color="#8b7355" transparent opacity={0} />
      </instancedMesh>

      {/* 어두운 틴트 (폭풍 느낌) */}
      <mesh position={[0, 0, -2]}>
        <planeGeometry args={[80, 60]} />
        <meshBasicMaterial ref={tintRef} color="#4a5568" transparent opacity={0} side={THREE.DoubleSide} />
      </mesh>
    </group>
  );
}
