@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

set "APP_NAME=IvaAsins"
set "SRC_DIR=%ROOT%\src"
set "MANIFEST=%SRC_DIR%\META-INF\MANIFEST.MF"
set "BUILD_DIR=%ROOT%\out\build\classes"
set "DIST_DIR=%ROOT%\out\dist"
set "JPACKAGE_INPUT=%DIST_DIR%\jpackage-input"
set "JAR_FILE=%JPACKAGE_INPUT%\%APP_NAME%.jar"
set "SOURCES_FILE=%DIST_DIR%\sources.list"
set "ICON_FILE=%ROOT%\IvaAsins.ico"
set "APP_VERSION=1.0.0"
set "JAVA_BIN="
set "JAVAC_EXE=javac"
set "JAR_EXE=jar"
set "JPACKAGE_EXE=jpackage"
set "INSTALLER_EXE="
set "INSTALLER_STATUS=SKIPPED"

if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_BIN=%JAVA_HOME%\bin"
)
if not defined JAVA_BIN (
    for /f "usebackq delims=" %%I in (`where java 2^>nul`) do (
        set "JAVA_BIN=%%~dpI"
        goto :found_java_bin
    )
)
:found_java_bin
if defined JAVA_BIN (
    if "%JAVA_BIN:~-1%"=="\" set "JAVA_BIN=%JAVA_BIN:~0,-1%"
)
if not exist "%JAVA_BIN%\jpackage.exe" (
    for /f "delims=" %%I in ('dir /b /ad /o-n "C:\Program Files\Java\jdk*" 2^>nul') do (
        if exist "C:\Program Files\Java\%%I\bin\jpackage.exe" (
            set "JAVA_BIN=C:\Program Files\Java\%%I\bin"
            goto :found_full_jdk_bin
        )
    )
)
:found_full_jdk_bin
if defined JAVA_BIN (
    if exist "%JAVA_BIN%\javac.exe" set "JAVAC_EXE=%JAVA_BIN%\javac.exe"
    if exist "%JAVA_BIN%\jar.exe" set "JAR_EXE=%JAVA_BIN%\jar.exe"
    if exist "%JAVA_BIN%\jpackage.exe" set "JPACKAGE_EXE=%JAVA_BIN%\jpackage.exe"
)

echo [1/5] Verificando herramientas Java...
"%JAVAC_EXE%" -version >nul 2>nul || (echo ERROR: No se encontro javac en PATH o JAVA_HOME.& exit /b 1)
"%JAR_EXE%" --version >nul 2>nul || (echo ERROR: No se encontro jar en PATH o JAVA_HOME.& exit /b 1)
"%JPACKAGE_EXE%" --version >nul 2>nul || (echo ERROR: No se encontro jpackage en PATH o JAVA_HOME.& exit /b 1)

echo [2/5] Preparando carpetas de salida...
if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
mkdir "%BUILD_DIR%" || exit /b 1
mkdir "%JPACKAGE_INPUT%" || exit /b 1

echo [3/5] Recolectando fuentes Java...
if exist "%SOURCES_FILE%" del /q "%SOURCES_FILE%"
for /r "%SRC_DIR%" %%F in (*.java) do (
    echo %%F>>"%SOURCES_FILE%"
)

if not exist "%SOURCES_FILE%" (
    echo ERROR: No se encontraron archivos .java en %SRC_DIR%.
    exit /b 1
)

echo [4/5] Compilando y generando JAR...
"%JAVAC_EXE%" -encoding UTF-8 -d "%BUILD_DIR%" @"%SOURCES_FILE%" || exit /b 1
"%JAR_EXE%" cfm "%JAR_FILE%" "%MANIFEST%" -C "%BUILD_DIR%" . || exit /b 1

echo [5/5] Empaquetando EXE con jpackage...
"%JPACKAGE_EXE%" ^
  --type app-image ^
  --name "%APP_NAME%" ^
  --input "%JPACKAGE_INPUT%" ^
  --main-jar "%APP_NAME%.jar" ^
  --dest "%DIST_DIR%" ^
  --icon "%ICON_FILE%" || exit /b 1

if not exist "%DIST_DIR%\%APP_NAME%\%APP_NAME%.exe" (
    echo ERROR: No se genero el ejecutable esperado.
    exit /b 1
)

echo.
echo Listo. Ejecutable generado en:
echo %DIST_DIR%\%APP_NAME%\%APP_NAME%.exe
if defined INSTALLER_EXE (
    echo Instalador generado en:
    echo %INSTALLER_EXE%
) else (
    echo Estado instalador: %INSTALLER_STATUS%
)
exit /b 0





