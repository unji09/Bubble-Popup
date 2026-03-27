import axios from "axios";
import { useState, type FormEvent } from "react";
import AppHeader from "../components/common/AppHeader";
import Badge from "../components/common/Badge";
import Button from "../components/common/Button";
import FloatingBubbles from "../components/common/FloatingBubbles";
import {
  reserveSeasonDemoSkip,
  type SeasonDemoSkipResponse,
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

function extractErrorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const responseMessage = error.response?.data?.message;
    if (typeof responseMessage === "string" && responseMessage.trim()) {
      return responseMessage;
    }
  }
  return "데모 스킵 예약 요청을 처리하지 못했습니다.";
}

export default function AdminDemoSkipPage() {
  const [seasonIdInput, setSeasonIdInput] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<SeasonDemoSkipResponse | null>(null);
  const [submissionState, setSubmissionState] = useState<SubmissionState>({
    type: "idle",
    message: null,
  });

  const parsedSeasonId = Number(seasonIdInput);
  const isSeasonIdValid =
    seasonIdInput.trim().length > 0 &&
    Number.isInteger(parsedSeasonId) &&
    parsedSeasonId > 0;

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
        message: extractErrorMessage(error),
      });
    } finally {
      setSubmitting(false);
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
