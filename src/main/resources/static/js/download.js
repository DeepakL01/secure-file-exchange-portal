document.addEventListener("DOMContentLoaded", async () => {
  const loadingBox = document.getElementById("loadingBox");
  const fileInfoBox = document.getElementById("fileInfoBox");
  const statusAlert = document.getElementById("statusAlert");
  const statusAlertText = document.getElementById("statusAlertText");
  const successBox = document.getElementById("successBox");
  const metaFilename = document.getElementById("metaFilename");
  const metaFileIcon = document.getElementById("metaFileIcon");
  const metaStatusBadge = document.getElementById("metaStatusBadge");
  const metaCipherSize = document.getElementById("metaCipherSize");
  const metaExpiresAt = document.getElementById("metaExpiresAt");
  const metaDownloadLimit = document.getElementById("metaDownloadLimit");
  const metaKeyStatus = document.getElementById("metaKeyStatus");
  const decryptDownloadBtn = document.getElementById("decryptDownloadBtn");
  const downloadBtnIcon = document.getElementById("downloadBtnIcon");
  const downloadBtnText = document.getElementById("downloadBtnText");

  // 1. Extract token from URL query params
  const urlParams = new URLSearchParams(window.location.search);
  const token = urlParams.get("token");

  // 2. Extract decryption key from URL fragment (#key=<base64url>)
  let keyBase64Url = null;
  if (window.location.hash && window.location.hash.length > 1) {
    const hashContent = window.location.hash.substring(1);
    const hashParams = new URLSearchParams(hashContent);
    keyBase64Url = hashParams.get("key");
  }

  let fileMetadata = null;

  if (!token) {
    showError("Missing access token in URL query parameter. Cannot locate file.");
    loadingBox.style.display = "none";
    return;
  }

  // 3. Fetch file metadata
  try {
    const metaResponse = await fetch(`/api/files/meta?token=${encodeURIComponent(token)}`);

    if (!metaResponse.ok) {
      const errJson = await metaResponse.json().catch(() => ({}));
      if (metaResponse.status === 404) {
        throw new Error("Invalid or unknown download link. The file does not exist.");
      } else if (metaResponse.status === 410) {
        throw new Error(errJson.message || "This download link is no longer accessible (expired, revoked, or download limit reached).");
      } else {
        throw new Error(errJson.message || `Server returned error (${metaResponse.status})`);
      }
    }

    fileMetadata = await metaResponse.json();

    // Populate UI details
    metaFilename.textContent = fileMetadata.originalFilename;
    metaFileIcon.textContent = CryptoUtils.getFileIcon(fileMetadata.originalFilename);
    metaCipherSize.textContent = CryptoUtils.formatBytes(fileMetadata.ciphertextSizeBytes);
    metaExpiresAt.textContent = new Date(fileMetadata.expiresAt).toLocaleString();

    if (fileMetadata.maxDownloads) {
      metaDownloadLimit.textContent = `${fileMetadata.downloadCount} of ${fileMetadata.maxDownloads} used`;
    } else {
      metaDownloadLimit.textContent = `${fileMetadata.downloadCount} (Unlimited)`;
    }

    if (fileMetadata.revoked) {
      metaStatusBadge.className = "badge badge-revoked";
      metaStatusBadge.textContent = "Revoked";
      throw new Error("This link was revoked by the uploader and can no longer be downloaded.");
    } else if (fileMetadata.expired) {
      metaStatusBadge.className = "badge badge-expired";
      metaStatusBadge.textContent = "Expired";
      throw new Error("This link has expired and is no longer accessible.");
    } else if (fileMetadata.downloadLimitReached) {
      metaStatusBadge.className = "badge badge-limit";
      metaStatusBadge.textContent = "Limit Reached";
      throw new Error("This file reached its maximum allowed downloads and is no longer accessible.");
    } else {
      metaStatusBadge.className = "badge badge-active";
      metaStatusBadge.textContent = "Active";
    }

    // Check presence of decryption key in URL fragment
    if (!keyBase64Url) {
      metaKeyStatus.textContent = "Missing Key";
      metaKeyStatus.style.color = "var(--danger)";
      decryptDownloadBtn.disabled = true;
      showError("The secret decryption key is missing from the URL fragment (#key=...). Without this key, the file cannot be decrypted.");
    } else {
      metaKeyStatus.textContent = "Present in URL (#)";
      metaKeyStatus.style.color = "var(--success)";
    }

    loadingBox.style.display = "none";
    fileInfoBox.style.display = "block";

  } catch (err) {
    loadingBox.style.display = "none";
    showError(err.message || "Could not retrieve file information.");
    return;
  }

  // 4. Decrypt and Download Action
  decryptDownloadBtn.addEventListener("click", async () => {
    if (!keyBase64Url || !token) {
      showError("Decryption key or token is missing.");
      return;
    }

    hideError();
    successBox.style.display = "none";
    setLoading(true, "Downloading encrypted ciphertext from server...");

    try {
      // Step A: Download ciphertext from server
      const response = await fetch(`/api/files/download?token=${encodeURIComponent(token)}`);
      if (!response.ok) {
        const errJson = await response.json().catch(() => ({}));
        throw new Error(errJson.message || `Download failed with HTTP ${response.status}`);
      }

      const encryptedBuffer = await response.arrayBuffer();

      setLoading(true, "Decrypting and verifying AES-256-GCM integrity...");

      // Step B: Import secret key
      const cryptoKey = await CryptoUtils.importKeyFromBase64Url(keyBase64Url);

      // Step C: Decrypt in-browser (authenticates GCM tag automatically)
      const decryptedPlaintextBuffer = await CryptoUtils.decryptFile(encryptedBuffer, cryptoKey);

      setLoading(true, "Saving file to your device...");

      // Step D: Trigger file download in browser
      const mimeType = fileMetadata.contentType || "application/octet-stream";
      const blob = new Blob([decryptedPlaintextBuffer], { type: mimeType });
      const downloadUrl = URL.createObjectURL(blob);

      const downloadAnchor = document.createElement("a");
      downloadAnchor.href = downloadUrl;
      downloadAnchor.download = fileMetadata.originalFilename || "decrypted_file.bin";
      document.body.appendChild(downloadAnchor);
      downloadAnchor.click();
      document.body.removeChild(downloadAnchor);

      setTimeout(() => URL.revokeObjectURL(downloadUrl), 8000);

      successBox.style.display = "flex";
      successBox.scrollIntoView({ behavior: "smooth" });

      // Increment local UI download count
      if (fileMetadata.downloadCount !== undefined) {
        fileMetadata.downloadCount++;
        if (fileMetadata.maxDownloads) {
          metaDownloadLimit.textContent = `${fileMetadata.downloadCount} of ${fileMetadata.maxDownloads} used`;
          if (fileMetadata.downloadCount >= fileMetadata.maxDownloads) {
            metaStatusBadge.className = "badge badge-limit";
            metaStatusBadge.textContent = "Limit Reached";
            decryptDownloadBtn.disabled = true;
          }
        }
      }

    } catch (err) {
      showError(err.message || "Decryption failed. Please verify the URL key and integrity.");
    } finally {
      setLoading(false);
    }
  });

  function setLoading(isLoading, text = "") {
    decryptDownloadBtn.disabled = isLoading;
    if (isLoading) {
      downloadBtnIcon.innerHTML = `<span class="spinner"></span>`;
      downloadBtnText.textContent = text;
    } else {
      downloadBtnIcon.textContent = "⬇️";
      downloadBtnText.textContent = "Decrypt & Download File";
    }
  }

  function showError(msg) {
    statusAlertText.textContent = msg;
    statusAlert.style.display = "flex";
  }

  function hideError() {
    statusAlert.style.display = "none";
  }
});
