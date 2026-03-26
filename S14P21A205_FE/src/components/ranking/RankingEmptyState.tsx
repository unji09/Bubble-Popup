export default function RankingEmptyState() {
  return (
    <section className="relative overflow-hidden rounded-[36px] border border-[#E5E8E0] bg-white/90 px-6 py-12 shadow-soft sm:px-10 sm:py-16">
      <div
        className="pointer-events-none absolute inset-0 opacity-90"
        style={{
          background:
            "radial-gradient(circle at 15% 20%, rgba(231, 237, 226, 0.85), transparent 28%), radial-gradient(circle at 85% 78%, rgba(245, 242, 236, 0.92), transparent 30%), linear-gradient(180deg, rgba(255,255,255,0.96) 0%, rgba(253,253,251,0.96) 100%)",
        }}
      />
      <div className="absolute -left-10 top-10 size-32 rounded-full bg-[#EDF2E8] blur-3xl" />
      <div className="absolute -right-12 bottom-8 size-40 rounded-full bg-[#F7F2EC] blur-3xl" />

      <div className="relative mx-auto flex max-w-[560px] flex-col items-center text-center">
        <div
          className="relative mb-10 flex min-h-[240px] w-full items-end justify-center sm:min-h-[300px]"
          role="img"
          aria-label="텅"
        >
          <div className="select-none text-[#262225] [text-shadow:0_10px_16px_rgba(24,24,27,0.14)]">
            <span className="block text-[clamp(6rem,20vw,9rem)] font-black leading-[0.82] tracking-[-0.16em]">
              터
            </span>
            <span className="block -mt-4 text-[clamp(5.8rem,18vw,8.2rem)] font-black leading-[0.75] tracking-[-0.08em]">
              ㅇ
            </span>
          </div>

          <div className="absolute bottom-4 left-[24%] translate-y-1/3 rotate-[-16deg] sm:left-[27%]">
            <div className="relative h-16 w-12 rounded-b-[18px] rounded-t-[10px] bg-[#76C8CF] shadow-[0_10px_18px_rgba(27,55,59,0.18)]">
              <div className="absolute inset-x-1 top-3 h-4 rounded-full bg-white/70" />
              <div className="absolute left-1/2 top-[-18px] h-9 w-1 -translate-x-1/2 rounded-full bg-slate-200" />
              <div className="absolute left-[64%] top-[-13px] h-7 w-1 rotate-[14deg] rounded-full bg-slate-200" />
            </div>
            <div className="absolute -bottom-1 left-1 h-3 w-10 rounded-full bg-black/10 blur-sm" />
          </div>

          <div className="absolute bottom-2 right-[24%] translate-y-1/3 rotate-[24deg] sm:right-[28%]">
            <div className="relative h-14 w-11 rounded-[12px] border border-slate-200 bg-white shadow-[0_10px_18px_rgba(31,41,55,0.14)]">
              <div className="absolute left-2 right-2 top-3 h-1 rounded-full bg-slate-200" />
              <div className="absolute left-2 right-3 top-6 h-1 rounded-full bg-slate-200" />
              <div className="absolute left-2 right-4 top-9 h-1 rounded-full bg-slate-200" />
            </div>
            <div className="absolute -bottom-1 left-1 h-3 w-9 rounded-full bg-black/10 blur-sm" />
          </div>
        </div>

        <div className="space-y-3">
          <h2 className="text-2xl font-black tracking-tight text-slate-900 sm:text-3xl">
            이번 시즌에는 참여자가 없었어요
          </h2>
          <p className="mx-auto break-keep text-sm leading-7 text-slate-500 sm:text-base sm:whitespace-nowrap">
            참여한 팝업스토어가 없어 시즌 랭킹을 표시할 수 없어요.
          </p>
        </div>
      </div>
    </section>
  );
}
