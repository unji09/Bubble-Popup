import { useRef, useMemo } from "react";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";
import { useEffectLifecycle } from "../useEffectLifecycle";

const DOC_COUNT = 30;

interface Props {
  durationMs: number;
}

/** 빨간 "규제" 스탬프 텍스처 — 잉크 번짐이 있는 실제 도장 느낌 */
function createStampTexture(): THREE.CanvasTexture {
  const size = 512;
  const cx = size / 2;
  const cy = size / 2;
  const c = document.createElement("canvas");
  c.width = size;
  c.height = size;
  const ctx = c.getContext("2d")!;
  ctx.clearRect(0, 0, size, size);

  const red = "rgb(180, 22, 22)";
  const redA = (a: number) => `rgba(180, 22, 22, ${a})`;

  // 잉크 노이즈 — 도장 찍힌 느낌을 위해 반투명 점을 흩뿌림
  const addInkNoise = (region: Path2D | null, count: number, alpha: number) => {
    ctx.save();
    if (region) ctx.clip(region);
    for (let i = 0; i < count; i++) {
      const nx = cx + (Math.random() - 0.5) * size * 0.85;
      const ny = cy + (Math.random() - 0.5) * size * 0.85;
      const nr = 0.5 + Math.random() * 2.5;
      ctx.globalAlpha = alpha * (0.3 + Math.random() * 0.7);
      ctx.fillStyle = red;
      ctx.beginPath();
      ctx.arc(nx, ny, nr, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.restore();
  };

  // 클리핑용 원 영역
  const stampClip = new Path2D();
  stampClip.arc(cx, cy, 210, 0, Math.PI * 2);

  // ── 외곽 원 (두꺼운 테두리) ──
  ctx.strokeStyle = red;
  ctx.lineWidth = 16;
  ctx.beginPath();
  ctx.arc(cx, cy, 200, 0, Math.PI * 2);
  ctx.stroke();

  // ── 내곽 원 ──
  ctx.lineWidth = 6;
  ctx.beginPath();
  ctx.arc(cx, cy, 170, 0, Math.PI * 2);
  ctx.stroke();

  // ── 가로 구분선 (위/아래) ──
  ctx.lineWidth = 4;
  ctx.beginPath();
  ctx.moveTo(cx - 165, cy - 40);
  ctx.lineTo(cx + 165, cy - 40);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(cx - 165, cy + 40);
  ctx.lineTo(cx + 165, cy + 40);
  ctx.stroke();

  // ── 메인 텍스트 "규 제" ──
  ctx.fillStyle = red;
  ctx.font = "bold 110px sans-serif";
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillText("규 제", cx, cy);

  // ── 상단 소제목 ──
  ctx.font = "bold 36px sans-serif";
  ctx.fillText("정 책 변 경", cx, cy - 72);

  // ── 하단 소제목 ──
  ctx.fillText("시 행 완 료", cx, cy + 72);

  // ── 별 장식 (상단/하단) ──
  ctx.font = "28px sans-serif";
  ctx.fillText("★", cx - 130, cy - 72);
  ctx.fillText("★", cx + 130, cy - 72);
  ctx.fillText("★", cx - 130, cy + 72);
  ctx.fillText("★", cx + 130, cy + 72);

  // ── 잉크 번짐/노이즈 오버레이 ──
  addInkNoise(stampClip, 600, 0.15);

  // ── 잉크가 약간 빠진 부분 (지우개 효과) ──
  ctx.save();
  ctx.globalCompositeOperation = "destination-out";
  for (let i = 0; i < 200; i++) {
    const nx = cx + (Math.random() - 0.5) * 400;
    const ny = cy + (Math.random() - 0.5) * 400;
    const nr = 1 + Math.random() * 4;
    ctx.globalAlpha = 0.08 + Math.random() * 0.15;
    ctx.beginPath();
    ctx.arc(nx, ny, nr, 0, Math.PI * 2);
    ctx.fill();
  }
  ctx.restore();

  // ── 외곽 글로우 (부드러운 번짐) ──
  ctx.save();
  ctx.globalCompositeOperation = "source-over";
  ctx.shadowColor = redA(0.4);
  ctx.shadowBlur = 8;
  ctx.strokeStyle = redA(0.08);
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.arc(cx, cy, 204, 0, Math.PI * 2);
  ctx.stroke();
  ctx.restore();

  const tex = new THREE.CanvasTexture(c);
  tex.needsUpdate = true;
  return tex;
}

/**
 * 정책변경 이펙트 - 공문서/서류가 휘날리는 느낌
 */
export default function DocumentEffect({ durationMs }: Props) {
  const opacity = useEffectLifecycle(durationMs, 400, 2000);

  const paperRef = useRef<THREE.InstancedMesh>(null);
  const stampRef = useRef<THREE.Mesh>(null);
  const stampMatRef = useRef<THREE.MeshBasicMaterial>(null);
  const dummy = useMemo(() => new THREE.Object3D(), []);
  const startTimeRef = useRef<number | null>(null);

  const stampTex = useMemo(() => createStampTexture(), []);

  const docData = useMemo(() => {
    return Array.from({ length: DOC_COUNT }, () => ({
      x: (Math.random() - 0.5) * 22,
      z: (Math.random() - 0.5) * 3,
      speed: 0.5 + Math.random() * 1.2,
      offset: Math.random() * 20,
      // 좌우 흔들림
      swayFreq: 0.3 + Math.random() * 1.5,
      swayAmp: 1.0 + Math.random() * 3,
      // 휘날림 (빠른 펄럭임)
      flutterFreqX: 2 + Math.random() * 4,
      flutterAmpX: 0.6 + Math.random() * 1.2,
      flutterFreqZ: 1.5 + Math.random() * 3,
      flutterAmpZ: 0.4 + Math.random() * 0.8,
      // 천천히 돌기
      tumbleSpeed: 0.1 + Math.random() * 0.4,
      // 앞뒤 깊이 흔들림
      zWobbleFreq: 0.5 + Math.random() * 1.5,
      zWobbleAmp: 0.3 + Math.random() * 0.8,
    }));
  }, []);

  const paperTexture = useMemo(() => {
    const canvas = document.createElement("canvas");
    canvas.width = 200;
    canvas.height = 260;
    const ctx = canvas.getContext("2d")!;

    // 흰 배경
    ctx.fillStyle = "#f8f8f0";
    ctx.fillRect(0, 0, 200, 260);

    // 테두리
    ctx.strokeStyle = "#333";
    ctx.lineWidth = 1;
    ctx.strokeRect(8, 8, 184, 244);

    // 관인 번호
    ctx.fillStyle = "#666";
    ctx.font = "9px serif";
    ctx.textAlign = "left";
    ctx.fillText("제 2026-042 호", 16, 26);

    // 제목
    ctx.fillStyle = "#111";
    ctx.font = "bold 14px serif";
    ctx.textAlign = "center";
    ctx.fillText("정 책 변 경 통 보 서", 100, 50);

    // 제목 아래 구분선
    ctx.strokeStyle = "#333";
    ctx.lineWidth = 0.8;
    ctx.beginPath();
    ctx.moveTo(30, 58);
    ctx.lineTo(170, 58);
    ctx.stroke();

    // 본문 텍스트 줄
    ctx.fillStyle = "#999";
    const lineWidths = [140, 155, 130, 160, 145, 135, 155, 120, 150, 140, 160, 130, 100];
    for (let i = 0; i < lineWidths.length; i++) {
      const y = 72 + i * 11;
      const indent = i % 4 === 0 ? 25 : 15;
      ctx.fillRect(indent, y, Math.min(lineWidths[i], 185 - indent), 2.5);
    }

    // 날짜
    ctx.fillStyle = "#666";
    ctx.font = "9px serif";
    ctx.textAlign = "center";
    ctx.fillText("20XX. XX. XX.", 100, 218);

    // 서명란
    ctx.strokeStyle = "#999";
    ctx.lineWidth = 0.5;
    ctx.beginPath();
    ctx.moveTo(110, 232);
    ctx.lineTo(175, 232);
    ctx.stroke();
    ctx.fillStyle = "#999";
    ctx.font = "7px serif";
    ctx.textAlign = "right";
    ctx.fillText("(인)", 185, 231);

    // 빨간 원형 관인 도장 (진한 빨간, 이중원)
    ctx.strokeStyle = "rgba(190, 20, 20, 0.85)";
    ctx.lineWidth = 2.5;
    ctx.beginPath();
    ctx.arc(152, 210, 20, 0, Math.PI * 2);
    ctx.stroke();
    // 안쪽 원
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.arc(152, 210, 15, 0, Math.PI * 2);
    ctx.stroke();
    // 도장 텍스트
    ctx.fillStyle = "rgba(190, 20, 20, 0.9)";
    ctx.font = "bold 11px serif";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText("승", 147, 207);
    ctx.fillText("인", 157, 213);
    ctx.textBaseline = "alphabetic";

    const tex = new THREE.CanvasTexture(canvas);
    tex.needsUpdate = true;
    return tex;
  }, []);

  useFrame(({ clock }) => {
    const t = clock.getElapsedTime();
    if (startTimeRef.current === null) startTimeRef.current = t;
    const elapsed = t - startTimeRef.current;
    const o = opacity.current;

    // 스탬프: 2초 후 등장, 바운스하며 찍힘
    if (stampRef.current && stampMatRef.current) {
      const stampDelay = 1.5;
      const stampT = Math.max(0, elapsed - stampDelay);
      if (stampT > 0) {
        const impactDuration = 0.3;
        const progress = Math.min(stampT / impactDuration, 1);
        // 스케일: 크게 → 작게 바운스
        const bounce = progress < 0.5
          ? 2.5 - progress * 3.0
          : 1.0 + Math.sin((progress - 0.5) * Math.PI * 4) * 0.15 * (1 - progress);
        stampRef.current.scale.setScalar(bounce);
        // 살짝 랜덤 회전
        stampRef.current.rotation.z = -0.15;
        stampMatRef.current.opacity = o * Math.min(stampT * 3, 1.0);
      } else {
        stampMatRef.current.opacity = 0;
      }
    }

    if (paperRef.current) {
      for (let i = 0; i < DOC_COUNT; i++) {
        const d = docData[i];
        const rawY = (d.offset + t * d.speed) % 20;
        const y = 10 - rawY;
        const x = d.x + Math.sin(t * d.swayFreq + i * 0.9) * d.swayAmp;
        const z = d.z + Math.sin(t * d.zWobbleFreq + i * 1.3) * d.zWobbleAmp;

        dummy.position.set(x, y, z);
        // 휘날림: 빠른 펄럭임 + 느린 회전
        dummy.rotation.set(
          Math.sin(t * d.flutterFreqX + i * 0.7) * d.flutterAmpX,
          t * d.tumbleSpeed + Math.sin(t * 0.5 + i) * 0.3,
          Math.sin(t * d.flutterFreqZ + i * 1.1) * d.flutterAmpZ,
        );
        dummy.scale.set(1, 1, 1);
        dummy.updateMatrix();
        paperRef.current.setMatrixAt(i, dummy.matrix);
      }
      paperRef.current.instanceMatrix.needsUpdate = true;
      (paperRef.current.material as THREE.MeshBasicMaterial).opacity = o;
    }
  });

  return (
    <group>
      {/* 서류 */}
      <instancedMesh ref={paperRef} args={[undefined, undefined, DOC_COUNT]}>
        <planeGeometry args={[0.5, 0.65]} />
        <meshBasicMaterial
          map={paperTexture}
          transparent
          opacity={0}
          side={THREE.DoubleSide}
        />
      </instancedMesh>

      {/* 규제 스탬프 */}
      <mesh ref={stampRef} position={[5, -2.5, 1]} scale={0}>
        <planeGeometry args={[4, 4]} />
        <meshBasicMaterial
          ref={stampMatRef}
          map={stampTex}
          transparent
          opacity={0}
          side={THREE.DoubleSide}
          depthWrite={false}
        />
      </mesh>
    </group>
  );
}
