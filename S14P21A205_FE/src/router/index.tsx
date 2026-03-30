import { createBrowserRouter } from "react-router-dom";
import PrivateRoute from "./PrivateRoute";
import GameGuard from "./GameGuard";
import AppShell from "./AppShell";
import HomePage from "../pages/HomePage";
import LoginPage from "../pages/LoginPage";
import MyPage from "../pages/MyPage";
import LocationSelectPage from "../pages/LocationSelectPage";
import BrandNamingPage from "../pages/BrandNamingPage";
import PrepPage from "../pages/PrepPage";
import PlayPage from "../pages/PlayPage";
import ReportPage from "../pages/ReportPage";
import RankingPage from "../pages/RankingPage";
import NewsPage from "../pages/NewsPage";
import CozyPrepPage from "../pages/CozyPrepPage";
import WaitingPage from "../pages/WaitingPage";
import AuthCallbackPage from "../pages/AuthCallbackPage";
import BadRequestPage from "../pages/BadRequestPage";
import AdminDemoSkipPage from "../pages/AdminDemoSkipPage";
import ForbiddenPage from "../pages/ForbiddenPage";
import InternalServerErrorPage from "../pages/InternalServerErrorPage";
import NotFoundPage from "../pages/NotFoundPage";
import ServiceUnavailablePage from "../pages/ServiceUnavailablePage";
import UnauthorizedPage from "../pages/UnauthorizedPage";
import TutorialPage from "../pages/TutorialPage";

const router = createBrowserRouter([
  {
    element: <AppShell />,
    children: [
      { path: "/", element: <HomePage /> },
      { path: "/login", element: <LoginPage /> },
      { path: "/auth/callback", element: <AuthCallbackPage /> },
      { path: "/news", element: <NewsPage /> },
      { path: "/cozy/prep", element: <CozyPrepPage /> },
      { path: "/403", element: <ForbiddenPage /> },
      {
        element: <PrivateRoute requiredRole="ADMIN" unauthorizedRedirectTo="/" />,
        children: [
          { path: "/admin/demo-skip", element: <AdminDemoSkipPage /> },
        ],
      },
      {
        element: <PrivateRoute />,
        children: [
          { path: "/mypage", element: <MyPage /> },
          { path: "/tutorial", element: <TutorialPage /> },
          {
            element: <GameGuard />,
            children: [
              { path: "/game/setup/location", element: <LocationSelectPage /> },
              { path: "/game/setup/naming", element: <BrandNamingPage /> },
              { path: "/game/waiting", element: <WaitingPage /> },
              { path: "/game/:day/prep", element: <PrepPage /> },
              { path: "/game/:day/play", element: <PlayPage /> },
              { path: "/game/:day/report", element: <ReportPage /> },
              { path: "/ranking", element: <RankingPage /> },
            ],
          },
        ],
      },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
  { path: "/", element: <HomePage /> },
  { path: "/login", element: <LoginPage /> },
  { path: "/auth/callback", element: <AuthCallbackPage /> },
  { path: "/news", element: <NewsPage /> },
  { path: "/cozy/prep", element: <CozyPrepPage /> },
  { path: "/400", element: <BadRequestPage /> },
  { path: "/401", element: <UnauthorizedPage /> },
  { path: "/403", element: <ForbiddenPage /> },
  { path: "/404", element: <NotFoundPage /> },
  { path: "/500", element: <InternalServerErrorPage /> },
  { path: "/503", element: <ServiceUnavailablePage /> },
  {
    element: <PrivateRoute />,
    children: [
      // 가드 없는 페이지 (언제든 접근 가능)
      { path: "/mypage", element: <MyPage /> },
      { path: "/tutorial", element: <TutorialPage /> },

      // 게임 페이즈 가드 적용
      {
        element: <GameGuard />,
        children: [
          { path: "/game/setup/location", element: <LocationSelectPage /> },
          { path: "/game/setup/naming", element: <BrandNamingPage /> },
          { path: "/game/waiting", element: <WaitingPage /> },
          { path: "/game/:day/prep", element: <PrepPage /> },
          { path: "/game/:day/play", element: <PlayPage /> },
          { path: "/game/:day/report", element: <ReportPage /> },
          { path: "/ranking", element: <RankingPage /> },
        ],
      },
    ],
  },
  { path: "*", element: <NotFoundPage /> },
]);

export default router;
