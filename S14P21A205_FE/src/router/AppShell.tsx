import { Outlet } from "react-router-dom";
import Button from "../components/common/Button";
import BgmController from "../components/common/BgmController";
import { useAppNoticeStore } from "../stores/useAppNoticeStore";

export default function AppShell() {
  const authNotice = useAppNoticeStore((state) => state.authNotice);
  const serverNotice = useAppNoticeStore((state) => state.serverNotice);
  const flashNotice = useAppNoticeStore((state) => state.flashNotice);
  const clearAuthNotice = useAppNoticeStore((state) => state.clearAuthNotice);
  const clearServerNotice = useAppNoticeStore((state) => state.clearServerNotice);
  const clearFlashNotice = useAppNoticeStore((state) => state.clearFlashNotice);

  const handleRetry = () => {
    clearServerNotice();
    window.location.reload();
  };

  return (
    <>
      <Outlet />
      <BgmController />

      {authNotice && (
        <div className="pointer-events-none fixed inset-x-0 top-20 z-[70] flex justify-center px-4">
          <div className="pointer-events-auto flex w-full max-w-xl items-start gap-3 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-amber-900 shadow-lg">
            <span className="material-symbols-outlined text-[20px] text-amber-500">
              warning
            </span>
            <p className="flex-1 text-sm font-medium leading-6">{authNotice}</p>
            <button
              type="button"
              onClick={clearAuthNotice}
              className="rounded-lg p-1 text-amber-500 transition-colors hover:bg-amber-100"
              aria-label="인증 알림 닫기"
            >
              <span className="material-symbols-outlined text-[18px]">close</span>
            </button>
          </div>
        </div>
      )}

      {flashNotice && (
        <div
          className={`pointer-events-none fixed inset-x-0 z-[72] flex justify-center px-4 ${
            authNotice ? "top-40" : "top-20"
          }`}
        >
          <div className="pointer-events-auto flex w-full max-w-xl items-start gap-3 rounded-2xl border border-sky-200 bg-sky-50 px-4 py-3 text-sky-900 shadow-lg">
            <span className="material-symbols-outlined text-[20px] text-sky-500">
              info
            </span>
            <p className="flex-1 text-sm font-medium leading-6">{flashNotice}</p>
            <button
              type="button"
              onClick={clearFlashNotice}
              className="rounded-lg p-1 text-sky-500 transition-colors hover:bg-sky-100"
              aria-label="안내 닫기"
            >
              <span className="material-symbols-outlined text-[18px]">close</span>
            </button>
          </div>
        </div>
      )}

      {serverNotice && (
        <div className="fixed inset-0 z-[80] flex items-center justify-center bg-black/40 px-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-[28px] bg-white p-7 shadow-premium">
            <div className="flex items-start gap-3">
              <div className="flex size-11 shrink-0 items-center justify-center rounded-2xl bg-rose-50 text-rose-500">
                <span className="material-symbols-outlined text-[24px]">cloud_off</span>
              </div>
              <div className="space-y-2">
                <h2 className="text-xl font-bold text-slate-900">서버 연결 오류</h2>
                <p className="text-sm leading-6 text-slate-600">{serverNotice}</p>
              </div>
            </div>

            <div className="mt-6">
              <Button fullWidth size="lg" onClick={handleRetry}>
                다시 시도
              </Button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
