document.addEventListener("DOMContentLoaded", () => {
  const statTotal = document.getElementById("statTotal");
  const statActive = document.getElementById("statActive");
  const statRevoked = document.getElementById("statRevoked");
  const tableContainer = document.getElementById("tableContainer");
  const emptyState = document.getElementById("emptyState");
  const linksTableBody = document.getElementById("linksTableBody");
  const refreshAllBtn = document.getElementById("refreshAllBtn");
  const dashboardAlert = document.getElementById("dashboardAlert");
  const dashboardAlertText = document.getElementById("dashboardAlertText");

  // Manual search elements
  const tokenLookupForm = document.getElementById("tokenLookupForm");
  const revokeTokenInput = document.getElementById("revokeTokenInput");
  const lookupBtn = document.getElementById("lookupBtn");
  const manualAlert = document.getElementById("manualAlert");
  const manualAlertText = document.getElementById("manualAlertText");
  const manualResultBox = document.getElementById("manualResultBox");
  const manualFilename = document.getElementById("manualFilename");
  const manualBadge = document.getElementById("manualBadge");
  const manualSize = document.getElementById("manualSize");
  const manualExpires = document.getElementById("manualExpires");
  const manualDownloads = document.getElementById("manualDownloads");
  const manualRevokeBtn = document.getElementById("manualRevokeBtn");

  // Modal elements
  const confirmModal = document.getElementById("confirmModal");
  const modalCancelBtn = document.getElementById("modalCancelBtn");
  const modalConfirmBtn = document.getElementById("modalConfirmBtn");

  let pendingRevokeToken = null;
  let pendingFileId = null;
  let manualCurrentToken = null;

  // Initial load
  loadLocalDashboard();

  // Auto-check if URL has ?revokeToken=...
  const urlParams = new URLSearchParams(window.location.search);
  const tokenFromUrl = urlParams.get("revokeToken");
  if (tokenFromUrl) {
    revokeTokenInput.value = tokenFromUrl;
    fetchManualStatus(tokenFromUrl);
  }

  // Refresh all button
  refreshAllBtn.addEventListener("click", () => {
    loadLocalDashboard(true);
  });

  // Modal event listeners
  modalCancelBtn.addEventListener("click", () => {
    confirmModal.style.display = "none";
    pendingRevokeToken = null;
    pendingFileId = null;
  });

  modalConfirmBtn.addEventListener("click", async () => {
    if (pendingRevokeToken) {
      await executeRevocation(pendingRevokeToken, pendingFileId);
    }
    confirmModal.style.display = "none";
  });

  // Dashboard Loader
  async function loadLocalDashboard(forceRefresh = false) {
    const items = LocalShareStore.getAll();

    statTotal.textContent = items.length;
    if (items.length === 0) {
      tableContainer.style.display = "none";
      emptyState.style.display = "block";
      statActive.textContent = "0";
      statRevoked.textContent = "0";
      return;
    }

    tableContainer.style.display = "block";
    emptyState.style.display = "none";
    linksTableBody.innerHTML = "";

    let activeCount = 0;
    let revokedCount = 0;

    for (const item of items) {
      // If forceRefresh, fetch live status from server
      let liveStatus = item;
      if (forceRefresh && !item.revoked) {
        try {
          const res = await fetch(`/api/files/status?revokeToken=${encodeURIComponent(item.revokeToken)}`);
          if (res.ok) {
            const data = await res.json();
            liveStatus = {
              ...item,
              downloadCount: data.downloadCount,
              revoked: data.revoked,
              expired: data.expired,
              downloadLimitReached: data.downloadLimitReached
            };
            LocalShareStore.updateStatus(item.fileId, liveStatus);
          }
        } catch (e) {
          // Keep cached status if network fails
        }
      }

      // Check client-side expiry
      const isExpired = liveStatus.expired || (new Date(liveStatus.expiresAt).getTime() < Date.now());
      const isRevoked = liveStatus.revoked;
      const isLimitReached = liveStatus.downloadLimitReached;

      if (isRevoked) revokedCount++;
      else if (!isExpired && !isLimitReached) activeCount++;

      const tr = document.createElement("tr");

      // File icon + name
      const icon = CryptoUtils.getFileIcon(liveStatus.originalFilename);
      const filenameTd = `
        <div style="display: flex; align-items: center; gap: 0.6rem;">
          <span style="font-size: 1.4rem;">${icon}</span>
          <div style="font-weight: 600; max-width: 200px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;" title="${escapeHtml(liveStatus.originalFilename)}">
            ${escapeHtml(liveStatus.originalFilename)}
          </div>
        </div>
      `;

      // Size
      const sizeTd = CryptoUtils.formatBytes(liveStatus.ciphertextSizeBytes);

      // Expiry
      const expiryTd = new Date(liveStatus.expiresAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', month: 'short', day: 'numeric' });

      // Downloads
      const downloadsTd = liveStatus.maxDownloads
        ? `${liveStatus.downloadCount || 0} / ${liveStatus.maxDownloads}`
        : `${liveStatus.downloadCount || 0} / ∞`;

      // Badge
      let badgeHtml = "";
      if (isRevoked) {
        badgeHtml = `<span class="badge badge-revoked">Revoked</span>`;
      } else if (isExpired) {
        badgeHtml = `<span class="badge badge-expired">Expired</span>`;
      } else if (isLimitReached) {
        badgeHtml = `<span class="badge badge-limit">Limit Reached</span>`;
      } else {
        badgeHtml = `<span class="badge badge-active">Active</span>`;
      }

      // Actions
      const actionsTd = `
        <div class="table-actions">
          <button type="button" class="btn btn-outline btn-icon copy-share-btn" data-url="${escapeHtml(liveStatus.downloadUrl)}" title="Copy Share Link">📋</button>
          <a href="${escapeHtml(liveStatus.downloadUrl)}" target="_blank" class="btn btn-outline btn-icon" title="Open Share Page">👁️</a>
          ${!isRevoked ? `<button type="button" class="btn btn-danger btn-icon revoke-btn" data-token="${escapeHtml(liveStatus.revokeToken)}" data-id="${escapeHtml(liveStatus.fileId)}" title="Revoke Link">🗑️</button>` : ''}
          <button type="button" class="btn btn-outline btn-icon remove-row-btn" data-id="${escapeHtml(liveStatus.fileId)}" title="Remove from list">✕</button>
        </div>
      `;

      tr.innerHTML = `
        <td>${filenameTd}</td>
        <td>${sizeTd}</td>
        <td>${expiryTd}</td>
        <td>${downloadsTd}</td>
        <td>${badgeHtml}</td>
        <td>${actionsTd}</td>
      `;

      linksTableBody.appendChild(tr);
    }

    statActive.textContent = activeCount;
    statRevoked.textContent = revokedCount;

    // Attach row events
    document.querySelectorAll(".copy-share-btn").forEach(btn => {
      btn.addEventListener("click", async () => {
        const url = btn.getAttribute("data-url");
        try {
          await navigator.clipboard.writeText(url);
          showDashboardAlert("Share link copied to clipboard!");
        } catch (e) {
          prompt("Copy sharing link:", url);
        }
      });
    });

    document.querySelectorAll(".revoke-btn").forEach(btn => {
      btn.addEventListener("click", () => {
        pendingRevokeToken = btn.getAttribute("data-token");
        pendingFileId = btn.getAttribute("data-id");
        confirmModal.style.display = "flex";
      });
    });

    document.querySelectorAll(".remove-row-btn").forEach(btn => {
      btn.addEventListener("click", () => {
        const fileId = btn.getAttribute("data-id");
        LocalShareStore.remove(fileId);
        loadLocalDashboard();
      });
    });
  }

  // Execute Revocation
  async function executeRevocation(revokeToken, fileId) {
    try {
      const response = await fetch("/api/files/revoke", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ revokeToken: revokeToken })
      });

      if (!response.ok) {
        const err = await response.json().catch(() => ({}));
        throw new Error(err.message || "Failed to revoke file");
      }

      if (fileId) {
        LocalShareStore.updateStatus(fileId, { revoked: true });
      }

      showDashboardAlert("File link revoked successfully. Encrypted file purged from server disk.");
      loadLocalDashboard();

      // If manual search result is showing the same token, update it
      if (manualCurrentToken === revokeToken) {
        manualBadge.className = "badge badge-revoked";
        manualBadge.textContent = "Revoked";
        manualRevokeBtn.disabled = true;
        manualRevokeBtn.textContent = "Already Revoked";
      }

    } catch (e) {
      alert("Error revoking file: " + e.message);
    }
  }

  // Manual Token Search
  tokenLookupForm.addEventListener("submit", (e) => {
    e.preventDefault();
    const token = revokeTokenInput.value.trim();
    if (token) fetchManualStatus(token);
  });

  async function fetchManualStatus(token) {
    manualAlert.style.display = "none";
    manualResultBox.style.display = "none";
    lookupBtn.disabled = true;
    lookupBtn.textContent = "Checking...";

    try {
      const res = await fetch(`/api/files/status?revokeToken=${encodeURIComponent(token)}`);
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.message || "No file record found matching this revocation token.");
      }

      const data = await res.json();
      manualCurrentToken = token;

      manualFilename.textContent = data.originalFilename;
      manualSize.textContent = CryptoUtils.formatBytes(data.ciphertextSizeBytes);
      manualExpires.textContent = new Date(data.expiresAt).toLocaleString();
      manualDownloads.textContent = data.maxDownloads ? `${data.downloadCount} of ${data.maxDownloads}` : `${data.downloadCount} (Unlimited)`;

      if (data.revoked) {
        manualBadge.className = "badge badge-revoked";
        manualBadge.textContent = "Revoked";
        manualRevokeBtn.disabled = true;
        manualRevokeBtn.textContent = "Already Revoked";
      } else if (data.expired) {
        manualBadge.className = "badge badge-expired";
        manualBadge.textContent = "Expired";
        manualRevokeBtn.disabled = false;
        manualRevokeBtn.textContent = "Revoke & Purge Disk";
      } else if (data.downloadLimitReached) {
        manualBadge.className = "badge badge-limit";
        manualBadge.textContent = "Limit Reached";
        manualRevokeBtn.disabled = false;
        manualRevokeBtn.textContent = "Revoke & Purge Disk";
      } else {
        manualBadge.className = "badge badge-active";
        manualBadge.textContent = "Active";
        manualRevokeBtn.disabled = false;
        manualRevokeBtn.textContent = "🗑️ Revoke Link Now";
      }

      manualResultBox.style.display = "block";

    } catch (err) {
      manualAlertText.textContent = err.message || "Could not retrieve status for this token.";
      manualAlert.style.display = "flex";
    } finally {
      lookupBtn.disabled = false;
      lookupBtn.textContent = "Check Status";
    }
  }

  manualRevokeBtn.addEventListener("click", () => {
    if (manualCurrentToken) {
      pendingRevokeToken = manualCurrentToken;
      pendingFileId = null;
      confirmModal.style.display = "flex";
    }
  });

  function showDashboardAlert(msg) {
    dashboardAlertText.textContent = msg;
    dashboardAlert.style.display = "flex";
    setTimeout(() => {
      dashboardAlert.style.display = "none";
    }, 4000);
  }

  function escapeHtml(str) {
    if (!str) return "";
    return str.replace(/[&<>"']/g, function (m) {
      return ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
      })[m];
    });
  }
});
