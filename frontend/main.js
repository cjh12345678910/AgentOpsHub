// Check authentication
checkAuth();
addLogoutButton();

const apiBaseUrl = (window.__APP_CONFIG && window.__APP_CONFIG.API_BASE_URL) || "http://localhost:8080";
const form = document.getElementById("taskForm");
const result = document.getElementById("result");

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  result.innerHTML = "";

  const docIds = (form.docIds.value || "")
    .split(/[\n,]/)
    .map((id) => id.trim())
    .filter(Boolean);

  const payload = {
    prompt: form.prompt.value,
    outputFormat: form.outputFormat.value,
    docIds
  };

  try {
    // Debug: 检查 token 是否存在
    const token = localStorage.getItem('agentops_auth_token');
    console.log('Creating task with token:', token ? 'Token exists' : 'No token found');
    console.log('Token value:', token);

    const res = await fetchWithAuth(`${apiBaseUrl}/api/tasks`, {
      method: "POST",
      body: JSON.stringify(payload)
    });
    const data = await res.json();
    if (!res.ok) {
      throw new Error(`${data.code}: ${data.message}`);
    }
    result.innerHTML = `
      <p>Task created successfully</p>
      <p>taskId: <strong>${data.taskId}</strong></p>
      <a href="task.html?taskId=${data.taskId}">Go to Task Detail</a>
    `;
  } catch (error) {
    result.innerHTML = `<p class="error">${error.message}</p>`;
  }
});
