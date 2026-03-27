import { useMemo, useRef } from "react";
import { Shape, ExtrudeGeometry, Vector3 } from "three";
import { Html } from "@react-three/drei";
import { useFrame } from "@react-three/fiber";
import type { Mesh } from "three";
import type { DistrictGeo } from "./seoulDistricts";

interface DistrictMeshProps {
  district: DistrictGeo;
  isSelected: boolean;
  isHovered: boolean;
  onPointerOver: () => void;
  onPointerOut: () => void;
  onClick: () => void;
}

const gradeColors: Record<string, string> = {
  "S등급": "#f9a8b8",
  "A등급": "#f5d86e",
  "B등급": "#8bb8f0",
};

const gradeHoverColors: Record<string, string> = {
  "S등급": "#f47a95",
  "A등급": "#f0c830",
  "B등급": "#5a9ce8",
};

const selectedColor = "#A8BFA9";

function makeGeo(polygon: [number, number][], depth: number, bevel: boolean) {
  const shape = new Shape();
  shape.moveTo(polygon[0][0], -polygon[0][1]);
  for (let i = 1; i < polygon.length; i++) {
    shape.lineTo(polygon[i][0], -polygon[i][1]);
  }
  shape.closePath();
  return new ExtrudeGeometry(shape, {
    depth,
    bevelEnabled: bevel,
    bevelThickness: bevel ? 0.04 : 0,
    bevelSize: bevel ? 0.04 : 0,
    bevelSegments: bevel ? 2 : 0,
  });
}

export default function DistrictMesh({
  district, isSelected, isHovered, onPointerOver, onPointerOut, onClick,
}: DistrictMeshProps) {
  const meshRef = useRef<Mesh>(null);

  const geometry = useMemo(() => makeGeo(district.polygon, 0.35, true), [district.polygon]);

  const baseColor = isSelected ? selectedColor : gradeColors[district.grade] || "#94a3b8";
  const hoverColor = isSelected ? "#8DA98E" : gradeHoverColors[district.grade] || "#64748b";
  const color = isHovered ? hoverColor : baseColor;
  const targetY = isSelected ? 0.5 : isHovered ? 0.25 : 0;

  useFrame(() => {
    if (meshRef.current) {
      meshRef.current.position.y += (targetY - meshRef.current.position.y) * 0.12;
    }
  });

  return (
    <group>
      <mesh
        ref={meshRef}
        geometry={geometry}
        rotation={[-Math.PI / 2, 0, 0]}
        onPointerOver={(e) => { e.stopPropagation(); onPointerOver(); }}
        onPointerOut={onPointerOut}
        onClick={(e) => { e.stopPropagation(); onClick(); }}
        castShadow
        receiveShadow
      >
        <meshStandardMaterial
          color={color}
          metalness={0}
          roughness={0.7}
          transparent
          opacity={isSelected ? 1 : isHovered ? 0.97 : 0.95}
        />
      </mesh>

      {/* Label */}
      <Html
        position={new Vector3(district.center[0], (isSelected ? 0.9 : isHovered ? 0.6 : 0.4), district.center[1])}
        center
        zIndexRange={[1, 0]}
        style={{ pointerEvents: "none" }}
      >
        <div className={`flex flex-col items-center transition-all duration-200 ${isSelected || isHovered ? "scale-110" : ""}`}>
          <div className={`px-2.5 py-1 rounded-lg text-[11px] font-bold whitespace-nowrap shadow-md ${
            isSelected ? "bg-primary text-white" : isHovered ? "bg-white text-slate-800" : "bg-white/90 text-slate-600"
          }`}>
            {district.name}
          </div>
          {(isSelected || isHovered) && (
            <div className="mt-1 bg-slate-800/90 text-white text-[9px] font-bold px-2 py-0.5 rounded whitespace-nowrap">
              {district.grade} · {district.rent}/일
            </div>
          )}
        </div>
      </Html>
    </group>
  );
}

// ─── Background (flat, non-interactive) ───
export function BackgroundMesh({ polygon, name, center }: { polygon: [number, number][]; name: string; center: [number, number] }) {
  const geometry = useMemo(() => makeGeo(polygon, 0.04, false), [polygon]);

  return (
    <group>
      <mesh geometry={geometry} rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.05, 0]} receiveShadow>
        <meshStandardMaterial color="#f5f3ef" metalness={0} roughness={0.9} />
      </mesh>
      {/* Label */}
      <Html
        position={new Vector3(center[0], 0.08, center[1])}
        center
        zIndexRange={[1, 0]}
        style={{ pointerEvents: "none" }}
      >
        <span className="text-[8px] text-slate-400/60 font-medium whitespace-nowrap select-none">{name}</span>
      </Html>
    </group>
  );
}
