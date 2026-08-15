import { FormEvent, useState } from "react";
import { mockLogin, saveSession, SessionUser } from "../shared/api/client";

type AdminLoginProps = { onAuthenticated: (user: SessionUser) => void };

export function AdminLogin({ onAuthenticated }: AdminLoginProps) {
  const [employeeId, setEmployeeId] = useState("admin-001");
  const [name, setName] = useState("管理员");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError("");
    try {
      const result = await mockLogin(employeeId.trim(), name.trim());
      saveSession(result.sessionToken);
      onAuthenticated(result.user);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "登录失败");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="login-page">
      <a className="login-brand" href="/"><span className="brand-mark">K</span><span>客服管理中心</span></a>
      <form className="login-card" onSubmit={submit}>
        <span className="eyebrow">DEVELOPMENT ACCESS</span>
        <h1>登录管理后台</h1>
        <p>开发环境使用模拟身份；生产环境将切换为钉钉免登或扫码登录。</p>
        <label htmlFor="employee-id">员工编号</label>
        <input id="employee-id" value={employeeId} onChange={(event) => setEmployeeId(event.target.value)} required />
        <label htmlFor="employee-name">姓名</label>
        <input id="employee-name" value={name} onChange={(event) => setName(event.target.value)} required />
        {error && <p className="login-error" role="alert">{error}</p>}
        <button type="submit" disabled={submitting}>{submitting ? "登录中…" : "模拟登录"}</button>
      </form>
    </main>
  );
}
