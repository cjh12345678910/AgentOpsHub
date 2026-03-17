import json
import os
import re
from dataclasses import asdict
from typing import Any, Dict, List, Optional, Tuple

from llm_provider import LLMProviderError, ModelUsage, build_provider_from_env
from llm_parse import parse_act_decision_response, parse_plan_response
from models import AgentRunResult, ExecutionContext, ModelUsageTrace, StepTrace, ToolCallTrace
from tool_registry import ToolRegistry
from verifier import run_verifier


class StateMachine:
    def __init__(self) -> None:
        self._provider = build_provider_from_env()
        self._tool_registry = ToolRegistry()
        self._max_repair_rounds = max(0, int(os.getenv("AGENT_MAX_REPAIR_ROUNDS", "2")))
        self._coverage_threshold = float(os.getenv("AGENT_VERIFIER_COVERAGE_THRESHOLD", "0.7"))
        self._repair_enabled = os.getenv("AGENT_REPAIR_ENABLED", "true").lower() not in {"0", "false", "no"}
        self._plan_parse_fallback_enabled = os.getenv("AGENT_PLAN_PARSE_FALLBACK_ENABLED", "false").lower() in {"1", "true", "yes"}
        self._act_max_rounds = max(1, int(os.getenv("AGENT_ACT_MAX_ROUNDS", "3")))
        self._act_max_tool_calls = max(1, int(os.getenv("AGENT_ACT_MAX_TOOL_CALLS", "8")))

    def run(self, payload: Dict) -> AgentRunResult:
        task_id = payload.get("taskId", "")
        prompt = (payload.get("prompt") or "").strip()
        doc_ids: List[str] = payload.get("docIds") or []
        output_format = (payload.get("outputFormat") or "both").strip().lower()

        # 创建执行上下文
        context = ExecutionContext(
            token=(payload.get("token") or "").strip() or None,
            role=(payload.get("role") or "operator").strip().lower(),
            task_id=task_id
        )
        model_usage: List[ModelUsageTrace] = []

        if self._should_fail(prompt):
            return self._act_fail_result(task_id, prompt, doc_ids)

        plan_step, plan_obj, plan_error = self._plan_step(prompt)
        if plan_step.modelUsage:
            model_usage.append(plan_step.modelUsage)
        if plan_error and not self._plan_parse_fallback_enabled:
            result_obj = {
                "taskId": task_id,
                "status": "failed",
                "summary": prompt,
                "citations": [],
                "phase": "PLAN",
                "plan": [],
            }
            return self._final_failed(
                task_id,
                [plan_step],
                result_obj,
                [],
                {"overallPass": False, "failedReasons": [plan_error[0]], "ruleResults": [], "summary": "plan parse failed"},
                [],
                model_usage,
                "failed_unrecoverable",
                plan_error[0],
                plan_error[1],
                None,
                plan_step.parserStage,
                plan_step.rawResponseSnippet,
                plan_step.usageUnavailableReason,
            )

        act_step, citations, evidence_items, artifacts = self._act_step(prompt, doc_ids, context)
        if act_step.modelUsage:
            model_usage.append(act_step.modelUsage)
        if act_step.status != "SUCCEEDED":
            result_obj = {
                "taskId": task_id,
                "status": "failed",
                "summary": prompt,
                "citations": citations,
                "phase": "ACT",
                "plan": plan_obj.get("plan", []),
                "artifacts": artifacts,
            }
            return self._final_failed(
                task_id,
                [plan_step, act_step],
                result_obj,
                citations,
                {"overallPass": False, "failedReasons": [act_step.errorCode or "AGENT_ACT_FAILED"], "ruleResults": [], "summary": "tool execution failed"},
                [],
                model_usage,
                "failed_unrecoverable",
                act_step.errorCode or "AGENT_ACT_FAILED",
                act_step.errorMessage or "ACT step failed",
                None,
            )
        generate_step, final_answer_text, final_answer_error = self._generate_step(
            prompt,
            citations,
            evidence_items,
            output_format,
        )
        if generate_step.modelUsage:
            model_usage.append(generate_step.modelUsage)
        if final_answer_error:
            result_obj = {
                "taskId": task_id,
                "status": "failed",
                "summary": prompt,
                "citations": citations,
                "phase": "GENERATE",
                "plan": plan_obj.get("plan", []),
                "artifacts": artifacts,
                "finalAnswer": {
                    "text": "",
                    "format": self._normalize_answer_format(output_format),
                },
            }
            return self._final_failed(
                task_id,
                [plan_step, act_step, generate_step],
                result_obj,
                citations,
                {"overallPass": False, "failedReasons": [final_answer_error[0]], "ruleResults": [], "summary": "final answer generation failed"},
                [],
                model_usage,
                "failed_unrecoverable",
                final_answer_error[0],
                final_answer_error[1],
                None,
                usage_unavailable_reason=generate_step.usageUnavailableReason,
                final_answer={"text": "", "format": self._normalize_answer_format(output_format)},
            )

        result_obj = {
            "taskId": task_id,
            "status": "succeeded",
            "summary": final_answer_text,
            "citations": citations,
            "phase": "VERIFY",
            "plan": plan_obj.get("plan", []),
            "artifacts": artifacts,
            "finalAnswer": {
                "text": final_answer_text,
                "format": self._normalize_answer_format(output_format),
            },
        }

        steps: List[StepTrace] = [plan_step, act_step, generate_step]
        verify_seq = 4

        # Backward-compatible behavior:
        # - explicit `file_write` in prompt is still handled in ACT step
        # - natural-language "write/save document" requests can trigger auto persist after GENERATE
        if not self._has_file_write_call(act_step):
            auto_file_write_args = self._build_auto_file_write_args(
                prompt,
                output_format,
                final_answer_text,
                task_id,
            )
            if auto_file_write_args is not None:
                file_trace, file_result = self._tool_registry.execute(
                    1,
                    "file_write",
                    auto_file_write_args,
                    context,
                )
                persist_step = StepTrace(
                    seq=4,
                    stepType="TOOL",
                    status="SUCCEEDED" if file_trace.success and file_result is not None else "FAILED",
                    inputSummary="persist final answer via file_write",
                    outputSummary="file persisted" if file_trace.success and file_result is not None else "file_write denied or failed",
                    errorCode=None if file_trace.success and file_result is not None else file_trace.errorCode,
                    errorMessage=None if file_trace.success and file_result is not None else file_trace.errorMessage,
                    citations=citations,
                    toolCalls=[file_trace],
                )
                steps.append(persist_step)
                verify_seq = 5
                if not file_trace.success or file_result is None:
                    failed_result = dict(result_obj)
                    failed_result["status"] = "failed"
                    return self._final_failed(
                        task_id,
                        steps,
                        failed_result,
                        citations,
                        {"overallPass": False, "failedReasons": [file_trace.errorCode or "FILE_WRITE_FAILED"], "ruleResults": [], "summary": "file persistence failed"},
                        [],
                        model_usage,
                        "failed_unrecoverable",
                        file_trace.errorCode or "FILE_WRITE_FAILED",
                        file_trace.errorMessage or "file_write failed",
                        None,
                    )
                output = file_result.output if isinstance(file_result.output, dict) else {}
                artifacts.append(
                    {
                        "name": str(output.get("name") or ""),
                        "relativePath": str(output.get("relativePath") or ""),
                        "sizeBytes": int(output.get("sizeBytes") or 0),
                        "sourceTool": "file_write",
                    }
                )
                result_obj["artifacts"] = artifacts

        verify_step, verifier_report = self._verify_step(verify_seq, result_obj, citations, len(doc_ids), None, None)
        steps.append(verify_step)
        seq = verify_seq

        if verify_step.modelUsage:
            model_usage.append(verify_step.modelUsage)

        repair_rounds: List[Dict[str, Any]] = []
        current_round: Optional[int] = None
        final_decision = "passed_first_try"

        if not verifier_report.get("overallPass", False):
            if not self._repair_enabled:
                return self._final_failed(
                    task_id,
                    steps,
                    result_obj,
                    citations,
                    verifier_report,
                    repair_rounds,
                    model_usage,
                    "failed_unrecoverable",
                    "REPAIR_DISABLED",
                    "Repair loop disabled",
                    current_round,
                )

            for round_idx in range(1, self._max_repair_rounds + 1):
                current_round = round_idx
                repair_step, repaired_summary, repair_error = self._repair_step(
                    seq + 1,
                    prompt,
                    result_obj,
                    verifier_report,
                    round_idx,
                )
                steps.append(repair_step)
                if repair_step.modelUsage:
                    model_usage.append(repair_step.modelUsage)

                if repair_error:
                    return self._final_failed(
                        task_id,
                        steps,
                        result_obj,
                        citations,
                        verifier_report,
                        repair_rounds,
                        model_usage,
                        "failed_unrecoverable",
                        repair_error[0],
                        repair_error[1],
                        current_round,
                    )

                previous_summary = str(result_obj.get("summary") or "")
                result_obj["summary"] = repaired_summary
                result_obj["finalAnswer"] = {
                    "text": repaired_summary,
                    "format": self._normalize_answer_format(output_format),
                }
                diff_summary = self._diff_summary(previous_summary, repaired_summary)

                verify_step, verifier_report = self._verify_step(
                    seq + 2,
                    result_obj,
                    citations,
                    len(doc_ids),
                    round_idx,
                    diff_summary,
                )
                steps.append(verify_step)
                if verify_step.modelUsage:
                    model_usage.append(verify_step.modelUsage)

                repair_rounds.append(
                    {
                        "round": round_idx,
                        "inputSummary": previous_summary,
                        "roundSummary": repaired_summary,
                        "diffSummary": diff_summary,
                        "verifyOutcome": "passed" if verifier_report.get("overallPass") else "failed",
                        "failedReasons": verifier_report.get("failedReasons", []),
                    }
                )

                seq += 2
                if verifier_report.get("overallPass", False):
                    final_decision = "passed_after_repair"
                    break
            else:
                final_decision = "failed_after_max_rounds"

        if not verifier_report.get("overallPass", False):
            return self._final_failed(
                task_id,
                steps,
                result_obj,
                citations,
                verifier_report,
                repair_rounds,
                model_usage,
                final_decision,
                "VERIFY_FAILED",
                verifier_report.get("summary", "verification failed"),
                current_round,
            )

        result_obj["verifierReport"] = verifier_report
        result_obj["repairRounds"] = repair_rounds
        result_obj["finalDecision"] = final_decision
        result_obj["modelUsage"] = [asdict(item) for item in model_usage]

        result_md = (
            "# Task Result\n\n"
            f"- status: succeeded\n"
            f"- summary: {result_obj.get('summary')}\n"
            f"- finalAnswer: {result_obj.get('finalAnswer', {}).get('text', '')}\n"
            f"- citations: {', '.join(citations) if citations else 'none'}\n"
            f"- finalDecision: {final_decision}"
        )

        return AgentRunResult(
            status="SUCCEEDED",
            phase="VERIFY",
            phaseStatus="SUCCEEDED",
            resultMd=result_md,
            resultJson=json.dumps(result_obj, ensure_ascii=False),
            citations=citations,
            steps=steps,
            verifierReport=verifier_report,
            repairRounds=repair_rounds,
            finalDecision=final_decision,
            modelUsage=model_usage,
            currentRound=current_round,
            finalAnswer=result_obj.get("finalAnswer"),
        )

    def _act_fail_result(self, task_id: str, prompt: str, doc_ids: List[str]) -> AgentRunResult:
        plan_step = StepTrace(
            seq=1,
            stepType="PLAN",
            status="SUCCEEDED",
            inputSummary=prompt,
            outputSummary="generated plan: analyze -> retrieve evidence -> verify answer",
        )
        act_step = StepTrace(
            seq=2,
            stepType="TOOL",
            status="FAILED",
            inputSummary="execute act tools",
            outputSummary="act failed",
            errorCode="AGENT_ACT_FAILED",
            errorMessage="Prompt requested fail path",
            citations=[],
            toolCalls=[
                ToolCallTrace(
                    callOrder=1,
                    toolName="rag_search",
                    requestSummary=json.dumps({"query": prompt, "docIds": doc_ids}),
                    responseSummary="",
                    success=False,
                    errorCode="AGENT_ACT_FAILED",
                    errorMessage="Prompt requested fail path",
                    latencyMs=12,
                )
            ],
        )
        result_json = json.dumps(
            {
                "taskId": task_id,
                "status": "failed",
                "reason": "Prompt requested fail path",
                "citations": [],
                "finalDecision": "failed_unrecoverable",
                "finalAnswer": {"text": "", "format": "text"},
            },
            ensure_ascii=False,
        )
        return AgentRunResult(
            status="FAILED",
            phase="ACT",
            phaseStatus="FAILED",
            resultMd="# Task Result\n\n- status: failed\n- reason: Prompt requested fail path",
            resultJson=result_json,
            citations=[],
            steps=[plan_step, act_step],
            errorCode="AGENT_ACT_FAILED",
            errorMessage="Prompt requested fail path",
            finalDecision="failed_unrecoverable",
            finalAnswer={"text": "", "format": "text"},
        )

    def _plan_step(self, prompt: str) -> Tuple[StepTrace, Dict[str, Any], Optional[Tuple[str, str]]]:
        plan_prompt = (
            "You must generate a JSON object with exactly two keys: 'plan' and 'planSummary'.\n"
            "- 'plan' must be an array of step objects. Each step object must have: 'index' (number), 'name' (string), 'goal' (string).\n"
            "- 'planSummary' must be a non-empty string summarizing the overall plan.\n"
            "Example format:\n"
            '{"plan": [{"index": 1, "name": "analyze", "goal": "understand the task"}, '
            '{"index": 2, "name": "retrieve", "goal": "gather evidence"}], '
            '"planSummary": "analyze task and retrieve evidence"}\n'
            "Return ONLY the JSON object, no markdown formatting, no explanations.\n"
            f"User prompt: {prompt}"
        )
        try:
            raw_plan_text, usage = self._provider.generate_text(plan_prompt, {"schemaHint": "plan-schema"})
            parsed = parse_plan_response(raw_plan_text)
            if parsed.ok and parsed.plan is not None:
                return (
                    StepTrace(
                        seq=1,
                        stepType="PLAN",
                        status="SUCCEEDED",
                        inputSummary=prompt,
                        outputSummary=f"generated plan: {parsed.plan.get('planSummary', 'n/a')}",
                        modelUsage=self._to_usage_trace(usage),
                        parserStage=parsed.parserStage,
                        rawResponseSnippet=parsed.rawResponseSnippet,
                    ),
                    parsed.plan,
                    None,
                )

            fallback = {
                "plan": [
                    {"index": 1, "name": "analyze", "goal": "理解任务"},
                    {"index": 2, "name": "retrieve", "goal": "检索证据"},
                    {"index": 3, "name": "verify", "goal": "验证输出"},
                ],
                "planSummary": "fallback deterministic plan",
            }
            step = StepTrace(
                seq=1,
                stepType="PLAN",
                status="FAILED",
                inputSummary=prompt,
                outputSummary="plan parse failed",
                errorCode=parsed.errorCode or "llm_bad_response",
                errorMessage=parsed.errorMessage or "plan parse failed",
                modelUsage=self._to_usage_trace(usage),
                parserStage=parsed.parserStage,
                rawResponseSnippet=parsed.rawResponseSnippet,
            )
            return step, fallback, (step.errorCode or "llm_bad_response", step.errorMessage or "plan parse failed")
        except LLMProviderError as exc:
            fallback = {
                "plan": [
                    {"index": 1, "name": "analyze", "goal": "理解任务"},
                    {"index": 2, "name": "retrieve", "goal": "检索证据"},
                    {"index": 3, "name": "verify", "goal": "验证输出"},
                ],
                "planSummary": "fallback deterministic plan",
            }
            return (
                StepTrace(
                    seq=1,
                    stepType="PLAN",
                    status="FAILED",
                    inputSummary=prompt,
                    outputSummary="plan llm call failed",
                    errorCode=exc.code,
                    errorMessage=str(exc),
                    parserStage="extract",
                    usageUnavailableReason=exc.code,
                ),
                fallback,
                (exc.code, str(exc)),
            )

    def _act_step(
        self,
        prompt: str,
        doc_ids: List[str],
        context: ExecutionContext,
    ) -> Tuple[StepTrace, List[str], List[Dict[str, str]], List[Dict[str, Any]]]:
        tool_calls: List[ToolCallTrace] = []
        citations: List[str] = []
        evidence_items: List[Dict[str, str]] = []
        artifacts: List[Dict[str, Any]] = []
        observations: List[str] = []
        seen_tools: Dict[str, int] = {}
        total_tool_calls = 0
        done = False
        act_usages: List[ModelUsageTrace] = []
        available_tools = self._tool_registry.list_tools_for_role(context.role)

        for round_idx in range(1, self._act_max_rounds + 1):
            decision, usage_trace, decision_error = self._act_decide_tools(
                round_idx,
                prompt,
                doc_ids,
                citations,
                observations,
                available_tools,
                seen_tools,
            )
            if usage_trace:
                act_usages.append(usage_trace)
            if decision_error is not None:
                return (
                    StepTrace(
                        seq=2,
                        stepType="TOOL",
                        status="FAILED",
                        inputSummary="llm decide and execute act tools",
                        outputSummary="act planning failed",
                        errorCode=decision_error[0],
                        errorMessage=decision_error[1],
                        toolCalls=tool_calls,
                        citations=citations,
                        modelUsage=self._merge_usage_trace(act_usages),
                    ),
                    citations,
                    evidence_items,
                    artifacts,
                )

            round_tool_calls = list(decision.get("toolCalls") or [])
            done = bool(decision.get("done"))
            if done and not round_tool_calls:
                break
            if not round_tool_calls:
                observations.append(f"round={round_idx}: no tool call, continue")
                continue

            for call in round_tool_calls:
                if total_tool_calls >= self._act_max_tool_calls:
                    return (
                        StepTrace(
                            seq=2,
                            stepType="TOOL",
                            status="FAILED",
                            inputSummary="llm decide and execute act tools",
                            outputSummary="tool call budget exceeded",
                            errorCode="ACT_TOOL_CALL_LIMIT_EXCEEDED",
                            errorMessage=f"Reached max tool calls {self._act_max_tool_calls}",
                            toolCalls=tool_calls,
                            citations=citations,
                            modelUsage=self._merge_usage_trace(act_usages),
                        ),
                        citations,
                        evidence_items,
                        artifacts,
                    )

                tool_name = str(call.get("tool") or "").strip()
                args = call.get("args") if isinstance(call.get("args"), dict) else {}
                total_tool_calls += 1
                seen_tools[tool_name] = seen_tools.get(tool_name, 0) + 1
                trace, result = self._tool_registry.execute(total_tool_calls, tool_name, args, context)
                if (
                    tool_name == "file_write"
                    and (not trace.success or result is None)
                    and trace.errorCode == "FILE_ALREADY_EXISTS"
                    and "overwrite" not in args
                ):
                    retry_args = dict(args)
                    retry_args["overwrite"] = True
                    total_tool_calls += 1
                    trace, result = self._tool_registry.execute(total_tool_calls, tool_name, retry_args, context)
                tool_calls.append(trace)
                if not trace.success or result is None:
                    return (
                        StepTrace(
                            seq=2,
                            stepType="TOOL",
                            status="FAILED",
                            inputSummary="llm decide and execute act tools",
                            outputSummary=f"{tool_name} denied or failed",
                            errorCode=trace.errorCode,
                            errorMessage=trace.errorMessage,
                            toolCalls=tool_calls,
                            citations=citations,
                            modelUsage=self._merge_usage_trace(act_usages),
                        ),
                        citations,
                        evidence_items,
                        artifacts,
                    )

                if tool_name == "rag_search":
                    citations = list(result.citations or [])
                elif tool_name == "chunk_fetch":
                    chunk_items = (result.output or {}).get("items") if result else []
                    if isinstance(chunk_items, list):
                        for item in chunk_items:
                            if not isinstance(item, dict):
                                continue
                            chunk_id = str(item.get("chunkId") or "").strip()
                            content = str(item.get("content") or "").strip()
                            if not chunk_id or not content:
                                continue
                            evidence_items.append({"chunkId": chunk_id, "content": content})
                elif tool_name == "file_write":
                    output = result.output if isinstance(result.output, dict) else {}
                    artifacts.append(
                        {
                            "name": str(output.get("name") or ""),
                            "relativePath": str(output.get("relativePath") or ""),
                            "sizeBytes": int(output.get("sizeBytes") or 0),
                            "sourceTool": "file_write",
                        }
                    )
                    # file_write success is a practical terminal signal for "save document" tasks.
                    done = True
                observations.append(
                    f"round={round_idx} tool={tool_name} success=true citations={len(citations)} artifacts={len(artifacts)}"
                )

            if done:
                break

        if not done and total_tool_calls > 0:
            # Best-effort fallback: if ACT already produced usable outputs, do not fail the whole task only
            # because LLM did not emit done=true within round budget.
            if artifacts or evidence_items or citations:
                return (
                    StepTrace(
                        seq=2,
                        stepType="TOOL",
                        status="SUCCEEDED",
                        inputSummary="llm decide and execute act tools",
                        outputSummary=(
                            "act loop reached max rounds; continue with best-effort outputs "
                            f"(calls={total_tool_calls}, citations={len(citations)}, artifacts={len(artifacts)})"
                        ),
                        citations=citations,
                        toolCalls=tool_calls,
                        modelUsage=self._merge_usage_trace(act_usages),
                    ),
                    citations,
                    evidence_items,
                    artifacts,
                )
            return (
                StepTrace(
                    seq=2,
                    stepType="TOOL",
                    status="FAILED",
                    inputSummary="llm decide and execute act tools",
                    outputSummary="act loop exceeded rounds",
                    errorCode="ACT_MAX_ROUNDS_EXCEEDED",
                    errorMessage=f"Exceeded max rounds {self._act_max_rounds}",
                    toolCalls=tool_calls,
                    citations=citations,
                    modelUsage=self._merge_usage_trace(act_usages),
                ),
                citations,
                evidence_items,
                artifacts,
            )

        return (
            StepTrace(
                seq=2,
                stepType="TOOL",
                status="SUCCEEDED",
                inputSummary="llm decide and execute act tools",
                outputSummary=f"act tools done rounds<={self._act_max_rounds}, calls={total_tool_calls}, citations={len(citations)}",
                citations=citations,
                toolCalls=tool_calls,
                modelUsage=self._merge_usage_trace(act_usages),
            ),
            citations,
            evidence_items,
            artifacts,
        )

    def _act_decide_tools(
        self,
        round_idx: int,
        prompt: str,
        doc_ids: List[str],
        citations: List[str],
        observations: List[str],
        available_tools: List[Dict[str, Any]],
        seen_tools: Dict[str, int],
    ) -> Tuple[Dict[str, Any], Optional[ModelUsageTrace], Optional[Tuple[str, str]]]:
        act_prompt = self._build_act_prompt(
            prompt=prompt,
            doc_ids=doc_ids,
            citations=citations,
            observations=observations,
            available_tools=available_tools,
            seen_tools=seen_tools,
            round_idx=round_idx,
        )
        try:
            raw_text, usage = self._provider.generate_text(
                act_prompt,
                {
                    "phase": "act",
                    "round": round_idx,
                    "docIds": doc_ids,
                    "citations": citations,
                    "seenTools": seen_tools,
                    "hasChunkFetch": seen_tools.get("chunk_fetch", 0) > 0,
                    "userPrompt": prompt,
                },
            )
            parsed = parse_act_decision_response(raw_text)
            if not parsed.ok or parsed.decision is None:
                return {}, self._to_usage_trace(usage), (parsed.errorCode or "llm_bad_response", parsed.errorMessage or "ACT decision parse failed")
            return parsed.decision, self._to_usage_trace(usage), None
        except LLMProviderError as exc:
            mapped = exc.code if exc.code in {"llm_unavailable", "llm_timeout", "llm_bad_response", "llm_empty_output"} else "llm_unavailable"
            return {}, None, (mapped, str(exc))

    def _build_act_prompt(
        self,
        prompt: str,
        doc_ids: List[str],
        citations: List[str],
        observations: List[str],
        available_tools: List[Dict[str, Any]],
        seen_tools: Dict[str, int],
        round_idx: int,
    ) -> str:
        tools_json = json.dumps(available_tools, ensure_ascii=False)
        observations_text = "\n".join([f"- {item}" for item in observations[-8:]]) if observations else "- none"
        seen_text = json.dumps(seen_tools, ensure_ascii=False)
        citations_text = ", ".join(citations[:10]) if citations else "none"
        return (
            "You are ACT planner of an agent runtime.\n"
            "Decide which tools to call this round. Return ONLY JSON object.\n"
            "Output schema:\n"
            '{"thought":"short reasoning","toolCalls":[{"tool":"tool_name","args":{}}],"done":false}\n'
            "Rules:\n"
            "- Must choose tool names only from available tools.\n"
            "- Keep args valid for each tool required fields.\n"
            "- If enough evidence is collected, return done=true with empty toolCalls.\n"
            "- Never include markdown or explanations outside JSON.\n"
            f"Round: {round_idx}\n"
            f"User prompt: {prompt}\n"
            f"Doc IDs: {json.dumps(doc_ids, ensure_ascii=False)}\n"
            f"Current citations: {citations_text}\n"
            f"Seen tool calls: {seen_text}\n"
            "Recent observations:\n"
            f"{observations_text}\n"
            f"Available tools: {tools_json}"
        )

    def _merge_usage_trace(self, usages: List[ModelUsageTrace]) -> Optional[ModelUsageTrace]:
        if not usages:
            return None
        model = usages[-1].model
        return ModelUsageTrace(
            model=model,
            tokenIn=sum(item.tokenIn for item in usages),
            tokenOut=sum(item.tokenOut for item in usages),
            costEst=sum(item.costEst for item in usages),
            latencyMs=sum(item.latencyMs for item in usages),
        )

    def _extract_file_write_args(self, prompt: str) -> Optional[Dict[str, Any]]:
        # Prompt convention:
        # file_write: {"path":"reports/out.md","content":"hello","overwrite":false}
        marker = re.search(r"file_write\s*:\s*(\{.*\})", prompt, re.IGNORECASE | re.DOTALL)
        if marker:
            raw_json = marker.group(1).strip()
            try:
                parsed = json.loads(raw_json)
            except Exception:
                return None
            if not isinstance(parsed, dict):
                return None
            path = str(parsed.get("path") or "").strip()
            content = parsed.get("content")
            if not path or content is None:
                return None
            args: Dict[str, Any] = {"path": path, "content": content}
            if "overwrite" in parsed:
                args["overwrite"] = bool(parsed.get("overwrite"))
            return args

        # Fallback: file_write path=reports/out.md content=hello world
        kv = re.search(r"file_write\s+path=(\S+)\s+content=(.+)$", prompt, re.IGNORECASE)
        if not kv:
            return None
        return {"path": kv.group(1).strip(), "content": kv.group(2).strip()}

    def _has_file_write_call(self, act_step: StepTrace) -> bool:
        for call in act_step.toolCalls:
            if call.toolName == "file_write":
                return True
        return False

    def _build_auto_file_write_args(
        self,
        prompt: str,
        output_format: str,
        content: str,
        task_id: str,
    ) -> Optional[Dict[str, Any]]:
        if "file_write" in (prompt or "").lower():
            return None
        if not self._should_auto_file_write(prompt):
            return None

        explicit_path = self._extract_explicit_output_path(prompt)
        if explicit_path:
            return {"path": explicit_path, "content": content, "overwrite": True}

        answer_format = self._normalize_answer_format(output_format)
        ext = ".md"
        if answer_format == "json":
            ext = ".json"
        elif answer_format == "text":
            ext = ".txt"
        safe_task = re.sub(r"[^a-zA-Z0-9_-]+", "-", (task_id or "task").strip()).strip("-") or "task"
        return {"path": f"reports/{safe_task}_result{ext}", "content": content, "overwrite": True}

    def _should_auto_file_write(self, prompt: str) -> bool:
        lowered = (prompt or "").lower()
        positive_markers = [
            "保存",
            "写入文件",
            "写到文件",
            "落盘",
            "导出",
            "output to file",
            "save to file",
            "write to file",
            "persist to",
        ]
        has_save_intent = any(marker in lowered for marker in positive_markers)
        has_doc_intent = any(marker in lowered for marker in ["文档", "markdown", ".md", "报告", "report", "doc"])
        return has_save_intent and has_doc_intent

    def _extract_explicit_output_path(self, prompt: str) -> Optional[str]:
        # examples: 保存到 reports/out.md / save to reports/out.md / output to reports/out.md
        m = re.search(r"(?:保存到|写到|输出到|save to|output to)\s+([A-Za-z0-9_./-]+\.(?:md|txt|json|csv))", prompt or "", re.IGNORECASE)
        if not m:
            return None
        return m.group(1).strip()

    def _generate_step(
        self,
        prompt: str,
        citations: List[str],
        evidence_items: List[Dict[str, str]],
        output_format: str,
    ) -> Tuple[StepTrace, str, Optional[Tuple[str, str]]]:
        generate_prompt = self._build_generate_prompt(prompt, citations, evidence_items, output_format)
        try:
            answer_text, usage = self._provider.generate_text(generate_prompt, {"phase": "generate"})
            cleaned = (answer_text or "").strip()
            if not cleaned:
                return (
                    StepTrace(
                        seq=3,
                        stepType="GENERATE",
                        status="FAILED",
                        inputSummary="generate final answer from prompt + evidence",
                        outputSummary="final answer generation failed",
                        errorCode="llm_empty_output",
                        errorMessage="LLM returned empty output for generate step",
                        modelUsage=self._to_usage_trace(usage),
                        usageUnavailableReason="llm_empty_output",
                    ),
                    "",
                    ("llm_empty_output", "LLM returned empty output for generate step"),
                )
            return (
                StepTrace(
                    seq=3,
                    stepType="GENERATE",
                    status="SUCCEEDED",
                    inputSummary="generate final answer from prompt + evidence",
                    outputSummary=cleaned,
                    modelUsage=self._to_usage_trace(usage),
                    finalAnswerText=cleaned,
                ),
                cleaned,
                None,
            )
        except LLMProviderError as exc:
            mapped_code = exc.code if exc.code in {"llm_unavailable", "llm_timeout", "llm_bad_response", "llm_empty_output"} else "llm_unavailable"
            return (
                StepTrace(
                    seq=3,
                    stepType="GENERATE",
                    status="FAILED",
                    inputSummary="generate final answer from prompt + evidence",
                    outputSummary="final answer generation failed",
                    errorCode=mapped_code,
                    errorMessage=str(exc),
                    usageUnavailableReason=mapped_code,
                ),
                "",
                (mapped_code, str(exc)),
            )

    def _build_generate_prompt(
        self,
        prompt: str,
        citations: List[str],
        evidence_items: List[Dict[str, str]],
        output_format: str,
    ) -> str:
        format_hint = self._normalize_answer_format(output_format)
        citation_text = ", ".join(citations) if citations else "none"
        evidence_lines: List[str] = []
        for item in evidence_items[:5]:
            chunk_id = item.get("chunkId", "")
            content = item.get("content", "").replace("\n", " ").strip()
            if len(content) > 400:
                content = content[:400] + "...(truncated)"
            evidence_lines.append(f"- [{chunk_id}] {content}")
        evidence_text = "\n".join(evidence_lines) if evidence_lines else "- none"
        return (
            "You are an answer generation assistant.\n"
            "Generate the final answer for user request grounded in provided evidence.\n"
            f"User prompt: {prompt}\n"
            f"Evidence citations: {citation_text}\n"
            "Evidence snippets:\n"
            f"{evidence_text}\n"
            f"Output format: {format_hint}\n"
            "When evidence is available, prefer evidence-grounded statements and keep citation markers in text.\n"
            "Return concise and factual answer text only."
        )

    def _normalize_answer_format(self, output_format: str) -> str:
        lowered = (output_format or "").strip().lower()
        if lowered in {"md", "markdown"}:
            return "markdown"
        if lowered == "json":
            return "json"
        if lowered in {"text", "txt"}:
            return "text"
        return "markdown" if lowered == "both" else "text"

    def _verify_step(
        self,
        seq: int,
        result_obj: Dict[str, Any],
        citations: List[str],
        expected_doc_count: int,
        round_idx: Optional[int],
        diff_summary: Optional[str],
    ) -> Tuple[StepTrace, Dict[str, Any]]:
        report = run_verifier(result_obj, citations, self._coverage_threshold, expected_doc_count)
        report_dict = asdict(report)
        status = "SUCCEEDED" if report.overallPass else "FAILED"
        summary = report.summary
        return (
            StepTrace(
                seq=seq,
                stepType="VERIFY",
                status=status,
                inputSummary="verify schema + citations + coverage + risk",
                outputSummary=summary,
                citations=citations,
                round=round_idx,
                verifierReport=report_dict,
                diffSummary=diff_summary,
                errorCode=None if report.overallPass else "VERIFY_FAILED",
                errorMessage=None if report.overallPass else summary,
            ),
            report_dict,
        )

    def _repair_step(
        self,
        seq: int,
        user_prompt: str,
        result_obj: Dict[str, Any],
        verifier_report: Dict[str, Any],
        round_idx: int,
    ) -> Tuple[StepTrace, str, Optional[Tuple[str, str]]]:
        prompt = (
            "You are repair assistant. Fix the result summary based on verifier failures.\n"
            f"Original user prompt: {user_prompt}\n"
            f"Current summary: {result_obj.get('summary', '')}\n"
            f"Verifier failed reasons: {', '.join(verifier_report.get('failedReasons', []))}\n"
            "Output plain text repaired summary."
        )

        try:
            repaired_summary, usage = self._provider.generate_text(prompt, {"round": round_idx})
            return (
                StepTrace(
                    seq=seq,
                    stepType="REPAIR",
                    status="SUCCEEDED",
                    inputSummary="repair from verifier feedback",
                    outputSummary="repair finished",
                    round=round_idx,
                    modelUsage=self._to_usage_trace(usage),
                ),
                repaired_summary,
                None,
            )
        except LLMProviderError as exc:
            return (
                StepTrace(
                    seq=seq,
                    stepType="REPAIR",
                    status="FAILED",
                    inputSummary="repair from verifier feedback",
                    outputSummary="repair failed",
                    round=round_idx,
                    errorCode=exc.code,
                    errorMessage=str(exc),
                ),
                "",
                (exc.code, str(exc)),
            )

    def _final_failed(
        self,
        task_id: str,
        steps: List[StepTrace],
        result_obj: Dict[str, Any],
        citations: List[str],
        verifier_report: Dict[str, Any],
        repair_rounds: List[Dict[str, Any]],
        model_usage: List[ModelUsageTrace],
        final_decision: str,
        error_code: str,
        error_message: str,
        current_round: Optional[int],
        parser_stage: Optional[str] = None,
        raw_response_snippet: Optional[str] = None,
        usage_unavailable_reason: Optional[str] = None,
        final_answer: Optional[Dict[str, Any]] = None,
    ) -> AgentRunResult:
        effective_final_answer = final_answer if final_answer is not None else result_obj.get("finalAnswer")
        result_obj["status"] = "failed"
        result_obj["finalDecision"] = final_decision
        result_obj["verifierReport"] = verifier_report
        result_obj["repairRounds"] = repair_rounds
        result_obj["modelUsage"] = [asdict(item) for item in model_usage]
        result_obj["parserStage"] = parser_stage
        result_obj["rawResponseSnippet"] = raw_response_snippet
        result_obj["usageUnavailableReason"] = usage_unavailable_reason
        result_obj["finalAnswer"] = effective_final_answer

        return AgentRunResult(
            status="FAILED",
            phase=("ACT" if steps and steps[-1].stepType == "TOOL" else (steps[-1].stepType if steps else "VERIFY")),
            phaseStatus="FAILED",
            resultMd=(
                "# Task Result\n\n"
                f"- status: failed\n"
                f"- reason: {error_message}\n"
                f"- finalDecision: {final_decision}"
            ),
            resultJson=json.dumps(result_obj, ensure_ascii=False),
            citations=citations,
            steps=steps,
            errorCode=error_code,
            errorMessage=error_message,
            verifierReport=verifier_report,
            repairRounds=repair_rounds,
            finalDecision=final_decision,
            modelUsage=model_usage,
            currentRound=current_round,
            parserStage=parser_stage,
            rawResponseSnippet=raw_response_snippet,
            usageUnavailableReason=usage_unavailable_reason,
            finalAnswer=effective_final_answer,
        )

    def _to_usage_trace(self, usage: ModelUsage) -> ModelUsageTrace:
        return ModelUsageTrace(
            model=usage.model,
            tokenIn=usage.tokenIn,
            tokenOut=usage.tokenOut,
            costEst=usage.costEst,
            latencyMs=usage.latencyMs,
        )

    def _diff_summary(self, before: str, after: str) -> str:
        if before == after:
            return "no changes"
        return f"summary changed from {len(before)} chars to {len(after)} chars"

    def _build_citations(self, doc_ids: List[str]) -> List[str]:
        if not doc_ids:
            return []
        # 使用 deterministic chunk 引用生成规则，便于 Java 侧验收与回放一致。
        return [f"{doc_id}-c-1" for doc_id in doc_ids[:3]]

    def _extract_http_url(self, prompt: str) -> Optional[str]:
        lowered = (prompt or "").lower()
        if "[use-http]" not in lowered:
            return None
        for token in (prompt or "").split():
            if token.startswith("http://") or token.startswith("https://"):
                return token.strip()
        return None

    def _extract_sql_query(self, prompt: str) -> Optional[str]:
        marker = "sql:"
        lowered = (prompt or "").lower()
        idx = lowered.find(marker)
        if idx < 0:
            return None
        query = (prompt or "")[idx + len(marker):].strip()
        return query or None

    def _should_fail(self, prompt: str) -> bool:
        lowered = prompt.lower()
        return "[fail]" in lowered or "force-fail" in lowered or "mock-fail" in lowered
