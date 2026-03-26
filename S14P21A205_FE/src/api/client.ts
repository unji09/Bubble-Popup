import axios, { type AxiosError, type InternalAxiosRequestConfig } from "axios";
import { refreshAccessToken } from "./auth";
import { clearAuthSession } from "../hooks/useAuth";
import { redirectToErrorPage, type ErrorPageState } from "../utils/errorPageState";

export const GAME_EXIT_CODES = new Set(["STORE-001", "GAME-003"]);

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

function buildErrorPageState(error: AxiosError, status: number): ErrorPageState {
  const responseData = error.response?.data as
    | { code?: unknown; message?: unknown; path?: unknown; timestamp?: unknown }
    | undefined;

  return {
    status,
    code: typeof responseData?.code === "string" ? responseData.code : null,
    message: typeof responseData?.message === "string" ? responseData.message : null,
    path: typeof responseData?.path === "string" ? responseData.path : null,
    timestamp: typeof responseData?.timestamp === "string" ? responseData.timestamp : null,
    returnTo: window.location.pathname,
  };
}

function redirectToStatusPage(error: AxiosError, status: 401 | 500 | 503) {
  const targetPath = status === 401 ? "/401" : status === 500 ? "/500" : "/503";
  redirectToErrorPage(targetPath, buildErrorPageState(error, status));
}

client.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retried?: boolean };
    const status = error.response?.status;

    if (status === 500 || status === 503) {
      redirectToStatusPage(error, status);
      return Promise.reject(error);
    }

    if (status !== 401) {
      return Promise.reject(error);
    }

    if (!originalRequest || originalRequest._retried) {
      clearAuthSession();
      redirectToStatusPage(error, 401);
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
      processQueue(null, newToken);

      originalRequest.headers.Authorization = `Bearer ${newToken}`;
      return client(originalRequest);
    } catch (refreshError) {
      processQueue(refreshError, null);
      clearAuthSession();

      if (axios.isAxiosError(refreshError)) {
        redirectToStatusPage(refreshError, 401);
      } else {
        redirectToErrorPage("/401", {
          status: 401,
          message: "Authentication is required.",
          returnTo: window.location.pathname,
        });
      }

      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  },
);

export default client;
