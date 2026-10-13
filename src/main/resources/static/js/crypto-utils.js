/**
 * Client-Side Cryptographic Utilities & Storage Helpers
 * 
 * Algorithm: AES-256-GCM (Authenticated Encryption with Associated Data)
 * Key Size: 256 bits
 * IV Size: 96 bits (12 bytes standard for AES-GCM)
 * 
 * Security Guarantee:
 * - Encryption and decryption occur strictly inside the client's browser.
 * - The decryption key is exported to the URL fragment (#key=...) and NEVER
 *   transmitted over HTTP to the backend server.
 */

const CryptoUtils = {
  IV_BYTE_LENGTH: 12,

  /**
   * Generates a new 256-bit AES-GCM CryptoKey using browser's cryptographically secure generator.
   */
  async generateAesKey() {
    return await window.crypto.subtle.generateKey(
      {
        name: "AES-GCM",
        length: 256
      },
      true, // extractable for URL fragment packaging
      ["encrypt", "decrypt"]
    );
  },

  /**
   * Exports a CryptoKey to a URL-safe Base64 string for URL fragment storage.
   */
  async exportKeyToBase64Url(key) {
    const rawBuffer = await window.crypto.subtle.exportKey("raw", key);
    return CryptoUtils.bufferToBase64Url(rawBuffer);
  },

  /**
   * Imports a Base64URL string back into an AES-GCM CryptoKey.
   */
  async importKeyFromBase64Url(base64UrlStr) {
    const rawBuffer = CryptoUtils.base64UrlToBuffer(base64UrlStr);
    return await window.crypto.subtle.importKey(
      "raw",
      rawBuffer,
      {
        name: "AES-GCM",
        length: 256
      },
      false, // non-extractable after import
      ["decrypt"]
    );
  },

  /**
   * Encrypts file data using AES-256-GCM.
   * Format of output: [12-byte IV] + [Ciphertext + 16-byte Auth Tag]
   */
  async encryptFile(file, key) {
    const plaintextBuffer = await file.arrayBuffer();

    // Generate fresh, cryptographically secure 96-bit IV
    const iv = window.crypto.getRandomValues(new Uint8Array(CryptoUtils.IV_BYTE_LENGTH));

    const ciphertextBuffer = await window.crypto.subtle.encrypt(
      {
        name: "AES-GCM",
        iv: iv
      },
      key,
      plaintextBuffer
    );

    // Pack IV and ciphertext into a single byte payload
    const combined = new Uint8Array(iv.length + ciphertextBuffer.byteLength);
    combined.set(iv, 0);
    combined.set(new Uint8Array(ciphertextBuffer), iv.length);

    return new Blob([combined], { type: "application/octet-stream" });
  },

  /**
   * Decrypts an encrypted payload using AES-256-GCM.
   * Expects input format: [12-byte IV] + [Ciphertext + 16-byte Auth Tag]
   */
  async decryptFile(encryptedArrayBuffer, key) {
    if (encryptedArrayBuffer.byteLength < CryptoUtils.IV_BYTE_LENGTH + 16) {
      throw new Error("Ciphertext payload is too small to contain valid IV and authentication tag.");
    }

    const dataView = new Uint8Array(encryptedArrayBuffer);
    const iv = dataView.slice(0, CryptoUtils.IV_BYTE_LENGTH);
    const ciphertext = dataView.slice(CryptoUtils.IV_BYTE_LENGTH);

    try {
      const decryptedBuffer = await window.crypto.subtle.decrypt(
        {
          name: "AES-GCM",
          iv: iv
        },
        key,
        ciphertext
      );
      return decryptedBuffer;
    } catch (err) {
      // In AES-GCM, any alteration to ciphertext or wrong key causes authentication failure
      throw new Error("Decryption failed. The encryption key is incorrect or the ciphertext has been modified/tampered with.");
    }
  },

  /**
   * Encodes an ArrayBuffer into URL-safe Base64 without padding.
   */
  bufferToBase64Url(buffer) {
    const bytes = new Uint8Array(buffer);
    let binary = "";
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    const base64 = btoa(binary);
    return base64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  },

  /**
   * Decodes a URL-safe Base64 string back into an ArrayBuffer.
   */
  base64UrlToBuffer(base64UrlStr) {
    let base64 = base64UrlStr.replace(/-/g, "+").replace(/_/g, "/");
    while (base64.length % 4) {
      base64 += "=";
    }
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes.buffer;
  },

  /**
   * Fetches public base URL configuration from the backend, falling back to window.location.origin.
   */
  async getPublicBaseUrl() {
    try {
      const res = await fetch("/api/files/config");
      if (res.ok) {
        const config = await res.json();
        if (config.publicBaseUrl && config.publicBaseUrl.trim().length > 0) {
          // Remove trailing slash if present
          return config.publicBaseUrl.trim().replace(/\/+$/, "");
        }
      }
    } catch (e) {
      // Ignore network errors and fallback to origin
    }
    return window.location.origin;
  },

  /**
   * Formats byte size into human-readable string.
   */
  formatBytes(bytes) {
    if (!bytes || bytes === 0) return "0 Bytes";
    const k = 1024;
    const sizes = ["Bytes", "KB", "MB", "GB", "TB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
  },

  /**
   * Detects suitable emoji icon based on filename extension.
   */
  getFileIcon(filename) {
    if (!filename) return "📄";
    const ext = filename.split(".").pop().toLowerCase();
    switch (ext) {
      case "pdf": return "📕";
      case "jpg": case "jpeg": case "png": case "gif": case "webp": case "svg": return "🖼️";
      case "zip": case "tar": case "gz": case "7z": case "rar": return "📦";
      case "mp3": case "wav": case "flac": case "ogg": return "🎵";
      case "mp4": case "mkv": case "mov": case "avi": case "webm": return "🎬";
      case "doc": case "docx": case "odt": case "rtf": return "📝";
      case "xls": case "xlsx": case "csv": return "📊";
      case "ppt": case "pptx": return "📽️";
      case "js": case "java": case "py": case "html": case "css": case "json": case "xml": return "💻";
      default: return "📄";
    }
  }
};

/**
 * Local Storage Helper to allow uploader to manage their links locally
 * without requiring user accounts or leaking keys to the server.
 */
const LocalShareStore = {
  STORAGE_KEY: "cryptashare_uploads",

  getAll() {
    try {
      const data = localStorage.getItem(this.STORAGE_KEY);
      return data ? JSON.parse(data) : [];
    } catch (e) {
      return [];
    }
  },

  save(record) {
    try {
      const items = this.getAll();
      // Prepend so newest is first
      items.unshift(record);
      // Keep up to 50 records
      if (items.length > 50) items.pop();
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify(items));
    } catch (e) {
      console.warn("Could not save to localStorage", e);
    }
  },

  updateStatus(fileId, statusUpdates) {
    try {
      const items = this.getAll();
      const updated = items.map(item => {
        if (item.fileId === fileId) {
          return { ...item, ...statusUpdates };
        }
        return item;
      });
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify(updated));
    } catch (e) {
      console.warn("Could not update localStorage", e);
    }
  },

  remove(fileId) {
    try {
      const items = this.getAll().filter(item => item.fileId !== fileId);
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify(items));
    } catch (e) {
      console.warn("Could not remove from localStorage", e);
    }
  }
};
