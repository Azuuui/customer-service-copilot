export type SessionUser = {
  employeeId: string;
  name: string;
  roles: string[];
  status: "active" | "disabled";
};

const SESSION_KEY = "copilot.session";

export function sessionToken() {
  return window.sessionStorage.getItem(SESSION_KEY);
}

export function saveSession(token: string) {
  window.sessionStorage.setItem(SESSION_KEY, token);
}

export function clearSession() {
  window.sessionStorage.removeItem(SESSION_KEY);
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const token = sessionToken();
  const headers = new Headers(init?.headers);
  headers.set("content-type", "application/json");
  headers.set("x-anonymous-session", anonymousSession());
  if (token) headers.set("authorization", `Bearer ${token}`);
  const response = await fetch(path, { ...init, headers });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body.error ?? body.message ?? body.detail ?? `请求失败（${response.status}）`);
  }
  return response.json() as Promise<T>;
}

function anonymousSession() {
  const key = "copilot.anonymous-session";
  const existing = window.localStorage.getItem(key);
  if (existing) return existing;
  const created = window.crypto.randomUUID();
  window.localStorage.setItem(key, created);
  return created;
}

export type LoginResponse = { sessionToken: string; user: SessionUser };
export type MeResponse = { user: SessionUser; roles: string[] };
export type AdminMenuItem = { moduleCode: string; displayName: string; routeAnchor: string };
export type MenuOrderResponse = { items: AdminMenuItem[] };
export type AnswerBlock = { text: string; ticket_recommendations: string[]; tags: string[]; notes: string[] };
export type QueryResult = {
  id: string; standard_question: string; category: string; original_answer: string;
  answer_blocks: AnswerBlock[]; user_questions?: string[]; keywords?: string[];
};
export type QueryResponse = {
  query: string; limit: number; offset: number; embedding_status: "ready" | "unavailable";
  degraded: boolean; degradation_reason?: string | null; results: QueryResult[];
};

export function mockLogin(employeeId: string, name: string) {
  return apiRequest<LoginResponse>("/api/v1/auth/mock-login", {
    method: "POST",
    body: JSON.stringify({ employeeId, name }),
  });
}

export function loadCurrentUser() {
  return apiRequest<MeResponse>("/api/v1/me");
}

export function loadAdminMenu() {
  return apiRequest<MenuOrderResponse>("/api/v1/admin/menu-order");
}

export function searchKnowledge(query: string, offset: number, requestKind: "query" | "display_more" = "query") {
  return apiRequest<QueryResponse>("/api/v1/query", {
    method: "POST",
    body: JSON.stringify({ query, limit: 4, offset, requestKind }),
  });
}

export function reportFeedback(query: string, type: string, detail = "", confirmDuplicate = false) {
  return apiRequest<{status: string; confirmationRequired: boolean}>("/api/v1/feedback", {
    method: "POST", body: JSON.stringify({ query, type, detail, confirmDuplicate }),
  });
}

export type KnowledgeSummary = { sourceKey: string; standardQuestion: string; category: string; status: string; currentVersion: number; embedded: boolean };
export function loadKnowledge(query = "") {
  return apiRequest<{items: KnowledgeSummary[]; total: number}>(`/api/v1/admin/knowledge?size=10&query=${encodeURIComponent(query)}`);
}
export function createKnowledge(payload: {category: string; standardQuestion: string; originalAnswer: string; reason: string; userQuestions: string[]; keywords: string[]}) {
  return apiRequest<{knowledge: unknown; indexReady: boolean}>("/api/v1/admin/knowledge", { method: "POST", body: JSON.stringify(payload) });
}
export function loadFeedbackCases() { return apiRequest<Array<{id:number; query:string; status:string; reportCount:number}>>("/api/v1/admin/feedback?size=10"); }
export function loadCategories() { return apiRequest<Array<{id:number; parentId?:number; name:string; depth:number}>>("/api/v1/admin/taxonomy/categories"); }

export type AnnouncementImage = {id:number;filename:string;mimeType:string;byteSize:number;sortOrder:number;url:string};
export type Announcement = { id:number; title:string; content:string; publicationStatus:string; pinned:boolean; publishAt?:string;images:AnnouncementImage[] };
export type QueueItem = { id:number; employeeId:string; employeeName:string; requestedDurationMinutes:number; position:number };
export type StatusDashboard = { shortBreakQueue:QueueItem[]; longBreakQueue:QueueItem[]; requests:Array<{id:number;employeeName:string;displayName:string;requestStatus:string;overCapacity:boolean}>; currentStatuses:Array<{employeeName:string;statusCode:string;overtime:boolean}>; metrics:{shortBreakWaiting:number;longBreakWaiting:number;active:number;overtime:number} };
export type WorkSchedule = { id:number; weekStart:string; scheduleStatus:string };
export type ShiftAssignment = { id:number;shiftDate:string;shiftCode:string;startsAt:string;endsAt:string;employeeId:string;employeeName:string;dispatcher:boolean };
export function loadAnnouncements(admin=false){return apiRequest<{items:Announcement[];total:number}>(admin?"/api/v1/admin/announcements?size=10":"/api/v1/announcements?size=10");}
export function publishAnnouncement(payload:{title:string;content:string;contentFormat:string;pinned:boolean;publishAt?:string;images:Array<{filename:string;mimeType:string;base64Data:string}>}){return apiRequest<Announcement>("/api/v1/admin/announcements",{method:"POST",body:JSON.stringify(payload)});}
export function withdrawAnnouncement(id:number){return apiRequest<Announcement>(`/api/v1/admin/announcements/${id}/withdraw`,{method:"POST"});}
export function loadStatusDashboard(){return apiRequest<StatusDashboard>("/api/v1/status/dashboard");}
export function loadAdminStatusDashboard(){return apiRequest<StatusDashboard>("/api/v1/admin/status/dashboard");}
export function requestStatus(statusCode:string,durationMinutes:number){return apiRequest<{id:number}>("/api/v1/status/requests",{method:"POST",body:JSON.stringify({statusCode,durationMinutes})});}
export function approveStatus(id:number,allowOverCapacity=false,reason="批准申请"){return apiRequest(`/api/v1/admin/status/requests/${id}/approve`,{method:"POST",body:JSON.stringify({allowOverCapacity,reason})});}
export function loadSchedules(){return apiRequest<WorkSchedule[]>("/api/v1/admin/schedules");}
export function createSchedule(weekStart:string){return apiRequest<{id:number}>("/api/v1/admin/schedules",{method:"POST",body:JSON.stringify({weekStart})});}
export function publishSchedule(id:number){return apiRequest<WorkSchedule>(`/api/v1/admin/schedules/${id}/publish`,{method:"POST"});}
export function loadCurrentSchedule(){return apiRequest<{schedule:WorkSchedule|null;assignments:ShiftAssignment[]}>("/api/v1/schedules/current");}
