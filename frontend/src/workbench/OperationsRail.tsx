import { FormEvent, useEffect, useState } from "react";
import {
  loadCurrentSchedule, loadCurrentUser, loadStatusDashboard, mockLogin, requestStatus,
  saveSession, sessionToken, ShiftAssignment, StatusDashboard,
} from "../shared/api/client";

export function OperationsRail() {
  const [dashboard, setDashboard] = useState<StatusDashboard | null>(null);
  const [assignments, setAssignments] = useState<ShiftAssignment[]>([]);
  const [message, setMessage] = useState("");
  const [userName, setUserName] = useState("");
  const [showLogin, setShowLogin] = useState(false);
  const [employeeId, setEmployeeId] = useState("agent-001");
  const [name, setName] = useState("客服");

  function refresh() {
    if (sessionToken()) loadStatusDashboard().then(setDashboard).catch(() => undefined);
    loadCurrentSchedule().then((data) => setAssignments(Array.isArray(data.assignments) ? data.assignments : [])).catch(() => undefined);
  }
  useEffect(() => {
    if (sessionToken()) loadCurrentUser().then((data) => setUserName(data.user.name)).catch(() => undefined);
    refresh();
    const id = window.setInterval(refresh, 5000);
    return () => window.clearInterval(id);
  }, []);

  async function login(event: FormEvent) {
    event.preventDefault();
    try {
      const result = await mockLogin(employeeId.trim(), name.trim());
      saveSession(result.sessionToken);
      setUserName(result.user.name);
      setShowLogin(false);
      setMessage("登录成功");
      refresh();
    } catch (reason) {
      setMessage(reason instanceof Error ? reason.message : "登录失败");
    }
  }
  async function apply(code: string) {
    if (!sessionToken()) { setShowLogin(true); setMessage("请先登录再申请状态"); return; }
    try {
      await requestStatus(code, code === "short_break" ? 10 : 20);
      setMessage("已加入对应队列");
      refresh();
    } catch (reason) { setMessage(reason instanceof Error ? reason.message : "申请失败"); }
  }

  const current = dashboard?.currentStatuses?.[0];
  const statusNames: Record<string, string> = { working: "工作", short_break: "小休", long_break: "大休", meal: "吃饭", coaching: "辅导", meeting: "会议" };
  return (
    <aside className="rail operations-rail" aria-label="班务和状态">
      <div className="account-row"><span className="avatar">客</span><div><span className="eyebrow">CURRENT USER</span><strong>{userName || "未登录"}</strong></div><button type="button" className="text-button" onClick={() => setShowLogin((value) => !value)}>{userName ? "切换" : "登录"}</button></div>
      {showLogin && <form className="rail-login" onSubmit={login}><label>员工编号<input value={employeeId} onChange={(event) => setEmployeeId(event.target.value)} required /></label><label>姓名<input value={name} onChange={(event) => setName(event.target.value)} required /></label><button type="submit">模拟登录</button></form>}
      <section className="side-block"><span className="section-index">02 / SHIFT</span><h2>本周班务</h2>{assignments.length ? assignments.slice(0, 4).map((shift, index) => <div className={`shift-card ${index === 0 ? "active" : ""}`} key={shift.id}><span>{shift.shiftDate}</span><strong>{shift.shiftCode === "early" ? "早班" : "晚班"}</strong><time>{shift.startsAt.slice(0, 5)} — {shift.endsAt.slice(0, 5)}</time><small>{shift.employeeName}{shift.dispatcher ? " · 调度" : ""}</small></div>) : <p className="empty-copy">本周暂未发布班务</p>}</section>
      <section className="side-block"><span className="section-index">03 / STATUS</span><div className="status-heading"><h2>当前状态</h2><span className="live-dot">{statusNames[current?.statusCode ?? "working"]}</span></div><p>{message || `小休排队 ${dashboard?.metrics?.shortBreakWaiting ?? 0} 人 · 大休排队 ${dashboard?.metrics?.longBreakWaiting ?? 0} 人`}</p><div className="status-actions"><button type="button" onClick={() => apply("short_break")}>小休</button><button type="button" onClick={() => apply("long_break")}>大休</button></div></section>
    </aside>
  );
}
