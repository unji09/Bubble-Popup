import { useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import ErrorPageDetails from "../components/common/ErrorPageDetails";
import ErrorStateLayout from "../components/common/ErrorStateLayout";
import { consumeErrorPageState } from "../utils/errorPageState";

export default function NotFoundPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [errorState] = useState(() => consumeErrorPageState());

  if (location.pathname === "/404" && errorState?.status !== 404) {
    return <Navigate to="/" replace />;
  }

  return (
    <ErrorStateLayout
      code="404"
      badge="페이지 없음"
      title="요청한 페이지를 찾을 수 없습니다"
      description="주소가 잘못되었거나 더 이상 유효하지 않은 경로입니다."
      primaryAction={{
        label: "홈으로",
        onClick: () => navigate("/", { replace: true }),
      }}
      secondaryAction={{
        label: "이전으로",
        onClick: () => navigate(-1),
        variant: "secondary",
      }}
      footer={
        <ErrorPageDetails
          code={errorState?.code}
          message={errorState?.message}
          path={errorState?.path}
        />
      }
    />
  );
}
