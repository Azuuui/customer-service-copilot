import { useEffect, useState } from "react";
import { apiRequest } from "../shared/api/client";

type Account={employeeId:string;name:string;status:string;roles:string};
type Audit={timestamp:string;actor:string;module:string;action:string;targetType:string;targetId:string;result:string};
type Setting={settingKey:string;value:string;description:string;currentVersion:number};

export function SystemAdminPanel({moduleCode,onClose}:{moduleCode:string;onClose:()=>void}){
  const [rows,setRows]=useState<Account[]|Audit[]|Setting[]>([]);const [message,setMessage]=useState("");
  useEffect(()=>{const path=moduleCode==="accounts"?"/api/v1/admin/accounts":moduleCode==="audit"?"/api/v1/admin/audit?limit=50":"/api/v1/admin/settings";apiRequest<unknown>(path).then(result=>setRows(Array.isArray(result)?result:(result as {items:Audit[]}).items??[])).catch(e=>setMessage(e.message))},[moduleCode]);
  const title={accounts:"账号与权限",audit:"操作日志",settings:"系统设置"}[moduleCode]??"系统管理";
  return <section className="governance-panel"><header className="governance-header"><div><span className="eyebrow">CONTROL</span><h2>{title}</h2></div><button className="text-button" onClick={onClose}>返回总览</button></header>{message&&<p role="alert">{message}</p>}<table><thead><tr>{moduleCode==="accounts"?<><th>员工</th><th>状态</th><th>角色</th></>:moduleCode==="audit"?<><th>时间</th><th>模块</th><th>操作</th><th>对象</th></>:<><th>配置</th><th>当前值</th><th>版本</th></>}</tr></thead><tbody>{moduleCode==="accounts"?(rows as Account[]).map(row=><tr key={row.employeeId}><td>{row.name}<small>{row.employeeId}</small></td><td>{row.status}</td><td>{row.roles}</td></tr>):moduleCode==="audit"?(rows as Audit[]).map((row,index)=><tr key={`${row.timestamp}-${index}`}><td>{new Date(row.timestamp).toLocaleString("zh-CN")}</td><td>{row.module}</td><td>{row.action}</td><td>{row.targetType} {row.targetId}</td></tr>):(rows as Setting[]).map(row=><tr key={row.settingKey}><td>{row.description}<small>{row.settingKey}</small></td><td>{row.value}</td><td>v{row.currentVersion}</td></tr>)}</tbody></table></section>;
}
