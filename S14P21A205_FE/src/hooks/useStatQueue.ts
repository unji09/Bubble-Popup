import { useCallback, useRef } from "react";

interface QueueEntry {
  stockStep: number;
  balanceStep: number;
}

const INTERVAL_MS = 150;

function getStepCount(delta: number): number {
  const abs = Math.abs(delta);
  if (abs < 100) return 1;
  if (abs < 10_000) return 3;
  if (abs < 100_000) return 5;
  return 8;
}

function splitIntoSteps(delta: number, steps: number): number[] {
  if (steps <= 1) return [delta];
  const step = Math.round(delta / steps);
  const result = Array(steps).fill(step) as number[];
  result[steps - 1] = delta - step * (steps - 1);
  return result;
}

export default function useStatQueue(
  setStock: React.Dispatch<React.SetStateAction<number>>,
  setBalance: React.Dispatch<React.SetStateAction<number>>,
) {
  const queueRef = useRef<QueueEntry[]>([]);
  const timerRef = useRef<number | null>(null);

  const processNext = useCallback(() => {
    const entry = queueRef.current.shift();
    if (!entry) {
      if (timerRef.current !== null) {
        window.clearInterval(timerRef.current);
        timerRef.current = null;
      }
      return;
    }

    if (entry.stockStep !== 0) {
      setStock((prev) => prev + entry.stockStep);
    }
    if (entry.balanceStep !== 0) {
      setBalance((prev) => prev + entry.balanceStep);
    }

    if (queueRef.current.length === 0 && timerRef.current !== null) {
      window.clearInterval(timerRef.current);
      timerRef.current = null;
    }
  }, [setStock, setBalance]);

  const startTimer = useCallback(() => {
    if (timerRef.current !== null) return;
    timerRef.current = window.setInterval(processNext, INTERVAL_MS);
  }, [processNext]);

  const flush = useCallback(() => {
    if (timerRef.current !== null) {
      window.clearInterval(timerRef.current);
      timerRef.current = null;
    }

    let totalStock = 0;
    let totalBalance = 0;
    for (const entry of queueRef.current) {
      totalStock += entry.stockStep;
      totalBalance += entry.balanceStep;
    }
    queueRef.current = [];

    if (totalStock !== 0) {
      setStock((prev) => prev + totalStock);
    }
    if (totalBalance !== 0) {
      setBalance((prev) => prev + totalBalance);
    }
  }, [setStock, setBalance]);

  const enqueue = useCallback(
    (opts: {
      targetStock: number;
      targetBalance: number;
      currentStock: number;
      currentBalance: number;
    }) => {
      flush();

      const stockDelta = opts.targetStock - opts.currentStock;
      const balanceDelta = opts.targetBalance - opts.currentBalance;

      if (stockDelta === 0 && balanceDelta === 0) return;

      const steps = Math.max(getStepCount(stockDelta), getStepCount(balanceDelta));
      const stockSteps = splitIntoSteps(stockDelta, steps);
      const balanceSteps = splitIntoSteps(balanceDelta, steps);

      for (let i = 0; i < steps; i++) {
        queueRef.current.push({
          stockStep: stockSteps[i],
          balanceStep: balanceSteps[i],
        });
      }

      startTimer();
    },
    [flush, startTimer],
  );

  const enqueueDelta = useCallback(
    (opts: { stockDelta?: number; balanceDelta?: number }) => {
      const stockDelta = opts.stockDelta ?? 0;
      const balanceDelta = opts.balanceDelta ?? 0;

      if (stockDelta === 0 && balanceDelta === 0) return;

      const steps = Math.max(getStepCount(stockDelta), getStepCount(balanceDelta));
      const stockSteps = splitIntoSteps(stockDelta, steps);
      const balanceSteps = splitIntoSteps(balanceDelta, steps);

      for (let i = 0; i < steps; i++) {
        queueRef.current.push({
          stockStep: stockSteps[i],
          balanceStep: balanceSteps[i],
        });
      }

      startTimer();
    },
    [startTimer],
  );

  return { enqueue, enqueueDelta, flush };
}
