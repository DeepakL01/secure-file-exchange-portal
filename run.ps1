# ==============================================================================
# Secure File Exchange Portal - Windows PowerShell Launcher
# ==============================================================================

Write-Host "--------------------------------------------------------" -ForegroundColor Cyan
Write-Host " Secure File Exchange Portal (Java 21 + MySQL)" -ForegroundColor Cyan
Write-Host "--------------------------------------------------------" -ForegroundColor Cyan

# Set Java 21
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# Display local IP for other devices on the same Wi-Fi
$localIp = (Get-NetIPAddress -AddressFamily IPv4 -InterfaceAlias "Wi-Fi*", "Ethernet*" -ErrorAction SilentlyContinue | Where-Object { $_.IPAddress -notlike "169.254*" } | Select-Object -First 1).IPAddress
if ($localIp) {
    Write-Host "Your Local Network IP: http://$localIp:8080" -ForegroundColor Yellow
    Write-Host "Other devices on your Wi-Fi can open that link directly!" -ForegroundColor Yellow
    Write-Host ""
}

# Prompt for MySQL password if not preset in environment
if (-not $env:DB_PASSWORD) {
    $securePass = Read-Host -Prompt "Enter MySQL root password (press Enter if blank)" -AsSecureString
    $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePass)
    $env:DB_PASSWORD = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
}

Write-Host "Starting Spring Boot application on port 8080..." -ForegroundColor Green
Write-Host "Open your browser at: http://localhost:8080" -ForegroundColor Green
Write-Host "--------------------------------------------------------" -ForegroundColor Cyan

.\mvnw.cmd spring-boot:run
