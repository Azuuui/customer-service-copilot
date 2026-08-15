import { useEffect, useState } from "react";
import { AdminMenuItem, clearSession, loadAdminMenu, loadCurrentUser, sessionToken, SessionUser } from "../shared/api/client";
import { AdminConsole } from "./AdminConsole";
import { AdminLogin } from "./AdminLogin";

const ADMIN_ROLES = new Set(["supervisor", "knowledge_admin", "system_admin"]);

export function AdminPage() {
  const [user, setUser] = useState<SessionUser | null>(null);
  const [checking, setChecking] = useState(Boolean(sessionToken()));
  const [menuItems, setMenuItems] = useState<AdminMenuItem[] | null>(null);

  useEffect(() => {
    if (!sessionToken()) return;
    loadCurrentUser()
      .then((response) => setUser(response.user))
      .catch(() => clearSession())
      .finally(() => setChecking(false));
  }, []);

  useEffect(() => {
    if (!user || !user.roles.some((role) => ADMIN_ROLES.has(role))) return;
    loadAdminMenu()
      .then((response) => setMenuItems(response.items))
      .catch(() => {
        clearSession();
        setUser(null);
      });
  }, [user]);

  if (checking) return <main className="login-page"><p role="status">正在验证会话…</p></main>;
  if (!user) return <AdminLogin onAuthenticated={setUser} />;
  if (!user.roles.some((role) => ADMIN_ROLES.has(role))) {
    return <main className="login-page"><section className="login-card"><h1>无后台访问权限</h1><p>当前账号为客服角色，请联系管理员授权。</p><a href="/">返回查询工作台</a></section></main>;
  }
  if (!menuItems) return <main className="login-page"><p role="status">正在加载权限菜单…</p></main>;
  return <AdminConsole user={user} menuItems={menuItems} />;
}
