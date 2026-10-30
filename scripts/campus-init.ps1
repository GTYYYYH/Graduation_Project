param([Parameter(Mandatory=$true)][ValidateSet('dev','demo')][string]$Profile,
      [Parameter(Mandatory=$true)][string]$TargetDatabase)
$ErrorActionPreference='Stop'
$campusRoot=Split-Path -Parent $PSScriptRoot
if ($TargetDatabase -cne "campus_maintenance_$Profile") { throw '必须明确对应环境目标库' }
. (Join-Path $PSScriptRoot 'use-jdk.ps1')
$campusJar=Join-Path $campusRoot 'backend\target\campus-maintenance-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $campusJar -PathType Leaf)) { throw '请先按README构建后端' }
$env:CAMPUS_LOCAL_CONFIG="file:C:/Users/29675/.graduation-project/local-$Profile.yml"
$env:SPRING_PROFILES_ACTIVE=$Profile
& "$env:JAVA_HOME\bin\java.exe" '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -jar $campusJar '--spring.main.web-application-type=none' '--maintenance.command=campus-init' "--maintenance.target-database=$TargetDatabase"
if ($LASTEXITCODE -ne 0) { throw '校园初始化失败，请核对配置及脱敏日志' }
