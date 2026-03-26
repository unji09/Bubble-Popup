import { Outlet } from "react-router-dom";
import BgmController from "../components/common/BgmController";

export default function AppShell() {
  return (
    <>
      <Outlet />
      <BgmController />
    </>
  );
}
