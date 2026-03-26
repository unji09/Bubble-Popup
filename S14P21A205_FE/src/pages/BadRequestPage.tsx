import { useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import ErrorPageDetails from "../components/common/ErrorPageDetails";
import ErrorStateLayout from "../components/common/ErrorStateLayout";
import { consumeErrorPageState } from "../utils/errorPageState";

export default function BadRequestPage() {
  const navigate = useNavigate();
  const [errorState] = useState(() => consumeErrorPageState());

  if (errorState?.status !== 400) {
    return <Navigate to="/" replace />;
  }

  return (
    <ErrorStateLayout
      code="400"
      badge="잘못된 요청"
      title="요청을 처리할 수 없습니다"
      description="요청 정보가 올바르지 않거나 현재 게임 진행 상태와 맞지 않아 이 화면을 열 수 없습니다."
      primaryAction={{
        label: "이전으로",
        onClick: () => navigate(-1),
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
