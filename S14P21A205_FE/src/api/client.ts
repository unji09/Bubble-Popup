import axios, {
  type AxiosError,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from "axios";
import { refreshAccessToken } from "./auth";
import { clearAuthSession } from "../hooks/useAuth";
import {
  AUTH_EXPIRED_NOTICE,
  SERVER_ISSUE_NOTICE,
  useAppNoticeStore,
} from "../stores/useAppNoticeStore";

export const GAME_EXIT_CODES = new Set(["STORE-001", "GAME-003"]);

export interface AppRequestConfig extends AxiosRequestConfig {
  suppressGlobalErrorHandling?: boolean;
}

type AppInternalAxiosRequestConfig = InternalAxiosRequestConfig &
  AppRequestConfig & {
    _retried?: boolean;
  };

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "",
  headers: { "Content-Type": "application/json" },
  withCredentials: true,
});

client.interceptors.request.use((config) => {
  const token = localStorage.getItem("accessToken");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let isRefreshing = false;
let failedQueue: Array<{
  resolve: (token: string) => void;
  reject: (err: unknown) => void;
}> = [];

function processQueue(error: unknown, token: string | null) {
  failedQueue.forEach(({ resolve, reject }) => {
    if (token) {
      resolve(token);
      return;
    }

    reject(error);
  });
  failedQueue = [];
}

function showServerIssue() {
  useAppNoticeStore.getState().showServerNotice(SERVER_ISSUE_NOTICE);
}

function shouldSuppressGlobalErrorHandling(config?: AppRequestConfig | null) {
  return config?.suppressGlobalErrorHandling === true;
}

function handleExpiredSession() {
  clearAuthSession();
  useAppNoticeStore.getState().clearServerNotice();
  useAppNoticeStore.getState().showAuthNotice(AUTH_EXPIRED_NOTICE);

  if (typeof window === "undefined") {
    return;
  }

  if (window.location.pathname === "/") {
    window.location.reload();
    return;
  }

  window.location.replace("/");
}

client.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as AppInternalAxiosRequestConfig | undefined;
    const status = error.response?.status;

    if (status === 500 || status === 503 || (status == null && error.request)) {
      if (!shouldSuppressGlobalErrorHandling(originalRequest)) {
        showServerIssue();
      }
      return Promise.reject(error);
    }

    if (status !== 401) {
      return Promise.reject(error);
    }

    if (!originalRequest || originalRequest._retried) {
      if (!shouldSuppressGlobalErrorHandling(originalRequest)) {
        handleExpiredSession();
      }
      return Promise.reject(error);
    }

    if (isRefreshing) {
      return new Promise<string>((resolve, reject) => {
        failedQueue.push({ resolve, reject });
      }).then((token) => {
        originalRequest.headers.Authorization = `Bearer ${token}`;
        return client(originalRequest);
      });
    }

    originalRequest._retried = true;
    isRefreshing = true;

    try {
      const newToken = await refreshAccessToken();
      localStorage.setItem("accessToken", newToken);
      useAppNoticeStore.getState().clearAuthNotice();
      processQueue(null, newToken);

      originalRequest.headers.Authorization = `Bearer ${newToken}`;
      return client(originalRequest);
    } catch (refreshError) {
      processQueue(refreshError, null);

      if (shouldSuppressGlobalErrorHandling(originalRequest)) {
        return Promise.reject(refreshError);
      }

      if (axios.isAxiosError(refreshError)) {
        const refreshStatus = refreshError.response?.status;

        if (refreshStatus === 500 || refreshStatus === 503 || (refreshStatus == null && refreshError.request)) {
          showServerIssue();
        } else {
          handleExpiredSession();
        }
      } else {
        showServerIssue();
      }

      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  },
);

export default client;
