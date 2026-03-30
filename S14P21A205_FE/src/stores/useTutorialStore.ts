import { create } from "zustand";

const STORAGE_KEY = "bp_tutorial";

interface TutorialGameState {
  selectedItemIds: number[];
  selectedLocationId: number | null;
  brandName: string;
  selectedMenuId: number | null;
  menuPrice: number;
  orderQuantity: number;
}

interface TutorialState {
  currentStep: number;
  completedSteps: number[];
  hasCompletedTutorial: boolean;
  tutorialGameState: TutorialGameState;

  goToStep: (step: number) => void;
  completeCurrentStep: () => void;
  nextStep: () => void;
  prevStep: () => void;
  resetTutorial: () => void;
  updateGameState: (partial: Partial<TutorialGameState>) => void;
}

const TOTAL_STEPS = 10;

function readPersisted(): Partial<TutorialState> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    return {
      currentStep: parsed.currentStep ?? 0,
      completedSteps: parsed.completedSteps ?? [],
      hasCompletedTutorial: parsed.hasCompletedTutorial ?? false,
    };
  } catch {
    return {};
  }
}

function persist(state: TutorialState) {
  try {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        currentStep: state.currentStep,
        completedSteps: state.completedSteps,
        hasCompletedTutorial: state.hasCompletedTutorial,
      }),
    );
  } catch {
    // ignore
  }
}

const defaultGameState: TutorialGameState = {
  selectedItemIds: [],
  selectedLocationId: null,
  brandName: "",
  selectedMenuId: null,
  menuPrice: 0,
  orderQuantity: 0,
};

const persisted = readPersisted();

export const useTutorialStore = create<TutorialState>((set, get) => ({
  currentStep: persisted.currentStep ?? 0,
  completedSteps: persisted.completedSteps ?? [],
  hasCompletedTutorial: persisted.hasCompletedTutorial ?? false,
  tutorialGameState: { ...defaultGameState },

  goToStep: (step: number) => {
    set({ currentStep: Math.max(0, Math.min(step, TOTAL_STEPS - 1)) });
    persist(get());
  },

  completeCurrentStep: () => {
    const { currentStep, completedSteps } = get();
    const updated = completedSteps.includes(currentStep)
      ? completedSteps
      : [...completedSteps, currentStep];
    const isAllDone = updated.length >= TOTAL_STEPS;
    set({ completedSteps: updated, hasCompletedTutorial: isAllDone });
    persist(get());
  },

  nextStep: () => {
    const { currentStep } = get();
    const next = Math.min(currentStep + 1, TOTAL_STEPS - 1);
    get().completeCurrentStep();
    set({ currentStep: next });
    persist(get());
  },

  prevStep: () => {
    const { currentStep } = get();
    set({ currentStep: Math.max(currentStep - 1, 0) });
    persist(get());
  },

  resetTutorial: () => {
    set({
      currentStep: 0,
      completedSteps: [],
      hasCompletedTutorial: false,
      tutorialGameState: { ...defaultGameState },
    });
    persist(get());
  },

  updateGameState: (partial) => {
    set((state) => ({
      tutorialGameState: { ...state.tutorialGameState, ...partial },
    }));
  },
}));

export const TUTORIAL_CHAPTERS = [
  {
    title: "시작하기",
    steps: [{ index: 0, title: "대시보드" }],
  },
  {
    title: "매장 준비",
    steps: [
      { index: 1, title: "지역 선택" },
      { index: 2, title: "영업 준비" },
    ],
  },
  {
    title: "영업 안내",
    steps: [
      { index: 3, title: "영업 화면" },
      { index: 4, title: "이벤트 도감" },
      { index: 5, title: "액션 체험" },
    ],
  },
  {
    title: "결과 확인",
    steps: [
      { index: 6, title: "일일 리포트" },
      { index: 7, title: "2일차 영업 준비" },
    ],
  },
  {
    title: "시즌 종료",
    steps: [
      { index: 8, title: "파산 조건" },
      { index: 9, title: "최종 랭킹" },
    ],
  },
];

export const TOTAL_TUTORIAL_STEPS = TOTAL_STEPS;
