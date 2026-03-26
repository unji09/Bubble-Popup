import { useRef, useMemo } from "react";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";
import { useEffectLifecycle } from "../useEffectLifecycle";

/**
 * 포켓몬 능력치 상승/하락 이펙트 (최종)
 * - 화면 전체에 반투명 컬러 틴트가 빠르게 펄스
 * - 큼직한 화살표가 화면 중앙부에서 빠르게 위/아래로 스윕
 * - 반복 웨이브: 번쩍 + 화살표 스윕이 1초 간격으로 반복
 */

const ARROW_COUNT = 8;

interface Props {
  durationMs: number;
  direction?: "up" | "down";
}

/** 꽉 찬 화살표 텍스처 (이미지 화살표처럼 + 글로우 + 라운드) */
function createArrowTexture(color: string): THREE.CanvasTexture {
  const c = document.createElement("canvas");
  c.width = 128;
  c.height = 128;
  const ctx = c.getContext("2d")!;

  ctx.clearRect(0, 0, 128, 128);

  const r = parseInt(color.slice(1, 3), 16);
  const g = parseInt(color.slice(3, 5), 16);
  const b = parseInt(color.slice(5, 7), 16);

  // 화살표 패스 (꽉 찬 형태: 넓은 삼각형 머리 + 두꺼운 몸통, 둥근 느낌)
  const drawArrow = () => {
    ctx.beginPath();
    // 삼각형 머리 (넓고 뭉툭)
    ctx.moveTo(64, 6);       // 꼭대기 중앙
    ctx.lineTo(118, 58);     // 오른쪽 끝
    ctx.lineTo(84, 58);      // 오른쪽 안쪽 (몸통 시작)
    // 몸통 (둥근 모서리)
    ctx.quadraticCurveTo(84, 62, 84, 64);
    ctx.lineTo(84, 118);
    ctx.quadraticCurveTo(84, 122, 80, 122);
    ctx.lineTo(48, 122);
    ctx.quadraticCurveTo(44, 122, 44, 118);
    ctx.lineTo(44, 64);
    ctx.quadraticCurveTo(44, 62, 44, 58);
    // 왼쪽 안쪽 (몸통 끝)
    ctx.lineTo(10, 58);      // 왼쪽 끝
    ctx.closePath();
  };

  // 외곽 글로우
  ctx.shadowColor = `rgba(${r}, ${g}, ${b}, 0.7)`;
  ctx.shadowBlur = 18;
  drawArrow();
  ctx.fillStyle = `rgba(${r}, ${g}, ${b}, 0.25)`;
  ctx.fill();

  // 메인 화살표 (그라데이션)
  ctx.shadowBlur = 10;
  drawArrow();
  const grad = ctx.createLinearGradient(64, 6, 64, 122);
  grad.addColorStop(0, `rgba(${Math.min(r + 80, 255)}, ${Math.min(g + 80, 255)}, ${Math.min(b + 80, 255)}, 1)`);
  grad.addColorStop(0.3, color);
  grad.addColorStop(1, `rgba(${Math.max(r - 40, 0)}, ${Math.max(g - 40, 0)}, ${Math.max(b - 40, 0)}, 1)`);
  ctx.fillStyle = grad;
  ctx.fill();

  // 하이라이트 (왼쪽 밝은 면)
  ctx.shadowBlur = 0;
  ctx.save();
  ctx.clip();
  const hlGrad = ctx.createLinearGradient(20, 0, 80, 0);
  hlGrad.addColorStop(0, "rgba(255, 255, 255, 0.3)");
  hlGrad.addColorStop(0.5, "rgba(255, 255, 255, 0.08)");
  hlGrad.addColorStop(1, "rgba(255, 255, 255, 0)");
  ctx.fillStyle = hlGrad;
  ctx.fillRect(0, 0, 128, 128);
  ctx.restore();

  // 테두리 (살짝 어두운 윤곽)
  drawArrow();
  ctx.strokeStyle = `rgba(${Math.max(r - 60, 0)}, ${Math.max(g - 60, 0)}, ${Math.max(b - 60, 0)}, 0.4)`;
  ctx.lineWidth = 1.5;
  ctx.stroke();

  const tex = new THREE.CanvasTexture(c);
  tex.needsUpdate = true;
  return tex;
}

export default function PriceArrowEffect({ durationMs, direction = "down" }: Props) {
  const opacity = useEffectLifecycle(durationMs, 200, 1500);

  const isUp = direction === "up";
  const color = isUp ? "#ff4444" : "#4499ff";
  const meshRef = useRef<THREE.InstancedMesh>(null);
  const dummy = useMemo(() => new THREE.Object3D(), []);

  const arrowTex = useMemo(() => createArrowTexture(color), [color]);

  const arrowData = useMemo(() => {
    // 8개를 균등 배치, 중앙(x: -2~+2) 회피, y축도 분산
    const span = 14; // -7 ~ +7
    const slotWidth = span / ARROW_COUNT;
    return Array.from({ length: ARROW_COUNT }, (_, i) => {
      let x = -7 + slotWidth * (i + 0.5) + (Math.random() - 0.5) * slotWidth * 0.6;
      // 중앙 회피: |x| < 2이면 바깥으로 밀기
      if (Math.abs(x) < 2) x += x >= 0 ? 2 : -2;
      return {
        x,
        yOffset: (Math.random() - 0.5) * 3, // y축 분산 (-1.5 ~ +1.5)
        waveGroup: Math.floor(i / 4),
        size: 0.8 + Math.random() * 0.4,
        extraDelay: Math.random() * 0.2,
      };
    });
  }, []);

  useFrame(({ clock }) => {
    const t = clock.getElapsedTime();
    const o = opacity.current;

    if (!meshRef.current) return;

    const dir = isUp ? 1 : -1;
    // 1초 주기 반복 웨이브
    const cycle = t % 1.0;

    for (let i = 0; i < ARROW_COUNT; i++) {
      const d = arrowData[i];
      // 각 웨이브 그룹 시차 + 개별 시차
      const groupDelay = d.waveGroup * 0.15 + d.extraDelay;
      const localT = cycle - groupDelay;

      if (localT < 0 || localT > 0.6) {
        // 보이지 않는 구간
        dummy.scale.setScalar(0);
      } else {
        // 0~0.6초 동안: 빠르게 나타나서 이동 후 사라짐
        const progress = localT / 0.6;
        // 빠르게 페이드인/아웃
        const alpha = progress < 0.3
          ? progress / 0.3
          : 1 - (progress - 0.3) / 0.7;

        const moveDistance = 3;
        const y = dir * (-0.5 + progress * moveDistance) + d.yOffset;

        dummy.position.set(d.x, y, 0);
        dummy.rotation.set(0, 0, isUp ? 0 : Math.PI);
        dummy.scale.setScalar(d.size * Math.max(0, alpha));
      }

      dummy.updateMatrix();
      meshRef.current.setMatrixAt(i, dummy.matrix);
    }
    meshRef.current.instanceMatrix.needsUpdate = true;
    (meshRef.current.material as THREE.MeshBasicMaterial).opacity = o * 0.8;

  });

  return (
    <group>
      <instancedMesh ref={meshRef} args={[undefined, undefined, ARROW_COUNT]}>
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
