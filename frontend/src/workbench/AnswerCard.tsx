import { useState } from "react";
import { QueryResult, reportFeedback } from "../shared/api/client";

export function AnswerCard({ result, query }: { result: QueryResult; query: string }) {
  const [expanded, setExpanded] = useState(false);
  const [feedback, setFeedback] = useState("");

  async function copy(text: string) {
    await navigator.clipboard.writeText(text);
  }

  async function report(confirmDuplicate = false) {
    const response = await reportFeedback(query, "answer_issue", `知识：${result.standard_question}`, confirmDuplicate);
    if (response.confirmationRequired) {
      if (window.confirm("24 小时内已上报过同一问题，仍要再次上报吗？")) await report(true);
      return;
    }
    setFeedback("已上报");
  }

  return (
    <article className="answer-card">
      <header><div><span>{result.category}</span><h3>{result.standard_question}</h3></div><button onClick={() => copy(result.original_answer)}>复制全文</button></header>
      <div className={expanded ? "answer-content expanded" : "answer-content"}>
        {result.answer_blocks.map((block, index) => (
          <div className="answer-block" key={`${result.id}-${index}`}>
            <p>{block.text}</p><button aria-label={`复制第 ${index + 1} 项`} onClick={() => copy(block.text)}>复制此项</button>
          </div>
        ))}
      </div>
      {result.answer_blocks.length > 3 && <button className="expand-button" onClick={() => setExpanded(!expanded)}>{expanded ? "收起原文" : "展开全部原文"}</button>}
      <footer><button onClick={() => report()}>{feedback || "答案有问题"}</button></footer>
    </article>
  );
}
