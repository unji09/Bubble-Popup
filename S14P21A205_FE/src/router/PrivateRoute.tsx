import { useEffect } from "react";
import { Navigate, Outlet } from "react-router-dom";
import { isAuthenticated } from "../hooks/useAuth";
import { useUserStore } from "../stores/useUserStore";

interface PrivateRouteProps {
  requiredRole?: string;
  unauthorizedRedirectTo?: string;
}

export default function PrivateRoute({
  requiredRole,
  unauthorizedRedirectTo = "/",
}: PrivateRouteProps) {
  const hasToken = isAuthenticated();
  const isLoaded = useUserStore((s) => s.isLoaded);
  const role = useUserStore((s) => s.role);
  const fetchUser = useUserStore((s) => s.fetchUser);

  useEffect(() => {
    if (hasToken && !isLoaded) {
      fetchUser();
    }
  }, [hasToken, isLoaded, fetchUser]);

  if (!hasToken) return <Navigate to="/login" replace />;

  if (!isLoaded) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[#FDFDFB] text-slate-900 font-display">
        <p className="text-lg font-semibold">불러오는 중...</p>
      </div>
    );
  }

  if (requiredRole && role !== requiredRole) {
    return <Navigate to={unauthorizedRedirectTo} replace />;
  }

  return <Outlet />;
}
