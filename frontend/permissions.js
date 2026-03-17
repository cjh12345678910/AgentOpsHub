const apiBaseUrl = (window.__APP_CONFIG && window.__APP_CONFIG.API_BASE_URL) || "http://localhost:8080";
const roleInput = document.getElementById("roleInput");
const scopesInput = document.getElementById("scopesInput");
const saveBtn = document.getElementById("saveBtn");
const permissionBody = document.getElementById("permissionBody");
const error = document.getElementById("error");

checkAuth();
addLogoutButton();

saveBtn.addEventListener("click", async () => {
  error.textContent = "";
  const role = roleInput.value.trim();
  const scopes = scopesInput.value
    .split(/[\n,]/)
    .map((item) => item.trim())
    .filter(Boolean);

  try {
    const res = await fetchWithAuth(`${apiBaseUrl}/api/tools/permissions/${encodeURIComponent(role)}`, {
      method: "PUT",
      body: JSON.stringify({ role, scopes }),
    });
    const data = await res.json();
    if (!res.ok) {
      throw new Error(`${data.code}: ${data.message}`);
    }
    roleInput.value = data.role || "";
    scopesInput.value = (data.scopes || []).join(", ");
    await loadPermissions();
  } catch (e) {
    error.textContent = e.message;
  }
});

loadPermissions();

async function loadPermissions() {
  error.textContent = "";
  permissionBody.innerHTML = "";
  try {
    const res = await fetchWithAuth(`${apiBaseUrl}/api/tools/permissions`);
    const data = await res.json();
    if (!res.ok) {
      throw new Error(`${data.code}: ${data.message}`);
    }
    permissionBody.innerHTML = data.map((item) => `
      <tr>
        <td>${escapeHtml(item.role)}</td>
        <td>${escapeHtml((item.scopes || []).join(", "))}</td>
        <td><button type="button" data-role="${escapeHtml(item.role)}" class="delete-btn">Delete</button></td>
      </tr>
    `).join("");
    bindDeleteButtons();
  } catch (e) {
    error.textContent = e.message;
  }
}

function bindDeleteButtons() {
  const buttons = permissionBody.querySelectorAll(".delete-btn");
  buttons.forEach((btn) => {
    btn.addEventListener("click", async () => {
      const role = btn.getAttribute("data-role");
      if (!role) {
        return;
      }
      try {
        const res = await fetchWithAuth(`${apiBaseUrl}/api/tools/permissions/${encodeURIComponent(role)}`, {
          method: "DELETE",
        });
        if (!res.ok) {
          const data = await res.json();
          throw new Error(`${data.code}: ${data.message}`);
        }
        await loadPermissions();
      } catch (e) {
        error.textContent = e.message;
      }
    });
  });
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
