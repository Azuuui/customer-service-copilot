import { useEffect, useState } from "react";
import { Announcement, loadAnnouncements } from "../shared/api/client";

const fallbackNotices = [
  { time: "今天 · 08:30", title: "本周服务提醒", body: "请按最新知识答案处理客户问题，遇到缺失内容可直接上报。" },
  { time: "昨天 · 16:10", title: "班务调整", body: "晚班值班安排已更新，请在右侧查看本周班务。" },
];

export function AnnouncementRail() {
  const [notices,setNotices]=useState<Announcement[]>([]);
  useEffect(()=>{loadAnnouncements().then(r=>setNotices(Array.isArray(r.items)?r.items:[])).catch(()=>undefined)},[]);
  return (
    <aside className="rail announcement-rail" aria-label="公告">
      <div className="rail-title"><span className="brand-mark">K</span><div><span className="eyebrow">SERVICE DESK</span><h1>内部公告</h1></div></div>
      <div className="notice-list">
        {(notices.length?notices:fallbackNotices).map((notice) => (
          <article className="notice" key={notice.title}>
            <time>{"publishAt" in notice && notice.publishAt ? new Date(notice.publishAt).toLocaleString("zh-CN") : "内部公告"}</time><h2>{notice.title}</h2><p>{"content" in notice?notice.content:notice.body}</p>{"images" in notice&&notice.images?.length>0&&<div className="notice-images">{notice.images.map(image=><img key={image.id} src={image.url} alt={image.filename}/>)}</div>}
          </article>
        ))}
      </div>
      <p className="rail-footnote">信息由管理后台统一发布</p>
    </aside>
  );
}
