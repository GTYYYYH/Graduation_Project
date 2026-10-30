param([string]$JdkHome = 'C:\Users\29675\Java\jdk-21')

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$javaExe = Join-Path $JdkHome 'bin\java.exe'
$javacExe = Join-Path $JdkHome 'bin\javac.exe'
if (!(Test-Path -LiteralPath $javaExe) -or !(Test-Path -LiteralPath $javacExe)) {
    throw 'The configured JDK directory must contain java.exe and javac.exe.'
}
$javaVersion = (& $javaExe -version 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $javaVersion -notmatch 'version "21\.') {
    throw 'This project requires JDK 21.'
}
$env:JAVA_HOME = $JdkHome
$env:MAVEN_SKIP_RC = 'true'
$env:PATH = (Join-Path $JdkHome 'bin') + [IO.Path]::PathSeparator + $env:PATH
$env:MAVEN_USER_HOME = Join-Path $projectRoot '.cache\maven'
$repository = Join-Path $env:MAVEN_USER_HOME 'repository'
$env:MAVEN_OPTS = '-Dmaven.repo.local="' + $repository + '"'
Write-Output ('Project JDK: ' + $JdkHome)
Write-Output ('Project Maven cache: ' + $env:MAVEN_USER_HOME)

