// Authentication utility functions
const AUTH_TOKEN_KEY = 'agentops_auth_token';
const AUTH_USER_KEY = 'agentops_auth_user';

// Get token from localStorage
function getToken() {
  return localStorage.getItem(AUTH_TOKEN_KEY);
}

// Set token to localStorage
function setToken(token) {
  localStorage.setItem(AUTH_TOKEN_KEY, token);
}

// Remove token from localStorage
function removeToken() {
  localStorage.removeItem(AUTH_TOKEN_KEY);
  localStorage.removeItem(AUTH_USER_KEY);
}

// Get user info from localStorage
function getUser() {
  const userStr = localStorage.getItem(AUTH_USER_KEY);
  return userStr ? JSON.parse(userStr) : null;
}

// Set user info to localStorage
function setUser(user) {
  localStorage.setItem(AUTH_USER_KEY, JSON.stringify(user));
}

// Check if user is authenticated
function isAuthenticated() {
  return !!getToken();
}

// Logout
function logout() {
  removeToken();
  window.location.href = 'login.html';
}

// Check authentication and redirect to login if not authenticated
function checkAuth() {
  if (!isAuthenticated()) {
    window.location.href = 'login.html';
  }
}

// Fetch with authentication header
async function fetchWithAuth(url, options = {}) {
  const token = getToken();
  const headers = {
    ...options.headers,
  };

  const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData;
  if (!isFormData && !headers['Content-Type'] && !headers['content-type']) {
    headers['Content-Type'] = 'application/json';
  }

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(url, {
    ...options,
    headers,
  });

  // If 401, redirect to login
  if (response.status === 401) {
    removeToken();
    window.location.href = 'login.html';
    throw new Error('Authentication required');
  }

  return response;
}

// Add logout button to navigation
function addLogoutButton() {
  const nav = document.querySelector('.nav-links');
  if (nav && !document.getElementById('logoutBtn')) {
    const user = getUser();
    const userInfo = user ? `<span class="muted" style="padding: 8px 12px;">User: ${user.username}</span>` : '';
    const logoutBtn = document.createElement('a');
    logoutBtn.id = 'logoutBtn';
    logoutBtn.href = '#';
    logoutBtn.textContent = 'Logout';
    logoutBtn.style.marginLeft = 'auto';
    logoutBtn.onclick = (e) => {
      e.preventDefault();
      logout();
    };
    if (userInfo) {
      nav.insertAdjacentHTML('beforeend', userInfo);
    }
    nav.appendChild(logoutBtn);
  }
}
