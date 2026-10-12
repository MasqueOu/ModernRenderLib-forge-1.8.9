[CmdletBinding(PositionalBinding = $false)]
param(
    [string]$BuildJavaHome = $env:JAVA_HOME,
    [string]$Java8Home = $env:JAVA8_HOME,
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs = @('build')
)

$ErrorActionPreference = 'Stop'
$jdkDirectory = Join-Path $env:USERPROFILE '.jdks'

function Test-JavaVersion([string]$jdkPath, [string]$versionPattern) {
    if ([string]::IsNullOrWhiteSpace($jdkPath)) { return $false }
    $releasePath = Join-Path $jdkPath 'release'
    return (Test-Path -LiteralPath $releasePath) -and
        ((Get-Content -LiteralPath $releasePath -Raw) -match $versionPattern)
}

if (!(Test-JavaVersion $BuildJavaHome 'JAVA_VERSION="17\.')) {
    $BuildJavaHome = Get-ChildItem -LiteralPath $jdkDirectory -Directory |
        Where-Object { Test-JavaVersion $_.FullName 'JAVA_VERSION="17\.' } |
        Select-Object -First 1 -ExpandProperty FullName
}
if (!(Test-JavaVersion $Java8Home 'JAVA_VERSION="1\.8\.')) {
    $Java8Home = Get-ChildItem -LiteralPath $jdkDirectory -Directory |
        Where-Object { Test-JavaVersion $_.FullName 'JAVA_VERSION="1\.8\.' } |
        Select-Object -First 1 -ExpandProperty FullName
}
if (!$BuildJavaHome -or !$Java8Home) {
    throw 'Install JDK 17 and JDK 8, or pass -BuildJavaHome and -Java8Home explicitly.'
}

$previousJavaHome = $env:JAVA_HOME
$previousJava8Home = $env:JAVA8_HOME
$previousJavaOpts = $env:JAVA_OPTS
$buildExitCode = 1
Push-Location -LiteralPath $PSScriptRoot
try {
    $env:JAVA_HOME = $BuildJavaHome
    $env:JAVA8_HOME = $Java8Home
    # Use certificates already trusted by Windows; HTTPS and distribution checksums stay enabled.
    $trustArguments = @('-Djavax.net.ssl.trustStoreType=Windows-ROOT', '-Djavax.net.ssl.trustStore=NONE')
    $env:JAVA_OPTS = (($previousJavaOpts, ($trustArguments -join ' ')) -join ' ').Trim()
    & .\gradlew.bat --no-daemon --console plain @trustArguments @GradleArgs
    $buildExitCode = $LASTEXITCODE
} finally {
    Pop-Location
    $env:JAVA_HOME = $previousJavaHome
    $env:JAVA8_HOME = $previousJava8Home
    $env:JAVA_OPTS = $previousJavaOpts
}
exit $buildExitCode
