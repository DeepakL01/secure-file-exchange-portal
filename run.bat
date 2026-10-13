@echo off
setlocal
echo ========================================================
echo Starting Secure File Exchange Portal (Java 21 + MySQL)
echo ========================================================

if "%JAVA_HOME%"=="" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.12.1"
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

if "%DB_PASSWORD%"=="" (
    set /p DB_PASSWORD="Enter MySQL root password (press Enter if blank): "
)

call .\mvnw.cmd spring-boot:run
endlocal
