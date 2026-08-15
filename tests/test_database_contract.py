import unittest
from pathlib import Path

from app.importer import prepare_import_records


ROOT = Path(__file__).resolve().parents[1]
MIGRATIONS = ROOT / 'db/migrations'


def read_migrations(*names):
    return '\n'.join((MIGRATIONS / name).read_text(encoding='utf-8') for name in names)


class DatabaseContractTests(unittest.TestCase):
    def test_migration_contains_required_tables_and_pgvector_indexes(self):
        sql = (ROOT / 'db/migrations/001_initial.sql').read_text(encoding='utf-8')
        self.assertIn('CREATE EXTENSION IF NOT EXISTS vector', sql)
        self.assertIn('CREATE TABLE IF NOT EXISTS knowledge_items', sql)
        self.assertIn('CREATE TABLE IF NOT EXISTS knowledge_phrases', sql)
        self.assertIn('CREATE TABLE IF NOT EXISTS knowledge_item_versions', sql)
        self.assertIn('vector(1024)', sql)
        self.assertIn('USING hnsw', sql)
        self.assertIn('USING gin', sql)

    def test_import_preparation_keeps_inactive_and_indexes_original_answer(self):
        json_records = [{
            'id': 'demo-0001',
            'standard_question': '智能锁维修/安装',
            'user_questions': ['智能锁坏了'],
            'keywords': ['智能锁', '密码锁'],
            'category': '客服中心知识库/产品分类/修锁换锁',
            'scenarios': [],
            'original_reply': '录入智能锁开锁/安装',
            'retrieval_text': '旧字段不应直接复用',
        }]
        source_rows = [{
            '标准问题(必填)': '智能锁维修/安装',
            '生效状态(TRUE/FALSE)': 'FALSE',
            '生效时间(yyyy-mm-dd)': '2026-01-01',
            '失效时间(yyyy-mm-dd)': '2026-12-31',
            '创建人': '创建者', '修改人': '修改者',
            '创建时间': '2026-01-02 03:04:05', '修改时间': '2026-02-03 04:05:06',
        }]
        records = prepare_import_records(json_records, source_rows, tokenizer=lambda text: ['智能锁', '维修'])
        item = records[0]
        self.assertFalse(item['is_active'])
        self.assertEqual(item['document_length'], 2)
        self.assertIn('录入智能锁开锁/安装', item['search_text'])
        self.assertIn('智能锁坏了', item['search_text'])
        self.assertEqual(item['source_updated_by'], '修改者')

    def test_content_hash_is_stable_and_empty_answer_is_retained(self):
        base = {
            'id': 'demo-0002', 'standard_question': '流程问题',
            'user_questions': ['流程怎么做'], 'keywords': [], 'category': '系统操作',
            'scenarios': [], 'original_reply': '', 'retrieval_text': '',
        }
        source = [{'标准问题(必填)': '流程问题', '生效状态(TRUE/FALSE)': 'TRUE'}]
        first = prepare_import_records([base], source, tokenizer=lambda text: ['流程'])[0]
        second = prepare_import_records([dict(base)], source, tokenizer=lambda text: ['流程'])[0]
        self.assertEqual(first['content_hash'], second['content_hash'])
        self.assertEqual(first['original_answer'], '')

    def test_enterprise_migrations_are_ordered_and_keep_initial_history(self):
        names = sorted(path.name for path in MIGRATIONS.glob('*.sql'))
        self.assertEqual(names, [
            '001_initial.sql',
            '002_identity_access.sql',
            '003_knowledge_governance.sql',
            '004_query_feedback.sql',
            '005_operations_status.sql',
            '006_settings_menu.sql',
            '007_runtime_constraints.sql',
            '008_knowledge_projection_sync.sql',
            '009_knowledge_change_reason.sql',
            '010_knowledge_bulk_operations.sql',
            '011_operations_runtime.sql',
            '012_default_working_status.sql',
            '013_announcement_image_content.sql',
        ])

    def test_runtime_migration_allows_mock_and_dingtalk_session_methods(self):
        sql = read_migrations('007_runtime_constraints.sql')
        self.assertIn("login_method IN ('mock', 'dingtalk_sso', 'dingtalk_qr')", sql)
        self.assertIn('idx_users_dingtalk_identity', sql)

    def test_identity_rbac_session_and_audit_contract(self):
        sql = read_migrations('002_identity_access.sql')
        for table in ('users', 'roles', 'permissions', 'user_roles', 'role_permissions', 'user_sessions', 'audit_logs'):
            self.assertIn(f'CREATE TABLE IF NOT EXISTS {table}', sql)
        self.assertIn('UNIQUE (corp_id, dingtalk_user_id)', sql)
        self.assertIn("status IN ('active', 'disabled')", sql)
        self.assertIn('session_token_hash', sql)
        self.assertNotIn('password_hash', sql)
        self.assertNotIn('access_token text', sql)
        self.assertIn("permission_code IN ('view', 'edit', 'export')", sql)
        self.assertIn('INSERT INTO permissions', sql)
        self.assertIn('INSERT INTO role_permissions', sql)

    def test_knowledge_subject_immutable_version_category_and_tag_contract(self):
        sql = read_migrations('003_knowledge_governance.sql')
        for table in (
            'knowledge_categories', 'knowledge_tags', 'knowledge_entries',
            'knowledge_versions', 'knowledge_version_tags', 'knowledge_relations',
            'knowledge_publication_schedule',
        ):
            self.assertIn(f'CREATE TABLE IF NOT EXISTS {table}', sql)
        self.assertIn('CHECK (depth BETWEEN 1 AND 3)', sql)
        self.assertIn('validate_knowledge_category_depth', sql)
        self.assertIn('WITH RECURSIVE category_ancestors', sql)
        self.assertIn('idx_knowledge_categories_unique_path', sql)
        self.assertIn('UNIQUE (category_id, standard_question_normalized)', sql)
        self.assertIn('UNIQUE (knowledge_entry_id, version_number)', sql)
        self.assertIn('prevent_knowledge_version_mutation', sql)
        self.assertIn('BEFORE UPDATE OR DELETE ON knowledge_versions', sql)
        self.assertIn('original_answer text NOT NULL', sql)
        self.assertIn('answer_blocks jsonb NOT NULL', sql)

    def test_query_anonymous_session_repeat_and_feedback_contract(self):
        sql = read_migrations('004_query_feedback.sql')
        for table in ('anonymous_sessions', 'query_events', 'feedback_cases', 'feedback_reports'):
            self.assertIn(f'CREATE TABLE IF NOT EXISTS {table}', sql)
        self.assertIn('anonymous_session_id', sql)
        self.assertIn('duplicate_of_query_event_id', sql)
        self.assertIn('is_repeat_within_10s', sql)
        self.assertIn('CHECK (repeat_window_seconds = 10)', sql)
        self.assertIn('validate_query_repeat_window', sql)
        self.assertIn("interval '10 seconds'", sql)
        self.assertIn('idx_feedback_cases_unique_open_query', sql)
        self.assertIn("status IN ('pending', 'processing', 'updated', 'closed')", sql)
        self.assertIn('ignored_at', sql)
        self.assertIn('ignore_undone_at', sql)

    def test_announcement_shift_substitution_and_status_queue_contract(self):
        sql = read_migrations('005_operations_status.sql')
        for table in (
            'announcements', 'announcement_images', 'work_schedules',
            'shift_assignments', 'shift_substitutions', 'status_type_rules',
            'status_requests', 'employee_current_statuses',
            'employee_status_history', 'status_request_events',
        ):
            self.assertIn(f'CREATE TABLE IF NOT EXISTS {table}', sql)
        self.assertIn("mime_type IN ('image/jpeg', 'image/png')", sql)
        self.assertIn('CHECK (byte_size <= 5242880)', sql)
        self.assertIn('enforce_announcement_image_limit', sql)
        self.assertIn('FOR UPDATE', sql)
        self.assertIn("queue_name IN ('short_break', 'long_break')", sql)
        self.assertIn("queue_name IS NULL", sql)
        self.assertIn('position_override', sql)
        self.assertIn('is_over_capacity', sql)
        self.assertIn('ended_at', sql)

    def test_settings_menu_order_and_version_contract(self):
        sql = read_migrations('006_settings_menu.sql')
        for table in ('system_settings', 'system_setting_versions', 'admin_menu_items', 'user_menu_orders'):
            self.assertIn(f'CREATE TABLE IF NOT EXISTS {table}', sql)
        self.assertIn('value jsonb NOT NULL', sql)
        self.assertIn('UNIQUE (setting_id, version_number)', sql)
        self.assertIn('UNIQUE (user_id, position)', sql)
        self.assertIn('CHECK (position > 0)', sql)
        self.assertNotIn('is_visible', sql)

    def test_enterprise_schema_has_no_upgrade_event_module(self):
        sql = read_migrations(
            '002_identity_access.sql', '003_knowledge_governance.sql',
            '004_query_feedback.sql', '005_operations_status.sql',
            '006_settings_menu.sql', '007_runtime_constraints.sql',
        ).lower()
        self.assertNotIn('upgrade_event', sql)


if __name__ == '__main__':
    unittest.main()
