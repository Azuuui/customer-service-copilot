const form = document.querySelector('#search-form');
const input = document.querySelector('#query');
const statusNode = document.querySelector('#status');
const resultsNode = document.querySelector('#results');
const moreButton = document.querySelector('#more');
const reportButton = document.querySelector('#report');
let lastQuery = '';
let offset = 0;

const escapeHtml = (value) => String(value ?? '').replace(/[&<>'"]/g, (char) => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[char]));
function icon(name) { return name === 'copy' ? '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 8h10v12H8zM6 16H4V4h10v2"/></svg>' : '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14M12 5l7 7-7 7"/></svg>'; }
function answerBlockHtml(block) {
  const chips = [...(block.ticket_recommendations || []), ...(block.tags || [])].filter(Boolean).map((value) => `<span class="chip">${escapeHtml(value)}</span>`).join('');
  return `<div class="answer-block"><p>${escapeHtml(block.text)}</p>${chips ? `<div class="chips">${chips}</div>` : ''}<button class="copy" data-copy="${escapeHtml(block.text)}" type="button">${icon('copy')}复制此段</button></div>`;
}
function answerPreview(item) {
  const lines = String(item.original_answer || '').split(/\r?\n/).filter((line) => line.trim()).slice(0, 3);
  return lines.length ? escapeHtml(lines.join('\n')) : 'Excel 原始答案为空，系统不会自动生成客服结论。';
}
function cardHtml(item, index) {
  const fullAnswer = item.answer_blocks.length ? item.answer_blocks.map(answerBlockHtml).join('') : answerPreview(item);
  return `<article class="result-card"><div class="result-meta"><span>结果 ${index + 1}</span><span title="${escapeHtml(item.category || '未分类')}">${escapeHtml(item.category || '未分类')}</span></div><h2>${escapeHtml(item.standard_question)}</h2><div class="answer-label">原始答案</div><div class="answer ${item.original_answer ? '' : 'empty-answer'}"><p class="answer-preview">${answerPreview(item)}</p><div class="answer-full">${fullAnswer}</div></div><div class="card-footer"><span class="source">来自知识库原文</span><button class="copy" data-copy="${escapeHtml(item.original_answer || '')}" type="button">${icon('copy')}复制答案</button><button class="expand" type="button">展开</button></div></article>`;
}
function render(data, append = false) { const html = data.results.length ? data.results.map(cardHtml).join('') : '<div class="empty">没有找到生效中的匹配知识，请换一种问法。</div>'; resultsNode.innerHTML = append ? resultsNode.innerHTML + html : html; moreButton.hidden = data.results.length < 4; moreButton.disabled = data.results.length < 4; }
async function fetchResults(queryText, append = false) {
  const response = await fetch('/api/search', {method:'POST', headers:{'content-type':'application/json'}, body:JSON.stringify({query:queryText, limit:4, offset})});
  const data = await response.json(); if (!response.ok) throw new Error(data.detail || '查询失败'); render(data, append); statusNode.textContent = `已返回 ${data.results.length} 条结果`; reportButton.disabled = false;
}
form.addEventListener('submit', async (event) => { event.preventDefault(); const queryText = input.value.trim(); if (!queryText) { statusNode.textContent = '请输入客户问题。'; statusNode.className='status error'; return; } lastQuery=queryText; offset=0; statusNode.className='status'; statusNode.textContent='正在检索…'; resultsNode.innerHTML=''; moreButton.hidden=true; try { await fetchResults(queryText); } catch(error) { statusNode.className='status error'; statusNode.textContent=error.message; } });
moreButton.addEventListener('click', async () => { if (!lastQuery || moreButton.disabled) return; offset += 4; moreButton.disabled=true; try { await fetchResults(lastQuery, true); } catch(error) { statusNode.className='status error'; statusNode.textContent=error.message; } });
reportButton.addEventListener('click', () => { statusNode.className='status'; statusNode.textContent='已收到上报，管理员会在维护列表中处理。'; });
resultsNode.addEventListener('click', async (event) => { const button=event.target.closest('[data-copy]'); if(button && button.dataset.copy !== undefined) { await navigator.clipboard.writeText(button.dataset.copy); const old=button.innerHTML; button.textContent='已复制'; setTimeout(()=>button.innerHTML=old,1200); } const expand=event.target.closest('.expand'); if(expand) { const card=expand.closest('.result-card'); card.classList.toggle('is-expanded'); expand.textContent=card.classList.contains('is-expanded')?'收起':'展开'; } });
