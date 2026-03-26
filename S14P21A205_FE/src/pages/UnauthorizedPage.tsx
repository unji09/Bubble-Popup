import { useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import ErrorPageDetails from "../components/common/ErrorPageDetails";
import ErrorStateLayout from "../components/common/ErrorStateLayout";
import { consumeErrorPageState } from "../utils/errorPageState";

export default function UnauthorizedPage() {
  const navigate = useNavigate();
  const [errorState] = useState(() => consumeErrorPageState());

  if (errorState?.status !== 401) {
    return <Navigate to="/" replace />;
  }

  return (
    <ErrorStateLayout
      code="401"
      badge="세션 만료"
      title="다시 로그인이 필요합니다"
      description="로그인 정보가 만료되었거나 유효하지 않습니다. 다시 로그인한 뒤 계속 진행해주세요."
      primaryAction={{
        label: "로그인하기",
        onClick: () => navigate("/login", { replace: true }),
      }}
      secondaryAction={{
        label: "홈으로",
        onClick: () => navigate("/", { replace: true }),
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
