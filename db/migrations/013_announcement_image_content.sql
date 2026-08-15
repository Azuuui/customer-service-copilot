-- 公告图片作为正式业务数据保存在 PostgreSQL，避免依赖未配置的外部对象存储。
ALTER TABLE announcement_images ADD COLUMN IF NOT EXISTS content bytea;
