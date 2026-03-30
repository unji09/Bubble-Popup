import { useRef, useMemo } from "react";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";
import { useEffectLifecycle } from "../useEffectLifecycle";

const ARROW_COUNT = 8;
const TRAIL_COUNT = 40;

interface Props {
  durationMs: number;
  direction?: "up" | "down";
}

/* ── easing helpers ── */
function easeOutCubic(t: number) {
  return 1 - (1 - t) ** 3;
}

/* ── 화살표 텍스처 (256x256) ── */
function createArrowTexture(color: string): THREE.CanvasTexture {
  const size = 256;
  const c = document.createElement("canvas");
  c.width = size;
  c.height = size;
  const ctx = c.getContext("2d")!;
  ctx.clearRect(0, 0, size, size);

  const r = parseInt(color.slice(1, 3), 16);
  const g = parseInt(color.slice(3, 5), 16);
  const b = parseInt(color.slice(5, 7), 16);

  const s = size / 128; // scale factor from old 128 coords

  const drawArrow = () => {
    ctx.beginPath();
    ctx.moveTo(64 * s, 6 * s);
    ctx.lineTo(118 * s, 58 * s);
    ctx.lineTo(84 * s, 58 * s);
    ctx.quadraticCurveTo(84 * s, 62 * s, 84 * s, 64 * s);
    ctx.lineTo(84 * s, 118 * s);
    ctx.quadraticCurveTo(84 * s, 122 * s, 80 * s, 122 * s);
    ctx.lineTo(48 * s, 122 * s);
    ctx.quadraticCurveTo(44 * s, 122 * s, 44 * s, 118 * s);
    ctx.lineTo(44 * s, 64 * s);
    ctx.quadraticCurveTo(44 * s, 62 * s, 44 * s, 58 * s);
    ctx.lineTo(10 * s, 58 * s);
    ctx.closePath();
  };

  // 외곽 글로우
  ctx.shadowColor = `rgba(${r}, ${g}, ${b}, 0.7)`;
  ctx.shadowBlur = 24;
  drawArrow();
  ctx.fillStyle = `rgba(${r}, ${g}, ${b}, 0.25)`;
  ctx.fill();

  // 메인 화살표
  ctx.shadowBlur = 14;
  drawArrow();
  const grad = ctx.createLinearGradient(64 * s, 6 * s, 64 * s, 122 * s);
  grad.addColorStop(0, `rgba(${Math.min(r + 80, 255)}, ${Math.min(g + 80, 255)}, ${Math.min(b + 80, 255)}, 1)`);
  grad.addColorStop(0.3, color);
  grad.addColorStop(1, `rgba(${Math.max(r - 40, 0)}, ${Math.max(g - 40, 0)}, ${Math.max(b - 40, 0)}, 1)`);
  ctx.fillStyle = grad;
  ctx.fill();

  // 하이라이트
  ctx.shadowBlur = 0;
  ctx.save();
  ctx.clip();
  const hlGrad = ctx.createLinearGradient(20 * s, 0, 80 * s, 0);
  hlGrad.addColorStop(0, "rgba(255, 255, 255, 0.35)");
  hlGrad.addColorStop(0.5, "rgba(255, 255, 255, 0.1)");
  hlGrad.addColorStop(1, "rgba(255, 255, 255, 0)");
  ctx.fillStyle = hlGrad;
  ctx.fillRect(0, 0, size, size);
  ctx.restore();

  // 테두리
  drawArrow();
  ctx.strokeStyle = `rgba(${Math.max(r - 60, 0)}, ${Math.max(g - 60, 0)}, ${Math.max(b - 60, 0)}, 0.4)`;
  ctx.lineWidth = 2;
  ctx.stroke();

  const tex = new THREE.CanvasTexture(c);
  tex.needsUpdate = true;
  return tex;
}

/* ── 트레일 텍스처 (부드러운 점) ── */
function createTrailTexture(color: string): THREE.CanvasTexture {
  const size = 64;
  const c = document.createElement("canvas");
  c.width = size;
  c.height = size;
  const ctx = c.getContext("2d")!;
  ctx.clearRect(0, 0, size, size);

  const r = parseInt(color.slice(1, 3), 16);
  const g = parseInt(color.slice(3, 5), 16);
  const b = parseInt(color.slice(5, 7), 16);

  const grad = ctx.createRadialGradient(32, 32, 0, 32, 32, 32);
  grad.addColorStop(0, `rgba(${r}, ${g}, ${b}, 0.9)`);
  grad.addColorStop(0.5, `rgba(${r}, ${g}, ${b}, 0.3)`);
  grad.addColorStop(1, "rgba(0, 0, 0, 0)");
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, size, size);

  const tex = new THREE.CanvasTexture(c);
  tex.needsUpdate = true;
  return tex;
}

export default function PriceArrowEffect({ durationMs, direction = "down" }: Props) {
  const opacity = useEffectLifecycle(durationMs, 200, 1500);

  const isUp = direction === "up";
  const color = isUp ? "#ff4444" : "#4499ff";
  const dir = isUp ? 1 : -1;

  const arrowRef = useRef<THREE.InstancedMesh>(null);
  const trailRef = useRef<THREE.InstancedMesh>(null);
  const dummy = useMemo(() => new THREE.Object3D(), []);

  const arrowTex = useMemo(() => createArrowTexture(color), [color]);
  const trailTex = useMemo(() => createTrailTexture(color), [color]);

  /* ── 화살표 데이터 ── */
  const arrowData = useMemo(() => {
    const span = 14;
    const slotWidth = span / ARROW_COUNT;
    return Array.from({ length: ARROW_COUNT }, (_, i) => {
      let x = -7 + slotWidth * (i + 0.5) + (Math.random() - 0.5) * slotWidth * 0.6;
      if (Math.abs(x) < 2) x += x >= 0 ? 2 : -2;
      return {
        x,
        yOffset: (Math.random() - 0.5) * 3,
        waveGroup: Math.floor(i / 4),
        size: 0.8 + Math.random() * 0.4,
        extraDelay: Math.random() * 0.3,
        cycle: 2.2 + Math.random() * 0.8, // 2.2~3.0초 주기 (느긋하게)
        wobblePhase: Math.random() * Math.PI * 2,
      };
    });
  }, []);

  /* ── 트레일 데이터 ── */
  const trailData = useMemo(() =>
    Array.from({ length: TRAIL_COUNT }, () => ({
      x: (Math.random() - 0.5) * 16,
      yOffset: (Math.random() - 0.5) * 5,
      size: 0.15 + Math.random() * 0.25,
      speed: 0.3 + Math.random() * 0.4,
      cycle: 1.0 + Math.random() * 0.8,
      delay: Math.random() * 2.0,
    })),
  []);

  useFrame(({ clock }) => {
    const t = clock.getElapsedTime();
    const o = opacity.current;

    /* ── 화살표 애니메이션 ── */
    if (arrowRef.current) {
      for (let i = 0; i < ARROW_COUNT; i++) {
        const d = arrowData[i];
        const cycle = t % d.cycle;
        const groupDelay = d.waveGroup * 0.3 + d.extraDelay;
        const localT = cycle - groupDelay;
        const sweepDuration = d.cycle * 0.8;

        if (localT < 0 || localT > sweepDuration) {
          dummy.scale.setScalar(0);
        } else {
          const progress = localT / sweepDuration;
          const easedProgress = easeOutCubic(progress);

          // 페이드: 부드러운 등장 → 부드러운 소멸
          const alpha = progress < 0.15
            ? progress / 0.15
            : 1 - ((progress - 0.15) / 0.85) ** 2;

          // 스케일: 살짝 커졌다 안정 (과하지 않게)
          const bounce = progress < 0.15
            ? 1.0 + 0.12 * (1 - progress / 0.15)
            : 1.0;

          const moveDistance = 3.0;
          const y = dir * (-1.0 + easedProgress * moveDistance) + d.yOffset;
          // 미세 좌우 흔들림 (느리고 작게)
          const wobbleX = Math.sin(t * 1.5 + d.wobblePhase) * 0.06;

          dummy.position.set(d.x + wobbleX, y, 0);
          dummy.rotation.set(0, 0, isUp ? 0 : Math.PI);
          dummy.scale.setScalar(d.size * bounce * Math.max(0, alpha));
        }

        dummy.updateMatrix();
        arrowRef.current.setMatrixAt(i, dummy.matrix);
      }
      arrowRef.current.instanceMatrix.needsUpdate = true;
      (arrowRef.current.material as THREE.MeshBasicMaterial).opacity = o * 0.85;
    }

    /* ── 트레일 애니메이션 ── */
    if (trailRef.current) {
      for (let i = 0; i < TRAIL_COUNT; i++) {
        const tr = trailData[i];
        const cycle = (t - tr.delay) % tr.cycle;
        const sweepDuration = tr.cycle * 0.8;

        if (cycle < 0 || cycle > sweepDuration) {
          dummy.scale.setScalar(0);
        } else {
          const progress = cycle / sweepDuration;
          // 트레일은 천천히 나타나고 더 천천히 사라짐 (잔상 효과)
          const alpha = progress < 0.15
            ? progress / 0.15
            : (1 - progress) / 0.85;
          const moveDistance = 1.8 * tr.speed;
          const y = dir * (-0.5 + progress * moveDistance) + tr.yOffset;

          dummy.position.set(tr.x, y, -0.2);
          dummy.rotation.set(0, 0, 0);
          dummy.scale.setScalar(tr.size * Math.max(0, alpha));
        }

        dummy.updateMatrix();
        trailRef.current.setMatrixAt(i, dummy.matrix);
      }
      trailRef.current.instanceMatrix.needsUpdate = true;
      (trailRef.current.material as THREE.MeshBasicMaterial).opacity = o * 0.6;
    }
  });

  return (
    <group>
      {/* 트레일 (가장 뒤) */}
      <instancedMesh ref={trailRef} args={[undefined, undefined, TRAIL_COUNT]}>
        <planeGeometry args={[0.4, 0.4]} />
        <meshBasicMaterial
          map={trailTex}
          transparent
          opacity={0}
          side={THREE.DoubleSide}
          depthWrite={false}
          blending={THREE.AdditiveBlending}
        />
      </instancedMesh>

      {/* 화살표 (가장 앞) */}
      <instancedMesh ref={arrowRef} args={[undefined, undefined, ARROW_COUNT]}>
        <planeGeometry args={[0.7, 1.4]} />
        <meshBasicMaterial
          map={arrowTex}
          transparent
          opacity={0}
          side={THREE.DoubleSide}
          alphaTest={0.05}
        />
      </instancedMesh>
    </group>
  );
}
