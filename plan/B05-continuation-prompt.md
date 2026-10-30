# B05续接提示词（2026-10-16，中途交接，尚未交付）

你是我的毕业项目工程伙伴，请使用中文，继续现有 B05 开发。

项目：`D:\快速访问\Desktop\毕业项目文件\Graduation_Project`

本文件是我的本次指令组成部分。B01—B04已经通过我的验收；我已明确授权完成 B05（T030—T036）的开发、测试、独立审阅和交付。此次是接续已进行的工作，不是重新开始批次。按三个内部检查点连续完成，批末统一等待我验收，不进入B06，不重新初始化，不重置数据库，不覆盖现有业务/账号/图片，不回改已应用迁移，不安装新工具或更换技术栈。

## 1. 必须先读的依据

1. 完整读取 `plan/B05-handoff-prompt.md`，其中的文档、源码读取要求、业务规则、范围、测试及环境规则继续生效。不要用本续接快照替代原交接词。
2. 读取原交接词要求的当前 requirements、architecture、implementation-batches、tasks、data-dictionary、api-contracts、dependency-lock、environment、README、campus配置及B01—B04验收证据。前一轮部分大文件输出曾截断，不能直接声称全部已完整读过；按块读取，特别核对 architecture §20.2。
3. 阅读下列已修改源码及全部 B05 测试/证据。代码已经保存在磁盘，不能重写成另一套实现。
4. 使用 Superpowers 执行已有计划、TDD、审阅反馈处理和完成前验证；Vue使用 vue-best-practices / vue-testing-best-practices。用户曾显式调用 find-skills 和 Open Code Review，技能路径可从本机技能目录读取；不要为此安装工具。

最新规则：首次派单只须主动选合格维修人员，不要求设备台账。`equipment:null`合法。台账有/无关联工单均须闭环。V006是可选设备关联迁移，不能误写成索引迁移。

## 2. 当前状态与已落地代码

状态：**B05开发中，尚未完整交付，更未通过用户验收**。三个检查点的主体实现已在磁盘，但浏览器闭环、最终审阅、完整收尾证据和最终回归尚未完成。README/tasks/implementation-batches仍有旧B05未开始文字，不能当作实际状态，也不能在验收前改成用户已接受。

后端主要增量：

- `backend/src/main/java/cn/wzbc/maintenance/workorder/dto/`：Accept/Refuse/Assign/Progress/SubmitRepairResult/Acceptance DTO。
- `workorder/service/WorkOrderMutationSupport.java`：参与用户有序锁→工单、条件版本更新、事件与持久回执；业务写服务局部 READ_COMMITTED。
- `WorkOrderTransitionService.java`：接单开启round1、首次acceptedAt固定；拒单结束分派、待重派。
- `AssignmentService.java`：FIRST_ASSIGN保留，增加CHANGE_BEFORE_ACCEPT/REASSIGN搜索分页名单、曾拒单提示/确认、同工单重新分派、接单后固定人员。接单前替换时追加原分派可见的历史事件。
- `RepairProcessingService.java`：独立进度、本轮新凭证结果提交；`AcceptanceService.java`：原报修人通过完成/不通过开新轮。
- `WorkOrderMapper.java`：真实分派/轮次/进度/事件读写与锁。
- `WorkOrderQueryService.java`和VO：allowedActions、三角色优先级排序、原报修名称`reportedEquipmentName`、当前/有限历史投影。
- `AttachmentService.java`：REPAIR_VOUCHER上传与最终绑定校验，真实工单/当前轮/当前实际维修者和事务绑定。
- `OperationResultService.java`：本批操作持久查询/回放；`WorkOrderController.java`和`SecurityConfig.java`精确开放路径与角色。
- **最后一项后端修复**：`AcceptanceService.decide`在通过分支拒绝任何非null reason，避免将不同业务字段静默归零后重放。定向有效RED→GREEN已完成，但之后尚未完整verify/重新打包。

前端主要增量：

- `components/workorder/TechnicianWorkPanel.vue`、`AcceptancePanel.vue`、`ReassignmentPanel.vue`：三角色业务写入，复用 InlineConfirmation、useBusinessAction 和待确认恢复。
- `api/workorder.ts`、`api/business.ts`、`api/attachment.ts`、`composables/useMutationConfirmation.ts`、`useImageSelection.ts`：操作接口、本操作resource/version/round回执核对、凭证上下文。
- `stores/drafts.ts`：同次登录内存、按用户/工单/操作/轮次编辑隔离，File/ObjectURL释放。
- `WorkOrderHistoryContent.vue`当前轮优先、旧轮折叠；工单列表/详情、router/MainLayout管理员入口等增量接线。
- 原有`api/workorder.test.ts`测试样例增加新字段reportedEquipmentName，没有放松原设备关联校验。
- 最近修改的`router/businessNavigation.ts`及测试、`RequestDetailView.vue`、`WorkOrderDetailView.vue`、`EquipmentDetailView.vue`：受控本地返回路径，保留筛选/页码/原详情，往返原记录复用原路由避免嵌套循环。**这些最新修改尚未跑全套前端验证。**

没有新增迁移；V001—V006须保持原值。没有Git仓库，没有提交/推送/部署。

## 3. 独立审阅结果及修复状态

上一对话使用独立只读子代理`b05_review`。第一次曾因配额中断；第二次继续读代码并反馈。尚未取得完整最终审阅结论；新对话须做最终独立审阅，不能把零散反馈写成整批已通过。

已发现/处理：

1. **记录失权后编辑File/ObjectURL未清**：最初补了`pruneOrderDrafts`并有RED/GREEN。进一步读architecture §20.2发现需要区分“仍可查看但无写权”和“失去查看权”。最新代码改为`drafts.clearOrderEditors(user,order)`，只在工单详情401/403回调清该工单编辑草稿，独立UNKNOWN操作键的原payload继续保留；200但allowedActions消失时保留草稿并禁旧写入，新轮按独立键从空开始。成功刷新不再清草稿。对应`drafts.test.ts`已改调用新函数，**改名/语义细化后尚未重跑**。注意函数内有多余缩进，可最小整理。
2. **凭证上传期间可编辑和重复提交**：`TechnicianWorkPanel`新增uploading锁，从CSRF/上传到业务提交全程锁定，校验身份/详情/轮次/结果文字/图片选择快照/组件存活。`technician.test.ts`有效RED→GREEN（1项）通过。随后成功回调又有下条修改，需回归。
3. **验收/改派确认框打开后仍可改原因/勾选**：两组件busy包含confirmation，确认后重核原原因/人员/确认值；`acceptance.test.ts`新增确认期textarea禁用有效RED→GREEN（1项）。改派组件还需实际交互及相关回归。
4. **旧轮响应清新轮草稿**：`useBusinessAction`的confirmed回调新增第二参数original，send/check都传原冻结提交；维修进度/结果及验收成功回调依据original.resourceId/payload.round清本份草稿。**此最新修复尚缺专门在途跨轮回归测试和最终typecheck。**其它调用者忽略第二参数应继续兼容，需验证。
5. **验收通过静默忽略reason，完整摘要不成立**：后端通过分支拒绝非null原因。`B05BoundaryIT.passed_acceptance_must_not_silently_ignore_a_supplied_reason`有效400/200失败已保存，修复后通过。
6. **申请/工单/台账返回上下文丢失**：已加入上述businessNavigation工具及三个详情页接线，**尚未运行新测试及浏览器验证**。

最终审阅还要核查锁序、旧分派事件/附件/结果权限、上传/清理互斥、原标识重试、所有共享改动是否在白名单并登记。不能只凭前端按钮隐藏宣称权限合格。

## 4. 已有测试与真实结果（注意覆盖时点）

所有路径相对项目根，日志位于`tests/evidence/`。保留失败日志，不覆盖为成功。

- `B05-a-backend-red.log`：A检查点有效业务RED；`B05-a-backend-green.log`：4项通过。`first-green`里含修正夹具version前的一次失败，保留事实。
- 固定测试已添加：AcceptRefuseIT.wrong_technician_cannot_accept；ReassignmentIT.reassignment_keeps_order_identity；RepairProcessingIT.progress_retry_is_not_duplicated；AcceptanceIT.old_round_cannot_accept_new_round。最后这个固定用例仍用SQL历史夹具制造旧轮，不能单独当完整验收闭环证据。
- `B05WorkflowIT`：无台账API首次派单→接单→进度→第一轮真实新凭证→退回→第二轮新凭证→通过；核对同工单/原维修者/首次acceptedAt/最终才completedAt/轮次/附件。有关联设备另有一轮完成与持久回执/绑定断言。**不是两类工单各自所有拒派/两轮组合都已覆盖。**
- `B05-backend-verify.log`第一次完整111项有3个旧B04“只读”断言失败；已按B05契约更新当前维修员allowedActions、接单200及已开放REASSIGN上下文不存在资源404，保留原角色/归属断言。
- `B05-backend-regression-fix.log`：30项通过；`B05-concurrency-refusal.log`：4项通过（接vs拒同时真实HTTP仅一成功、曾拒单须确认、拒后重派身份不变、旧人CURRENT移除/HISTORY保留）。
- **最近完整后端** `B05-backend-verify-final.log`：114项全通过、verify打包成功；时间2026-10-16 13:01左右。该日志虽然叫final，发生在新增B05BoundaryIT和最后AcceptanceService修复之前，**不是最终源码完整验证**。
- `B05BoundaryIT`新增5项：接单vs改派两个HTTP事务竞态/同人禁止/接后禁止；同标识并发进度count/event=1且状态变化后仍重放/异内容409/他人NOT_CONFIRMED；真实凭证先绑定一张再因另一人附件失败全事务回滚、错工单/错轮上传、24小时真实时钟过期/无图；通过vs退回双请求仅一成功、非原报修人和管理员403/空原因400/赢者重放；通过带reason400。
- `B05-boundaries-red.log`：通过带reason预期400实际200为有效RED；同次还有过期用例直接改expires_at触发数据库CHECK的夹具错误，该错误不能当有效RED。
- 过期用例已改用测试Clock推进24小时并重新登录（不破坏CHECK）。**`B05-boundaries-green.log`已结束：5项、0失败、0错误、BUILD SUCCESS，2026-10-16 13:05:08。**刚才用户中断没有使该进程遗留；java PID10880/15964现已不存在。
- 前端`B05-c-frontend-red/green.log`：新轮空结果/旧轮回执拒绝有效RED→GREEN；`B05-c-acceptance-red/green.log`：确认前请求0。
- **最近整套前端** `B05-frontend-tests-final.log`：19文件136项通过；同批typecheck/build日志exit0。该结果发生在新增technician.test.ts、确认/原提交精确清草稿/businessNavigation/clearOrderEditors最后调整之前，**不是最终源码验证**。
- 后续定向`B05-review-draft-red/green.log`、`B05-review-upload-red/green.log`、`B05-review-confirmation-red/green.log`分别保存发现/修复证据。draft-green仍是函数旧名pruneOrderDrafts时，最新clearOrderEditors需再跑。
- **浏览器B05闭环完全未做**。本轮只通过cua.getState核对了可用Chrome。不能把HTTP IT称为真实浏览器通过。
- **OCR已实际尝试**：`B05-ocr-attempt.log`，exit1，`is not a git repository`。没有成功审阅输出，不为OCR自行git init。

## 5. 环境、备份与精确清理

2026-10-16续接前快照（新对话必须实时重核，不沿用PID）：

- 唯一日常监听：127.0.0.1:3307，当前PID8124，Executable为`C:\Users\29675\.graduation-project\project\tools\mysql-8.4.11-winx64\bin\mysqld.exe`，参数`--defaults-file=C:\Users\29675\.graduation-project\project\config\mysql\my.ini`。
- **8080、8081、5173、5174、5175当时均无监听**。没有启动B05浏览器夹具、临时前端、隔离服务。日常后端和前端尚未恢复。
- 真实test连接已验证 MySQL8.4.11、campus_maintenance_test、campus_test@127.0.0.1、UTC、Flyway001—006成功。
- 在B05BoundaryIT完成后再次只读查test：user_account=0、work_order=0、repair_request=0、attachment_file=0。这只核对4张表，不等于所有表/文件全量清理已复验。所有测试复用B04HttpTestSupport按owned IDs精确清理；不得清未知记录。
- 本次代码开始前源备份：`.cache/b05/source-before`（173文件），hash清单`.cache/b05/source-before-sha256.txt`。不能用当前target/JAR当修改前源码。
- dev开始前备份：`.cache/b05/dev-before-20261015-205609.sql`，480080字节，SHA256 `A7C0AD4F363A0C7CA42297663A69AF6D8E62D94305B82C1F194C7099D97F2B88`；禁止打印备份内容/秘密，不恢复覆盖用户数据。
- 开始时dev读数9账号/1817设备/3申请/3图片/**2工单**，与旧B04历史快照1工单不同，不误删第2张用户工单。开发和所有测试只写test，未拿dev现有工单造测试；最后仍须对比实际数据/图片和已应用迁移。
- 秘密在项目外`.graduation-project`，不输出密码、Cookie/Token。mysql.exe可以使用`--defaults-extra-file=...client-test.cnf`；mysqladmin对该文件会因database字段报错，不要反复用这个配置ping；用mysql只读SELECT核对即可。
- Maven在项目PowerShell先`. .\scripts\use-jdk.ps1`。可用`mvn -f backend/pom.xml -Ptest-local ...`或backend/mvnw.cmd。项目根没有`./mvnw`。PowerShell含逗号的参数要整体引用，例如`'-Dtest=B05BoundaryIT'`。日志转向tests/evidence。
- 当前JAR是114项verify时的版本，未包含最后AcceptanceService修复，不能直接启动后宣称最新代码运行。打包前核对并仅停止匹配当前项目target JAR的进程；不批量停止java/node。
- 日常5173/5174必须代理dev8080。浏览器隔离另用空闲端口8081/5175等，且Cookie按主机隔离（localhost/127.0.0.2），不能只换端口。
- 新建/复用浏览器fixture前必须完整读`backend/src/test/java/cn/wzbc/maintenance/support/B04BrowserHarness.java`生命周期、ready前失败/finally清理和精确恢复；它会写B04-control和B04账号，不要随便运行覆盖旧证据，宜增量建B05专用夹具。旧B04超时事实保持原样。
- Chrome在cua inventory可用。浏览器交互只用cua_repl已提供的文档API；新会话先正确初始化并读取返回文档，不假设旧绑定/tab继续存在。本轮未打开业务页、未操作用户日常登录。

## 6. 建议续接顺序（直接继续，不逐点等待许可）

1. 读依据和实际源码，确认没有遗留测试写库进程，核对DB/附件路径和当前listener；保留现有修改与失败日志。
2. 完成审阅修复后的回归：草稿失权语义/原提交跨轮清理、确认期锁、上传期锁、三详情返回路径。加真正的“旧响应到达时当前详情已新轮，不清新轮编辑”行为测试；不要只做实现镜像测试。
3. 核查并补足后端未测边界（真实旧分派后续正式凭证403、跨工单同人附件绑定、旧version在状态重复后拒绝、已绑定/旧轮图片、两类工单完整闭环、原标识拒单/改派/结果/验收持久恢复）。已有B04附件格式/大小/清理测试要保留。
4. 更新必要共享白名单/职责，建立缺失的`tests/evidence/B05-progress.md`。不要把后来补的记录伪装成修改前已登记；如实写补记时间。
5. 按原交接词搭建B05专用隔离浏览器fixture，用管理员/原报修人/两名合格维修员/他人账号验证正常完成、拒派、接前改派、两轮返工、新凭证/有限历史、管理员无代验收、无台账详情图片、UNKNOWN、返回筛选页码、320px及键盘。完成及时结束fixture、恢复临时网络/viewport、精确核验清理。
6. 重新完整test-local verify及全套前端test/typecheck/build；若运行target JAR先核对再停止；完成后启动最新dev后端和日常前端，核对3307→8080→5173/5174、直连/代理CSRF、真实Cookie/权限；不更改用户数据造验收材料。
7. 最终独立审阅及必要修复/复测。OCR非Git限制如实记录，用源备份/hash+实际文件独立审阅，禁止声称OCR通过。
8. 补齐`B05-verification.json`、`B05-runtime.json`、`B05-code-review.md`、`B05-acceptance.md`；更新T030—T036/批次/README真实入口与迁移事实。保留B04验收证据，当前仅到“B05待用户验收”。
9. 按Changed / Verified / 请我测试 / Remaining Risk交付。停下来等我验收，不自动开始B06。

请先简短报告你读到的当前B05完成度、最新回归覆盖时点和服务状态，然后直接接续剩余工作。不要把本续接文件当成所有要求已经完成的证明。
