import { FormEvent, useState } from "react";
import { QueryResult, searchKnowledge } from "../shared/api/client";
import { AnswerCard } from "./AnswerCard";

export function QueryPanel() {
  const [query, setQuery] = useState("");
  const [message, setMessage] = useState("输入客户描述，查找生效中的标准答案。");
  const [submittedQuery, setSubmittedQuery] = useState("");
  const [results, setResults] = useState<QueryResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [degraded, setDegraded] = useState(false);
  const [hasMore, setHasMore] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const value = query.trim();
    if (!value) { setMessage("请先输入问题或关键词。"); return; }
    setLoading(true);
    try {
      const response = await searchKnowledge(value, 0);
      setSubmittedQuery(value); setResults(response.results); setDegraded(response.degraded);
      setHasMore(response.results.length === 4); setMessage(response.results.length ? `找到 ${response.results.length} 条结果` : "暂未找到相关答案，可直接上报补充。");
    } catch (error) { setMessage(error instanceof Error ? error.message : "查询失败，请稍后重试。"); }
    finally { setLoading(false); }
  }

  async function loadMore() {
    setLoading(true);
    try {
      const response = await searchKnowledge(submittedQuery, results.length, "display_more");
      const known = new Set(results.map((item) => item.id));
      const appended = response.results.filter((item) => !known.has(item.id));
      setResults([...results, ...appended]); setHasMore(response.results.length === 4 && appended.length > 0);
    } finally { setLoading(false); }
  }

  return (
    <main className="query-panel" aria-label="知识查询">
      <header className="workspace-header">
        <div><span className="eyebrow">KNOWLEDGE ASSISTANT</span><h1>客服知识工作台</h1></div>
        <a className="admin-link" href="/admin">进入管理后台</a>
      </header>
      <section className="search-stage" aria-labelledby="query-heading">
        <span className="section-index">01 / QUERY</span>
        <h2 id="query-heading">今天需要查什么？</h2>
        <form className="search-bar" role="search" onSubmit={submit}>
          <label className="sr-only" htmlFor="query">输入问题或关键词</label>
          <input id="query" type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="例如：马桶堵塞怎么处理" aria-label="输入问题或关键词" />
          <button type="submit" disabled={loading}>{loading ? "查询中" : "查询"}</button>
        </form>
        <p className="query-message" role="status">{message}</p>
      </section>
      <section className="results" aria-label="查询结果">
        {degraded && <p className="degraded-notice">语义检索暂不可用，当前为关键词降级结果。</p>}
        {results.map((result) => <AnswerCard key={result.id} result={result} query={submittedQuery} />)}
        {!results.length && <div className="empty-results"><div className="empty-number">04</div><div><h2>首次展示四条结果</h2><p>答案保留知识库原文，支持追加、展开、分项复制与反馈上报。</p></div></div>}
        {hasMore && <button className="load-more" disabled={loading} onClick={loadMore}>再显示 4 条</button>}
      </section>
    </main>
  );
}
