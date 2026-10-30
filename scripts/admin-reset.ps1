param([Parameter(Mandatory=$true)][ValidateSet('dev','demo')][string]$Profile,
      [Parameter(Mandatory=$true)][string]$TargetDatabase)
$ErrorActionPreference='Stop'
$resetRoot=Split-Path -Parent $PSScriptRoot
if($TargetDatabase -cne "campus_maintenance_$Profile"){throw '必须明确对应环境目标库'}
. (Join-Path $PSScriptRoot 'use-jdk.ps1')
$resetJar=Join-Path $resetRoot 'backend\target\campus-maintenance-0.1.0-SNAPSHOT.jar'
if(-not(Test-Path -LiteralPath $resetJar -PathType Leaf)){throw '请先按README构建后端'}
$resetPreviousConfig=$env:CAMPUS_LOCAL_CONFIG
$resetPreviousProfile=$env:SPRING_PROFILES_ACTIVE
try {
    $env:CAMPUS_LOCAL_CONFIG="file:C:/Users/29675/.graduation-project/local-$Profile.yml"
    $env:SPRING_PROFILES_ACTIVE=$Profile
    & "$env:JAVA_HOME\bin\java.exe" '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -jar $resetJar '--spring.main.web-application-type=none' '--maintenance.command=admin-reset' "--maintenance.target-database=$TargetDatabase"
    if($LASTEXITCODE -ne 0){throw '本地管理员重置未完成，请核对目标库；勿将控制台密码写入日志'}
} finally {$env:CAMPUS_LOCAL_CONFIG=$resetPreviousConfig;$env:SPRING_PROFILES_ACTIVE=$resetPreviousProfile}
