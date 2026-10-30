param([Parameter(Mandatory=$true)][ValidateSet('dev','demo')][string]$Profile,
      [Parameter(Mandatory=$true)][string]$TargetDatabase,
      [Parameter(Mandatory=$true)][long]$LocationId,
      [Parameter(Mandatory=$true)][int]$Version,
      [Parameter(Mandatory=$true)][string]$NewName,
      [Parameter(Mandatory=$true)][string]$OperatorLabel,
      [Parameter(Mandatory=$true)][string]$Reason)
$ErrorActionPreference='Stop'
if($TargetDatabase -cne "campus_maintenance_$Profile"){throw '必须明确对应环境目标库'}
$locationRoot=Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'use-jdk.ps1')
$locationJar=Join-Path $locationRoot 'backend\target\campus-maintenance-0.1.0-SNAPSHOT.jar'
if(-not(Test-Path -LiteralPath $locationJar -PathType Leaf)){throw '请先按README构建后端'}
$locationPreviousConfig=$env:CAMPUS_LOCAL_CONFIG;$locationPreviousProfile=$env:SPRING_PROFILES_ACTIVE
try {
  $env:CAMPUS_LOCAL_CONFIG="file:C:/Users/29675/.graduation-project/local-$Profile.yml";$env:SPRING_PROFILES_ACTIVE=$Profile
  & "$env:JAVA_HOME\bin\java.exe" '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -jar $locationJar '--spring.main.web-application-type=none' '--maintenance.command=correct-location-name' "--maintenance.target-database=$TargetDatabase" "--maintenance.location.id=$LocationId" "--maintenance.location.version=$Version" "--maintenance.location.name=$NewName" "--maintenance.operator-label=$OperatorLabel" "--maintenance.reason=$Reason"
  if($LASTEXITCODE -ne 0){throw '位置名称修正失败，核对目标位置和版本后重试'}
} finally {$env:CAMPUS_LOCAL_CONFIG=$locationPreviousConfig;$env:SPRING_PROFILES_ACTIVE=$locationPreviousProfile}
