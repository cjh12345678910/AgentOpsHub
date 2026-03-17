(function () {
  const protocol = window.location.protocol || "http:";
  const host = window.location.hostname || "localhost";
  window.__APP_CONFIG = {
    API_BASE_URL: `${protocol}//${host}:8080`
  };
})();
