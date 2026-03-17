const apiBaseUrl = (window.__APP_CONFIG && window.__APP_CONFIG.API_BASE_URL) || "http://localhost:8080";
const decisionFilter = document.getElementById("decisionFilter");
const limitInput = document.getElementById("limitInput");
const queryBtn = document.getElementById("queryBtn");
const auditBody = document.getElementById("auditBody");
const error = document.getElementById("error");

checkAuth();
addLogoutButton();

queryBtn.addEventListener("click", () => {
  loadAudit();
});

loadAudit();

async function loadAudit() {
  error.textContent = "";
  auditBody.innerHTML = "";
  const params = new URLSearchParams();
  if (decisionFilter.value) {
    params.set("policyDecision", decisionFilter.value);
  }
  if (limitInput.value) {
    params.set("limit", limitInput.value);
  }

  try {
    const res = await fetchWithAuth(`${apiBaseUrl}/api/tools/audit?${params.toString()}`);
    const data = await res.json();
    if (!res.ok) {
      throw new Error(`${data.code}: ${data.message}`);
    }
    if (!Array.isArray(data) || data.length === 0) {
      auditBody.innerHTML = "<tr><td colspan='5'>no audit logs</td></tr>";
      return;
    }
    auditBody.innerHTML = data.map((item) => `
      <tr>
        <td>${escapeHtml(item.createdAt || "-")}</td>
        <td>${escapeHtml(item.taskId || "-")} / ${escapeHtml(item.toolName || "-")}</td>
        <td>${escapeHtml(item.policyDecision || "-")} / ${escapeHtml(item.denyReason || "-")}</td>
        <td>${escapeHtml(item.requiredScope || "-")} / ${escapeHtml(item.safetyRule || "-")}</td>
        <td>${escapeHtml(item.errorCode || "-")}</td>
      </tr>
    `).join("");
  } catch (e) {
    error.textContent = e.message;
  }
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
