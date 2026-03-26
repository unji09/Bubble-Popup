import { useEffect, useState } from "react";
import { useGameStore } from "../stores/useGameStore";
import { useUserStore } from "../stores/useUserStore";

export function isAuthenticated() {
  return Boolean(localStorage.getItem("accessToken"));
}

export function clearAuthSession() {
  localStorage.removeItem("accessToken");
  localStorage.removeItem("tokenType");
  localStorage.removeItem("profileNickname");
  useGameStore.getState().clearGame();
  useGameStore.getState().clearBankruptNotice();
  useUserStore.getState().clearUser();
}

export default function useAuth() {
  const [isLoggedIn, setIsLoggedIn] = useState(isAuthenticated);

  useEffect(() => {
    const syncAuthState = () => {
      setIsLoggedIn(isAuthenticated());
    };

    window.addEventListener("storage", syncAuthState);
    window.addEventListener("focus", syncAuthState);

    return () => {
      window.removeEventListener("storage", syncAuthState);
      window.removeEventListener("focus", syncAuthState);
    };
  }, []);

  return { isLoggedIn };
}
