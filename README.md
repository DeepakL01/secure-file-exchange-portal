# CryptaShare — Secure File Exchange Portal

A zero-knowledge, end-to-end encrypted file sharing website built with **Java 21**, **Spring Boot 3.3**, **MySQL**, and **vanilla HTML5/CSS3/JavaScript** using the native browser **Web Crypto API**.

---

## 1. System Architecture & Cryptographic Design

Traditional cloud file hosts receive files in plaintext, exposing sensitive data to server compromise, insider threats, and subpoena. **CryptaShare** solves this through client-side zero-knowledge architecture:

```
[ Uploader Browser ]
   │
   ├─ 1. Generates 256-bit AES-GCM Key (CryptoKey) in browser RAM
   ├─ 2. Generates cryptographically secure 96-bit random IV
   ├─ 3. Encrypts plaintext file locally into ciphertext with GCM Auth Tag
   │
   ├─ 4. Transmits ONLY Ciphertext to Spring Boot Backend ────────┐
   │                                                             ▼
   └─ 5. Builds Share Link:                              [ Spring Boot Server ]
         http://host/download.html?token=XXX#key=YYY             │
                                      ▲        ▲                 ├─ Generates 256-bit SecureRandom tokens
                                      │        │                 ├─ Hashes tokens with SHA-256
                   Sent to server ────┘        │                 ├─ Saves metadata & hashed tokens in MySQL
                                               │                 ├─ Saves ciphertext to ./storage/uploads/<uuid>.enc
        NEVER sent to server (URL Fragment) ───┘                 └─ ZERO knowledge of encryption key or plaintext
                                                                         ▲
[ Recipient Browser ]                                                    │
   │                                                                     │
   ├─ 1. Reads access token from query param (?token=XXX) ───────────────┤
   ├─ 2. Extracts secret key from URL fragment (#key=YYY)                │
   ├─ 3. Requests encrypted ciphertext from backend ─────────────────────┘
   ├─ 4. Decrypts ciphertext locally using Web Crypto API in browser
   └─ 5. Authenticates integrity tag and triggers native download of original file
```

### Core Cryptographic Decisions
1. **Client-Side AES-256-GCM:** 256-bit authenticated encryption with a unique 96-bit (12-byte) IV per upload. Authenticates integrity; any file corruption or altered key fails immediately with an integrity error.
2. **URL Fragment Key Isolation (`#key=...`):** Per RFC 3986 and browser specifications, HTTP user agents never transmit URL fragments in request lines or headers. The decryption key never reaches network logs or server memory.
3. **Server-Side Token Hashing:** Access and revocation tokens are generated with Java `SecureRandom` (256-bit entropy). Only their **SHA-256 hashes** are stored in MySQL. Database dumps do not expose working download tokens.
4. **Storage Isolation & Path Traversal Mitigations:** Ciphertext is saved in `./storage/uploads/<uuid>.enc` outside public web roots. User-provided filenames are never used on the filesystem.
5. **Configurable Download Limits & Expiry:** Enforces time-based expiration (15m to 7 days) and optional download counters (e.g. 1-download self-destruct) atomically on the server.
6. **Immediate Disk Purge on Revocation:** Revoking a link flags the database record and immediately deletes the ciphertext file from disk.

---

## 2. Features

- **Modern Cloud Storage UI:** Clean, responsive interface inspired by modern privacy products (Proton Drive, Firefox Send, Bitwarden Send).
- **Drag-and-Drop Uploads:** File picker with real-time file type detection (PDF, images, zip, audio, video, documents, code).
- **Multi-Device & LAN Support:** Spring Boot binds to `0.0.0.0`, allowing phones, laptops, and tablets on the same Wi-Fi network to open and test links directly.
- **Configurable Public Base URL:** Supports deployment behind reverse proxies, local tunnels, or custom domains.
- **My Shared Links Dashboard:** Local browser storage tracks uploads created in your session, showing live active/expired/revoked states, download counts, and one-click revocation.
- **Revocation & Self-Destruct:** Single-use download limits and instant deletion.
- **Zero Third-Party CDN Dependencies:** Pure vanilla JavaScript and CSS served directly by Spring Boot.

---

## 3. Technology Stack

- **Backend:** Java 21 LTS, Spring Boot 3.3.4, Spring Data JPA, Jakarta Validation
- **Database:** MySQL 8.0+ (with in-memory H2 database for isolated automated testing)
- **Frontend:** Semantic HTML5, CSS3, Vanilla JavaScript (ECMAScript 2022)
- **Cryptography:** Native Browser [Web Crypto API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Crypto_API) (`SubtleCrypto` AES-256-GCM) + Java `MessageDigest` (SHA-256) / `SecureRandom`
- **Build & Packaging:** Apache Maven 3.9+ / Maven Wrapper (`mvnw.cmd` / `mvnw`)

---

## 4. Quick Start (Windows Local Development)

### Prerequisites
- **Java 21 JDK** (Installed at `C:\Program Files\Java\jdk-21.0.12.1`)
- **MySQL Server 8.0+** (Running locally on port 3306)

### Step 1: Create Database
In PowerShell or MySQL Command Line Client:
```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p -e "CREATE DATABASE IF NOT EXISTS secure_file_share CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

### Step 2: Run Application
Run the PowerShell launcher script:
```powershell
.\run.ps1
```
*(Enter your MySQL root password when prompted)*

Or run directly via command line:
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
$env:DB_PASSWORD = "your_mysql_password"
.\mvnw.cmd spring-boot:run
```

The server starts on port `8080`. Open your browser at:
**`http://localhost:8080/`**

---

## 5. Sharing with Other Devices on Local Network (Wi-Fi / LAN)

Because Spring Boot binds to `0.0.0.0`, any device connected to the same Wi-Fi network can access the portal:

1. Find your machine's local IP address:
   ```powershell
   ipconfig
   ```
   *(Look for IPv4 Address under Wireless LAN adapter Wi-Fi, e.g. `192.168.1.15`)*
2. On your phone or another laptop connected to the same Wi-Fi, open:
   ```
   http://192.168.1.15:8080
   ```
3. Upload a file on your PC:
   - If you set `$env:PUBLIC_BASE_URL = "http://192.168.1.15:8080"`, generated links automatically use your LAN IP.
   - Or copy the link and replace `localhost` with `192.168.1.15`.
4. Open the link on your mobile phone: the phone browser downloads the ciphertext and decrypts the file locally!

---

## 6. Production Deployment & Internet Access

> **Note on Network Scope:** Currently, the application is **local-only** (accessible on localhost and local Wi-Fi LAN). To make it genuinely accessible over the public internet, deploy using one of the two approaches below.

### Option A: Docker Compose (Recommended)
1. Copy environment template:
   ```bash
   cp .env.example .env
   ```
2. Edit `.env` with your desired database password and public domain:
   ```env
   DB_PASSWORD=my_strong_db_password
   PUBLIC_BASE_URL=https://share.yourdomain.com
   ```
3. Start multi-container stack:
   ```bash
   docker compose up -d --build
   ```

### Option B: Reverse Proxy with HTTPS (Nginx / Caddy / Cloudflare)
Configure an Nginx reverse proxy to forward traffic and provide TLS/HTTPS:
```nginx
server {
    listen 443 ssl http2;
    server_name share.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/share.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/share.yourdomain.com/privkey.pem;

    client_max_body_size 60M;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Set the public base URL environment variable on the backend:
```bash
export PUBLIC_BASE_URL="https://share.yourdomain.com"
```

---

## 7. Environment Variables Reference

| Variable | Description | Default |
|---|---|---|
| `DB_URL` | JDBC connection string | `jdbc:mysql://localhost:3306/secure_file_share?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true` |
| `DB_USERNAME` | Database username | `root` |
| `DB_PASSWORD` | Database password | *(empty)* |
| `PUBLIC_BASE_URL` | Public base URL used for share links | *(empty - falls back to current browser origin)* |
| `STORAGE_DIR` | Filesystem folder for encrypted ciphertext | `./storage/uploads` |
| `MAX_FILE_SIZE_MB` | Maximum allowed file upload size | `50MB` |
| `MAX_REQUEST_SIZE_MB`| Maximum allowed multipart request size | `55MB` |

---

## 8. REST API Reference

| Method | Endpoint | Description | Status Codes |
|---|---|---|---|
| `GET` | `/api/health` | Health check endpoint | `200 OK` |
| `GET` | `/api/files/config` | Client configuration (publicBaseUrl, max file size) | `200 OK` |
| `POST` | `/api/files/upload` | Multipart upload of encrypted ciphertext blob | `200 OK`, `400 Bad Request`, `413 Payload Too Large` |
| `GET` | `/api/files/meta?token=...` | Query file metadata (checks expiry, revocation, download limits) | `200 OK`, `404 Not Found`, `410 Gone` |
| `GET` | `/api/files/download?token=...` | Stream encrypted ciphertext binary payload | `200 OK`, `404 Not Found`, `410 Gone` |
| `POST` | `/api/files/revoke` | Revoke a link using revocation token and purge file from disk | `200 OK`, `400 Bad Request`, `404 Not Found` |
| `GET` | `/api/files/status?revokeToken=...` | Query live file status and download statistics for uploader | `200 OK`, `404 Not Found` |

---

## 9. Automated Testing

Run the full automated test suite (uses isolated in-memory H2 database, requiring no live MySQL instance):
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
.\mvnw.cmd test
```

### Verified Test Suite (24 Tests, 0 Failures):
- **`SecurityTokenServiceTest`** (4 tests): Token generation entropy, SHA-256 64-char format, deterministic hashing, constant-time verification.
- **`FileApiControllerTest`** (6 tests): Endpoints routing, `/api/files/config`, metadata 200/404/410 mappings, revocation response.
- **`FileExchangeServiceTest`** (8 tests): Ciphertext disk writes, SHA-256 token hashing, expiry rejection, download limits rejection, disk purge on revocation.
- **`SecureFileExchangeIntegrationTest`** (6 tests): Full Spring Boot test context validating:
  - `/api/health` returns `UP`.
  - `/api/files/config` returns limits.
  - End-to-end binary upload $\rightarrow$ storage $\rightarrow$ download $\rightarrow$ integrity match $\rightarrow$ revocation $\rightarrow$ 410 Gone.
  - Download limit enforcement (single-use link allows 1st download, rejects 2nd download with 410 Gone).
  - Time-based expiration rejection.
  - Non-existent token 404 rejection.

---

## 10. Manual Viva / Demonstration Guide

### Scenario 1: End-to-End Encrypted Upload & Decryption
1. Open `http://localhost:8080/`.
2. Select any file (PDF, image, audio, or binary document).
3. Choose Expiry (e.g. `15 Minutes`) and Download Limit (e.g. `3 Downloads`).
4. Click **Encrypt & Generate Secret Link**.
5. Observe the 4-step progress bar (key generation, local encryption, ciphertext transmission, completion).
6. Copy the share link. Note that the decryption key is located strictly after `#key=...`.
7. Open the link in an Incognito tab or on another device.
8. Click **Decrypt & Download File**. The file is retrieved, decrypted locally, and saved. Open the file to confirm it matches the original.

### Scenario 2: Single-Use Self-Destruct Download Limit
1. Upload a file with Download Limit set to **1 Download (Self-Destruct)**.
2. Open the share link and download it once. The download succeeds.
3. Refresh the page or click download again: the portal rejects access with **"Download Limit Reached"**.

### Scenario 3: Immediate Link Revocation & Disk Purge
1. Open **My Shared Links** (`http://localhost:8080/manage.html`).
2. Locate your uploaded file and click **🗑️ Revoke Link**. Confirm the dialog.
3. Observe the status update to **Revoked**.
4. Check `./storage/uploads/` on the server: the encrypted file has been deleted from disk.
5. Re-open the recipient share link: access is denied with **"Link Revoked"**.

### Scenario 4: Cryptographic Integrity & Tamper Rejection
1. Copy an active download link.
2. In the browser address bar, alter any character in the `#key=` fragment (e.g. change an `A` to a `B`).
3. Click **Decrypt & Download File**: the Web Crypto API fails authentication tag verification and rejects decryption with a prominent tamper warning alert.

---

## 11. Creating a Submission ZIP File

To create a clean submission ZIP file excluding large build caches and temporary test directories:
In PowerShell:
```powershell
Compress-Archive -Path pom.xml, mvnw, mvnw.cmd, .mvn, schema.sql, Dockerfile, docker-compose.yml, .env.example, run.ps1, run.bat, README.md, src -DestinationPath SecureFileExchangePortal.zip -Force
```
