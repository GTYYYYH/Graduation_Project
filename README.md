2026-10-28更新：B10已获用户统一验收，B11已授权，准备项目绑定新对话实施；B12未授权。见 tests/evidence/B10-user-acceptance.json、plan/B11-handoff-prompt.md。下方旧状态保留为历史记录。

# 校园设备维护管理系统

2026-10-26当前状态：B01—B09已获用户验收。用户直接反馈“B09已经验收成功，请你开始执行B10”，见[验收记录](tests/evidence/B09-user-acceptance.json)。B10（T058—T059）已通过项目绑定交接，实现及初轮内部验证已完成，正做独立审阅与交付收尾；待用户统一验收，B11及后续未授权。B09历史自动化、HTTP与浏览器结果见[核验索引](tests/evidence/B09-verification.json)和[验收清单](tests/evidence/B09-acceptance.md)，保留原验证边界。下方历史状态以对应日期为准。

2026-10-23早期反馈增量：已修复剖层墙体消失与单区域相机目标覆盖；当时前端223项通过，见[修复记录](tests/evidence/B07-wall-fix/README.md)。后续B07增量见执行账本。

历史批次概览（截至2026-10-18）：B01—B05已通过用户验收。B05（T030—T036，含三端导航数量增量）于2026-10-18收到直接反馈“B05验收通过”；当时B06后续批次尚待实施，B07—B14未开始。当前批次状态以上方记录为准。当前普通页面支持接单／拒单、人工重派、独立进度、本轮新结果及凭证、原报修人验收和同工单返工。首次派单只需主动选择合格人员，台账关联可空。证据见[B05核验汇总](tests/evidence/B05-verification.json)、[运行状态](tests/evidence/B05-runtime.json)、[执行账本](tests/evidence/B05-progress.md)；基线见[实施批次计划](plan/implementation-batches.md)、[依赖锁定](plan/dependency-lock.md)。

## Project Plan

目标：按14批完成三角色业务及完整3D示意校园；B01—B09已验收，接续B10真实统计看板与明细下钻。
技术栈：JDK21 / Spring Boot4.1.1 / MyBatis-Plus3.5.17 / 独立MySQL8.4.11；Vue3 / TypeScript / Vite / Pinia / Three.js。
目录：backend后端、frontend前端、plan基线、config非秘密配置、scripts项目环境、tests/evidence验证证据；tools/data/.cache/logs为本机运行目录。
任务拆分：B01—B09已验收；B10（T058—T059）已实现并完成初轮检查，独立审阅中；B11及后续未授权。
验收标准：B10按[实施批次计划](plan/implementation-batches.md#b10)核对上海月度完成量、均时、每日分布、设备频次、全部当前状态及对应明细；保留已有账号、业务、图片和已应用迁移。已验收3D范围及全校园性能阶段保持原计划。

B05用户验收来源见[直接反馈记录](tests/evidence/B05-user-acceptance.json)。下一批交接见[B06执行提示词](plan/B06-handoff-prompt.md)。最新B05源码完整回归为后端132、前端159、typecheck/build通过；下方旧数值按交付时点保留。

## B06历史核验（截至2026-10-19）

文博1首层已接权限汇总、静态DOM提示／事项列表、共用业务和10秒读取；失败保有权旧数据并提示过期，UNKNOWN保留原操作。最新源码后端verify141、前端27文件195、typecheck/build通过；真实Canvas及六身份闭环、补充改位置、拒重派、两轮返工、同步计时均有证据。原夹具中断后已按登记恢复清理，日常服务已恢复。

普通新报修页returnTo漏接及图片证据脚本问题已修并独立复核关闭；真实页面已完成108室内→普通报修提交→详情→返回室内补验。按2026-10-19用户批注，三端单条彩色标识可直接进入对应详情，多条先选择，空间目录与3D共用颜色和阶段数量。新增四次跨端观察上界1.923／15.413／17.094／13.815秒；当前dev11账号／8申请／6工单／13图及44条会话与本轮开始全行hash一致，测试数据已精确清理。截至2026-10-19当时：B06待用户验收，B07／B08未开始；后续状态以上方当前批次记录为准。详见[B06核验](tests/evidence/B06-verification.json)、[统一验收步骤与限制](tests/evidence/B06-acceptance.md)、[独立审阅](tests/evidence/B06-code-review.md)、[实时运行](tests/evidence/B06-runtime.json)。后续历史数值只代表各原批次时点。

## B05验收入口（2026-10-18）

追加功能：维修人员“我的任务”的红色数量为本人待接单＋处理中（含返工）；报修用户“我的工单”的红色数量为本人待验收。0隐藏，进入列表不清零，确认操作成功后立即重读，其他端变化由可见页面30秒核对或切回窗口发现。验证记录见[B05入口数量红标增量](tests/evidence/B05-order-count-verification.md)；下述128／143项保留为原B05交付时点，新功能最终回归结果以增量记录为准。

2026-10-18继续追加：管理员“工单管理”统计全部未完成工单（待接单、待重派、处理中含返工、待验收），已完成不计，0隐藏；复用上述核对与刷新。最新验证见[B05管理员数量红标](tests/evidence/B05-admin-order-count-verification.md)，不将此前131／155条当作本次源码通过证明。

使用[日常登录5173](http://127.0.0.1:5173/login)或[5174](http://127.0.0.1:5174/login)，均代理dev8080，沿用既有账号。维修人员进入“我的任务”，报修用户进入“我的工单”，管理员进入“工单管理”。三角色复用工单详情、确认和原操作核对；当前轮优先，旧轮折叠。原被改派人员仅保留本人阶段有限历史，后续维修图片仍由后端鉴权。

最终后端test-local verify **128/128**；前端**21文件143/143**、typecheck/build退出0。真实浏览器五账号覆盖正常一轮完成、拒单重派／接前改派、无台账两轮新凭证返工、UNKNOWN恢复、返回筛选页码及320px键盘。完整步骤和证据边界见[B05验收清单](tests/evidence/B05-acceptance.md)、[独立审阅](tests/evidence/B05-code-review.md)。旧日志即使含final也不替代最新核验汇总。

V001—V006原样保留，V006仅为可选设备关联；未新增迁移。dev原有9账号、1817设备、3申请、3图片和2工单的全行指纹不变；运行核验新增4条会话均已主动退出，原27条会话指纹不变。隔离test的21张业务表和附件目录已精确清理为空，8081／5175已退出。实体触屏、全校园性能及B06空间提示联调不属于当时的B06通过声明。B06状态随后已验收；当前B07状态与验收入口见文首。

## B04验收入口

以下保留B04验收当时的事实与限制；当前维修／验收入口和进度以上方B05为准。

打开[日常入口5174](http://127.0.0.1:5174/campus)或[5173](http://127.0.0.1:5173/campus)，二者均连接dev 8080并使用已有账号和数据。顶栏和左侧工作入口固定，当前位置/报修面板在3D画布下方；普通入口可独立报修，3D加载失败也不阻断。

- 报修用户：普通业务入口→新建报修，选择可报修位置，填原文、联系人并选1—5张真实PNG/JPEG，明确提交；我的申请中对照退回意见补充原申请，可零新增图片，或撤回待审核/待补充申请。草稿仅同次登录内存，退出/刷新不恢复。
- 管理员：[申请审核与首次派单](http://127.0.0.1:5174/admin/requests)进入申请，主动选择合格维修人员并确认即可通过，不需要匹配设备。成功只生成一张待接单工单，原申请/图片保留；范围外可按原因退回，真实重复在折叠区域匹配具体设备后带原因关闭。
- 管理员：[设备台账](http://127.0.0.1:5174/equipment)维护资料及独立启停用，有未完结工单不能移动；资料编辑不隐式启用。[人员管理](http://127.0.0.1:5174/admin/personnel)沿用已验收注册资格审核。
- 管理员两处红标分别显示全部待审核报修申请、全部待审核维修人员数量，包含重新提交/申请；人员沿用既有待审列表对停用账号的口径。红标不随列表搜索改变，0隐藏；可见页面每30秒、回到窗口及确认处理后读取更新。失败显示未知或过期，不伪造0。
- 合格维修人员：我的任务与历史读取本人允许材料；曾拒单/接单前改派的有限历史不含后续他人维修材料。本批不开放接单、维修和验收操作。

2026-10-15按用户最新要求完成首次派单简化：后端test-local verify 106项、前端132项、类型检查和构建均通过；三角色真实浏览器验证无台账工单与原图，台账接口断连也可正常派单。dev原有9账号、1817设备、3申请、3图片和1工单的全行指纹保持一致；追加V006允许工单设备关联为空，已应用V001—V005不改。隔离夹具在中断后触发4小时超时并自动精确清理，已独立核验21张测试业务表/附件均为空，详情见[本次核验与审阅](tests/evidence/B04-person-only-review.md)。统一步骤见[B04验收清单](tests/evidence/B04-acceptance.md)、[运行记录](tests/evidence/B04-runtime.json)。用户已于2026-10-15直接确认“B04审核通过”；B05仅准备执行提示词，不在本轮自动开始。

## B03验收入口

以下保留B03验收当时的范围和证据，当前业务以B04入口为准。

打开[校园浏览](http://127.0.0.1:5173/campus)，使用现有账号登录；重启电脑后仍须登录自己的会话，无须重新初始化。页面标有“示意校园”，只接入文博1五层外观和首层内景，尺寸由参数化模型表达，不代表学校测绘。

1. 选择文博1，拖动画布旋转、右键拖动平移、滚轮缩放；点“回到校园总览”。触控支持单指旋转、双指缩放/平移；选择器和按钮可用Tab、方向键、Enter操作。
2. 选择首层和101，待门口定位结束后点“进入观察（开门）”；选择本身不自动进入。观察两组上床下桌、独立卫浴、空调、灯及插座，再点“返回楼层”。
3. 依次进入101—110、门厅、宿管值班室。走廊两侧各5间，门厅位于入口中部，值班室邻门厅；公共空间仅供观察，值班室只有1桌1椅。
4. 快速切换位置、返回、复位、离页重进，核对地址与门不串；勾选“减少动态”检查直接定位，缩窄窗口检查操作入口。3D失败时仍可选择位置或打开普通列表，并用“重试3D”恢复。

本批没有报修提交、工单或故障业务提示；其他楼栋/楼层仅保留真实目录，内景尚未接入。采用原创参数化几何，原MAX/VRmesh素材许可未知且未分发。素材、实测与限制见[模型检查](plan/model-inspection.md)、[内部核验](tests/evidence/B03-verification.json)、[统一手测与验收记录](tests/evidence/B03-acceptance.md)和[执行账本](tests/evidence/B03-progress.md)。用户已于2026-10-13明确反馈“B03验收通过”；不据此追加未报告的逐项手测或性能结论。

普通内景超时重试保留当前画布；场景代码下载失败时，页面会说明重试需要重新加载。重试只短暂保存当前几何code/观察意图及隔离摘要，60秒且一次消费；实际会话或目录核对失败、身份变化或离页即丢弃，不跨登录恢复旧进入动作。没有保存登录Token或用户业务记录。

最终前端60/60、类型检查/构建通过，B02目录/会话定向10/10；独立审阅Important全部关闭。默认斜视角部分标签仍重叠，可用选择器/完整目录。实体触屏、真实第二账号切换和完整FPS专项未测；详细代价、资源统计和构建警告见统一手测清单。

## B02验收入口

以下保留B02验收当时的范围和证据，当前业务以B05入口为准。

打开[本机登录页面](http://127.0.0.1:5173/login)。dev已初始化示意校园及管理员，登录账号为`campus_admin`。首次随机密码由维护者接收并以Windows DPAPI保存于项目外受限文件；只可由本机当前Windows用户解密。请在自己的PowerShell中读取并保存，不把输出粘贴到聊天、源码或证据：

```powershell
$b02AdminCredential = Import-Clixml -LiteralPath 'C:\Users\29675\.graduation-project\B02-admin-initial.clixml'
$b02AdminCredential.UserName
$b02AdminCredential.GetNetworkCredential().Password
$b02AdminCredential = $null
```

这是本次初始化的维护交付记录；应用和MySQL不提供明文密码查询，重复初始化不会再次输出密码。

1. 分别公开注册报修用户和维修人员，使用不同的演示账号/手机号。角色必须主动选择；注册不自动登录，前往登录时账号带入、密码留空。尝试重复账号/手机号，检查409反馈。
2. 登录待审核维修人员，核对“校园浏览”“资格审核”入口、本人申请快照；刷新后身份由真实会话恢复。
3. 管理员进入“人员管理”的待审核列表，打开详情，填写拒绝原因，核对后提交。维修人员使用原账号修改姓名/手机号重提，再由管理员通过；展开各轮历史，检查原快照和拒绝原因仍在。
4. 核对目录10栋/41层/296处观察单位/257个报修位置；公共空间只观察，未合格维修人员仍可浏览。测试退出、刷新和重新登录。

文博1首层3D样板已在B03接入，报修/工单闭环在B04—B05，完整人员管理、启停用和密码重置在B09。任务入口展示资格后的导航，尚无真实工单。停用行为已用隔离测试fixture验证，当前没有启停用网页操作。

实际证据见[B02核验汇总](tests/evidence/B02-verification.json)、[运行记录](tests/evidence/B02-runtime.json)、[浏览器取证](tests/evidence/B02-browser.json)及[独立审阅和修复](tests/evidence/B02-code-review.md)。助手内部验证与用户验收分别记录；用户已于2026-10-13确认[B02验收通过](tests/evidence/B02-acceptance.md)。

## 本机配置与启动

项目根目录R：`D:\快速访问\Desktop\毕业项目文件\Graduation_Project`。
复用JDK：`C:\Users\29675\Java\jdk-21`；Node24.11.0/npm11.20.0。
专用MySQL只监听127.0.0.1:3307，开发/演示/测试分别为campus_maintenance_dev/demo/test及campus_dev/demo/test账号。不修改phpStudy和全局JAVA_HOME。

秘密仅保存在`C:\Users\29675\.graduation-project\local-dev.yml`、`local-demo.yml`、`local-test.yml`，不要复制到源码/聊天/日志。实查ACL为当前Windows用户完全控制、CodexSandboxUsers组读执行，父目录禁止继承，未擅自改权限；详见[脱敏与ACL核验](tests/evidence/B02-secret-check.json)。管理员维护记录另由当前Windows用户的DPAPI加密。项目仅保留[无密码模板](config/local.example.yml)。

MySQL的Windows原生路径兼容使用`C:\Users\29675\.graduation-project\project` junction指向R，实际程序/数据仍在R；移动项目须更新映射和配置。不要递归删除junction。启动时使用此ASCII路径，且先确认3307没有已运行的本项目实例：

```powershell
Set-Location 'D:\快速访问\Desktop\毕业项目文件\Graduation_Project'
Get-NetTCPConnection -LocalPort 3307 -State Listen -ErrorAction SilentlyContinue
# 没有监听才执行；不需要管理员/Windows服务/防火墙变更
Start-Process -FilePath 'C:\Users\29675\.graduation-project\project\tools\mysql-8.4.11-winx64\bin\mysqld.exe' -ArgumentList '--defaults-file=C:\Users\29675\.graduation-project\project\config\mysql\my.ini' -WorkingDirectory 'C:\Users\29675\.graduation-project\project' -WindowStyle Hidden
```

日常前端入口为`http://127.0.0.1:5173`和`http://127.0.0.1:5174`，二者均连接dev的8080。如需重新启动，先按末节核对对应旧进程，避免重复占用8080/5173/5174。Windows下重新执行verify打包前须先停止运行该target JAR的后端，防止文件占用导致repackage失败；单独test不重打包，可与dev并存。

先构建后启动后端（下面环境变量只作用于当前PowerShell及其子进程；必须点加载JDK脚本）：

```powershell
Set-Location 'D:\快速访问\Desktop\毕业项目文件\Graduation_Project'
. .\scripts\use-jdk.ps1
Set-Location backend
.\mvnw.cmd -version
.\mvnw.cmd -Ptest-local verify
$env:CAMPUS_LOCAL_CONFIG='file:C:/Users/29675/.graduation-project/local-dev.yml'
$env:SPRING_PROFILES_ACTIVE='dev'
& "$env:JAVA_HOME\bin\java.exe" -jar .\target\campus-maintenance-0.1.0-SNAPSHOT.jar
```

后端入口`http://127.0.0.1:8080`。普通启动仅按Flyway追加尚未应用的结构迁移（当前V001—V006），不自动初始化校园或管理员。未登录GET `/api/auth/me`返回401 AUTH_REQUIRED；写请求缺少/错误CSRF返回403 CSRF_INVALID，响应含requestId与private/no-store。认证、目录、资格审核及B04／B05已实现接口按精确路径和角色开放，其余动作默认拒绝；具体业务仍重核归属、版本、状态与轮次。

另一PowerShell启动日常前端（复用现有依赖；已运行的端口无需重复启动）：

```powershell
Set-Location 'D:\快速访问\Desktop\毕业项目文件\Graduation_Project'
.\scripts\start-frontend-dev.ps1 -Port 5173
# 需要第二入口时再执行：
.\scripts\start-frontend-dev.ps1 -Port 5174
```

脚本预检8080真实响应及空闲端口，将子进程代理固定为8080，并恢复调用者原有CAMPUS_BACKEND_PORT。它不安装依赖、不停止占用端口的进程；日志在logs目录。5173/5174共用同一主机Cookie，任一入口退出后，另一入口再次核对会话也会退出。刷新通过me恢复会话；读取失败可重试，核对前停止业务入口。B04隔离取证使用8081及另选空闲前端端口（如5175），不再占用日常5174。

## 本地幂等维护

从项目根目录显式执行，目标库必须与profile一致；没有公开HTTP初始化入口。dev已经执行，以下重复执行应显示changed=false，保留当前身份、名称、启停用及密码哈希：

```powershell
Set-Location 'D:\快速访问\Desktop\毕业项目文件\Graduation_Project'
.\scripts\campus-init.ps1 -Profile dev -TargetDatabase campus_maintenance_dev
.\scripts\admin-init.ps1 -Profile dev -TargetDatabase campus_maintenance_dev
```

新环境首次admin-init必须额外提供`-LoginName`、`-Name`、`-Phone`；随机密码只在本地首次成功返回，应立即妥善接收。demo尚未运行迁移或初始化，切换环境必须明确选择对应配置与目标库。完整初始目录见[逐位置台账](plan/campus-inventory.md)。

## 验证与测试隔离

```powershell
# R/backend，先点加载../scripts/use-jdk.ps1
.\mvnw.cmd -Ptest-local test
.\mvnw.cmd -Ptest-local verify
.\mvnw.cmd dependency:tree
# R/frontend
npm.cmd run test
npm.cmd run typecheck
npm.cmd run build
npm.cmd ls --depth=0
```

test-local显式加载项目外local-test.yml并启用test profile；缺配置即失败，不回退dev/demo。迁移前验证URL、账号、实际MySQL版本/库/权限/UTC及精确附件路径；禁止flyway clean。BootstrapIT故意执行无DDL失败SQL以验证启动失败，UUID新目录拒绝已有对象，写入/清理均检查真实路径，仅删除自身失败历史标记及自行创建的test临时文件。未知JSON字段及格式/字段校验统一400 VALIDATION_FAILED。

如需手工核对三个库，无须在命令行输入密码；以下只读SQL读取外置受限客户端配置：

```powershell
Set-Location 'D:\快速访问\Desktop\毕业项目文件\Graduation_Project'
foreach ($stage in @('dev','demo','test')) {
  & '.\tools\mysql-8.4.11-winx64\bin\mysql.exe' "--defaults-extra-file=C:\Users\29675\.graduation-project\client-$stage.cnf" "--database=campus_maintenance_$stage" -e 'SELECT VERSION(), DATABASE(), CURRENT_USER(), @@session.time_zone; SHOW GRANTS;'
}
```

截至2026-10-14，dev/test已实际应用V001—V005，旧迁移checksum保持原值；dev原4个账号和1817条台账的字段指纹与修复前一致。demo专用库和账号已可连接，演示应用尚未启动，因此demo尚无业务表。

测试只注册合成测试账号，并回滚或按本次记录ID精确清理。B02验收阶段常规后端42项、前端33项通过；真实HTTP验证固定8小时边界、CSRF轮换、不续期、撤销及并发唯一性。浏览器另以test-local的8081/5174隔离服务验证真实页面，fixture生命周期1项通过，取证账号/校园数据清零、临时进程已停止。B03当前前端全套60项通过，后端按影响定向10项通过，未重新运行整套42项或使用`B02BrowserHarness`。该fixture只能显式运行，不进入默认verify或生产包；共享test库不得并行运行这些集成测试。

403保留身份，确定的受保护401才清身份；公共登录401只反馈表单。读取失败不清展示身份，但会话恢复失败阻断业务。写请求超时/断网/5xx/非法成功响应返回UNKNOWN，保留原operationId和输入，不自动重试；登录/退出UNKNOWN必须通过me核实Cookie，明确未登录后解除提交锁定。同账号每次新登录也隔离旧响应。认证身份和CSRF只在内存，登录Token由HttpOnly Cookie承载；Pinia/Web Storage不保存登录Token。

## 停止与证据

交互启动的前后端按Ctrl+C退出。对助手留下的开发进程，应先核对`tests/evidence/B02-runtime.json`中的记录及当前端口所属进程的路径/命令，再仅停止对应进程；旧PID不是当前进程证明。不要使用Stop-Process -Name java/node/mysqld批量操作。
专用MySQL可安全关闭（先确认回环3307属于本项目，不连接phpStudy）：

```powershell
& 'C:\Users\29675\.graduation-project\project\tools\mysql-8.4.11-winx64\bin\mysqladmin.exe' '--defaults-extra-file=C:\Users\29675\.graduation-project\root.cnf' shutdown
```

当前批次的验证证据、实施裁定和实际结果见[B04执行记录](tests/evidence/B04-progress.md)及[任务记录](plan/tasks.md)；B01—B03证据原样保留。日常服务修复见[会话修复记录](tests/evidence/B04-session-fix.md)及[运行快照](tests/evidence/B04-session-runtime.json)，仍须结合实时端口/命令核对，不能直接采用旧PID。当前没有Git仓库；OCR需要Git，独立审阅及其限制逐批记录，未建仓/提交/推送或部署。



2026-10-23最新验收反馈：教学楼房间等尺寸均分，食堂/体育馆剖层直接观察设施；前端224项通过，详见[本次调整](tests/evidence/B07-room-area-feedback/README.md)。


最新教学楼方案：用户确认左4间/中走廊/右3间；1与4为厕所，首层6为门厅。已重建，前端225项，见[本次模型与核验](tests/evidence/B07-corridor-feedback/README.md)。

## B09 本地维护

仅维护者需要时显式执行，正常启动不会重置账号或修正名称。先完成后端构建；命令要求明确环境和目标库。管理员原ID及历史保留，成功新密码仅在本次控制台显示，请勿重定向到日志。

```powershell
.\scripts\admin-reset.ps1 -Profile dev -TargetDatabase campus_maintenance_dev
```

空间名称修正使用同稳定ID、当前version和操作说明；请先核对目标位置，不能用此命令搬移设备或补绑旧申请。

```powershell
.\scripts\correct-location-name.ps1 -Profile dev -TargetDatabase campus_maintenance_dev -LocationId <目标ID> -Version <当前版本> -NewName '修正名称' -OperatorLabel '维护者说明' -Reason '更名原因'
```

上述为人工按需维护示例，本次只在隔离test库实际验证命令，未对dev执行重置或更名。
