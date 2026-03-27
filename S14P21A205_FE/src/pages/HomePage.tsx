import { useEffect } from "react";
import DashboardPage from "./DashboardPage";
import { isAuthenticated } from "../hooks/useAuth";
import { useUserStore } from "../stores/useUserStore";

export default function HomePage() {
  const loggedIn = isAuthenticated();
  const isLoaded = useUserStore((s) => s.isLoaded);
  const fetchUser = useUserStore((s) => s.fetchUser);

  useEffect(() => {
    if (loggedIn && !isLoaded) {
      fetchUser();
    }
  }, [loggedIn, isLoaded, fetchUser]);

  return <DashboardPage />;
}
