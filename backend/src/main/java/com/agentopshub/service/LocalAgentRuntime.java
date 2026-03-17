package com.agentopshub.service;

import com.agentopshub.dto.AgentRunRequest;
import com.agentopshub.dto.AgentRunResponse;
import com.agentopshub.dto.AgentStepResult;
import com.agentopshub.dto.AgentToolCallResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class LocalAgentRuntime {
    private final ObjectMapper objectMapper;

    public LocalAgentRuntime(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AgentRunResponse run(AgentRunRequest request) {
        String prompt = request.getPrompt() == null ? "" : request.getPrompt();
        List<String> docIds = request.getDocIds() == null ? List.of() : request.getDocIds();
        List<String> citations = buildCitations(docIds);

        AgentStepResult plan = new AgentStepResult();
        plan.setSeq(1);
        plan.setStepType("PLAN");
        plan.setStatus("SUCCEEDED");
        plan.setInputSummary(prompt);
        plan.setOutputSummary("generated plan: analyze -> retrieve evidence -> verify");
        plan.setRetryCount(0);
        plan.setCitations(List.of());
        plan.setToolCalls(List.of());
        plan.setRound(null);

        AgentStepResult act = new AgentStepResult();
        act.setSeq(2);
        act.setStepType("TOOL");
        act.setInputSummary("execute rag_search + chunk_fetch");
        act.setRetryCount(0);
        act.setRound(null);

        if (shouldFail(prompt)) {
            act.setStatus("FAILED");
            act.setOutputSummary("act failed");
            act.setErrorCode("AGENT_ACT_FAILED");
            act.setErrorMessage("Prompt requested fail path");
            act.setCitations(List.of());
            act.setToolCalls(List.of(toolCall(1, "rag_search", false, List.of(), "AGENT_ACT_FAILED", "Prompt requested fail path")));

            AgentRunResponse fail = new AgentRunResponse();
            fail.setStatus("FAILED");
            fail.setPhase("ACT");
            fail.setPhaseStatus("FAILED");
            fail.setResultMd("# Task Result\n\n- status: failed\n- reason: Prompt requested fail path");
            fail.setResultJson(toJson(Map.of(
                "status", "failed",
                "summary", prompt,
                "citations", List.of(),
                "phase", "ACT",
                "finalDecision", "failed_unrecoverable",
                "artifacts", List.of(),
                "verifierReport", Map.of("overallPass", false, "failedReasons", List.of("llm_unavailable")),
                "repairRounds", List.of(),
                "modelUsage", List.of()
            )));
            fail.setCitations(List.of());
            fail.setSteps(List.of(plan, act));
            fail.setErrorCode("AGENT_ACT_FAILED");
            fail.setErrorMessage("Prompt requested fail path");
            fail.setFinalDecision("failed_unrecoverable");
            fail.setRepairRounds(List.of());
            fail.setModelUsage(List.of());
            return fail;
        }

        act.setStatus("SUCCEEDED");
        act.setOutputSummary("retrieved citations count=" + citations.size());
        act.setCitations(citations);
        act.setToolCalls(List.of(
            toolCall(1, "rag_search", true, citations, null, null),
            toolCall(2, "chunk_fetch", true, citations, null, null)
        ));

        AgentStepResult generate = new AgentStepResult();
        generate.setSeq(3);
        generate.setStepType("GENERATE");
        generate.setStatus("SUCCEEDED");
        generate.setInputSummary("generate final answer from prompt + evidence");
        generate.setOutputSummary(prompt);
        generate.setRetryCount(0);
        generate.setCitations(citations);
        generate.setToolCalls(List.of());
        generate.setRound(null);
        generate.setFinalAnswerText(prompt);

        AgentStepResult verify = new AgentStepResult();
        verify.setSeq(4);
        verify.setStepType("VERIFY");
        verify.setStatus("SUCCEEDED");
        verify.setInputSummary("verify schema + citations");
        verify.setOutputSummary("verification passed");
        verify.setRetryCount(0);
        verify.setCitations(citations);
        verify.setToolCalls(List.of());
        verify.setRound(null);
        verify.setVerifierReport(Map.of(
            "overallPass", true,
            "failedReasons", List.of(),
            "ruleResults", List.of(),
            "summary", "all verifier rules passed"
        ));

        AgentRunResponse success = new AgentRunResponse();
        success.setStatus("SUCCEEDED");
        success.setPhase("VERIFY");
        success.setPhaseStatus("SUCCEEDED");
        success.setResultMd("# Task Result\n\n- status: succeeded\n- summary: " + prompt + "\n- citations: " + (citations.isEmpty() ? "none" : String.join(", ", citations)));
        success.setResultJson(toJson(Map.of(
            "status", "succeeded",
                "summary", prompt,
                "citations", citations,
                "phase", "VERIFY",
                "artifacts", List.of(),
                "finalAnswer", Map.of("text", prompt, "format", "text"),
                "verifierReport", verify.getVerifierReport(),
                "repairRounds", List.of(),
                "finalDecision", "passed_first_try",
                "modelUsage", List.of()
            )));
        success.setCitations(citations);
        success.setSteps(List.of(plan, act, generate, verify));
        success.setVerifierReport(verify.getVerifierReport());
        success.setRepairRounds(List.of());
        success.setFinalDecision("passed_first_try");
        success.setModelUsage(List.of());
        success.setFinalAnswer(Map.of("text", prompt, "format", "text"));
        success.setCurrentRound(null);
        return success;
    }

    private AgentToolCallResult toolCall(int order, String name, boolean success, List<String> citations, String errorCode, String errorMessage) {
        AgentToolCallResult call = new AgentToolCallResult();
        call.setCallOrder(order);
        call.setToolName(name);
        call.setRequestSummary("{\"tool\":\"" + name + "\"}");
        call.setResponseSummary(success ? "{\"status\":\"ok\",\"citations\":" + citations.size() + "}" : "{\"status\":\"failed\"}");
        call.setSuccess(success);
        call.setLatencyMs((long) (8 + order));
        call.setErrorCode(errorCode);
        call.setErrorMessage(errorMessage);
        call.setCitations(citations);
        call.setPolicyDecision(success ? "allow" : "deny");
        call.setDenyReason(success ? null : "local_runtime_failure");
        if ("rag_search".equals(name)) {
            call.setRequiredScope("rag:search");
        } else if ("chunk_fetch".equals(name)) {
            call.setRequiredScope("rag:chunk:read");
        } else {
            call.setRequiredScope(null);
        }
        call.setSafetyRule(null);
        return call;
    }

    private List<String> buildCitations(List<String> docIds) {
        List<String> citations = new ArrayList<>();
        for (int i = 0; i < Math.min(3, docIds.size()); i++) {
            String docId = docIds.get(i);
            if (docId != null && !docId.isBlank()) {
                citations.add(docId.trim() + "-c-1");
            }
        }
        return citations;
    }

    private boolean shouldFail(String prompt) {
        String lowered = prompt.toLowerCase(Locale.ROOT);
        return lowered.contains("[fail]") || lowered.contains("force-fail") || lowered.contains("mock-fail");
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
