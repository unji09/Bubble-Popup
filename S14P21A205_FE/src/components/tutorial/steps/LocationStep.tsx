import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import GuideOverlay from "../GuideOverlay";
import { useTutorialDataContext } from "../TutorialDataContext";
import { useTutorialStore } from "../../../stores/useTutorialStore";
import SeoulMap3D from "../../game/SeoulMap3D";
import { seoulDistricts, type DistrictGeo } from "../../game/seoulDistricts";

const BRAND_NAME_MAX_LENGTH = 10;

/** seoulDistricts name → 서버 locationName 매칭 */
const LOCATION_NAME_MAP: Record<string, string> = {
  "홍대": "홍대",
  "여의도": "여의도",
  "명동": "명동",
  "이태원": "이태원",
  "서울숲/성수": "서울숲/성수",
  "신도림": "신도림",
  "강남": "강남",
  "잠실": "잠실",
};

function getCongestionStyle(congestion: string): { label: string; text: string } {
  const rankMatch = congestion.match(/(\d+)위/);
  if (rankMatch) {
    const rank = Number(rankMatch[1]);
    if (rank <= 2) return { label: congestion, text: "text-rose-500" };
    if (rank <= 5) return { label: congestion, text: "text-amber-600" };
    return { label: congestion, text: "text-sky-600" };
  }
  const toneMap: Record<string, { label: string; text: string }> = {
    "매우 혼잡": { label: "매우 혼잡", text: "text-rose-500" },
    혼잡: { label: "혼잡", text: "text-amber-600" },
    보통: { label: "보통", text: "text-primary-dark" },
    여유: { label: "여유", text: "text-sky-600" },
    "매우 여유": { label: "매우 여유", text: "text-slate-500" },
  };
  return toneMap[congestion] ?? { label: congestion, text: "text-primary-dark" };
}

const gradeTone: Record<string, string> = {
  S: "border-accent-rose bg-accent-rose text-white",
  A: "border-amber-200 bg-amber-100 text-amber-800",
  B: "border-sky-300 bg-sky-100 text-sky-800",
};

/** 지도 위에 absolute로 뜨는 튜토리얼용 상세 패널 */
function MapDetailPanel({
  district,
  interiorCost,
  onComplete,
  onClose,
}: {
  district: DistrictGeo;
  interiorCost: string | null;
  onComplete: (brandName: string) => void;
  onClose: () => void;
}) {
  const [brandName, setBrandName] = useState("");
  const [visible, setVisible] = useState(false);
  const congestionMeta = getCongestionStyle(district.congestion);
  const grade = district.grade.charAt(0);

  useEffect(() => {
    const frame = requestAnimationFrame(() => setVisible(true));
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", handleEscape);
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener("keydown", handleEscape);
    };
  }, [onClose]);

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const trimmed = brandName.trim().slice(0, BRAND_NAME_MAX_LENGTH);
    if (!trimmed) return;
    onComplete(trimmed);
  };

  return (
    <div className="absolute inset-0 z-20 flex items-center justify-center px-4">
      {/* 백드롭 */}
      <div
        className={`absolute inset-0 bg-slate-900/38 backdrop-blur-sm transition-opacity duration-200 rounded-2xl ${
          visible ? "opacity-100" : "opacity-0"
        }`}
        onClick={onClose}
      />

      {/* 패널 */}
      <div
        className={`custom-scrollbar relative max-h-[calc(100%-2rem)] w-full max-w-2xl overflow-y-auto rounded-[30px] border border-white/70 bg-white shadow-premium transition-all duration-300 ease-out ${
          visible ? "translate-y-0 scale-100 opacity-100" : "translate-y-4 scale-95 opacity-0"
        }`}
      >
        <div className="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-primary via-primary-dark to-accent-rose rounded-t-[30px]" />

        <button
          type="button"
          onClick={onClose}
          className="absolute right-5 top-5 z-30 flex h-11 w-11 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 shadow-soft transition-colors hover:border-slate-300 hover:text-slate-800"
          aria-label="닫기"
        >
          <span className="material-symbols-outlined text-[22px]">close</span>
        </button>

        <div className="border-b border-slate-100 bg-[#F8FBF8] px-6 pb-6 pt-7 sm:px-7 rounded-t-[30px]">
          <div className="pr-12">
            <p className="text-[11px] font-bold uppercase tracking-[0.24em] text-slate-400">
              Selected District
            </p>
            <div className="mt-2 flex items-center gap-3">
              <h2 className="text-3xl font-black tracking-tight text-slate-900">
                {district.name}
              </h2>
              <span
                className={`rounded-full border px-4 py-1.5 text-sm font-extrabold ${
                  gradeTone[grade] ?? gradeTone.B
                }`}
              >
                {district.grade}
              </span>
            </div>
          </div>
        </div>

        <div className="p-6 sm:p-7">
          <p className="text-sm leading-relaxed text-slate-500">{district.description}</p>

          <div className="mt-5 flex flex-wrap gap-2">
            {district.tags.map((tag) => (
              <span
                key={tag}
                className="rounded-full border border-primary/15 bg-primary/10 px-3 py-1 text-xs font-semibold text-primary-dark"
              >
                #{tag.replaceAll("_", " ")}
              </span>
            ))}
          </div>

          <div className="mt-6 grid gap-3 sm:grid-cols-3">
            <div className="rounded-2xl border border-slate-200 bg-slate-50/90 p-4">
              <span className="mb-1 block text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                일일 임대료
              </span>
              <span className="font-mono text-lg font-bold text-slate-900">
                {district.rent}
              </span>
            </div>

            <div className="rounded-2xl border border-slate-200 bg-slate-50/90 p-4">
              <span className="mb-1 block text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                유동인구 순위
              </span>
              <span className={`text-lg font-bold ${congestionMeta.text}`}>
                {congestionMeta.label}
              </span>
            </div>

            <div className="rounded-2xl border border-slate-200 bg-slate-50/90 p-4">
              <span className="mb-1 block text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                인테리어 비용
              </span>
              <span className="font-mono text-lg font-bold text-slate-900">
                {interiorCost ?? "-"}
              </span>
            </div>
          </div>

          <p className="mt-4 text-xs text-slate-400">
            인테리어 비용은 선택 지역의 기본 임대료를 기준으로 계산됩니다.
          </p>

          <form className="mt-6" onSubmit={handleSubmit}>
            <label className="mb-2 block text-sm font-semibold text-slate-600">
              팝업 브랜드명
            </label>
            <input
              type="text"
              value={brandName}
              onChange={(e) => setBrandName(e.target.value.slice(0, BRAND_NAME_MAX_LENGTH))}
              maxLength={BRAND_NAME_MAX_LENGTH}
              className="h-12 w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 text-base font-medium text-slate-800 transition-all placeholder:text-slate-300 focus:border-primary focus:ring-2 focus:ring-primary/15"
              placeholder="브랜드 이름을 입력해주세요"
              autoFocus
            />
            <p className="mt-2 text-right text-xs text-slate-400">
              {brandName.length}/{BRAND_NAME_MAX_LENGTH}자
            </p>

            <button
              type="submit"
              disabled={!brandName.trim()}
              className="mt-5 flex h-12 w-full items-center justify-center gap-2 rounded-2xl bg-primary font-bold text-slate-900 shadow-lg shadow-primary/20 transition-all hover:bg-primary-dark hover:text-white active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-40"
            >
              <span>{district.name}에서 시작하기</span>
              <span className="material-symbols-outlined text-lg">arrow_forward</span>
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}

export default function LocationStep() {
  const { locations } = useTutorialDataContext();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const { updateGameState } = useTutorialStore();

  // seoulDistricts에 API 임대료 반영
  const mergedDistricts = useMemo(() => {
    if (locations.length === 0) return seoulDistricts;
    return seoulDistricts.map((district) => {
      const serverName = LOCATION_NAME_MAP[district.name] ?? district.name;
      const loc = locations.find((l) => l.name === serverName);
      if (!loc) return district;
      return {
        ...district,
        rent: `₩${loc.rent.toLocaleString()}`,
      };
    });
  }, [locations]);

  const selectedDistrict = mergedDistricts.find((d) => d.id === selectedId);

  const selectedInteriorCost = useMemo(() => {
    if (!selectedDistrict) return null;
    const serverName = LOCATION_NAME_MAP[selectedDistrict.name] ?? selectedDistrict.name;
    const loc = locations.find((l) => l.name === serverName);
    if (loc) return `₩${loc.interiorCost.toLocaleString()}`;
    return null;
  }, [selectedDistrict, locations]);

  const handleSelect = (id: number) => {
    setSelectedId(id);
    updateGameState({ selectedLocationId: id });
  };

  const handleComplete = (brandName: string) => {
    updateGameState({ brandName });
    setSelectedId(null);
  };

  return (
    <GuideOverlay
      title="지역 선택"
      description={
        <span>
          서울의 <strong>8개 지역</strong> 중 하나를 선택하여 팝업 매장을 열 수 있어요.
          지역마다 임대료와 유동인구가 달라요! 3D 지도에서 지역을 클릭해보세요.
        </span>
      }
    >
      <div className="space-y-6">
        {/* 3D 서울 지도 + 상세 패널 오버레이 */}
        <div
          className="relative rounded-2xl overflow-hidden bg-gradient-to-b from-sky-50 to-white border border-slate-100 shadow-soft"
          style={{ height: "480px" }}
        >
          <SeoulMap3D
            districts={mergedDistricts}
            selectedId={selectedId}
            onSelect={handleSelect}
          />

          {selectedDistrict && (
            <MapDetailPanel
              district={selectedDistrict}
              interiorCost={selectedInteriorCost}
              onComplete={handleComplete}
              onClose={() => setSelectedId(null)}
            />
          )}
        </div>

        {/* 안내 */}
        <div className="space-y-2 text-sm text-slate-600 p-4 bg-slate-50 rounded-xl">
          <p>💰 초기 자본금: <strong>5,000,000원</strong> (여기서 인테리어 비용이 차감돼요!)</p>
          <p>🏗️ 인테리어 비용은 지역마다 달라요 — 비싼 지역일수록 초기 잔액이 줄어들어요</p>
          <p>🏙️ 같은 지역에 매장이 몰리면 임대료가 더 올라갈 수 있어요</p>
        </div>
      </div>
    </GuideOverlay>
  );
}
