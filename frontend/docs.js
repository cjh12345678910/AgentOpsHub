const apiBaseUrl = (window.__APP_CONFIG && window.__APP_CONFIG.API_BASE_URL) || "http://localhost:8080";

const uploadForm = document.getElementById("uploadForm");
const docFile = document.getElementById("docFile");
const uploadMessage = document.getElementById("uploadMessage");
const refreshDocs = document.getElementById("refreshDocs");
const docsLoading = document.getElementById("docsLoading");
const docsError = document.getElementById("docsError");
const docsEmpty = document.getElementById("docsEmpty");
const docsTable = document.getElementById("docsTable");

checkAuth();
addLogoutButton();

uploadForm.addEventListener("submit", uploadDoc);
refreshDocs.addEventListener("click", loadDocs);

loadDocs();

async function uploadDoc(event) {
  event.preventDefault();
  uploadMessage.textContent = "";
  if (!docFile.files.length) {
    uploadMessage.textContent = "请选择文件后再上传。";
    return;
  }

  const formData = new FormData();
  formData.append("file", docFile.files[0]);

  try {
    const response = await fetchWithAuth(`${apiBaseUrl}/api/docs/upload`, {
      method: "POST",
      body: formData
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(`${data.code}: ${data.message}`);
    }

    uploadMessage.textContent = `上传成功: docId=${data.docId}, status=${data.status}, chunkCount=${data.chunkCount}`;
    docFile.value = "";
    await loadDocs();
  } catch (error) {
    uploadMessage.textContent = `上传失败: ${error.message}`;
  }
}

async function loadDocs() {
  showLoading(true);
  clearState();

  try {
    const response = await fetchWithAuth(`${apiBaseUrl}/api/docs`);
    const data = await response.json();
    if (!response.ok) {
      throw new Error(`${data.code}: ${data.message}`);
    }

    if (!Array.isArray(data) || data.length === 0) {
      docsEmpty.classList.remove("hidden");
      docsTable.innerHTML = "";
      return;
    }

    docsTable.innerHTML = renderTable(data);
  } catch (error) {
    docsError.classList.remove("hidden");
    docsError.textContent = error.message;
  } finally {
    showLoading(false);
  }
}

function renderTable(rows) {
  const header = `
    <table class="simple-table">
      <thead>
        <tr>
          <th>docId</th>
          <th>name</th>
          <th>type</th>
          <th>status</th>
          <th>size</th>
          <th>updatedAt</th>
          <th>error</th>
        </tr>
      </thead>
      <tbody>
  `;
  const body = rows.map((row) => `
      <tr>
        <td>${escapeHtml(row.docId || "")}</td>
        <td>${escapeHtml(row.name || "")}</td>
        <td>${escapeHtml(row.contentType || "")}</td>
        <td>${renderStatus(row.status || "")}</td>
        <td>${escapeHtml(String(row.fileSize || 0))}</td>
        <td>${escapeHtml(row.updatedAt || "")}</td>
        <td>${escapeHtml(row.errorMessage || "")}</td>
      </tr>
  `).join("");
  return `${header}${body}</tbody></table>`;
}

function renderStatus(status) {
  const safe = escapeHtml(status);
  if (status === "FAILED") {
    return `<span class="error">${safe}</span>`;
  }
  if (status === "PARSING") {
    return `<span class="muted">${safe}</span>`;
  }
  return `<span>${safe}</span>`;
}

function clearState() {
  docsError.classList.add("hidden");
  docsError.textContent = "";
  docsEmpty.classList.add("hidden");
}

function showLoading(loading) {
  docsLoading.classList.toggle("hidden", !loading);
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
