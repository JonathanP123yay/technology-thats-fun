@echo off
setlocal

set "ROOT=%~dp0"
set "MOD_DIR=%ROOT%fabric-monitor"
set "SERVICE_DIR=%ROOT%windows-service"

if not exist "%MOD_DIR%\" (
    echo ERROR: Fabric mod directory was not found: "%MOD_DIR%"
    exit /b 1
)
if not exist "%SERVICE_DIR%\" (
    echo ERROR: Windows service directory was not found: "%SERVICE_DIR%"
    exit /b 1
)

echo ========================================
echo Building Desktop Monitor
echo ========================================
echo.

echo [1/2] Building Minecraft Fabric mod...
if exist "%MOD_DIR%\gradlew.bat" (
    pushd "%MOD_DIR%"
    call gradlew.bat build
    if errorlevel 1 (
        popd
        echo ERROR: Minecraft Fabric mod build failed.
        exit /b 1
    )
    popd
) else (
    where gradle >nul 2>nul
    if errorlevel 1 (
        echo ERROR: Gradle was not found.
        echo Install Gradle or place gradlew.bat in the fabric-monitor folder.
        exit /b 1
    ) else (
        pushd "%MOD_DIR%"
        call gradle build
        if errorlevel 1 (
            popd
            echo ERROR: Minecraft Fabric mod build failed.
            exit /b 1
        )
        popd
    )
)

echo.
echo [2/2] Building Windows companion service...
pushd "%SERVICE_DIR%"
dotnet build
if errorlevel 1 (
    popd
    echo ERROR: Windows companion service build failed.
    exit /b 1
)
popd

echo.
echo Build completed successfully.
exit /b 0
