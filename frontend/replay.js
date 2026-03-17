const apiBaseUrl = (window.__APP_CONFIG && window.__APP_CONFIG.API_BASE_URL) || "http://localhost:8080";
const params = new URLSearchParams(window.location.search);
const taskId = params.get("taskId");

const backToTask = document.getElementById("backToTask");
const replayLoading = document.getElementById("replayLoading");
const replayEmpty = document.getElementById("replayEmpty");
const replayError = document.getElementById("replayError");
const replayMeta = document.getElementById("replayMeta");
const timeline = document.getElementById("timeline");

checkAuth();
addLogoutButton();

if (!taskId) {
  showError("缺少 taskId，无法加载 replay timeline。");
} else {
  backToTask.href = `task.html?taskId=${taskId}`;
  loadTrace();
}

async function loadTrace() {
  setLoading(true);
  hideAllStates();

  try {
    const response = await fetchWithAuth(`${apiBaseUrl}/api/tasks/${taskId}/trace`);
    const data = await response.json();

    if (!response.ok) {
      if (data.code === "TRACE_NOT_AVAILABLE") {
        showEmpty("TRACE_NOT_AVAILABLE: 当前任务还没有可回放的 trace 数据。可以稍后重试。", true);
        return;
      }
      showError(`${data.code}: ${data.message}`);
      return;
    }

    renderMeta(data);
    renderTimeline(data.steps || []);
  } catch (e) {
    showError(`NETWORK_ERROR: ${e.message}`);
  } finally {
    setLoading(false);
  }
}

function renderMeta(data) {
  replayMeta.classList.remove("hidden");
  replayMeta.innerHTML = `
    <p>taskId: ${data.taskId}</p>
    <p>status: ${data.status}</p>
    <p>traceId: ${data.traceId || "N/A"}</p>
    <p>incomplete: ${data.incomplete ? "true" : "false"}</p>
  `;
}

function renderTimeline(steps) {
  if (!steps.length) {
    showEmpty("当前 trace 返回为空 timeline。", true);
    return;
  }

  timeline.classList.remove("hidden");
  timeline.innerHTML = steps.map(renderStepCard).join("");

  const toggles = timeline.querySelectorAll(".trace-toggle");
  toggles.forEach((button) => {
    button.addEventListener("click", () => {
      const targetId = button.getAttribute("data-target");
      const panel = document.getElementById(targetId);
      if (!panel) {
        return;
      }
      panel.classList.toggle("hidden");
      button.textContent = panel.classList.contains("hidden") ? "展开详情" : "收起详情";
    });
  });
}

function renderStepCard(step) {
  const toolCalls = step.toolCalls || [];
  const callsHtml = toolCalls.length ? toolCalls.map(renderToolCall).join("") : "<p class=\"muted\">No tool calls in this step.</p>";

  return `
    <article class="step-card step-${(step.status || "").toLowerCase()}">
      <header class="step-header">
        <h3>Step ${step.seq} / ${step.stepType}</h3>
        <p>status=${step.status} | latency=${step.latencyMs || 0}ms | retry=${step.retryCount || 0} | round=${step.round ?? "-"}</p>
      </header>
      <p><strong>Input:</strong> ${escapeHtml(step.inputSummary || "")}</p>
      <p><strong>Output:</strong> ${escapeHtml(step.outputSummary || "")}</p>
      <p><strong>Error:</strong> ${escapeHtml(step.errorCode || "N/A")} / ${escapeHtml(step.errorMessage || "N/A")}</p>
      <p><strong>Final Answer:</strong> ${escapeHtml(step.finalAnswerText || "N/A")}</p>
      <p><strong>Parser Stage:</strong> ${escapeHtml(step.parserStage || "N/A")}</p>
      <p><strong>Raw Snippet:</strong> ${escapeHtml(step.rawResponseSnippet || "N/A")}</p>
      <p><strong>Usage Unavailable Reason:</strong> ${escapeHtml(step.usageUnavailableReason || "N/A")}</p>
      <p><strong>Diff:</strong> ${escapeHtml(step.diffSummary || "N/A")}</p>
      <p><strong>Model Usage:</strong> ${escapeHtml(step.modelUsage ? JSON.stringify(step.modelUsage) : "N/A")}</p>
      <p><strong>Verifier:</strong> ${escapeHtml(step.verifierReport ? JSON.stringify(step.verifierReport) : "N/A")}</p>
      <div class="tool-call-list">${callsHtml}</div>
    </article>
  `;
}

function renderToolCall(call) {
  const detailId = `call-detail-${call.id}`;
  return `
    <div class="tool-call ${call.success ? "ok" : "fail"}">
      <div class="tool-call-head">
        <p>#${call.callOrder || 1} tool=${escapeHtml(call.toolName || "unknown")} | success=${call.success ? "true" : "false"} | latency=${call.latencyMs || 0}ms</p>
        <button class="trace-toggle" type="button" data-target="${detailId}">展开详情</button>
      </div>
      <div id="${detailId}" class="tool-call-detail hidden">
        <p><strong>request summary:</strong> ${escapeHtml(call.requestSummary || "")}</p>
        <p><strong>response summary:</strong> ${escapeHtml(call.responseSummary || "")}</p>
        <p><strong>error:</strong> ${escapeHtml(call.errorCode || "N/A")} / ${escapeHtml(call.errorMessage || "N/A")}</p>
        <p><strong>policy:</strong> ${escapeHtml(call.policyDecision || "N/A")} / ${escapeHtml(call.denyReason || "N/A")}</p>
        <p><strong>requiredScope:</strong> ${escapeHtml(call.requiredScope || "N/A")} / <strong>safetyRule:</strong> ${escapeHtml(call.safetyRule || "N/A")}</p>
        <p><strong>traceId:</strong> ${escapeHtml(call.traceId || "N/A")}</p>
        <p><strong>citations:</strong> ${(call.citations && call.citations.length) ? call.citations.map(escapeHtml).join(", ") : "(placeholder)"}</p>
      </div>
    </div>
  `;
}

function showEmpty(message, showMeta) {
  replayEmpty.classList.remove("hidden");
  replayEmpty.textContent = message;
  if (showMeta) {
    replayMeta.classList.remove("hidden");
    replayMeta.innerHTML = `<p>taskId: ${taskId}</p>`;
  }
}

function showError(message) {
  replayError.classList.remove("hidden");
  replayError.textContent = message;
}

function setLoading(loading) {
  replayLoading.classList.toggle("hidden", !loading);
}

function hideAllStates() {
  replayEmpty.classList.add("hidden");
  replayError.classList.add("hidden");
  replayMeta.classList.add("hidden");
  timeline.classList.add("hidden");
}

function escapeHtml(input) {
  if (input === null || input === undefined) {
    return "";
  }
  return String(input)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}
