import { lazy, Suspense } from "react";
import { useTutorialStore } from "../../stores/useTutorialStore";

const DashboardStep = lazy(() => import("./steps/DashboardStep"));
const LocationStep = lazy(() => import("./steps/LocationStep"));
const PrepStep = lazy(() => import("./steps/PrepStep"));
const PlayHeaderStep = lazy(() => import("./steps/PlayHeaderStep"));
const EventStep = lazy(() => import("./steps/EventStep"));
const ActionStep = lazy(() => import("./steps/ActionStep"));
const ReportStep = lazy(() => import("./steps/ReportStep"));
const Day2PrepStep = lazy(() => import("./steps/Day2PrepStep"));
const BankruptcyStep = lazy(() => import("./steps/BankruptcyStep"));
const FinalRankingStep = lazy(() => import("./steps/FinalRankingStep"));

const STEP_COMPONENTS = [
  DashboardStep,    // 0
  LocationStep,     // 1
  PrepStep,         // 2
  PlayHeaderStep,   // 3
  EventStep,        // 4
  ActionStep,       // 5
  ReportStep,       // 6
  Day2PrepStep,     // 7 (AI 뉴스 + 재고 관리 통합)
  BankruptcyStep,   // 8
  FinalRankingStep, // 9
];

function StepFallback() {
  return (
    <div className="flex items-center justify-center h-64">
      <div className="flex items-center gap-2 text-slate-400">
        <span className="material-symbols-outlined animate-spin">progress_activity</span>
        로딩 중...
      </div>
    </div>
  );
}

export default function TutorialStepRenderer() {
  const { currentStep } = useTutorialStore();
  const StepComponent = STEP_COMPONENTS[currentStep];

  if (!StepComponent) return null;

  return (
    <Suspense fallback={<StepFallback />}>
      <StepComponent />
    </Suspense>
  );
}
