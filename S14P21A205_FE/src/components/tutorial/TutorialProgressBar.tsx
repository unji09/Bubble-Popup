import { useTutorialStore, TOTAL_TUTORIAL_STEPS } from "../../stores/useTutorialStore";

export default function TutorialProgressBar() {
  const { currentStep, completedSteps } = useTutorialStore();
  const progress = Math.round(((completedSteps.length) / TOTAL_TUTORIAL_STEPS) * 100);

  return (
    <div className="flex items-center gap-3 px-6 py-3">
      <span className="text-xs font-bold text-slate-500 whitespace-nowrap">
        {currentStep + 1} / {TOTAL_TUTORIAL_STEPS}
      </span>
      <div className="flex-1 h-2 bg-slate-100 rounded-full overflow-hidden">
        <div
          className="h-full bg-primary rounded-full transition-all duration-500 ease-out"
          style={{ width: `${progress}%` }}
        />
      </div>
      <span className="text-xs font-bold text-primary whitespace-nowrap">{progress}%</span>
    </div>
  );
}
