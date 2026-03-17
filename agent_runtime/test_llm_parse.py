import unittest

from llm_parse import parse_act_decision_response, parse_plan_response


class LLMParseTest(unittest.TestCase):
    def test_parse_plain_json(self) -> None:
        raw = '{"plan":[{"index":1,"name":"analyze","goal":"x"}],"planSummary":"ok"}'
        result = parse_plan_response(raw)
        self.assertTrue(result.ok)
        self.assertEqual("validate", result.parserStage)
        self.assertEqual("ok", result.plan["planSummary"])

    def test_parse_fenced_json(self) -> None:
        raw = "Here is plan:\n```json\n{\"plan\":[],\"planSummary\":\"fine\"}\n```"
        result = parse_plan_response(raw)
        self.assertTrue(result.ok)
        self.assertEqual("fine", result.plan["planSummary"])

    def test_parse_malformed_json(self) -> None:
        raw = '{"plan":[{"index":1}], "planSummary": "oops"'
        result = parse_plan_response(raw)
        self.assertFalse(result.ok)
        self.assertEqual("parse", result.parserStage)
        self.assertEqual("llm_bad_response", result.errorCode)

    def test_parse_schema_mismatch(self) -> None:
        raw = '{"steps":[1,2], "summary":"missing fields"}'
        result = parse_plan_response(raw)
        self.assertFalse(result.ok)
        self.assertEqual("validate", result.parserStage)
        self.assertEqual("llm_schema_mismatch", result.errorCode)

    def test_parse_plan_object_normalized_to_array(self) -> None:
        raw = '{"plan":{"1":"intro","2":"types"},"planSummary":"ok"}'
        result = parse_plan_response(raw)
        self.assertTrue(result.ok)
        self.assertIsInstance(result.plan["plan"], list)
        self.assertEqual(2, len(result.plan["plan"]))
        self.assertEqual("intro", result.plan["plan"][0]["goal"])
        self.assertEqual(1, result.plan["plan"][0]["index"])

    def test_parse_act_decision_success(self) -> None:
        raw = '{"done":false,"toolCalls":[{"tool":"rag_search","args":{"query":"q","docIds":["doc-1"]}}],"thought":"first retrieve"}'
        result = parse_act_decision_response(raw)
        self.assertTrue(result.ok)
        self.assertFalse(result.decision["done"])
        self.assertEqual("rag_search", result.decision["toolCalls"][0]["tool"])

    def test_parse_act_decision_schema_mismatch(self) -> None:
        raw = '{"done":"no","toolCalls":"bad"}'
        result = parse_act_decision_response(raw)
        self.assertFalse(result.ok)
        self.assertEqual("validate", result.parserStage)
        self.assertEqual("llm_schema_mismatch", result.errorCode)


if __name__ == "__main__":
    unittest.main()
