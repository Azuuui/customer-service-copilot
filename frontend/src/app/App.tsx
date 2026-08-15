import { Navigate, Route, Routes } from "react-router-dom";
import { AdminPage } from "../admin/AdminPage";
import { WorkbenchPage } from "../workbench/WorkbenchPage";

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<WorkbenchPage />} />
      <Route path="/admin" element={<AdminPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
