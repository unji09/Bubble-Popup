import { create } from "zustand";
import { getUser, getUserPoints, patchNickname } from "../api/user";
import { useAppNoticeStore } from "./useAppNoticeStore";

interface UserState {
  nickname: string | null;
  email: string | null;
  role: string | null;
  currentPoints: number | null;
  isLoaded: boolean;

  fetchUser: () => Promise<void>;
  updateNickname: (nickname: string) => Promise<void>;
  clearUser: () => void;
}

export const useUserStore = create<UserState>((set) => ({
  nickname: null,
  email: null,
  role: null,
  currentPoints: null,
  isLoaded: false,

  fetchUser: async () => {
    try {
      // 유저 정보는 필수, 포인트는 실패해도 OK
      const userRes = await getUser();
      let points: number | null = null;
      try {
        const pointsData = await getUserPoints();
        points = pointsData.currentPoints;
      } catch { /* 포인트 조회 실패는 무시 */ }

      set({
        nickname: userRes.data.nickname,
        email: userRes.data.email,
        role: userRes.data.role,
        currentPoints: points,
        isLoaded: true,
      });
    } catch {
      // 토큰이 제거됐으면(인터셉터가 refresh 실패 처리) 로그인 페이지로
      if (!localStorage.getItem("accessToken")) {
        window.location.href = "/login";
        return;
      }
      // 토큰은 있지만 서버 문제(502 등)로 실패 → 서버 오류 모달 표시
      useAppNoticeStore.getState().showServerNotice();
      set((prev) => ({ ...prev, isLoaded: true }));
    }
  },

  updateNickname: async (nickname: string) => {
    const res = await patchNickname(nickname);
    set({ nickname: res.data.nickname });
  },

  clearUser: () =>
      set({
        nickname: null,
        email: null,
        role: null,
        currentPoints: null,
        isLoaded: false,
      }),
}));
