import { useTutorialStore, TOTAL_TUTORIAL_STEPS, TUTORIAL_CHAPTERS } from "../../stores/useTutorialStore";

export default function TutorialNavFooter() {
  const { currentStep, prevStep, nextStep } = useTutorialStore();
  const isFirst = currentStep === 0;
  const isLast = currentStep === TOTAL_TUTORIAL_STEPS - 1;

  // 현재 스텝의 타이틀 찾기
  const currentTitle = TUTORIAL_CHAPTERS
    .flatMap((ch) => ch.steps)
    .find((s) => s.index === currentStep)?.title ?? "";

  return (
    <div className="flex items-center justify-between px-6 pr-24 py-4 bg-white border-t border-slate-100">
      <button
        onClick={prevStep}
        disabled={isFirst}
        className={`
          flex items-center gap-1.5 px-4 py-2.5 rounded-xl text-sm font-bold transition-all
          ${isFirst
            ? "text-slate-300 cursor-not-allowed"
            : "text-slate-600 hover:bg-slate-50 hover:text-slate-800"
          }
        `}
      >
        <span className="material-symbols-outlined text-lg">chevron_left</span>
        이전
      </button>

      <span className="text-sm text-slate-500 font-medium hidden sm:block">
        {currentStep + 1}. {currentTitle}
      </span>

      <button
        onClick={nextStep}
        className={`
          flex items-center gap-1.5 px-5 py-2.5 rounded-xl text-sm font-bold transition-all
          ${isLast
            ? "bg-primary text-white hover:bg-primary-dark"
            : "bg-primary text-white hover:bg-primary-dark"
          }
        `}
      >
        {isLast ? "완료" : "다음"}
        {!isLast && <span className="material-symbols-outlined text-lg">chevron_right</span>}
      </button>
    </div>
  );
}
