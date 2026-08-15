# 客服辅助演示项目本地运行手册

## 安全提示

本仓库只包含虚构演示数据。运行前复制 .env.example 为 .env，设置仅用于本机的强数据库密码。默认认证模式为 disabled；只有本地演示时才应显式设置 COPILOT_AUTH_MODE=mock。

## 访问地址

- 查询工作台：http://127.0.0.1:8080/
- 管理后台：http://127.0.0.1:8080/admin
- 健康检查：http://127.0.0.1:8080/api/v1/health

服务默认只绑定本机回环地址。

## Docker Compose

1. 复制 .env.example 为 .env。
2. 编辑 .env，至少设置 POSTGRES_PASSWORD。
3. 执行 docker compose up --build -d。
4. 使用 docker compose ps 检查进程。

Compose 启动 PostgreSQL、数据库迁移、虚构知识导入、Python 检索服务和 Spring Boot 后端。Embedding 模型缓存以只读方式挂载；仓库不会保存模型文件。

## 本机开发

要求 Java 21、Maven 3.9+、Node.js 22+、npm 11+、Python 3.9+。

本次公开脱敏发布按用户要求未运行功能测试、生产构建、Docker 启动或接口验收。克隆者应在自己的隔离环境中完成验证后再部署。
