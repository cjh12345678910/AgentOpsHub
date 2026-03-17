# ACT LLM Tool Selection Change Log (2026-03-05)

## Background
- Goal: migrate ACT from fixed hardcoded tool flow to LLM-driven tool selection.
- Scope: `agent_runtime` ACT loop, backend runtime request role propagation, RBAC/catalog alignment for `file_write`.

## Changes
1. `agent_runtime/llm_parse.py`
- Added `ActDecisionParseResult`.
- Added `parse_act_decision_response(raw)` with strict schema validation:
  - `done` must be boolean.
  - `toolCalls` must be array.
  - each tool call requires non-empty `tool` and object `args`.

2. `agent_runtime/state_machine.py`
- Added ACT runtime configs:
  - `AGENT_ACT_MAX_ROUNDS` (default `3`)
  - `AGENT_ACT_MAX_TOOL_CALLS` (default `8`)
- Replaced ACT hardcoded execution with LLM decision-execution loop:
  - round-based decision via `_act_decide_tools`.
  - execute selected tools through `ToolRegistry.execute`.
  - collect citations/evidence/artifacts from tool outputs.
  - stop when `done=true`.
  - enforce round/call limits with explicit error codes:
    - `ACT_MAX_ROUNDS_EXCEEDED`
    - `ACT_TOOL_CALL_LIMIT_EXCEEDED`
- Added ACT prompt builder with visible tools, observations, and prior call stats.
- Added ACT model usage aggregation in step trace.
- Kept post-generate auto `file_write` fallback for natural-language "save document" intent.

3. `agent_runtime/tool_registry.py`
- Added `list_tools_for_role(role)` for ACT tool visibility.
- Added tool descriptions for prompt grounding.

4. `agent_runtime/llm_provider.py`
- Enhanced `MockLLMProvider` to return structured ACT decisions under `phase=act`.
- Supports explicit prompt triggers for compatibility:
  - `[use-http] <url>` -> selects `http_get`
  - `sql: ...` -> selects `sql_select`

5. Backend runtime request role propagation
- `backend/src/main/java/com/agentopshub/dto/AgentRunRequest.java`
  - Added `role` field.
- `backend/src/main/java/com/agentopshub/service/TaskService.java`
  - Added role extraction from Spring Security context.
  - Propagates `role` in runtime dispatch.
  - Updated dispatch method overloads accordingly.

6. Tool governance and RBAC alignment
- `backend/src/main/java/com/agentopshub/service/ToolGovernanceService.java`
  - Added `file_write` in tool catalog.
- Added migration:
  - `backend/src/main/resources/db/migration/V9__add_file_write_permission.sql`
  - inserts `tool:file_write` permission (idempotent).
  - grants `tool:file_write` to `operator` role (idempotent).

7. Tests and config docs
- `agent_runtime/test_llm_parse.py`
  - added ACT decision parse tests.
- `agent_runtime/test_state_machine.py`
  - added auto file-write intent test.
- `agent_runtime/.env.example`
  - added ACT loop env keys.
- `agent_runtime/README.md`
  - updated ACT section to LLM-driven model.

## Validation Commands
- `cd agent_runtime && python3 -m unittest -v test_llm_parse.py test_state_machine.py test_tool_registry.py`
- `cd backend && mvn -q -Dtest=TaskServiceTest,TaskServiceTraceTest test`

## Rollback Guide
1. Revert ACT loop:
- restore previous `_act_step` fixed tool order implementation in `agent_runtime/state_machine.py`.
2. Remove ACT parser additions:
- revert `parse_act_decision_response` related changes in `agent_runtime/llm_parse.py`.
3. Remove role propagation:
- drop `role` from `AgentRunRequest` and TaskService dispatch wiring.
4. Optional RBAC/catalog rollback:
- revert `V9__add_file_write_permission.sql` and `ToolGovernanceService` catalog entry.
