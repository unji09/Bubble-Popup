import { useState, Suspense, useMemo } from "react";
import { Canvas } from "@react-three/fiber";
import { OrbitControls, Environment, ContactShadows } from "@react-three/drei";
import { Shape, ExtrudeGeometry, PCFShadowMap } from "three";
import DistrictMesh, { BackgroundMesh } from "./DistrictMesh";
import { seoulDistricts, backgroundGus } from "./seoulDistricts";

interface SeoulMap3DProps {
  selectedId: number | null;
  onSelect: (id: number) => void;
}

// 한강 — 강북 남쪽 경계(north bank)와 강남 북쪽 경계(south bank) 사이
// 강북 구의 최남단 경계점들 (z값이 가장 큰 점들)
const NORTH_BANK: [number, number][] = [
  [-10.2, -2.3], [-7.5, -0.2], [-6.56, 0.31], [-3.67, 0.98],
  [-2.92, 1.56], [-1.74, 2.76], [-1.13, 2.98],
  [0.98, 2.98], [1.71, 2.64], [2.2, 2.39],
  [2.89, 1.7], [4.42, 0.76], [6.69, 1.59],
  [7.57, 1.93], [8.07, 2.05], [10.27, 1.71],
  [11.19, 0.37], [12.35, 0.79], [15.75, 0.24],
];

// 강남 구의 최북단 경계점들 (강남 구역이 z+2.5 offset 됨)
const SOUTH_BANK: [number, number][] = [
  [-10.2, 0.2], [-7.5, 2.3], [-6.56, 2.81], [-3.67, 3.48],
  [-2.92, 4.06], [-1.74, 5.26], [-1.13, 5.48],
  [0.98, 5.48], [1.71, 5.14], [2.2, 4.89],
  [2.89, 4.2], [4.42, 3.26], [6.69, 4.09],
  [7.57, 4.43], [8.07, 4.55], [10.27, 4.21],
  [11.19, 2.87], [12.35, 3.29], [15.75, 2.74],
];

function HanRiver() {
  const geometry = useMemo(() => {
    const shape = new Shape();

    // North bank (강북 남쪽 경계)
    shape.moveTo(NORTH_BANK[0][0], -NORTH_BANK[0][1]);
    for (let i = 1; i < NORTH_BANK.length; i++) shape.lineTo(NORTH_BANK[i][0], -NORTH_BANK[i][1]);
    // South bank (강남 북쪽 경계) — 역순으로
    for (let i = SOUTH_BANK.length - 1; i >= 0; i--) shape.lineTo(SOUTH_BANK[i][0], -SOUTH_BANK[i][1]);
    shape.closePath();

    return new ExtrudeGeometry(shape, { depth: 0.02, bevelEnabled: false });
  }, []);

  return (
    <mesh geometry={geometry} rotation={[-Math.PI / 2, 0, 0]} position={[0, -0.15, 0]}>
      <meshStandardMaterial color="#a8d4f0" metalness={0.1} roughness={0.3} />
    </mesh>
  );
}

function MapScene({ selectedId, onSelect }: SeoulMap3DProps) {
  const [hoveredId, setHoveredId] = useState<number | null>(null);

  return (
    <>
      <ambientLight intensity={0.6} />
      <directionalLight position={[10, 20, 10]} intensity={0.7} castShadow shadow-mapSize={1024} />
      <directionalLight position={[-8, 12, -6]} intensity={0.2} />

      {/* Background gu (flat) */}
      {backgroundGus.map((gu) => (
        <BackgroundMesh key={gu.name} polygon={gu.polygon} name={gu.name} center={gu.center} />
      ))}

      {/* Han River */}
      <HanRiver />

      {/* Interactive districts */}
      {seoulDistricts.map((d) => (
        <DistrictMesh
          key={d.id}
          district={d}
          isSelected={selectedId === d.id}
          isHovered={hoveredId === d.id}
          onPointerOver={() => setHoveredId(d.id)}
          onPointerOut={() => setHoveredId(null)}
          onClick={() => onSelect(d.id)}
        />
      ))}

      <ContactShadows position={[0, -0.01, 0]} opacity={0.12} scale={45} blur={3} />

      <OrbitControls
        makeDefault
        enablePan={false}
        minDistance={18}
        maxDistance={45}
        minPolarAngle={Math.PI / 6}
        maxPolarAngle={Math.PI / 2.5}
        target={[0, 0, 0]}
        autoRotate
        autoRotateSpeed={0.3}
      />

      <Environment preset="city" />
    </>
  );
}

export default function SeoulMap3D({ selectedId, onSelect }: SeoulMap3DProps) {
  return (
    <div className="w-full h-full relative z-0">
      <Canvas
        shadows={{ type: PCFShadowMap }}
        camera={{ position: [0, 22, 22], fov: 42 }}
        style={{ background: "transparent" }}
        onPointerMissed={() => {}}
      >
        <Suspense fallback={null}>
          <MapScene selectedId={selectedId} onSelect={onSelect} />
        </Suspense>
      </Canvas>

      <div className="absolute bottom-4 left-1/2 -translate-x-1/2 flex items-center gap-2 bg-white/80 backdrop-blur-sm px-3 py-1.5 rounded-full shadow-sm text-[11px] text-slate-400 font-medium pointer-events-none">
        <span className="material-symbols-outlined text-[14px]">3d_rotation</span>
        드래그하여 회전 · 스크롤하여 줌
      </div>
    </div>
  );
}
