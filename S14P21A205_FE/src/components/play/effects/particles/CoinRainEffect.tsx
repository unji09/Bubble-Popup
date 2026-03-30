import { useRef, useMemo } from "react";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";
import { useEffectLifecycle } from "../useEffectLifecycle";

const BILL_10K_COUNT = 22;
const BILL_50K_COUNT = 18;

interface Props {
  durationMs: number;
}

/** 길로쉬(보안무늬) 그리기 */
function drawGuilloches(
  ctx: CanvasRenderingContext2D,
  cx: number, cy: number,
  maxR: number, step: number, color: string,
) {
  ctx.strokeStyle = color;
  ctx.lineWidth = 0.8;
  for (let r = step; r < maxR; r += step) {
    ctx.beginPath();
    ctx.ellipse(cx, cy, r * 1.3, r, 0, 0, Math.PI * 2);
    ctx.stroke();
  }
  // 로제트 패턴
  ctx.lineWidth = 0.4;
  for (let a = 0; a < Math.PI * 2; a += Math.PI / 8) {
    ctx.beginPath();
    for (let r2 = 0; r2 < maxR; r2 += 2) {
      const wave = Math.sin(r2 * 0.15 + a * 3) * 4;
      const x = cx + Math.cos(a) * r2 + Math.cos(a + Math.PI / 2) * wave;
      const y = cy + Math.sin(a) * r2 + Math.sin(a + Math.PI / 2) * wave;
      if (r2 === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();
  }
}

/** 미세 물결 패턴 */
function drawMicroPattern(
  ctx: CanvasRenderingContext2D,
  x1: number, y1: number, x2: number, y2: number,
  color: string,
) {
  ctx.strokeStyle = color;
  ctx.lineWidth = 0.5;
  for (let y = y1; y < y2; y += 4) {
    ctx.beginPath();
    ctx.moveTo(x1, y);
    for (let x = x1; x < x2; x += 2) {
      ctx.lineTo(x, y + Math.sin(x * 0.2 + y * 0.15) * 1.2);
    }
    ctx.stroke();
  }
}

/** 만원권 (녹색, 세종대왕) */
function create10KTexture(): THREE.CanvasTexture {
  const W = 512, H = 240;
  const c = document.createElement("canvas");
  c.width = W; c.height = H;
  const ctx = c.getContext("2d")!;

  // 배경
  const bg = ctx.createLinearGradient(0, 0, W, H);
  bg.addColorStop(0, "#b0dc90");
  bg.addColorStop(0.3, "#90c870");
  bg.addColorStop(0.6, "#80b860");
  bg.addColorStop(1, "#70a850");
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, W, H);

  // 보안무늬
  drawGuilloches(ctx, W * 0.35, H * 0.55, 70, 4, "rgba(80, 150, 50, 0.18)");
  drawGuilloches(ctx, W * 0.75, H * 0.45, 50, 4, "rgba(70, 140, 40, 0.12)");
  drawMicroPattern(ctx, 20, 50, W * 0.45, H - 30, "rgba(60, 120, 35, 0.2)");
  drawMicroPattern(ctx, W * 0.6, 50, W - 20, H - 30, "rgba(60, 120, 35, 0.15)");

  // 삼중 테두리
  ctx.strokeStyle = "#3a6a25";
  ctx.lineWidth = 4;
  ctx.strokeRect(4, 4, W - 8, H - 8);
  ctx.strokeStyle = "#4a8a35";
  ctx.lineWidth = 2;
  ctx.strokeRect(10, 10, W - 20, H - 20);
  ctx.strokeStyle = "#5a9a45";
  ctx.lineWidth = 1;
  ctx.strokeRect(15, 15, W - 30, H - 30);

  // 코너 장식
  ctx.strokeStyle = "#3a6a25";
  ctx.lineWidth = 2;
  const corners: [number, number, number, number][] = [
    [18, 18, 1, 1], [W - 18, 18, -1, 1],
    [18, H - 18, 1, -1], [W - 18, H - 18, -1, -1],
  ];
  for (const [cx, cy, dx, dy] of corners) {
    ctx.beginPath();
    ctx.moveTo(cx, cy + dy * 16);
    ctx.lineTo(cx, cy);
    ctx.lineTo(cx + dx * 16, cy);
    ctx.stroke();
    // 안쪽 코너
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(cx + dx * 3, cy + dy * 12);
    ctx.lineTo(cx + dx * 3, cy + dy * 3);
    ctx.lineTo(cx + dx * 12, cy + dy * 3);
    ctx.stroke();
    ctx.lineWidth = 2;
  }

  // "한국은행"
  ctx.fillStyle = "#2a4a18";
  ctx.font = "bold 16px serif";
  ctx.textAlign = "center";
  ctx.fillText("한국은행", W * 0.5, 36);

  // "만원"
  ctx.font = "bold 22px serif";
  ctx.textAlign = "left";
  ctx.fillText("만원", 28, 50);

  // 세종대왕 초상화
  const px = W * 0.75, py = H * 0.5;

  // 초상화 배경 원
  const pGrad = ctx.createRadialGradient(px, py, 0, px, py, 55);
  pGrad.addColorStop(0, "rgba(100, 170, 60, 0.15)");
  pGrad.addColorStop(1, "rgba(100, 170, 60, 0)");
  ctx.fillStyle = pGrad;
  ctx.beginPath();
  ctx.arc(px, py, 55, 0, Math.PI * 2);
  ctx.fill();

  // 얼굴
  ctx.fillStyle = "#d8c8a4";
  ctx.beginPath();
  ctx.ellipse(px, py - 6, 30, 36, 0, 0, Math.PI * 2);
  ctx.fill();
  ctx.strokeStyle = "#5a6a38";
  ctx.lineWidth = 1.5;
  ctx.stroke();

  // 갓
  ctx.fillStyle = "#2a2a2a";
  ctx.beginPath();
  ctx.ellipse(px, py - 38, 40, 10, 0, Math.PI, 0);
  ctx.fill();
  ctx.fillRect(px - 18, py - 50, 36, 8);
  ctx.beginPath();
  ctx.ellipse(px, py - 52, 14, 16, 0, Math.PI, 0);
  ctx.fill();
  // 갓끈
  ctx.strokeStyle = "#444";
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(px - 32, py - 30);
  ctx.quadraticCurveTo(px - 36, py, px - 30, py + 15);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(px + 32, py - 30);
  ctx.quadraticCurveTo(px + 36, py, px + 30, py + 15);
  ctx.stroke();

  // 눈썹
  ctx.strokeStyle = "#555";
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.moveTo(px - 16, py - 16);
  ctx.quadraticCurveTo(px - 10, py - 19, px - 4, py - 16);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(px + 4, py - 16);
  ctx.quadraticCurveTo(px + 10, py - 19, px + 16, py - 16);
  ctx.stroke();

  // 눈
  ctx.fillStyle = "#333";
  ctx.beginPath();
  ctx.ellipse(px - 10, py - 10, 3.5, 2, 0, 0, Math.PI * 2);
  ctx.fill();
  ctx.beginPath();
  ctx.ellipse(px + 10, py - 10, 3.5, 2, 0, 0, Math.PI * 2);
  ctx.fill();

  // 코
  ctx.strokeStyle = "#8a7a5a";
  ctx.lineWidth = 1.2;
  ctx.beginPath();
  ctx.moveTo(px, py - 5);
  ctx.quadraticCurveTo(px - 2, py + 2, px, py + 4);
  ctx.stroke();

  // 입
  ctx.strokeStyle = "#8a6a5a";
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(px - 7, py + 10);
  ctx.quadraticCurveTo(px, py + 13, px + 7, py + 10);
  ctx.stroke();

  // 수염
  ctx.strokeStyle = "#666";
  ctx.lineWidth = 0.8;
  for (let i = -4; i <= 4; i++) {
    ctx.beginPath();
    ctx.moveTo(px + i * 2.5, py + 14);
    ctx.quadraticCurveTo(px + i * 3, py + 22, px + i * 3.5, py + 28);
    ctx.stroke();
  }

  // 옷 (곤룡포)
  const robeGrad = ctx.createLinearGradient(px - 35, py + 30, px + 35, py + 65);
  robeGrad.addColorStop(0, "#4a8a38");
  robeGrad.addColorStop(0.5, "#5a9a48");
  robeGrad.addColorStop(1, "#4a8a38");
  ctx.fillStyle = robeGrad;
  ctx.beginPath();
  ctx.moveTo(px - 32, py + 30);
  ctx.quadraticCurveTo(px, py + 38, px + 32, py + 30);
  ctx.lineTo(px + 40, py + 70);
  ctx.lineTo(px - 40, py + 70);
  ctx.closePath();
  ctx.fill();
  // 옷깃 V
  ctx.strokeStyle = "#fff8e0";
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(px - 14, py + 30);
  ctx.lineTo(px, py + 44);
  ctx.lineTo(px + 14, py + 30);
  ctx.stroke();

  // "10000" 큰 숫자
  ctx.fillStyle = "#2a5a18";
  ctx.font = "bold 48px serif";
  ctx.textAlign = "left";
  ctx.fillText("10000", 24, H * 0.68);

  // 오른쪽 위 작은 숫자
  ctx.font = "bold 20px serif";
  ctx.textAlign = "right";
  ctx.fillText("10000", W - 22, 38);

  // 일련번호
  ctx.fillStyle = "#2a4a18";
  ctx.font = "bold 12px monospace";
  ctx.textAlign = "left";
  ctx.fillText("AA 0000000 A", 22, H - 18);
  ctx.textAlign = "right";
  ctx.fillText("AA 0000000 A", W - 22, H - 18);

  // 한국은행 총재 텍스트
  ctx.fillStyle = "#3a5a28";
  ctx.font = "9px serif";
  ctx.textAlign = "left";
  ctx.fillText("한국은행 총재", 24, H - 32);

  const tex = new THREE.CanvasTexture(c);
  tex.needsUpdate = true;
  return tex;
}

/** 오만원권 (살구/분홍, 신사임당) */
function create50KTexture(): THREE.CanvasTexture {
  const W = 512, H = 240;
  const c = document.createElement("canvas");
  c.width = W; c.height = H;
  const ctx = c.getContext("2d")!;

  // 배경
  const bg = ctx.createLinearGradient(0, 0, W, H);
  bg.addColorStop(0, "#f2d4a8");
  bg.addColorStop(0.3, "#ecc89c");
  bg.addColorStop(0.6, "#e6bc90");
  bg.addColorStop(1, "#deb088");
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, W, H);

  // 보안무늬
  drawGuilloches(ctx, W * 0.6, H * 0.5, 65, 4, "rgba(180, 140, 80, 0.15)");
  drawGuilloches(ctx, W * 0.2, H * 0.45, 45, 4, "rgba(170, 130, 70, 0.12)");
  drawMicroPattern(ctx, W * 0.45, 50, W - 20, H - 30, "rgba(160, 120, 60, 0.15)");
  drawMicroPattern(ctx, 20, 50, W * 0.35, H - 30, "rgba(160, 120, 60, 0.12)");

  // 삼중 테두리
  ctx.strokeStyle = "#8a6a3a";
  ctx.lineWidth = 4;
  ctx.strokeRect(4, 4, W - 8, H - 8);
  ctx.strokeStyle = "#9a7a4a";
  ctx.lineWidth = 2;
  ctx.strokeRect(10, 10, W - 20, H - 20);
  ctx.strokeStyle = "#aa8a5a";
  ctx.lineWidth = 1;
  ctx.strokeRect(15, 15, W - 30, H - 30);

  // 코너 장식
  ctx.strokeStyle = "#8a6a3a";
  ctx.lineWidth = 2;
  const corners: [number, number, number, number][] = [
    [18, 18, 1, 1], [W - 18, 18, -1, 1],
    [18, H - 18, 1, -1], [W - 18, H - 18, -1, -1],
  ];
  for (const [cx, cy, dx, dy] of corners) {
    ctx.beginPath();
    ctx.moveTo(cx, cy + dy * 16);
    ctx.lineTo(cx, cy);
    ctx.lineTo(cx + dx * 16, cy);
    ctx.stroke();
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(cx + dx * 3, cy + dy * 12);
    ctx.lineTo(cx + dx * 3, cy + dy * 3);
    ctx.lineTo(cx + dx * 12, cy + dy * 3);
    ctx.stroke();
    ctx.lineWidth = 2;
  }

  // "한국은행"
  ctx.fillStyle = "#5a3a18";
  ctx.font = "bold 16px serif";
  ctx.textAlign = "center";
  ctx.fillText("한국은행", W * 0.5, 36);

  // "오만원"
  ctx.font = "bold 22px serif";
  ctx.textAlign = "left";
  ctx.fillText("오만원", 28, 50);

  // 신사임당 초상화 (왼쪽)
  const px = W * 0.25, py = H * 0.5;

  // 초상화 배경
  const pGrad = ctx.createRadialGradient(px, py, 0, px, py, 55);
  pGrad.addColorStop(0, "rgba(200, 160, 100, 0.15)");
  pGrad.addColorStop(1, "rgba(200, 160, 100, 0)");
  ctx.fillStyle = pGrad;
  ctx.beginPath();
  ctx.arc(px, py, 55, 0, Math.PI * 2);
  ctx.fill();

  // 얼굴
  ctx.fillStyle = "#ecdcc4";
  ctx.beginPath();
  ctx.ellipse(px, py - 4, 26, 32, 0, 0, Math.PI * 2);
  ctx.fill();
  ctx.strokeStyle = "#8a7050";
  ctx.lineWidth = 1.2;
  ctx.stroke();

  // 머리 (쪽머리)
  ctx.fillStyle = "#1a1a1a";
  ctx.beginPath();
  ctx.ellipse(px, py - 32, 28, 12, 0, Math.PI, 0);
  ctx.fill();
  // 쪽 (올림머리)
  ctx.beginPath();
  ctx.ellipse(px, py - 42, 12, 10, 0, 0, Math.PI * 2);
  ctx.fill();
  // 비녀
  ctx.strokeStyle = "#b09060";
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(px - 16, py - 42);
  ctx.lineTo(px + 16, py - 42);
  ctx.stroke();
  // 비녀 끝 장식
  ctx.fillStyle = "#c8a060";
  ctx.beginPath();
  ctx.arc(px + 16, py - 42, 3, 0, Math.PI * 2);
  ctx.fill();

  // 가르마
  ctx.strokeStyle = "#ecdcc4";
  ctx.lineWidth = 0.8;
  ctx.beginPath();
  ctx.moveTo(px, py - 22);
  ctx.lineTo(px, py - 38);
  ctx.stroke();

  // 눈썹
  ctx.strokeStyle = "#555";
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(px - 14, py - 14);
  ctx.quadraticCurveTo(px - 8, py - 17, px - 3, py - 14);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(px + 3, py - 14);
  ctx.quadraticCurveTo(px + 8, py - 17, px + 14, py - 14);
  ctx.stroke();

  // 눈
  ctx.fillStyle = "#333";
  ctx.beginPath();
  ctx.ellipse(px - 8, py - 8, 3, 2, 0, 0, Math.PI * 2);
  ctx.fill();
  ctx.beginPath();
  ctx.ellipse(px + 8, py - 8, 3, 2, 0, 0, Math.PI * 2);
  ctx.fill();

  // 코
  ctx.strokeStyle = "#b09878";
  ctx.lineWidth = 0.8;
  ctx.beginPath();
  ctx.moveTo(px, py - 3);
  ctx.quadraticCurveTo(px - 1, py + 3, px, py + 5);
  ctx.stroke();

  // 입
  ctx.fillStyle = "#cc7070";
  ctx.beginPath();
  ctx.ellipse(px, py + 11, 5, 2, 0, 0, Math.PI * 2);
  ctx.fill();

  // 한복 - 저고리
  const robeGrad = ctx.createLinearGradient(px - 35, py + 28, px + 35, py + 70);
  robeGrad.addColorStop(0, "#d8b878");
  robeGrad.addColorStop(0.5, "#e0c488");
  robeGrad.addColorStop(1, "#d8b878");
  ctx.fillStyle = robeGrad;
  ctx.beginPath();
  ctx.moveTo(px - 30, py + 28);
  ctx.quadraticCurveTo(px, py + 34, px + 30, py + 28);
  ctx.lineTo(px + 38, py + 70);
  ctx.lineTo(px - 38, py + 70);
  ctx.closePath();
  ctx.fill();
  // 옷깃 V
  ctx.strokeStyle = "#fff8ee";
  ctx.lineWidth = 2.5;
  ctx.beginPath();
  ctx.moveTo(px - 12, py + 28);
  ctx.lineTo(px, py + 42);
  ctx.lineTo(px + 12, py + 28);
  ctx.stroke();
  // 고름 (리본)
  ctx.strokeStyle = "#cc5555";
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(px + 2, py + 42);
  ctx.quadraticCurveTo(px + 8, py + 52, px + 5, py + 60);
  ctx.stroke();

  // 포도잎 장식 (오른쪽)
  const leafColors = ["#7ab050", "#6aa040", "#8ac060"];
  for (let i = 0; i < 5; i++) {
    const lx = W * 0.58 + i * 16 + Math.sin(i * 1.5) * 8;
    const ly = H * 0.3 + Math.cos(i * 1.2) * 12;
    const angle = -0.4 + i * 0.2;
    ctx.save();
    ctx.translate(lx, ly);
    ctx.rotate(angle);
    ctx.fillStyle = leafColors[i % 3];
    ctx.globalAlpha = 0.5;
    ctx.beginPath();
    ctx.moveTo(0, 0);
    ctx.bezierCurveTo(6, -8, 16, -8, 20, 0);
    ctx.bezierCurveTo(16, 8, 6, 8, 0, 0);
    ctx.fill();
    // 잎맥
    ctx.strokeStyle = "rgba(60, 90, 30, 0.4)";
    ctx.lineWidth = 0.5;
    ctx.beginPath();
    ctx.moveTo(0, 0);
    ctx.lineTo(18, 0);
    ctx.stroke();
    ctx.globalAlpha = 1;
    ctx.restore();
  }
  // 줄기
  ctx.strokeStyle = "rgba(80, 120, 40, 0.35)";
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(W * 0.55, H * 0.35);
  ctx.bezierCurveTo(W * 0.65, H * 0.2, W * 0.75, H * 0.28, W * 0.8, H * 0.38);
  ctx.stroke();

  // "50000" 큰 숫자
  ctx.fillStyle = "#6a4a20";
  ctx.font = "bold 48px serif";
  ctx.textAlign = "right";
  ctx.fillText("50000", W - 24, H * 0.68);

  // 오른쪽 위 작은 숫자
  ctx.font = "bold 20px serif";
  ctx.textAlign = "right";
  ctx.fillText("50000", W - 22, 38);

  // 일련번호
  ctx.fillStyle = "#6a4a20";
  ctx.font = "bold 12px monospace";
  ctx.textAlign = "left";
  ctx.fillText("AA 0973857 A", 22, H - 18);
  ctx.textAlign = "right";
  ctx.fillText("AA 0973857 A", W - 22, H - 18);

  // 한국은행 총재
  ctx.fillStyle = "#7a5a30";
  ctx.font = "9px serif";
  ctx.textAlign = "right";
  ctx.fillText("한국은행 총재", W - 24, H - 32);

  const tex = new THREE.CanvasTexture(c);
  tex.needsUpdate = true;
  return tex;
}

function makeBillData(count: number) {
  return Array.from({ length: count }, () => ({
    x: (Math.random() - 0.5) * 20,
    z: (Math.random() - 0.5) * 4,
    speed: 0.5 + Math.random() * 1.2,
    offset: Math.random() * 18,
    flutterFreqX: 2 + Math.random() * 4,
    flutterAmpX: 0.5 + Math.random() * 1.0,
    flutterFreqZ: 1.5 + Math.random() * 3,
    flutterAmpZ: 0.3 + Math.random() * 0.6,
    swayFreq: 0.3 + Math.random() * 1.5,
    swayAmp: 1.0 + Math.random() * 2.5,
    tumbleSpeed: 0.1 + Math.random() * 0.4,
    zWobbleFreq: 0.5 + Math.random() * 1.5,
    zWobbleAmp: 0.3 + Math.random() * 0.8,
  }));
}

function animateBills(
  mesh: THREE.InstancedMesh,
  data: ReturnType<typeof makeBillData>,
  dummy: THREE.Object3D,
  t: number,
  o: number,
) {
  for (let i = 0; i < data.length; i++) {
    const d = data[i];
    const y = 9 - ((d.offset + t * d.speed) % 18);
    const x = d.x + Math.sin(t * d.swayFreq + i * 0.9) * d.swayAmp;
    const z = d.z + Math.sin(t * d.zWobbleFreq + i * 1.3) * d.zWobbleAmp;

    dummy.position.set(x, y, z);
    dummy.rotation.set(
      Math.sin(t * d.flutterFreqX + i * 0.7) * d.flutterAmpX,
      t * d.tumbleSpeed + Math.sin(t * 0.5 + i) * 0.3,
      Math.sin(t * d.flutterFreqZ + i * 1.1) * d.flutterAmpZ,
    );
    dummy.scale.set(1, 1, 1);
    dummy.updateMatrix();
    mesh.setMatrixAt(i, dummy.matrix);
  }
  mesh.instanceMatrix.needsUpdate = true;
  (mesh.material as THREE.MeshBasicMaterial).opacity = o;
}

export default function CoinRainEffect({ durationMs }: Props) {
  const opacity = useEffectLifecycle(durationMs);

  const mesh10KRef = useRef<THREE.InstancedMesh>(null);
  const mesh50KRef = useRef<THREE.InstancedMesh>(null);
  const glowRef = useRef<THREE.MeshBasicMaterial>(null);
  const dummy = useMemo(() => new THREE.Object3D(), []);

  const tex10K = useMemo(() => create10KTexture(), []);
  const tex50K = useMemo(() => create50KTexture(), []);
  const data10K = useMemo(() => makeBillData(BILL_10K_COUNT), []);
  const data50K = useMemo(() => makeBillData(BILL_50K_COUNT), []);

  useFrame(({ clock }) => {
    const t = clock.getElapsedTime();
    const o = opacity.current;

    if (mesh10KRef.current) animateBills(mesh10KRef.current, data10K, dummy, t, o);
    if (mesh50KRef.current) animateBills(mesh50KRef.current, data50K, dummy, t + 5, o);

    if (glowRef.current) {
      glowRef.current.opacity = o * (0.04 + Math.sin(t * 2) * 0.015);
    }
  });

  return (
    <group>
      <instancedMesh ref={mesh10KRef} args={[undefined, undefined, BILL_10K_COUNT]}>
        <planeGeometry args={[1.1, 0.52]} />
        <meshBasicMaterial map={tex10K} transparent opacity={0} side={THREE.DoubleSide} />
      </instancedMesh>

      <instancedMesh ref={mesh50KRef} args={[undefined, undefined, BILL_50K_COUNT]}>
        <planeGeometry args={[1.1, 0.52]} />
        <meshBasicMaterial map={tex50K} transparent opacity={0} side={THREE.DoubleSide} />
      </instancedMesh>

      <mesh position={[0, 0, -3]}>
        <planeGeometry args={[80, 60]} />
        <meshBasicMaterial ref={glowRef} color="#f5c542" transparent opacity={0} side={THREE.DoubleSide} />
      </mesh>
    </group>
  );
}
