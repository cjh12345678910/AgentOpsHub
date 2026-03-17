// Check authentication
checkAuth();
addLogoutButton();

const apiBaseUrl = (window.__APP_CONFIG && window.__APP_CONFIG.API_BASE_URL) || "http://localhost:8080";
const params = new URLSearchParams(window.location.search);
const taskId = params.get("taskId");
const meta = document.getElementById("meta");
const cancelTaskBtn = document.getElementById("cancelTaskBtn");
const markdownView = document.getElementById("markdownView");
const jsonView = document.getElementById("jsonView");
const error = document.getElementById("error");
const replayLink = document.getElementById("replayLink");
const traceHint = document.getElementById("traceHint");
const failureSummary = document.getElementById("failureSummary");
const citationsView = document.getElementById("citationsView");
const verifierReportView = document.getElementById("verifierReportView");
const repairRoundsView = document.getElementById("repairRoundsView");
const finalDecisionView = document.getElementById("finalDecisionView");
const modelUsageView = document.getElementById("modelUsageView");
const finalAnswerView = document.getElementById("finalAnswerView");
const parserStageView = document.getElementById("parserStageView");
const usageUnavailableReasonView = document.getElementById("usageUnavailableReasonView");
const rawResponseSnippetView = document.getElementById("rawResponseSnippetView");
const artifactsSummaryView = document.getElementById("artifactsSummaryView");
const artifactsListView = document.getElementById("artifactsListView");

if (!taskId) {
  error.textContent = "缺少 taskId，请从创建页面进入。";
} else {
  replayLink.href = `replay.html?taskId=${taskId}`;
  cancelTaskBtn.addEventListener("click", cancelTask);
  pollStatus();
}

async function pollStatus() {
  let polling = true;
  while (polling) {
    try {
      const statusRes = await fetchWithAuth(`${apiBaseUrl}/api/tasks/${taskId}`);
      const statusData = await statusRes.json();
      if (!statusRes.ok) {
        throw new Error(`${statusData.code}: ${statusData.message}`);
      }

      meta.innerHTML = `
        <p>taskId: ${statusData.taskId}</p>
        <p>status: ${statusData.status}</p>
        <p>phase: ${statusData.phase || "-"}</p>
        <p>phaseStatus: ${statusData.phaseStatus || "-"}</p>
        <p>currentRound: ${statusData.currentRound ?? "-"}</p>
        <p>updatedAt: ${statusData.updatedAt}</p>
      `;
      updateCancelButton(statusData.status);

      updateTraceEntry(statusData);

      if (["SUCCEEDED", "FAILED", "CANCELLED"].includes(statusData.status)) {
        polling = false;
      }

      await loadResult();
      if (statusData.status === "FAILED" && statusData.traceAvailable) {
        await loadFailureSummary();
      } else {
        failureSummary.textContent = "";
      }

      if (polling) {
        await sleep(2000);
      }
    } catch (e) {
      error.textContent = e.message;
      polling = false;
    }
  }
}

async function cancelTask() {
  cancelTaskBtn.disabled = true;
  try {
    const response = await fetchWithAuth(`${apiBaseUrl}/api/tasks/${taskId}/cancel`, {
      method: "POST"
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(`${data.code}: ${data.message}`);
    }
    updateCancelButton(data.status);
    await loadResult();
  } catch (e) {
    error.textContent = e.message;
  } finally {
    cancelTaskBtn.disabled = false;
  }
}

function updateCancelButton(status) {
  const terminal = ["SUCCEEDED", "FAILED", "CANCELLED"].includes(status);
  cancelTaskBtn.disabled = terminal;
  cancelTaskBtn.textContent = terminal ? `Task ${status}` : "Cancel Task";
}

function updateTraceEntry(statusData) {
  if (statusData.traceAvailable) {
    replayLink.classList.remove("hidden");
    traceHint.textContent = "trace is available，可进入时间线回放。";
  } else {
    replayLink.classList.add("hidden");
    traceHint.textContent = "trace not available yet，执行后会自动出现入口。";
  }
}

async function loadFailureSummary() {
  try {
    const traceRes = await fetchWithAuth(`${apiBaseUrl}/api/tasks/${taskId}/trace`);
    const traceData = await traceRes.json();
    if (!traceRes.ok) {
      return;
    }

    const failedStep = (traceData.steps || []).find((step) => step.status === "FAILED");
    if (!failedStep) {
      failureSummary.textContent = "";
      return;
    }

    const firstFailedCall = (failedStep.toolCalls || []).find((call) => call.success === false);
    const callMsg = firstFailedCall
      ? ` / tool=${firstFailedCall.toolName} / code=${firstFailedCall.errorCode || "N/A"} / policy=${firstFailedCall.policyDecision || "N/A"} / denyReason=${firstFailedCall.denyReason || "N/A"}`
      : "";
    failureSummary.textContent = `失败定位：step=${failedStep.stepType}(${failedStep.seq}) / code=${failedStep.errorCode || "N/A"}${callMsg}`;
  } catch (_ignored) {
    // 详情页 failure summary 读取失败不影响主流程
  }
}

async function loadResult() {
  const resultRes = await fetchWithAuth(`${apiBaseUrl}/api/tasks/${taskId}/result`);
  const resultData = await resultRes.json();
  if (!resultRes.ok) {
    if (resultData.code === "RESULT_NOT_READY") {
      markdownView.textContent = "result not ready...";
      jsonView.textContent = JSON.stringify(resultData.details, null, 2);
      renderCitations([]);
      renderArtifacts([]);
      renderVerifierExtras(null, [], null, [], null, null, null, null);
      return;
    }
    throw new Error(`${resultData.code}: ${resultData.message}`);
  }

  markdownView.textContent = resultData.resultMd || "";
  try {
    jsonView.textContent = JSON.stringify(JSON.parse(resultData.resultJson), null, 2);
  } catch (_ignored) {
    jsonView.textContent = resultData.resultJson || "";
  }
  renderCitations(resultData.citations || []);
  renderArtifacts(resultData.artifacts || []);
  renderVerifierExtras(
    resultData.verifierReport || null,
    resultData.repairRounds || [],
    resultData.finalDecision || null,
    resultData.modelUsage || [],
    resultData.parserStage || null,
    resultData.rawResponseSnippet || null,
    resultData.usageUnavailableReason || null,
    resolveFinalAnswer(resultData)
  );
}

function renderCitations(citations) {
  if (!Array.isArray(citations) || citations.length === 0) {
    citationsView.innerHTML = "<li>none</li>";
    return;
  }
  citationsView.innerHTML = citations
    .map((citation) => `<li>${escapeHtml(citation)}</li>`)
    .join("");
}

function renderArtifacts(artifacts) {
  if (!Array.isArray(artifacts) || artifacts.length === 0) {
    artifactsSummaryView.textContent = "not available";
    artifactsListView.innerHTML = "<p>none</p>";
    return;
  }
  artifactsSummaryView.textContent = `count: ${artifacts.length}`;
  artifactsListView.innerHTML = artifacts
    .map((artifact, index) => {
      const name = escapeHtml(artifact.name || `artifact-${index + 1}`);
      const relativePath = escapeHtml(artifact.relativePath || "-");
      const sizeBytes = escapeHtml(String(artifact.sizeBytes || 0));
      const sourceTool = escapeHtml(artifact.sourceTool || "-");
      const downloadUrl = artifact.downloadUrl || "";
      const action = downloadUrl
        ? `<button type="button" class="download-artifact-btn" data-url="${escapeHtml(downloadUrl)}" data-name="${name}">Download</button>`
        : "<span class=\"muted\">no download url</span>";
      return `
        <div class="artifact-row">
          <p><strong>${name}</strong> (${sizeBytes} bytes)</p>
          <p class="muted">path=${relativePath} / tool=${sourceTool}</p>
          ${action}
        </div>
      `;
    })
    .join("");

  document.querySelectorAll(".download-artifact-btn").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const url = btn.getAttribute("data-url");
      const fileName = btn.getAttribute("data-name") || "artifact.bin";
      if (!url) {
        return;
      }
      try {
        const response = await fetchWithAuth(`${apiBaseUrl}${url}`);
        if (!response.ok) {
          const payload = await response.json();
          throw new Error(`${payload.code}: ${payload.message}`);
        }
        const blob = await response.blob();
        const href = URL.createObjectURL(blob);
        const anchor = document.createElement("a");
        anchor.href = href;
        anchor.download = fileName;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        URL.revokeObjectURL(href);
      } catch (e) {
        error.textContent = e.message;
      }
    });
  });
}

function renderVerifierExtras(
  verifierReport,
  repairRounds,
  finalDecision,
  modelUsage,
  parserStage,
  rawResponseSnippet,
  usageUnavailableReason,
  finalAnswer
) {
  finalDecisionView.textContent = `finalDecision: ${finalDecision || "not available"}`;

  if (verifierReport) {
    verifierReportView.textContent = JSON.stringify(verifierReport, null, 2);
  } else {
    verifierReportView.textContent = "not available";
  }

  if (!Array.isArray(repairRounds) || repairRounds.length === 0) {
    repairRoundsView.textContent = "not available";
  } else {
    repairRoundsView.innerHTML = repairRounds
      .map((round) => {
        const line = [
          `Round ${escapeHtml(round.round ?? "-")}`,
          `verify=${escapeHtml(round.verifyOutcome || "-")}`,
          `diff=${escapeHtml(round.diffSummary || "-")}`,
        ].join(" | ");
        return `<p>${line}</p>`;
      })
      .join("");
  }

  if (!Array.isArray(modelUsage) || modelUsage.length === 0) {
    const reason = usageUnavailableReason || "not available";
    modelUsageView.textContent = `not available (reason: ${reason})`;
  } else {
    modelUsageView.textContent = JSON.stringify(modelUsage, null, 2);
  }

  parserStageView.textContent = `stage: ${parserStage || "not available"}`;
  usageUnavailableReasonView.textContent = `usage missing reason: ${usageUnavailableReason || "not available"}`;
  rawResponseSnippetView.textContent = rawResponseSnippet || "not available";
  finalAnswerView.textContent = finalAnswer || "not available";
}

function resolveFinalAnswer(resultData) {
  if (resultData && resultData.finalAnswer && typeof resultData.finalAnswer.text === "string" && resultData.finalAnswer.text.trim()) {
    return resultData.finalAnswer.text;
  }
  try {
    const parsed = JSON.parse(resultData.resultJson || "{}");
    if (parsed && parsed.finalAnswer && typeof parsed.finalAnswer.text === "string" && parsed.finalAnswer.text.trim()) {
      return parsed.finalAnswer.text;
    }
  } catch (_ignored) {
    // ignore malformed json
  }
  return null;
}

function escapeHtml(value) {
  if (value === null || value === undefined) {
    return "";
  }
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
