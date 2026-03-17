const apiBaseUrl = (window.__APP_CONFIG && window.__APP_CONFIG.API_BASE_URL) || "http://localhost:8080";
const statusFilter = document.getElementById("statusFilter");
const refreshBtn = document.getElementById("refreshBtn");
const catalogBody = document.getElementById("catalogBody");
const error = document.getElementById("error");

checkAuth();
addLogoutButton();

refreshBtn.addEventListener("click", () => {
  loadCatalog();
});

loadCatalog();

async function loadCatalog() {
  error.textContent = "";
  catalogBody.innerHTML = "";
  const status = statusFilter.value;
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  try {
    const res = await fetchWithAuth(`${apiBaseUrl}/api/tools/catalog${query}`);
    const data = await res.json();
    if (!res.ok) {
      throw new Error(`${data.code}: ${data.message}`);
    }
    if (!Array.isArray(data) || data.length === 0) {
      catalogBody.innerHTML = "<tr><td colspan='6'>no tools</td></tr>";
      return;
    }
    catalogBody.innerHTML = data.map((item) => `
      <tr>
        <td>${escapeHtml(item.name)}</td>
        <td>${escapeHtml(item.type)}</td>
        <td>${item.enabled ? "enabled" : "disabled"}</td>
        <td>${escapeHtml(item.requiredScope || "N/A")}</td>
        <td>${escapeHtml(item.timeoutPolicy || "-")} / ${escapeHtml(item.retryPolicy || "-")}</td>
        <td><pre>${escapeHtml(JSON.stringify(item.safetyPolicy || {}, null, 2))}</pre></td>
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
