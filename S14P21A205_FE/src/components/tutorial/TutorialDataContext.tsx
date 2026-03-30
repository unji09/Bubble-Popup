import { createContext, useContext, type ReactNode } from "react";
import { useTutorialData, type TutorialData } from "./useTutorialData";

const TutorialDataContext = createContext<TutorialData | null>(null);

export function TutorialDataProvider({ children }: { children: ReactNode }) {
  const data = useTutorialData();
  return (
    <TutorialDataContext.Provider value={data}>
      {children}
    </TutorialDataContext.Provider>
  );
}

export function useTutorialDataContext(): TutorialData {
  const ctx = useContext(TutorialDataContext);
  if (!ctx) throw new Error("useTutorialDataContext must be used inside TutorialDataProvider");
  return ctx;
}
