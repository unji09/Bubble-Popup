import { useRef } from "react";
import { useFrame } from "@react-three/fiber";

/**
 * 이펙트의 fade-in / hold / fade-out 생명주기를 관리한다.
 * - 0 ~ fadeInMs: opacity 0→1
 * - fadeInMs ~ (durationMs - fadeOutMs): opacity 1
 * - (durationMs - fadeOutMs) ~ durationMs: opacity 1→0
 *
 * @returns opacity ref (매 프레임 갱신됨)
 */
export function useEffectLifecycle(
  durationMs: number,
  fadeInMs = 500,
  fadeOutMs = 1500,
) {
  const startTime = useRef(Date.now());
  const opacity = useRef(0);

  useFrame(() => {
    const elapsed = Date.now() - startTime.current;

    if (elapsed < fadeInMs) {
      opacity.current = elapsed / fadeInMs;
    } else if (elapsed < durationMs - fadeOutMs) {
      opacity.current = 1;
    } else if (elapsed < durationMs) {
      opacity.current = 1 - (elapsed - (durationMs - fadeOutMs)) / fadeOutMs;
    } else {
      opacity.current = 0;
    }
  });

  return opacity;
}
