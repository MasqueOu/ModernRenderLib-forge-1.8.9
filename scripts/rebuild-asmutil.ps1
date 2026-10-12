param(
    [Parameter(Mandatory = $true)][string]$SourceDirectory,
    [string]$Java8Home = $env:JAVA8_HOME,
    [string]$GradleUserHome = $env:GRADLE_USER_HOME
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (!$GradleUserHome) { $GradleUserHome = Join-Path $env:USERPROFILE '.gradle' }
if (!$Java8Home) {
    $Java8Home = Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.jdks') -Directory |
        Where-Object { (Get-Content -LiteralPath (Join-Path $_.FullName 'release') -Raw) -match 'JAVA_VERSION="1\.8\.' } |
        Select-Object -First 1 -ExpandProperty FullName
}
if (!(Test-Path -LiteralPath (Join-Path $Java8Home 'bin/javac.exe'))) { throw 'JDK 8 is required.' }

$sourceCommit = & git -C $SourceDirectory rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or $sourceCommit -ne 'c5dfc7c2f9fcf4e381828df888b2467eb5187dcc') {
    throw 'Use an unmodified ASMUtil checkout at tag v2.8.1 (c5dfc7c).'
}
& git -C $SourceDirectory diff --exit-code -- src/main/java
if ($LASTEXITCODE -ne 0) { throw 'ASMUtil sources must be unmodified.' }

$cache = Join-Path $GradleUserHome 'caches/modules-2/files-2.1'
$asmJar = Get-ChildItem -LiteralPath (Join-Path $cache 'org.ow2.asm/asm-debug-all/5.0.3') -Recurse -Filter 'asm-debug-all-5.0.3.jar' |
    Select-Object -First 1 -ExpandProperty FullName
$loggingJars = foreach ($module in @('log4j-api', 'log4j-core')) {
    Get-ChildItem -LiteralPath (Join-Path $cache "org.apache.logging.log4j/$module/2.0-beta9") -Recurse -Filter "$module-2.0-beta9.jar" |
        Select-Object -First 1 -ExpandProperty FullName
}
$compilationPath = (@($asmJar) + @($loggingJars)) -join [IO.Path]::PathSeparator
$buildRoot = Join-Path $projectRoot ('build/asmutil-beta9-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $buildRoot 'classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$javaFiles = Get-ChildItem -LiteralPath (Join-Path $SourceDirectory 'src/main/java') -Recurse -Filter '*.java' |
    Sort-Object FullName | Select-Object -ExpandProperty FullName
& (Join-Path $Java8Home 'bin/javac.exe') -proc:none -encoding UTF-8 -source 8 -target 8 -cp $compilationPath -d $classes @javaFiles
if ($LASTEXITCODE -ne 0) { throw 'ASMUtil Java 8 compilation failed.' }

# Compiling against beta9 selects String,Object[] logging overloads. Do not use
# the upstream binary, which was compiled against newer Log4j overloads.
$manifest = @(
    'Manifest-Version: 1.0',
    'Implementation-Title: ASMUtil',
    'Implementation-Version: 2.8.1-masqueou-log4j-beta9',
    'Implementation-Vendor: MasqueOu',
    'Specification-Version: 2.8',
    'Source-Commit: c5dfc7c2f9fcf4e381828df888b2467eb5187dcc',
    'Build-Java-Target: 8',
    'Build-ASM: 5.0.3',
    'Build-Log4j: 2.0-beta9', '', ''
) -join "`r`n"

Add-Type -AssemblyName System.IO.Compression
$outputJar = Join-Path $projectRoot 'vendor/asmutil-2.8.1-masqueou-log4j-beta9.jar'
$outputStream = [IO.File]::Open($outputJar, [IO.FileMode]::Create)
$archive = New-Object IO.Compression.ZipArchive($outputStream, [IO.Compression.ZipArchiveMode]::Create)
function Add-ZipBytes([string]$entryName, [byte[]]$entryBytes) {
    $entry = $archive.CreateEntry($entryName, [IO.Compression.CompressionLevel]::Optimal)
    $entry.LastWriteTime = [DateTimeOffset]::new(1980, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
    $stream = $entry.Open()
    try { $stream.Write($entryBytes, 0, $entryBytes.Length) } finally { $stream.Dispose() }
}
try {
    Add-ZipBytes 'META-INF/MANIFEST.MF' ([Text.Encoding]::UTF8.GetBytes($manifest))
    Add-ZipBytes 'META-INF/LICENSE' ([IO.File]::ReadAllBytes((Join-Path $SourceDirectory 'LICENSE')))
    foreach ($classFile in Get-ChildItem -LiteralPath $classes -Recurse -File | Sort-Object FullName) {
        $entryName = $classFile.FullName.Substring($classes.Length + 1).Replace('\', '/')
        Add-ZipBytes $entryName ([IO.File]::ReadAllBytes($classFile.FullName))
    }
} finally {
    $archive.Dispose()
    $outputStream.Dispose()
}
Get-FileHash -LiteralPath $outputJar -Algorithm SHA256
