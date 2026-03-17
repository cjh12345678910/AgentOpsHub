const apiBaseUrl = (window.__APP_CONFIG && window.__APP_CONFIG.API_BASE_URL) || "http://localhost:8080";
const form = document.getElementById("loginForm");
const errorDiv = document.getElementById("error");

// Redirect to index if already logged in
if (isAuthenticated()) {
  window.location.href = 'index.html';
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  errorDiv.classList.add("hidden");
  errorDiv.textContent = "";

  const username = form.username.value.trim();
  const password = form.password.value;

  if (!username || !password) {
    showError("Please enter both username and password");
    return;
  }

  const submitButton = form.querySelector('button[type="submit"]');
  submitButton.disabled = true;
  submitButton.textContent = "Logging in...";

  try {
    const response = await fetch(`${apiBaseUrl}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password })
    });

    const data = await response.json();

    if (!response.ok) {
      throw new Error(data.message || `Login failed: ${data.code}`);
    }

    // Store token and user info
    // 如果后端返回的字段名不是 token，需要修改这里
    // 例如：setToken(data.accessToken) 或 setToken(data.access_token)
    setToken(data.token);
    setUser(data.user);

    // Debug: 确认 token 已保存
    console.log('Token saved:', localStorage.getItem('agentops_auth_token'));

    // Redirect to main page
    window.location.href = 'index.html';
  } catch (error) {
    showError(error.message);
    submitButton.disabled = false;
    submitButton.textContent = "Login";
  }
});

function showError(message) {
  errorDiv.textContent = message;
  errorDiv.classList.remove("hidden");
}
