import axios from "axios";
import { useEffect, useState, type FormEvent } from "react";
import AppHeader from "../components/common/AppHeader";
import Badge from "../components/common/Badge";
import Button from "../components/common/Button";
import FloatingBubbles from "../components/common/FloatingBubbles";
import {
  getSeasonRuntimeControl,
  pauseSeasonRuntime,
  reserveSeasonDemoSkip,
  resumeSeasonRuntime,
  type SeasonDemoSkipResponse,
  type SeasonRuntimeControlResponse,
} from "../api/game";

type SubmissionState =
  | { type: "idle"; message: null }
  | { type: "success"; message: string }
  | { type: "error"; message: string };

const bubbles = [
  {
    size: "w-72 h-72",
    position: "top-[8%] left-[-4%]",
    opacity: "opacity-40",
    delay: "0s",
    variant: "glass" as const,
  },
  {
    size: "w-44 h-44",
    position: "top-[18%] right-[8%]",
    opacity: "opacity-30",
    delay: "2s",
    variant: "solid" as const,
  },
  {
    size: "w-32 h-32",
    position: "bottom-[10%] left-[12%]",
    opacity: "opacity-20",
    delay: "4s",
    variant: "glass" as const,
  },
];

function extractErrorMessage(
  error: unknown,
  fallback: string,
) {
  if (axios.isAxiosError(error)) {
    const responseMessage = error.response?.data?.message;
    if (typeof responseMessage === "string" && responseMessage.trim()) {
      return responseMessage;
    }
  }
  return fallback;
}

function formatDateTime(value: string | null) {
  if (!value) {
    return "-";
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  return parsed.toLocaleString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  });
}

export default function AdminDemoSkipPage() {
  const [seasonIdInput, setSeasonIdInput] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [runtimeLoading, setRuntimeLoading] = useState(true);
  const [runtimeSubmitting, setRuntimeSubmitting] = useState<
    "pause" | "resume" | "refresh" | null
  >(null);
  const [result, setResult] = useState<SeasonDemoSkipResponse | null>(null);
  const [runtimeControl, setRuntimeControl] =
    useState<SeasonRuntimeControlResponse | null>(null);
  const [submissionState, setSubmissionState] = useState<SubmissionState>({
    type: "idle",
    message: null,
  });
  const [runtimeState, setRuntimeState] = useState<SubmissionState>({
    type: "idle",
    message: null,
  });

  const parsedSeasonId = Number(seasonIdInput);
  const isSeasonIdValid =
    seasonIdInput.trim().length > 0 &&
    Number.isInteger(parsedSeasonId) &&
    parsedSeasonId > 0;

  const loadRuntimeControl = async (mode: "initial" | "refresh" = "refresh") => {
    if (mode === "initial") {
      setRuntimeLoading(true);
    } else {
      setRuntimeSubmitting("refresh");
    }

    try {
      const response = await getSeasonRuntimeControl();
      setRuntimeControl(response);
      setRuntimeState({ type: "idle", message: null });
    } catch (error) {
      setRuntimeState({
        type: "error",
        message: extractErrorMessage(
          error,
          "런타임 상태를 불러오지 못했습니다.",
        ),
      });
    } finally {
      setRuntimeLoading(false);
      setRuntimeSubmitting((prev) => (prev === "refresh" ? null : prev));
    }
  };

  useEffect(() => {
    loadRuntimeControl("initial");
  }, []);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!isSeasonIdValid) {
      setResult(null);
      setSubmissionState({
        type: "error",
        message: "올바른 seasonId를 입력해 주세요.",
      });
      return;
    }

    setSubmitting(true);
    setSubmissionState({ type: "idle", message: null });

    try {
      const response = await reserveSeasonDemoSkip({
        seasonId: parsedSeasonId,
      });
      setResult(response);
      setSubmissionState({
        type: "success",
        message: response.message,
      });
    } catch (error) {
      setResult(null);
      setSubmissionState({
        type: "error",
        message: extractErrorMessage(
          error,
          "데모 스킵 예약 요청을 처리하지 못했습니다.",
        ),
      });
    } finally {
      setSubmitting(false);
    }
  };

  const handlePause = async () => {
    setRuntimeSubmitting("pause");
    setRuntimeState({ type: "idle", message: null });

    try {
      const response = await pauseSeasonRuntime();
      setRuntimeControl(response);
      setRuntimeState({
        type: "success",
        message: "게임 시간이 정지되었습니다.",
      });
    } catch (error) {
      setRuntimeState({
        type: "error",
        message: extractErrorMessage(
          error,
          "게임 시간 정지 요청을 처리하지 못했습니다.",
        ),
      });
    } finally {
      setRuntimeSubmitting(null);
    }
  };

  const handleResume = async () => {
    setRuntimeSubmitting("resume");
    setRuntimeState({ type: "idle", message: null });

    try {
      const response = await resumeSeasonRuntime();
      setRuntimeControl(response);
      setRuntimeState({
        type: "success",
        message: "게임 시간이 다시 재생되었습니다.",
      });
    } catch (error) {
      setRuntimeState({
        type: "error",
        message: extractErrorMessage(
          error,
          "게임 시간 재생 요청을 처리하지 못했습니다.",
        ),
      });
    } finally {
      setRuntimeSubmitting(null);
    }
  };

  return (
    <div className="min-h-screen overflow-hidden bg-[#FDFDFB] text-slate-900">
      <AppHeader />
      <main className="relative isolate flex min-h-screen items-start justify-center px-4 pb-16 pt-28 sm:px-6">
        <FloatingBubbles bubbles={bubbles} />

        <div className="glass-panel relative z-10 flex w-full max-w-3xl flex-col gap-8 rounded-[32px] px-6 py-8 shadow-[0_24px_80px_rgba(15,23,42,0.08)] sm:px-10 sm:py-10">
          <div className="flex flex-col gap-4">
            <Badge variant="gray" size="md">
              관리자 페이지
            </Badge>
            <div className="space-y-2">
              <h1 className="text-3xl font-black tracking-tight text-slate-900 sm:text-4xl">
                시연용 3일 게임 만들기
              </h1>
              <p className="max-w-2xl text-sm leading-6 text-slate-500 sm:text-base">
                시즌 시작 전 특정 시즌에 데모 스킵 예약을 생성합니다. 이 페이지는
                관리자 계정으로 로그인한 경우에만 접근할 수 있습니다.
              </p>
            </div>
          </div>

          <section className="rounded-3xl border border-slate-200 bg-slate-50/80 p-5">
            <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <h2 className="text-lg font-bold text-slate-900">
                    시즌 시간 제어
                  </h2>
                  <Badge
                    variant={runtimeControl?.paused ? "rose" : "green"}
                    size="md"
                  >
                    {runtimeControl?.paused ? "정지됨" : "재생 중"}
                  </Badge>
                </div>
                <p className="text-sm text-slate-500">
                  현재 시즌 시간을 전역으로 멈추거나 다시 재생합니다.
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  loading={runtimeSubmitting === "refresh"}
                  onClick={() => loadRuntimeControl("refresh")}
                >
                  새로고침
                </Button>
                <Button
                  variant="danger"
                  size="sm"
                  disabled={runtimeLoading || runtimeControl?.paused === true}
                  loading={runtimeSubmitting === "pause"}
                  onClick={handlePause}
                >
                  시간 정지
                </Button>
                <Button
                  variant="primary"
                  size="sm"
                  disabled={runtimeLoading || runtimeControl?.paused !== true}
                  loading={runtimeSubmitting === "resume"}
                  onClick={handleResume}
                >
                  시간 재생
                </Button>
              </div>
            </div>

            {runtimeState.message && (
              <div
                className={`mb-4 rounded-2xl border px-4 py-4 text-sm leading-6 ${
                  runtimeState.type === "success"
                    ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                    : "border-rose-200 bg-rose-50 text-rose-700"
                }`}
              >
                {runtimeState.message}
              </div>
            )}

            <dl className="grid gap-4 text-sm sm:grid-cols-2 lg:grid-cols-4">
              <div className="space-y-1">
                <dt className="text-slate-500">현재 시즌</dt>
                <dd className="font-semibold text-slate-900">
                  {runtimeControl?.currentSeasonId ?? "-"}
                </dd>
              </div>
              <div className="space-y-1">
                <dt className="text-slate-500">시즌 상태</dt>
                <dd className="font-semibold text-slate-900">
                  {runtimeControl?.seasonStatus ?? "-"}
                </dd>
              </div>
              <div className="space-y-1">
                <dt className="text-slate-500">현재 Day</dt>
                <dd className="font-semibold text-slate-900">
                  {runtimeControl?.currentDay ?? "-"}
                </dd>
              </div>
              <div className="space-y-1">
                <dt className="text-slate-500">현재 Phase</dt>
                <dd className="font-semibold text-slate-900">
                  {runtimeControl?.phase ?? "-"}
                </dd>
              </div>
              <div className="space-y-1">
                <dt className="text-slate-500">남은 초</dt>
                <dd className="font-semibold text-slate-900">
                  {runtimeControl?.remainingPhaseSeconds ?? "-"}
                </dd>
              </div>
              <div className="space-y-1">
                <dt className="text-slate-500">정지 시각</dt>
                <dd className="font-semibold text-slate-900">
                  {formatDateTime(runtimeControl?.pausedAt ?? null)}
                </dd>
              </div>
              <div className="space-y-1 sm:col-span-2">
                <dt className="text-slate-500">현재 기준 시각</dt>
                <dd className="font-semibold text-slate-900">
                  {formatDateTime(runtimeControl?.effectiveNow ?? null)}
                </dd>
              </div>
            </dl>
          </section>

          <form className="flex flex-col gap-5" onSubmit={handleSubmit}>
            <label className="flex flex-col gap-2">
              <span className="text-sm font-semibold text-slate-700">
                Season ID
              </span>
              <input
                type="number"
                min={1}
                inputMode="numeric"
                value={seasonIdInput}
                onChange={(event) => setSeasonIdInput(event.target.value)}
                placeholder="예: 12"
                className="h-14 rounded-2xl border border-slate-200 bg-white px-4 text-base text-slate-900 shadow-sm outline-none transition focus:border-primary focus:ring-4 focus:ring-primary/10"
              />
            </label>

            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-sm text-slate-500">
                시작 전 `SCHEDULED` 시즌만 예약됩니다.
              </p>
              <Button
                variant="primary"
                size="md"
                disabled={!isSeasonIdValid}
                loading={submitting}
              >
                예약 생성
              </Button>
            </div>
          </form>

          {submissionState.message && (
            <div
              className={`rounded-2xl border px-4 py-4 text-sm leading-6 ${
                submissionState.type === "success"
                  ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                  : "border-rose-200 bg-rose-50 text-rose-700"
              }`}
            >
              {submissionState.message}
            </div>
          )}

          {result && (
            <div className="rounded-3xl border border-slate-200 bg-slate-50/80 p-5">
              <div className="mb-4 flex items-center justify-between gap-3">
                <h2 className="text-lg font-bold text-slate-900">예약 결과</h2>
                <Badge variant="green" size="md">
                  {result.status}
                </Badge>
              </div>

              <dl className="grid gap-4 text-sm sm:grid-cols-3">
                <div className="space-y-1">
                  <dt className="text-slate-500">Season ID</dt>
                  <dd className="font-semibold text-slate-900">
                    {result.seasonId}
                  </dd>
                </div>
                <div className="space-y-1">
                  <dt className="text-slate-500">Playable Days</dt>
                  <dd className="font-semibold text-slate-900">
                    {result.demoPlayableDays ?? "-"}
                  </dd>
                </div>
                <div className="space-y-1">
                  <dt className="text-slate-500">Status</dt>
                  <dd className="font-semibold text-slate-900">
                    {result.status}
                  </dd>
                </div>
              </dl>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
