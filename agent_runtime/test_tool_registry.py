import os
import tempfile
import unittest

from models import ExecutionContext
from tool_registry import ToolRegistry


class ToolRegistrySafetyTest(unittest.TestCase):
    def setUp(self) -> None:
        os.environ.pop("AGENT_TOOL_ROLE_SCOPES", None)
        os.environ["AGENT_TOOL_HTTP_ALLOWLIST"] = "localhost,127.0.0.1"
        os.environ["AGENT_TOOL_RAG_MOCK_ENABLED"] = "true"
        self.tmp_dir = tempfile.TemporaryDirectory()
        os.environ["AGENT_TOOL_FILE_WRITE_ENABLED"] = "true"
        os.environ["AGENT_TOOL_FILE_WRITE_BASE_DIR"] = self.tmp_dir.name
        os.environ["AGENT_TOOL_FILE_WRITE_ALLOW_EXTENSIONS"] = ".txt,.md,.json,.csv"
        os.environ["AGENT_TOOL_FILE_WRITE_MAX_BYTES"] = "64"
        self.registry = ToolRegistry()
        self.context = ExecutionContext(role="operator")

    def tearDown(self) -> None:
        self.tmp_dir.cleanup()

    def test_http_host_not_allowed(self) -> None:
        trace, result = self.registry.execute(
            1,
            "http_get",
            {"url": "https://example.com"},
            self.context,
        )
        self.assertIsNone(result)
        self.assertFalse(trace.success)
        self.assertEqual("HTTP_HOST_NOT_ALLOWED", trace.errorCode)
        self.assertEqual("deny", trace.policyDecision)
        self.assertEqual("host_not_allowed", trace.denyReason)

    def test_sql_readonly_violation(self) -> None:
        trace, result = self.registry.execute(
            1,
            "sql_select",
            {"query": "DELETE FROM tasks"},
            self.context,
        )
        self.assertIsNone(result)
        self.assertFalse(trace.success)
        self.assertEqual("SQL_READONLY_VIOLATION", trace.errorCode)
        self.assertEqual("deny", trace.policyDecision)
        self.assertEqual("readonly_violation", trace.denyReason)

    def test_file_write_success(self) -> None:
        trace, result = self.registry.execute(
            1,
            "file_write",
            {"path": "reports/out.md", "content": "hello"},
            self.context,
        )
        self.assertTrue(trace.success)
        self.assertIsNotNone(result)
        self.assertEqual("allow", trace.policyDecision)
        self.assertEqual("reports/out.md", result.output["relativePath"])

    def test_file_write_path_out_of_scope(self) -> None:
        trace, result = self.registry.execute(
            1,
            "file_write",
            {"path": "../escape.txt", "content": "x"},
            self.context,
        )
        self.assertIsNone(result)
        self.assertFalse(trace.success)
        self.assertEqual("FILE_PATH_OUT_OF_SCOPE", trace.errorCode)
        self.assertEqual("PATH_OUT_OF_SCOPE", trace.denyReason)

    def test_file_write_extension_not_allowed(self) -> None:
        trace, result = self.registry.execute(
            1,
            "file_write",
            {"path": "reports/out.exe", "content": "x"},
            self.context,
        )
        self.assertIsNone(result)
        self.assertFalse(trace.success)
        self.assertEqual("FILE_EXTENSION_NOT_ALLOWED", trace.errorCode)
        self.assertEqual("EXTENSION_NOT_ALLOWED", trace.denyReason)

    def test_file_write_too_large(self) -> None:
        trace, result = self.registry.execute(
            1,
            "file_write",
            {"path": "reports/out.txt", "content": "A" * 128},
            self.context,
        )
        self.assertIsNone(result)
        self.assertFalse(trace.success)
        self.assertEqual("FILE_WRITE_TOO_LARGE", trace.errorCode)
        self.assertEqual("FILE_TOO_LARGE", trace.denyReason)


if __name__ == "__main__":
    unittest.main()
