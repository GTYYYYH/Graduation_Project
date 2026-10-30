param([Parameter(Mandatory=$true)][ValidateSet('dev','demo')][string]$Profile,
      [Parameter(Mandatory=$true)][string]$TargetDatabase,
      [string]$LoginName,[string]$Name,[string]$Phone)
$ErrorActionPreference='Stop'
$adminRoot=Split-Path -Parent $PSScriptRoot
if ($TargetDatabase -cne "campus_maintenance_$Profile") { throw '必须明确对应环境目标库' }
. (Join-Path $PSScriptRoot 'use-jdk.ps1')
$adminJar=Join-Path $adminRoot 'backend\target\campus-maintenance-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $adminJar -PathType Leaf)) { throw '请先按README构建后端' }
$env:CAMPUS_LOCAL_CONFIG="file:C:/Users/29675/.graduation-project/local-$Profile.yml"
$env:SPRING_PROFILES_ACTIVE=$Profile
$adminArgs=@('-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-jar',$adminJar,'--spring.main.web-application-type=none','--maintenance.command=admin-init',"--maintenance.target-database=$TargetDatabase")
if ($LoginName) { $adminArgs+="--maintenance.admin.login-name=$LoginName" }
if ($Name) { $adminArgs+="--maintenance.admin.name=$Name" }
if ($Phone) { $adminArgs+="--maintenance.admin.phone=$Phone" }
& "$env:JAVA_HOME\bin\java.exe" @adminArgs
if ($LASTEXITCODE -ne 0) { throw '管理员初始化失败，请检查输入和目标配置；不覆盖既有管理员' }
