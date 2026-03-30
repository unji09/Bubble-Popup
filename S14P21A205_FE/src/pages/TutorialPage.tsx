import { useState } from "react";
import { useTutorialStore } from "../stores/useTutorialStore";
import { TutorialDataProvider } from "../components/tutorial/TutorialDataContext";
import TutorialProgressBar from "../components/tutorial/TutorialProgressBar";
import ChapterSidebar from "../components/tutorial/ChapterSidebar";
import TutorialStepRenderer from "../components/tutorial/TutorialStepRenderer";
import TutorialNavFooter from "../components/tutorial/TutorialNavFooter";
import AppHeader from "../components/common/AppHeader";

export default function TutorialPage() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const { currentStep } = useTutorialStore();
  const isPlayStep = currentStep >= 3 && currentStep <= 5;

  return (
    <TutorialDataProvider>
    <div className="flex flex-col h-screen bg-surface overflow-hidden">
      {/* 앱 헤더 */}
      <AppHeader />

      {/* 진행률 + 모바일 메뉴 버튼 (AppHeader h-16 아래) */}
      <div className="shrink-0 mt-16 flex items-center bg-white/80 backdrop-blur-sm border-b border-slate-100">
        <button
          onClick={() => setSidebarOpen(true)}
          className="lg:hidden shrink-0 pl-4 pr-2 py-3 text-slate-500 hover:text-slate-700"
        >
          <span className="material-symbols-outlined">menu</span>
        </button>
        <div className="flex-1">
          <TutorialProgressBar />
        </div>
      </div>

      {/* 본문 */}
      <div className="flex flex-1 min-h-0">
        {/* 사이드바 */}
        <ChapterSidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />

        {/* 메인 콘텐츠 */}
        <main className="flex-1 flex flex-col min-w-0">
          <div className={`flex-1 overflow-y-auto ${isPlayStep ? "" : "p-4 md:p-6 lg:p-8"}`}>
            <TutorialStepRenderer />
          </div>

          {/* 하단 네비게이션 */}
          <TutorialNavFooter />
        </main>
      </div>
    </div>
    </TutorialDataProvider>
  );
}
