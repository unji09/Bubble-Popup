import { useTutorialStore, TUTORIAL_CHAPTERS } from "../../stores/useTutorialStore";

interface ChapterSidebarProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function ChapterSidebar({ isOpen, onClose }: ChapterSidebarProps) {
  const { currentStep, completedSteps, goToStep } = useTutorialStore();

  return (
    <>
      {/* 모바일 오버레이 */}
      {isOpen && (
        <div className="fixed inset-0 bg-black/30 z-40 lg:hidden" onClick={onClose} />
      )}

      <aside
        className={`
          fixed lg:static top-0 left-0 z-50 h-full w-72
          bg-white border-r border-slate-100 overflow-y-auto
          transition-transform duration-300 ease-out
          ${isOpen ? "translate-x-0" : "-translate-x-full lg:translate-x-0"}
        `}
      >
        <div className="p-5">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-lg font-bold text-slate-800 flex items-center gap-2">
              <span className="material-symbols-outlined text-primary">menu_book</span>
              게임 가이드
            </h2>
            <button onClick={onClose} className="lg:hidden text-slate-400 hover:text-slate-600">
              <span className="material-symbols-outlined">close</span>
            </button>
          </div>

          <nav className="flex flex-col gap-4">
            {TUTORIAL_CHAPTERS.map((chapter, chIdx) => (
              <div key={chIdx}>
                <div className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-2 px-2">
                  Ch.{chIdx + 1} {chapter.title}
                </div>
                <div className="flex flex-col gap-1">
                  {chapter.steps.map((step) => {
                    const isActive = step.index === currentStep;
                    const isCompleted = completedSteps.includes(step.index);

                    return (
                      <button
                        key={step.index}
                        onClick={() => {
                          goToStep(step.index);
                          onClose();
                        }}
                        className={`
                          flex items-center gap-2.5 px-3 py-2.5 rounded-xl text-sm text-left transition-all
                          ${isActive
                            ? "bg-primary/10 text-primary font-bold shadow-sm"
                            : isCompleted
                              ? "text-slate-600 hover:bg-slate-50"
                              : "text-slate-400 hover:bg-slate-50"
                          }
                        `}
                      >
                        <span className={`
                          flex items-center justify-center w-6 h-6 rounded-full text-xs font-bold shrink-0
                          ${isActive
                            ? "bg-primary text-white"
                            : isCompleted
                              ? "bg-primary/20 text-primary"
                              : "bg-slate-100 text-slate-400"
                          }
                        `}>
                          {isCompleted && !isActive ? (
                            <span className="material-symbols-outlined text-sm">check</span>
                          ) : (
                            step.index + 1
                          )}
                        </span>
                        <span className="truncate">{step.title}</span>
                      </button>
                    );
                  })}
                </div>
              </div>
            ))}
          </nav>
        </div>
      </aside>
    </>
  );
}
