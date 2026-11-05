param([switch]$SkipTests)
$ErrorActionPreference = 'Stop'
$projectDir = $PSScriptRoot
Set-Location -LiteralPath $projectDir
$javaDir = $env:JAVA_HOME
if (-not $javaDir) {
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCommand) { $javaDir = Split-Path -Parent (Split-Path -Parent $javaCommand.Source) }
}
if (-not $javaDir -or -not (Test-Path -LiteralPath (Join-Path $javaDir 'bin\java.exe'))) { throw 'Set JAVA_HOME to JDK 17 or 21.' }
$env:JAVA_HOME = $javaDir
$env:PATH = (Join-Path $javaDir 'bin') + ';' + $env:PATH
$env:GRADLE_USER_HOME = Join-Path $projectDir '.gradle-home'
$tempDir = Join-Path $projectDir '.local\tmp'
New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
$env:TEMP = $tempDir
$env:TMP = $tempDir
$keyStore = Join-Path $projectDir '.local\debug.keystore'
if (-not (Test-Path -LiteralPath $keyStore)) {
    & (Join-Path $javaDir 'bin\keytool.exe') -genkeypair -noprompt -keystore $keyStore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10950 -dname 'CN=Coordinate Lab Development,O=Local,C=CN'
    if ($LASTEXITCODE -ne 0) { throw 'Debug keystore creation failed.' }
}
$gradle = Join-Path $projectDir 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradle)) { $gradle = Join-Path $projectDir '.tools\gradle-8.13\bin\gradle.bat' }
if (-not (Test-Path -LiteralPath $gradle)) { throw 'Gradle Wrapper is missing. Restore it from the repository before building.' }
$tasks = if ($SkipTests) { @('assembleDebug', 'lintDebug') } else { @('testDebugUnitTest', 'assembleDebug', 'lintDebug') }
& $gradle @tasks --no-daemon --console=plain ('-Djava.io.tmpdir=' + $tempDir)
if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' }
