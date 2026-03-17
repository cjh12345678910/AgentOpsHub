const apiBaseUrl = (window.__APP_CONFIG && window.__APP_CONFIG.API_BASE_URL) || "http://localhost:8080";

const ragForm = document.getElementById("ragForm");
const ragState = document.getElementById("ragState");
const ragMeta = document.getElementById("ragMeta");
const ragError = document.getElementById("ragError");
const ragResults = document.getElementById("ragResults");
const chunkDetail = document.getElementById("chunkDetail");

checkAuth();
addLogoutButton();

ragForm.addEventListener("submit", onSearch);

async function onSearch(event) {
  event.preventDefault();
  ragError.textContent = "";
  ragMeta.textContent = "";
  chunkDetail.classList.add("hidden");
  chunkDetail.innerHTML = "";

  const payload = {
    query: document.getElementById("query").value,
    docIds: (document.getElementById("docIds").value || "")
      .split(/[\n,]/)
      .map((v) => v.trim())
      .filter(Boolean),
    topK: Number(document.getElementById("topK").value)
  };

  ragState.textContent = "Searching...";
  ragResults.innerHTML = "";

  try {
    const response = await fetchWithAuth(`${apiBaseUrl}/api/rag/search`, {
      method: "POST",
      body: JSON.stringify(payload)
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(`${data.code}: ${data.message}`);
    }

    const items = data.items || [];
    ragMeta.textContent = buildMetaText(data);
    if (!items.length) {
      ragState.textContent = data.retrievalMode === "fallback_lexical"
        ? "No retrieval results (fallback_lexical)."
        : "No retrieval results.";
      return;
    }

    ragState.textContent = data.retrievalMode === "fallback_lexical"
      ? `Found ${items.length} result(s) via fallback_lexical.`
      : `Found ${items.length} result(s).`;
    ragResults.innerHTML = renderResults(items);

    const detailButtons = ragResults.querySelectorAll("[data-chunk-id]");
    detailButtons.forEach((button) => {
      button.addEventListener("click", async () => {
        await loadChunkDetail(button.getAttribute("data-chunk-id"));
      });
    });
  } catch (error) {
    ragState.textContent = "";
    ragError.textContent = error.message;
  }
}

function renderResults(items) {
  return items.map((item) => `
    <article class="result-card">
      <p><strong>rank:</strong> ${escapeHtml(String(item.rank ?? ""))}</p>
      <p><strong>chunkId:</strong> ${escapeHtml(item.chunkId)}</p>
      <p><strong>doc:</strong> ${escapeHtml(item.docId)} (${escapeHtml(item.docName || "")})</p>
      <p><strong>score:</strong> ${escapeHtml(String(item.score))} (${escapeHtml(item.scoreType || "unknown")})</p>
      <p><strong>snippet:</strong> ${escapeHtml(item.snippet || "")}</p>
      <button type="button" data-chunk-id="${escapeHtml(item.chunkId)}">View Chunk Detail</button>
    </article>
  `).join("");
}

function buildMetaText(data) {
  const parts = [];
  if (data.retrievalMode) {
    parts.push(`retrievalMode=${data.retrievalMode}`);
  }
  if (data.scoreType) {
    parts.push(`scoreType=${data.scoreType}`);
  }
  if (data.partialCoverage === true) {
    parts.push("partialCoverage=true");
  }
  if (typeof data.candidateCount === "number") {
    parts.push(`candidateCount=${data.candidateCount}`);
  }
  if (typeof data.rerankApplied === "boolean") {
    parts.push(`rerankApplied=${data.rerankApplied}`);
  }
  if (data.embeddingProvider) {
    parts.push(`embeddingProvider=${data.embeddingProvider}`);
  }
  if (Array.isArray(data.scoreDistribution) && data.scoreDistribution.length > 0) {
    parts.push(`scoreDist=[${data.scoreDistribution.map((v) => Number(v).toFixed(3)).join(", ")}]`);
  }
  if (data.fallbackReason) {
    parts.push(`fallbackReason=${data.fallbackReason}`);
  }
  return parts.join(" | ");
}

async function loadChunkDetail(chunkId) {
  try {
    const response = await fetchWithAuth(`${apiBaseUrl}/api/rag/chunk/${encodeURIComponent(chunkId)}`);
    const data = await response.json();
    if (!response.ok) {
      throw new Error(`${data.code}: ${data.message}`);
    }

    chunkDetail.classList.remove("hidden");
    chunkDetail.innerHTML = `
      <h2>Chunk Detail</h2>
      <p><strong>chunkId:</strong> ${escapeHtml(data.chunkId)}</p>
      <p><strong>docId:</strong> ${escapeHtml(data.docId)}</p>
      <p><strong>docName:</strong> ${escapeHtml(data.docName || "")}</p>
      <p><strong>chunkIndex:</strong> ${escapeHtml(String(data.chunkIndex || ""))}</p>
      <pre>${escapeHtml(data.content || "")}</pre>
      <pre>${escapeHtml(data.metadataJson || "")}</pre>
    `;
  } catch (error) {
    ragError.textContent = error.message;
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
