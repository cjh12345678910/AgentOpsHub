(function () {
  // 强制使用 HTTP 协议访问后端 API（后端未配置 SSL）
  const protocol = "http:";
  const host = window.location.hostname || "localhost";
  window.__APP_CONFIG = {
    API_BASE_URL: `${protocol}//${host}:8080`
  };
})();
