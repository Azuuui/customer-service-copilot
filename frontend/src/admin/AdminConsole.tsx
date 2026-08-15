import { AdminMenuItem, SessionUser } from "../shared/api/client";
import { adminModuleDescriptions } from "./adminModules";
import { useState } from "react";
import { KnowledgeGovernancePanel } from "./KnowledgeGovernancePanel";
import { OperationsAdminPanel } from "./OperationsAdminPanel";
import { SystemAdminPanel } from "./SystemAdminPanel";

export function AdminConsole({ user, menuItems }: { user: SessionUser; menuItems: AdminMenuItem[] }) {
  const [activeModule, setActiveModule] = useState("");
  return (
    <div className="admin-shell">
      <aside className="admin-sidebar">
        <a className="admin-brand" href="/"><span className="brand-mark">K</span><span><small>ENTERPRISE</small><strong>客服管理中心</strong></span></a>
        <nav aria-label="管理模块">
          {menuItems.map((module, index) => <a key={module.moduleCode} href={`#${module.routeAnchor}`} aria-label={module.displayName}><span aria-hidden="true">{String(index + 1).padStart(2, "0")}</span>{module.displayName}</a>)}
        </nav>
        <div className="admin-user"><span className="avatar">管</span><span><small>当前身份</small><strong>{user.name}</strong></span></div>
      </aside>
      <main className="admin-main">
        <header className="admin-header"><div><span className="eyebrow">OPERATIONS CONSOLE</span><h1>企业客服管理后台</h1><p>统一维护知识、人员与客服运营数据。</p></div><span className="environment-badge">开发环境 · 模拟登录</span></header>
        {(["knowledge", "feedback", "taxonomy"].includes(activeModule)) ? <KnowledgeGovernancePanel moduleCode={activeModule} onClose={() => setActiveModule("")} /> : (["announcements","schedules","status_requests"].includes(activeModule)) ? <OperationsAdminPanel moduleCode={activeModule} onClose={()=>setActiveModule("")}/> : (["accounts","audit","settings"].includes(activeModule)) ? <SystemAdminPanel moduleCode={activeModule} onClose={()=>setActiveModule("")}/> : <section className="module-grid" aria-label="后台功能总览">
          {menuItems.map((module, index) => (
            <article className="module-card" id={module.routeAnchor} key={module.moduleCode}>
              <span className="module-index">{String(index + 1).padStart(2, "0")}</span><div><h2>{module.displayName}</h2><p>{adminModuleDescriptions[module.moduleCode] ?? "管理模块"}</p></div><button type="button" aria-label={`打开${module.displayName}`} onClick={() => setActiveModule(module.moduleCode)}>进入</button>
            </article>
          ))}
        </section>}
      </main>
    </div>
  );
}
