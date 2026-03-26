import { useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import ErrorPageDetails from "../components/common/ErrorPageDetails";
import ErrorStateLayout from "../components/common/ErrorStateLayout";
import { consumeErrorPageState } from "../utils/errorPageState";

export default function ServiceUnavailablePage() {
  const navigate = useNavigate();
  const [errorState] = useState(() => consumeErrorPageState());

  if (errorState?.status !== 503) {
    return <Navigate to="/" replace />;
  }

  return (
    <ErrorStateLayout
      code="503"
      badge="일시적 이용 불가"
      title="현재 서비스를 이용할 수 없습니다"
      description="점검 중이거나 일시적인 장애가 발생했습니다. 잠시 후 다시 시도하거나 홈으로 이동해 주세요."
      primaryAction={{
        label: "홈으로",
        onClick: () => navigate("/", { replace: true }),
      }}
      secondaryAction={{
        label: "이전으로",
        onClick: () => navigate(-1),
        variant: "secondary",
      }}
      footer={(
        <ErrorPageDetails
          code={errorState?.code}
          message={errorState?.message}
          path={errorState?.path}
        />
      )}
    />
  );
}
