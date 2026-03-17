import json
import os
import re
from dataclasses import dataclass
from typing import Any, Dict, List, Optional


@dataclass
class ParseResult:
    ok: bool
    parserStage: str
    errorCode: Optional[str] = None
    errorMessage: Optional[str] = None
    rawResponseSnippet: Optional[str] = None
    plan: Optional[Dict[str, Any]] = None


@dataclass
class ActDecisionParseResult:
    ok: bool
    parserStage: str
    errorCode: Optional[str] = None
    errorMessage: Optional[str] = None
    rawResponseSnippet: Optional[str] = None
    decision: Optional[Dict[str, Any]] = None


def _snippet_limit() -> int:
    return max(64, int(os.getenv("AGENT_PLAN_RAW_SNIPPET_MAX", "240")))


def _sanitize_snippet(raw: str) -> str:
    if raw is None:
        return ""
    compact = raw.replace("\r", " ").replace("\n", " ").strip()
    limit = _snippet_limit()
    if len(compact) <= limit:
        return compact
    return compact[:limit] + "...(truncated)"


def extract_json_candidate(raw: str) -> str:
    text = (raw or "").strip()
    if not text:
        return ""

    # 优先提取 ```json ... ``` fenced block
    fenced = re.search(r"```(?:json)?\s*(\{[\s\S]*\})\s*```", text, re.IGNORECASE)
    if fenced:
        return fenced.group(1).strip()

    # 其次提取首个看似 JSON object 的片段
    first = text.find("{")
    last = text.rfind("}")
    if first >= 0 and last > first:
        return text[first : last + 1].strip()

    return text


def parse_json(candidate: str) -> Dict[str, Any]:
    return json.loads(candidate)


def normalize_plan_schema(obj: Dict[str, Any]) -> Dict[str, Any]:
    if not isinstance(obj, dict):
        return obj
    plan = obj.get("plan")
    if isinstance(plan, list):
        return obj
    if not isinstance(plan, dict):
        return obj

    normalized = []
    indexed_items = []
    for key, value in plan.items():
        try:
            idx = int(str(key).strip())
        except ValueError:
            idx = 10**9
        indexed_items.append((idx, str(key), value))

    indexed_items.sort(key=lambda item: (item[0], item[1]))
    auto_index = 1
    for idx, key, value in indexed_items:
        step_index = auto_index if idx == 10**9 else idx
        goal_text = str(value).strip() if value is not None else ""
        normalized.append(
            {
                "index": step_index,
                "name": f"step-{step_index}",
                "goal": goal_text,
            }
        )
        auto_index += 1

    obj["plan"] = normalized
    return obj


def validate_plan_schema(obj: Dict[str, Any]) -> Optional[str]:
    if not isinstance(obj, dict):
        return "plan payload must be object"
    if "plan" not in obj or not isinstance(obj.get("plan"), list):
        return "plan field must be array"
    if "planSummary" not in obj or not isinstance(obj.get("planSummary"), str) or not obj.get("planSummary").strip():
        return "planSummary field must be non-empty string"
    return None


def parse_plan_response(raw: str) -> ParseResult:
    snippet = _sanitize_snippet(raw or "")
    if not raw or not raw.strip():
        return ParseResult(
            ok=False,
            parserStage="extract",
            errorCode="llm_empty_output",
            errorMessage="LLM returned empty output",
            rawResponseSnippet=snippet,
        )

    candidate = extract_json_candidate(raw)
    if not candidate:
        return ParseResult(
            ok=False,
            parserStage="extract",
            errorCode="llm_empty_output",
            errorMessage="LLM returned empty output",
            rawResponseSnippet=snippet,
        )

    try:
        parsed = parse_json(candidate)
    except json.JSONDecodeError as exc:
        return ParseResult(
            ok=False,
            parserStage="parse",
            errorCode="llm_bad_response",
            errorMessage=f"LLM JSON parse failed: {exc}",
            rawResponseSnippet=snippet,
        )

    parsed = normalize_plan_schema(parsed)
    validation_error = validate_plan_schema(parsed)
    if validation_error is not None:
        return ParseResult(
            ok=False,
            parserStage="validate",
            errorCode="llm_schema_mismatch",
            errorMessage=validation_error,
            rawResponseSnippet=snippet,
        )

    return ParseResult(
        ok=True,
        parserStage="validate",
        rawResponseSnippet=snippet,
        plan=parsed,
    )


def _validate_tool_calls(tool_calls: Any) -> Optional[str]:
    if tool_calls is None:
        return None
    if not isinstance(tool_calls, list):
        return "toolCalls must be array"
    for idx, item in enumerate(tool_calls):
        if not isinstance(item, dict):
            return f"toolCalls[{idx}] must be object"
        tool_name = item.get("tool")
        if not isinstance(tool_name, str) or not tool_name.strip():
            return f"toolCalls[{idx}].tool must be non-empty string"
        args = item.get("args")
        if args is None:
            item["args"] = {}
            continue
        if not isinstance(args, dict):
            return f"toolCalls[{idx}].args must be object"
    return None


def parse_act_decision_response(raw: str) -> ActDecisionParseResult:
    snippet = _sanitize_snippet(raw or "")
    if not raw or not raw.strip():
        return ActDecisionParseResult(
            ok=False,
            parserStage="extract",
            errorCode="llm_empty_output",
            errorMessage="LLM returned empty output",
            rawResponseSnippet=snippet,
        )

    candidate = extract_json_candidate(raw)
    if not candidate:
        return ActDecisionParseResult(
            ok=False,
            parserStage="extract",
            errorCode="llm_empty_output",
            errorMessage="LLM returned empty output",
            rawResponseSnippet=snippet,
        )

    try:
        parsed = parse_json(candidate)
    except json.JSONDecodeError as exc:
        return ActDecisionParseResult(
            ok=False,
            parserStage="parse",
            errorCode="llm_bad_response",
            errorMessage=f"LLM JSON parse failed: {exc}",
            rawResponseSnippet=snippet,
        )

    if not isinstance(parsed, dict):
        return ActDecisionParseResult(
            ok=False,
            parserStage="validate",
            errorCode="llm_schema_mismatch",
            errorMessage="ACT decision payload must be object",
            rawResponseSnippet=snippet,
        )

    done = parsed.get("done")
    if not isinstance(done, bool):
        return ActDecisionParseResult(
            ok=False,
            parserStage="validate",
            errorCode="llm_schema_mismatch",
            errorMessage="done field must be boolean",
            rawResponseSnippet=snippet,
        )

    validation_error = _validate_tool_calls(parsed.get("toolCalls"))
    if validation_error is not None:
        return ActDecisionParseResult(
            ok=False,
            parserStage="validate",
            errorCode="llm_schema_mismatch",
            errorMessage=validation_error,
            rawResponseSnippet=snippet,
        )

    decision: Dict[str, Any] = {
        "done": done,
        "toolCalls": parsed.get("toolCalls") if isinstance(parsed.get("toolCalls"), list) else [],
        "thought": str(parsed.get("thought") or "").strip(),
    }
    return ActDecisionParseResult(
        ok=True,
        parserStage="validate",
        rawResponseSnippet=snippet,
        decision=decision,
    )
