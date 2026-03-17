import json
import os
import unittest

from models import ExecutionContext
from state_machine import StateMachine


class StateMachineTest(unittest.TestCase):
    def setUp(self) -> None:
        os.environ["AGENT_LLM_PROVIDER"] = "mock"
        os.environ["AGENT_REPAIR_ENABLED"] = "true"
        os.environ["AGENT_MAX_REPAIR_ROUNDS"] = "2"
        os.environ["AGENT_VERIFIER_COVERAGE_THRESHOLD"] = "0.7"
        os.environ["AGENT_PLAN_PARSE_FALLBACK_ENABLED"] = "false"
        os.environ["AGENT_TOOL_RAG_MOCK_ENABLED"] = "true"
        os.environ.pop("AGENT_TOOL_ROLE_SCOPES", None)
        self.sm = StateMachine()

    def test_success_path_contains_plan_act_verify(self) -> None:
        payload = {
            "taskId": "task-1",
            "prompt": "generate weekly summary",
            "docIds": ["doc-1", "doc-2"],
            "outputFormat": "both",
        }
        result = self.sm.run(payload)
        self.assertEqual("SUCCEEDED", result.status)
        self.assertEqual(["PLAN", "TOOL", "GENERATE", "VERIFY"], [s.stepType for s in result.steps])
        self.assertTrue(len(result.citations) > 0)
        parsed = json.loads(result.resultJson)
        self.assertEqual("succeeded", parsed["status"])
        self.assertIn("verifierReport", parsed)
        self.assertIn("finalAnswer", parsed)
        self.assertTrue(parsed["finalAnswer"]["text"])
        self.assertEqual("passed_first_try", result.finalDecision)

    def test_fail_path_returns_failed_status(self) -> None:
        payload = {
            "taskId": "task-2",
            "prompt": "please [fail] now",
            "docIds": ["doc-9"],
            "outputFormat": "both",
        }
        result = self.sm.run(payload)
        self.assertEqual("FAILED", result.status)
        self.assertEqual("ACT", result.phase)
        self.assertEqual("AGENT_ACT_FAILED", result.errorCode)

    def test_repair_loop_runs_when_verifier_fails(self) -> None:
        payload = {
            "taskId": "task-3",
            "prompt": "generate summary without citations",
            "docIds": [],
            "outputFormat": "both",
        }
        result = self.sm.run(payload)
        self.assertEqual("FAILED", result.status)
        self.assertEqual("failed_after_max_rounds", result.finalDecision)
        self.assertGreaterEqual(len(result.repairRounds), 1)
        step_types = [s.stepType for s in result.steps]
        self.assertIn("REPAIR", step_types)

    def test_plan_parse_fail_fast_default(self) -> None:
        payload = {
            "taskId": "task-4",
            "prompt": "force [llm-bad-json] in plan",
            "docIds": ["doc-1"],
            "outputFormat": "both",
        }
        result = self.sm.run(payload)
        self.assertEqual("FAILED", result.status)
        self.assertEqual("PLAN", result.phase)
        self.assertEqual("llm_bad_response", result.errorCode)
        parsed = json.loads(result.resultJson)
        self.assertEqual("parse", parsed.get("parserStage"))

    def test_plan_parse_fallback_toggle(self) -> None:
        os.environ["AGENT_PLAN_PARSE_FALLBACK_ENABLED"] = "true"
        sm = StateMachine()
        payload = {
            "taskId": "task-5",
            "prompt": "force [llm-bad-json] in plan",
            "docIds": ["doc-1"],
            "outputFormat": "both",
        }
        result = sm.run(payload)
        self.assertNotEqual("PLAN", result.phase)
        self.assertIn(result.status, {"SUCCEEDED", "FAILED"})
        self.assertGreaterEqual(len(result.steps), 4)

    def test_generate_fail_fast(self) -> None:
        payload = {
            "taskId": "task-6",
            "prompt": "please include [generate-llm-unavailable] token",
            "docIds": ["doc-1"],
            "outputFormat": "both",
        }
        result = self.sm.run(payload)
        self.assertEqual("FAILED", result.status)
        self.assertEqual("GENERATE", result.phase)
        self.assertIn(result.errorCode, {"llm_unavailable", "llm_timeout"})

    def test_tool_scope_denied_is_exposed(self) -> None:
        os.environ["AGENT_TOOL_ROLE_SCOPES"] = json.dumps({
            "operator": ["rag:search"],
        })
        sm = StateMachine()
        payload = {
            "taskId": "task-7",
            "prompt": "try with insufficient scope",
            "docIds": ["doc-1"],
            "outputFormat": "both",
            "role": "operator",
        }
        result = sm.run(payload)
        self.assertEqual("FAILED", result.status)
        self.assertEqual("ACT", result.phase)
        self.assertEqual("TOOL_SCOPE_DENIED", result.errorCode)
        self.assertEqual("deny", result.steps[1].toolCalls[-1].policyDecision)

    def test_tool_safety_denied_is_exposed(self) -> None:
        payload = {
            "taskId": "task-8",
            "prompt": "please [use-http] https://example.com",
            "docIds": ["doc-1"],
            "outputFormat": "both",
            "role": "operator",
        }
        result = self.sm.run(payload)
        self.assertEqual("FAILED", result.status)
        self.assertEqual("ACT", result.phase)
        self.assertEqual("HTTP_HOST_NOT_ALLOWED", result.errorCode)
        self.assertEqual("deny", result.steps[1].toolCalls[-1].policyDecision)

    def test_auto_file_write_for_save_document_prompt(self) -> None:
        payload = {
            "taskId": "task-9",
            "prompt": "请整理结论并保存为 markdown 文档",
            "docIds": ["doc-1"],
            "outputFormat": "markdown",
            "role": "operator",
        }
        result = self.sm.run(payload)
        self.assertEqual("SUCCEEDED", result.status)
        file_write_calls = []
        for step in result.steps:
            for call in step.toolCalls:
                if call.toolName == "file_write":
                    file_write_calls.append(call)
        self.assertTrue(file_write_calls)
        self.assertTrue(file_write_calls[0].success)

    def test_file_write_success_terminates_act_even_if_llm_not_done(self) -> None:
        sm = StateMachine()

        def fake_decide(round_idx, prompt, doc_ids, citations, observations, available_tools, seen_tools):
            if round_idx == 1:
                return (
                    {
                        "done": False,
                        "toolCalls": [
                            {
                                "tool": "file_write",
                                "args": {"path": "reports/out.md", "content": "hello", "overwrite": True},
                            }
                        ],
                    },
                    None,
                    None,
                )
            return ({"done": False, "toolCalls": []}, None, None)

        sm._act_decide_tools = fake_decide  # type: ignore[attr-defined]
        act_step, citations, evidence_items, artifacts = sm._act_step(  # type: ignore[attr-defined]
            "save summary to file",
            ["doc-1"],
            ExecutionContext(role="operator", task_id="task-10"),
        )
        self.assertEqual("SUCCEEDED", act_step.status)
        self.assertEqual([], citations)
        self.assertEqual([], evidence_items)
        self.assertTrue(len(artifacts) >= 1)
        self.assertTrue(any(call.toolName == "file_write" and call.success for call in act_step.toolCalls))

    def test_file_write_auto_retry_with_overwrite_on_exists(self) -> None:
        sm = StateMachine()

        def fake_decide(round_idx, prompt, doc_ids, citations, observations, available_tools, seen_tools):
            return (
                {
                    "done": True,
                    "toolCalls": [
                        {
                            "tool": "file_write",
                            "args": {"path": "reports/retry.md", "content": "new-content"},
                        }
                    ],
                },
                None,
                None,
            )

        sm._act_decide_tools = fake_decide  # type: ignore[attr-defined]
        context = ExecutionContext(role="operator", task_id="task-retry")
        sm._tool_registry.execute(1, "file_write", {"path": "reports/retry.md", "content": "old-content"}, context)

        act_step, _, _, artifacts = sm._act_step(  # type: ignore[attr-defined]
            "save summary to reports/retry.md",
            ["doc-1"],
            context,
        )
        self.assertEqual("SUCCEEDED", act_step.status)
        self.assertTrue(artifacts)
        self.assertTrue(any(call.toolName == "file_write" and call.success for call in act_step.toolCalls))


if __name__ == "__main__":
    unittest.main()
