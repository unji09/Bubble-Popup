import { useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import ErrorPageDetails from "../components/common/ErrorPageDetails";
import ErrorStateLayout from "../components/common/ErrorStateLayout";
import { consumeErrorPageState } from "../utils/errorPageState";

export default function ForbiddenPage() {
  const navigate = useNavigate();
  const [errorState] = useState(() => consumeErrorPageState());

  if (errorState?.status !== 403) {
    return <Navigate to="/" replace />;
  }

  return (
    <ErrorStateLayout
      code="403"
      badge="접근 제한"
      title="이 페이지에 접근할 수 없습니다"
      description="로그인은 되어 있지만 현재 권한이나 진행 상태로는 이 페이지를 이용할 수 없습니다."
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
