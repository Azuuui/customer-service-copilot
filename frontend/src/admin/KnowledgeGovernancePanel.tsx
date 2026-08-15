import { FormEvent, useEffect, useState } from "react";
import { createKnowledge, KnowledgeSummary, loadCategories, loadFeedbackCases, loadKnowledge } from "../shared/api/client";

export function KnowledgeGovernancePanel({ moduleCode, onClose }: { moduleCode: string; onClose: () => void }) {
  if (moduleCode === "knowledge") return <KnowledgePanel onClose={onClose} />;
  if (moduleCode === "feedback") return <FeedbackPanel onClose={onClose} />;
  return <TaxonomyPanel onClose={onClose} />;
}
function PanelHeader({ title, onClose }: { title: string; onClose: () => void }) { return <header className="governance-header"><div><span className="eyebrow">GOVERNANCE</span><h2>{title}</h2></div><button onClick={onClose}>返回总览</button></header>; }
function KnowledgePanel({ onClose }: { onClose: () => void }) {
  const [items,setItems]=useState<KnowledgeSummary[]>([]);const [total,setTotal]=useState(0);const [message,setMessage]=useState("");
  const [form,setForm]=useState({category:"客服中心知识库/产品分类/其他",standardQuestion:"",originalAnswer:"",reason:"新增知识"});
  async function refresh(){const result=await loadKnowledge();setItems(result.items);setTotal(result.total);}
  useEffect(()=>{refresh().catch(error=>setMessage(String(error)));},[]);
  async function submit(event:FormEvent){event.preventDefault();await createKnowledge({...form,userQuestions:[],keywords:[]});setMessage("知识已发布，索引正在同步");setForm({...form,standardQuestion:"",originalAnswer:""});await refresh();}
  return <section className="governance-panel"><PanelHeader title="知识库管理" onClose={onClose}/><div className="governance-grid"><div><div className="panel-metric"><strong>{total}</strong><span>知识总量</span></div><table><thead><tr><th>标准问题</th><th>类目</th><th>版本</th><th>向量</th></tr></thead><tbody>{items.map(item=><tr key={item.sourceKey}><td>{item.standardQuestion}</td><td>{item.category}</td><td>v{item.currentVersion}</td><td>{item.embedded?"已就绪":"同步中"}</td></tr>)}</tbody></table></div><form className="knowledge-form" onSubmit={submit}><h3>保存即发布</h3><label>三级类目<input value={form.category} onChange={e=>setForm({...form,category:e.target.value})}/></label><label>标准问题<input required value={form.standardQuestion} onChange={e=>setForm({...form,standardQuestion:e.target.value})}/></label><label>原始答案<textarea required value={form.originalAnswer} onChange={e=>setForm({...form,originalAnswer:e.target.value})}/></label><label>变更原因<input required value={form.reason} onChange={e=>setForm({...form,reason:e.target.value})}/></label><button>发布知识</button><p role="status">{message}</p></form></div></section>;
}
function FeedbackPanel({onClose}:{onClose:()=>void}){const[items,setItems]=useState<Array<{id:number;query:string;status:string;reportCount:number}>>([]);useEffect(()=>{loadFeedbackCases().then(setItems).catch(()=>setItems([]));},[]);return <section className="governance-panel"><PanelHeader title="待维护词与答案反馈" onClose={onClose}/><table><thead><tr><th>查询词</th><th>上报次数</th><th>状态</th></tr></thead><tbody>{items.map(item=><tr key={item.id}><td>{item.query}</td><td>{item.reportCount}</td><td>{item.status}</td></tr>)}</tbody></table></section>;}
function TaxonomyPanel({onClose}:{onClose:()=>void}){const[items,setItems]=useState<Array<{id:number;name:string;depth:number}>>([]);useEffect(()=>{loadCategories().then(setItems).catch(()=>setItems([]));},[]);return <section className="governance-panel"><PanelHeader title="类目与标签" onClose={onClose}/><div className="category-tree">{items.map(item=><div key={item.id} style={{paddingLeft:`${(item.depth-1)*24}px`}}><span>{item.depth}</span>{item.name}</div>)}</div></section>;}
