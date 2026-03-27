import { useState, useRef, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { clearAuthSession } from "../../hooks/useAuth";
import { logout } from "../../api/auth";
import { useUserStore } from "../../stores/useUserStore";

interface ProfileDropdownProps {
  nickname?: string;
}

export default function ProfileDropdown({ nickname = "Owner" }: ProfileDropdownProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const role = useUserStore((state) => state.role);
  const isAdmin = role === "ADMIN";

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  const handleLogout = async () => {
    try {
      await logout();
    } catch {
      // BE 실패해도 로컬 세션은 정리
    }
    useUserStore.getState().clearUser();
    clearAuthSession();
    navigate("/login");
  };

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(!open)}
        className="flex items-center gap-2 rounded-full h-10 pl-3 pr-1 bg-white border border-slate-200 hover:border-slate-300 shadow-sm hover:shadow transition-all"
      >
        <span className="text-sm font-semibold text-slate-700">{nickname}</span>
        <div className="size-8 rounded-full bg-primary/15 flex items-center justify-center">
          <span className="material-symbols-outlined text-primary text-lg">person</span>
        </div>
      </button>

      {open && (
        <div className="absolute right-0 top-12 w-48 bg-white rounded-xl shadow-premium border border-slate-100 py-2 z-50 animate-[fadeIn_0.15s_ease-out]">
          {isAdmin && (
            <>
              <button
                onClick={() => { setOpen(false); navigate("/admin/demo-skip"); }}
                className="w-full px-4 py-2.5 text-left text-sm text-slate-700 hover:bg-slate-50 flex items-center gap-3 transition-colors"
              >
                <span className="material-symbols-outlined text-slate-400 text-lg">shield_person</span>
                관리자 페이지
              </button>
              <div className="h-px bg-slate-100 mx-3" />
            </>
          )}
          <button
            onClick={() => { setOpen(false); navigate("/mypage"); }}
            className="w-full px-4 py-2.5 text-left text-sm text-slate-700 hover:bg-slate-50 flex items-center gap-3 transition-colors"
          >
            <span className="material-symbols-outlined text-slate-400 text-lg">account_circle</span>
            마이페이지
          </button>
          <div className="h-px bg-slate-100 mx-3" />
          <button
            onClick={handleLogout}
            className="w-full px-4 py-2.5 text-left text-sm text-red-500 hover:bg-red-50 flex items-center gap-3 transition-colors"
          >
            <span className="material-symbols-outlined text-red-400 text-lg">logout</span>
            로그아웃
          </button>
        </div>
      )}
    </div>
  );
}
