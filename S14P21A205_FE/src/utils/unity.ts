import type { RefObject } from "react";

const WEATHER_NAME_MAP: Record<string, string> = {
  SUNNY: "Clear",
  RAIN: "Rain",
  SNOW: "Snow",
  HEATWAVE: "Clear",
  COLDWAVE: "Clear",
  FOG: "Fog",
};

export function mapWeatherToUnity(weatherType: string): string {
  return WEATHER_NAME_MAP[weatherType] ?? "Clear";
}

export function sendToUnity(
  iframeRef: RefObject<HTMLIFrameElement | null>,
  method: string,
  payload = "",
) {
  iframeRef.current?.contentWindow?.postMessage(
    { type: "unity", method, payload },
    "*",
  );
}

/** 카메라를 특정 지역으로 이동 (index: 0~) */
export function setCameraRegion(
  iframeRef: RefObject<HTMLIFrameElement | null>,
  regionIndex: number,
) {
  sendToUnity(iframeRef, "SetCameraRegion", String(regionIndex));
}

/** 카메라를 메인 뷰로 복귀 */
export function returnToMain(
  iframeRef: RefObject<HTMLIFrameElement | null>,
) {
  sendToUnity(iframeRef, "ReturnToMain");
}

/** 팝업 방문객 스폰 (payload: "regionIndex,count") */
export function spawnPopupVisitors(
  iframeRef: RefObject<HTMLIFrameElement | null>,
  regionIndex: number,
  count: number,
) {
  sendToUnity(iframeRef, "SpawnPopupVisitors", `${regionIndex},${count}`);
}

/** 혼잡도 레벨 설정 */
export function setCongestionLevel(
  iframeRef: RefObject<HTMLIFrameElement | null>,
  level: number,
) {
  sendToUnity(iframeRef, "SetCongestionLevel", String(level));
}

/** 매장 스폰 (카메라 이동 없이) */
export function spawnShopAtIndex(
  iframeRef: RefObject<HTMLIFrameElement | null>,
  regionIndex: number,
) {
  sendToUnity(iframeRef, "SpawnShopAtIndex", String(regionIndex));
}

/** 날씨 설정 (백엔드 weatherType을 자동 변환, 카메라 지역 인덱스 포함) */
export function setWeather(
  iframeRef: RefObject<HTMLIFrameElement | null>,
  weatherType: string,
  regionIndex: number,
) {
  const weatherName = mapWeatherToUnity(weatherType);
  sendToUnity(iframeRef, "SetWeather", `${weatherName},${regionIndex}`);
}

/** 낮으로 설정 */
export function setDay(
  iframeRef: RefObject<HTMLIFrameElement | null>,
) {
  sendToUnity(iframeRef, "SetDay");
}

/** 하루 시작 (영업 시간(초)을 넘기면 Unity가 낮→노을→밤 자동 진행) */
export function startDay(
  iframeRef: RefObject<HTMLIFrameElement | null>,
  durationSeconds: number,
) {
  sendToUnity(iframeRef, "StartDay", String(durationSeconds));
}
