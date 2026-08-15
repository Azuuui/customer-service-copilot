import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AppRoutes } from "./App";

describe("统一前端入口", () => {
  afterEach(() => {
    cleanup();
    window.sessionStorage.clear();
    vi.restoreAllMocks();
  });
  it("查询页呈现公告、查询、班务状态三栏", () => {
    render(<MemoryRouter initialEntries={["/"]}><AppRoutes /></MemoryRouter>);

    expect(screen.getByRole("complementary", { name: "公告" })).toBeVisible();
    expect(screen.getByRole("main", { name: "知识查询" })).toBeVisible();
    expect(screen.getByRole("complementary", { name: "班务和状态" })).toBeVisible();
    expect(screen.getByRole("searchbox", { name: "输入问题或关键词" })).toBeEnabled();
  });

  it("未登录进入管理后台时只展示模拟登录", () => {
    render(<MemoryRouter initialEntries={["/admin"]}><AppRoutes /></MemoryRouter>);

    expect(screen.getByRole("heading", { name: "登录管理后台" })).toBeVisible();
    expect(screen.queryByRole("navigation", { name: "管理模块" })).not.toBeInTheDocument();
  });

  it("查询后展示原始答案且不暴露检索技术字段", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        query: "制冰机", limit: 4, offset: 0, embedding_status: "ready", degraded: false,
        results: [{
          id: "645", standard_question: "制冷机维修", category: "家电维修",
          original_answer: "判断是否为制冰机，录入对应产品维修",
          match_methods: ["semantic"],
          answer_blocks: [{ text: "判断是否为制冰机，录入对应产品维修", ticket_recommendations: [], tags: [], notes: [] }],
        }],
      }),
    }));
    render(<MemoryRouter initialEntries={["/"]}><AppRoutes /></MemoryRouter>);
    await userEvent.type(screen.getByRole("searchbox"), "制冰机");
    await userEvent.click(screen.getByRole("button", { name: "查询" }));

    expect(await screen.findByRole("heading", { name: "制冷机维修" })).toBeVisible();
    expect(screen.getByText("判断是否为制冰机，录入对应产品维修")).toBeVisible();
    expect(screen.queryByText("semantic")).not.toBeInTheDocument();
  });

  it("管理员模拟登录后展示十个业务模块目录", async () => {
    vi.stubGlobal("fetch", vi.fn().mockImplementation(async (input: string | URL | Request) => {
      const path = String(input);
      return {
        ok: true,
        json: async () => path.includes("menu-order")
          ? { items: [
              { moduleCode: "overview", displayName: "管理总览", routeAnchor: "overview" },
              { moduleCode: "status_requests", displayName: "状态申请", routeAnchor: "status-requests" },
              { moduleCode: "feedback", displayName: "待维护词与答案反馈", routeAnchor: "feedback" },
              { moduleCode: "knowledge", displayName: "知识库管理", routeAnchor: "knowledge" },
              { moduleCode: "announcements", displayName: "公告管理", routeAnchor: "announcements" },
              { moduleCode: "schedules", displayName: "班务与值班", routeAnchor: "schedules" },
              { moduleCode: "taxonomy", displayName: "类目与标签", routeAnchor: "taxonomy" },
              { moduleCode: "accounts", displayName: "账号与权限", routeAnchor: "accounts" },
              { moduleCode: "audit", displayName: "操作日志", routeAnchor: "audit" },
              { moduleCode: "settings", displayName: "系统设置", routeAnchor: "settings" },
            ] }
          : {
              sessionToken: "test-session",
              user: { employeeId: "admin-001", name: "管理员", status: "active", roles: ["system_admin"] },
            },
      };
    }));
    render(<MemoryRouter initialEntries={["/admin"]}><AppRoutes /></MemoryRouter>);
    await userEvent.click(screen.getByRole("button", { name: "模拟登录" }));

    const expected = ["管理总览", "状态申请", "待维护词与答案反馈", "知识库管理", "公告管理", "班务与值班", "类目与标签", "账号与权限", "操作日志", "系统设置"];
    await waitFor(() => expect(screen.getByRole("navigation", { name: "管理模块" })).toBeVisible());
    expected.forEach((item) => expect(screen.getByRole("link", { name: item })).toBeVisible());
  });
});
