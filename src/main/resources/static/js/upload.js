document.addEventListener("DOMContentLoaded", () => {
  const uploadForm = document.getElementById("uploadForm");
  const fileInput = document.getElementById("fileInput");
  const dropZone = document.getElementById("dropZone");
  const fileSelectedBox = document.getElementById("fileSelectedBox");
  const selectedFileName = document.getElementById("selectedFileName");
  const selectedFileSize = document.getElementById("selectedFileSize");
  const fileTypeIcon = document.getElementById("fileTypeIcon");
  const clearFileBtn = document.getElementById("clearFileBtn");
  const expirySelect = document.getElementById("expirySelect");
  const downloadLimitSelect = document.getElementById("downloadLimitSelect");
  const submitBtn = document.getElementById("submitBtn");
  const btnIcon = document.getElementById("btnIcon");
  const btnText = document.getElementById("btnText");
  const errorAlert = document.getElementById("errorAlert");
  const errorMessage = document.getElementById("errorMessage");
  const resultBox = document.getElementById("resultBox");
  const downloadUrlInput = document.getElementById("downloadUrlInput");
  const revokeUrlInput = document.getElementById("revokeUrlInput");
  const copyDownloadBtn = document.getElementById("copyDownloadBtn");
  const copyRevokeBtn = document.getElementById("copyRevokeBtn");
  const previewShareBtn = document.getElementById("previewShareBtn");
  const expiresAtSpan = document.getElementById("expiresAtSpan");
  const limitSpan = document.getElementById("limitSpan");
  const cipherSizeSpan = document.getElementById("cipherSizeSpan");
  
  const cryptoProgressBox = document.getElementById("cryptoProgressBox");
  const progressFill = document.getElementById("progressFill");
  const progressPercentage = document.getElementById("progressPercentage");
  const progressStatusText = document.getElementById("progressStatusText");

  let currentFile = null;

  // Dropzone interactions
  dropZone.addEventListener("click", () => fileInput.click());
  dropZone.addEventListener("keydown", (e) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      fileInput.click();
    }
  });

  ["dragenter", "dragover"].forEach(eventName => {
    dropZone.addEventListener(eventName, (e) => {
      e.preventDefault();
      e.stopPropagation();
      dropZone.classList.add("dragover");
    });
  });

  ["dragleave", "drop"].forEach(eventName => {
    dropZone.addEventListener(eventName, (e) => {
      e.preventDefault();
      e.stopPropagation();
      dropZone.classList.remove("dragover");
    });
  });

  dropZone.addEventListener("drop", (e) => {
    const files = e.dataTransfer.files;
    if (files.length > 0) {
      handleFileSelected(files[0]);
    }
  });

  fileInput.addEventListener("change", () => {
    if (fileInput.files.length > 0) {
      handleFileSelected(fileInput.files[0]);
    }
  });

  clearFileBtn.addEventListener("click", () => {
    currentFile = null;
    fileInput.value = "";
    fileSelectedBox.style.display = "none";
    dropZone.style.display = "block";
    hideError();
  });

  function handleFileSelected(file) {
    const maxSizeBytes = 50 * 1024 * 1024; // 50MB
    if (file.size > maxSizeBytes) {
      showError("File size exceeds 50 MB limit. Please select a smaller file.");
      return;
    }
    currentFile = file;
    selectedFileName.textContent = file.name;
    selectedFileSize.textContent = `${CryptoUtils.formatBytes(file.size)} • ${file.type || "binary payload"}`;
    fileTypeIcon.textContent = CryptoUtils.getFileIcon(file.name);
    dropZone.style.display = "none";
    fileSelectedBox.style.display = "flex";
    hideError();
  }

  function updateProgress(percent, statusMsg) {
    cryptoProgressBox.style.display = "block";
    progressFill.style.width = `${percent}%`;
    progressPercentage.textContent = `${percent}%`;
    progressStatusText.textContent = statusMsg;
  }

  // Submission handler
  uploadForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    if (!currentFile) {
      showError("Please select a file to encrypt and upload.");
      return;
    }

    hideError();
    resultBox.style.display = "none";
    setLoading(true, "Generating AES-256-GCM encryption key...");
    updateProgress(20, "Generating cryptographically secure 256-bit AES-GCM key in browser...");

    try {
      // 1. Generate client-side AES-256-GCM key
      const aesKey = await CryptoUtils.generateAesKey();

      // 2. Export key to URL-safe Base64 string for URL fragment
      const keyBase64Url = await CryptoUtils.exportKeyToBase64Url(aesKey);

      // 3. Encrypt file contents locally in browser
      updateProgress(45, "Encrypting file data with 96-bit random IV (AES-256-GCM)...");
      const encryptedBlob = await CryptoUtils.encryptFile(currentFile, aesKey);

      // 4. Transmit ciphertext to server
      updateProgress(75, "Transmitting encrypted payload to server...");
      setLoading(true, "Uploading encrypted payload...");

      const formData = new FormData();
      formData.append("file", encryptedBlob, currentFile.name + ".enc");
      formData.append("originalFilename", currentFile.name);
      formData.append("expiryMinutes", expirySelect.value);

      const maxDownloadsVal = parseInt(downloadLimitSelect.value, 10);
      if (maxDownloadsVal > 0) {
        formData.append("maxDownloads", maxDownloadsVal);
      }

      const response = await fetch("/api/files/upload", {
        method: "POST",
        body: formData
      });

      if (!response.ok) {
        const errorJson = await response.json().catch(() => ({}));
        throw new Error(errorJson.message || `Upload failed with HTTP ${response.status}`);
      }

      const data = await response.json();
      updateProgress(100, "Encryption and link generation complete!");

      // 5. Resolve public base URL (supports LAN IP / reverse proxy)
      const publicBaseUrl = await CryptoUtils.getPublicBaseUrl();

      // 6. Build the zero-knowledge download link:
      // Decryption key is strictly placed after #key=...
      const downloadUrl = `${publicBaseUrl}/download.html?token=${encodeURIComponent(data.accessToken)}#key=${keyBase64Url}`;
      const revokeUrl = `${publicBaseUrl}/manage.html?revokeToken=${encodeURIComponent(data.revokeToken)}`;

      // 7. Render result
      downloadUrlInput.value = downloadUrl;
      revokeUrlInput.value = revokeUrl;
      previewShareBtn.href = downloadUrl;
      expiresAtSpan.textContent = new Date(data.expiresAt).toLocaleString();
      limitSpan.textContent = data.maxDownloads ? `${data.maxDownloads} download(s)` : "Unlimited";
      cipherSizeSpan.textContent = CryptoUtils.formatBytes(data.ciphertextSizeBytes);

      // 8. Save to uploader's local dashboard store
      LocalShareStore.save({
        fileId: data.fileId,
        originalFilename: data.originalFilename,
        ciphertextSizeBytes: data.ciphertextSizeBytes,
        downloadUrl: downloadUrl,
        revokeToken: data.revokeToken,
        createdAt: data.createdAt,
        expiresAt: data.expiresAt,
        maxDownloads: data.maxDownloads,
        revoked: false
      });

      resultBox.style.display = "block";
      resultBox.scrollIntoView({ behavior: "smooth" });

    } catch (err) {
      showError(err.message || "An error occurred during encryption and upload.");
      cryptoProgressBox.style.display = "none";
    } finally {
      setLoading(false);
    }
  });

  // Clipboard copy buttons
  setupCopyButton(copyDownloadBtn, downloadUrlInput);
  setupCopyButton(copyRevokeBtn, revokeUrlInput);

  function setupCopyButton(button, inputElement) {
    button.addEventListener("click", async () => {
      try {
        await navigator.clipboard.writeText(inputElement.value);
        const originalText = button.textContent;
        button.textContent = "✅ Copied!";
        button.style.borderColor = "var(--success)";
        button.style.color = "var(--success)";
        setTimeout(() => {
          button.textContent = originalText;
          button.style.borderColor = "";
          button.style.color = "";
        }, 2000);
      } catch (err) {
        inputElement.select();
        document.execCommand("copy");
      }
    });
  }

  function setLoading(isLoading, text = "") {
    submitBtn.disabled = isLoading;
    if (isLoading) {
      btnIcon.innerHTML = `<span class="spinner"></span>`;
      btnText.textContent = text;
    } else {
      btnIcon.textContent = "🔒";
      btnText.textContent = "Encrypt & Generate Secret Link";
    }
  }

  function showError(msg) {
    errorMessage.textContent = msg;
    errorAlert.style.display = "flex";
  }

  function hideError() {
    errorAlert.style.display = "none";
  }
});
