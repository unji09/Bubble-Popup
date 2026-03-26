import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useAppNoticeStore } from "../stores/useAppNoticeStore";
import { useUserStore } from "../stores/useUserStore";

const AUTH_CALLBACK_HANDLED_KEY = "authCallbackHandled";

export default function AuthCallbackPage() {
  const navigate = useNavigate();

  useEffect(() => {
    const handleCallback = async () => {
      const hash = new URLSearchParams(window.location.hash.replace(/^#/, ""));
      const query = new URLSearchParams(window.location.search);

      const accessToken = hash.get("accessToken");
      const tokenType = hash.get("tokenType");

      if (accessToken) {
        localStorage.setItem("accessToken", accessToken);

        if (tokenType) {
          localStorage.setItem("tokenType", tokenType);
        } else {
          localStorage.removeItem("tokenType");
        }

        useAppNoticeStore.getState().clearAuthNotice();
        await useUserStore.getState().fetchUser();
        sessionStorage.setItem(AUTH_CALLBACK_HANDLED_KEY, "true");
        navigate("/", { replace: true });
        return;
      }

      if (
        sessionStorage.getItem(AUTH_CALLBACK_HANDLED_KEY) === "true" &&
        localStorage.getItem("accessToken")
      ) {
        sessionStorage.removeItem(AUTH_CALLBACK_HANDLED_KEY);
        navigate("/", { replace: true });
        return;
      }

      sessionStorage.removeItem(AUTH_CALLBACK_HANDLED_KEY);
      localStorage.removeItem("accessToken");
      localStorage.removeItem("tokenType");

      const loginError = query.get("loginError");
      const loginErrorDescription = query.get("loginErrorDescription");
      const errorQuery = new URLSearchParams();

      if (loginError) {
        errorQuery.set("loginError", loginError);
      }
      if (loginErrorDescription) {
        errorQuery.set("loginErrorDescription", loginErrorDescription);
      }

      navigate(
        {
          pathname: "/login",
          search: errorQuery.toString() ? `?${errorQuery.toString()}` : "",
        },
        { replace: true },
      );
    };

    handleCallback();
  }, [navigate]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-[#FDFDFB] text-slate-900 font-display">
      <div className="text-center">
        <p className="text-lg font-semibold">로그인 처리 중...</p>
        <p className="mt-2 text-sm text-slate-500">잠시만 기다려 주세요.</p>
      </div>
    </div>
  );
}
