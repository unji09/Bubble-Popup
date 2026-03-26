import { useEffect, useState } from "react";
import GuestHeader from "../components/common/GuestHeader";
import FloatingBubbles from "../components/common/FloatingBubbles";
import HeroCarousel, { HeroCTA, AnimatedParticipants } from "../components/common/HeroCarousel";
import { getGameWaitingStatus, type GameWaitingStatus } from "../api/game";

const landingBubbles = [
  { size: "w-96 h-96", position: "top-[-10%] left-[-10%]", opacity: "opacity-40", delay: "0s", variant: "glass" as const },
  { size: "w-64 h-64", position: "bottom-10 right-[-5%]", opacity: "opacity-30", delay: "2s", variant: "glass" as const },
  { size: "w-32 h-32", position: "top-1/4 right-[20%]", opacity: "opacity-60", delay: "4s", variant: "solid" as const },
  { size: "w-20 h-20", position: "bottom-1/3 left-[10%]", opacity: "opacity-20", delay: "1s", variant: "glass" as const },
];

export default function LobbyPage() {
  const [seasonNumber, setSeasonNumber] = useState<number | null>(null);
  const [seasonStatus, setSeasonStatus] = useState<GameWaitingStatus | null>(null);
  const [participantCount, setParticipantCount] = useState<number | null>(null);

  useEffect(() => {
    getGameWaitingStatus()
      .then((data) => {
        setSeasonNumber(data.nextSeasonNumber);
        setSeasonStatus(data.status);
        setParticipantCount(data.participantCount);
      })
      .catch(() => {});
  }, []);

  return (
    <div className="relative min-h-screen w-full flex flex-col bg-gradient-to-br from-[#F4F7F4] to-[#FFFBF2] text-slate-900 overflow-hidden font-display">
      <FloatingBubbles bubbles={landingBubbles} />
      <GuestHeader />

      <main className="flex-1 flex items-center justify-center w-full px-6 md:px-16 z-10 pt-20">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-20 w-full max-w-[1280px] mx-auto items-center">
          <div className="lg:col-span-7 order-2 lg:order-1">
            <HeroCarousel />
          </div>
          <div className="lg:col-span-5 flex flex-col justify-center order-1 lg:order-2 mb-8 lg:mb-0">
            <HeroCTA seasonNumber={seasonNumber} status={seasonStatus} />
            <div className="mt-8">
              <AnimatedParticipants count={participantCount ?? 0} />
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
