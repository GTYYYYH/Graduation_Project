# B05执行提示词：接单、维修、验收及异常闭环

你是我的毕业项目工程伙伴，请使用中文，接续现有项目开发。

项目目录：`D:\快速访问\Desktop\毕业项目文件\Graduation_Project`

B01已于2026-10-12通过我的验收；B02、B03已于2026-10-13通过我的验收。B04（T019—T029）已于2026-10-15通过我的验收，直接反馈原文为“B04审核通过”，记录见`tests/evidence/B04-acceptance.md`。不要把旧的WAITING_USER_ACCEPTANCE、早期检查点B或旧测试数量当作当前批次状态。

本次明确授权：开始B05（T030—T036），按现有实施批次计划完成整批开发、测试、独立审阅和交付。按下面三个内部检查点连续推进，不逐T或逐检查点等待批准；完成后统一等待我验收，不继续B06或其他批次。不要重新搭建、初始化或重写B01—B04，不重新制定整套项目计划。

本文件只有在我发送执行指令时才作为B05开发授权；生成、保存或阅读本文件不代表B05已经开始。当前文件、我的最新直接指令和验收记录优先于历史记忆及旧计划中的冲突文字。

## 一、完整读取当前依据，再修改

1. `plan/requirements.md`：完整读取，重点§7、13、14.2、16、17、19、24及并发/异常/历史权限要求。§15.3的旧“首次派单必须匹配设备”由B04最新直接修订覆盖，不能恢复。
2. `plan/architecture.md`：完整读取，重点业务分层、权限投影、状态转换、事务/锁顺序、图片、回执、时间及历史；保留原历史技术结论，不为同步进度重写。
3. `plan/implementation-batches.md`：完整读取Global Constraints、Review Focus、跨批前置和B05；B06及后续内容仅用于划定边界。
4. `plan/tasks.md`：完整读取§0、Global Constraints、Review Focus、§2.1—2.3，以及T030—T036全部14字段、前置任务、允许文件、固定失败测试和验收标准；核对T019/T025/T028/T029当前接口与B04最新修订。
5. `plan/data-dictionary.md`、`plan/api-contracts.md`、`plan/dependency-lock.md`、`plan/environment.md`、`README.md`、`config/campus.json`。确认当前枚举、Id、version/round、附件上下文、人工名单和错误码，区分历史环境快照与当前事实。
6. B01—B03验收记录；`tests/evidence/B04-acceptance.md`、`B04-progress.md`、`B04-verification.json`、`B04-runtime.json`、`B04-code-review.md`、`B04-person-only-review.md`及相关person-only实际证据。B04浏览器夹具中断超时及清理已如实记录，不把该日志改说成通过。
7. 当前实际源码：
   - 后端security/CurrentUser/BusinessAccessPolicy、SecurityConfig、统一响应/异常/Clock、OperationResultService；workorder全部Entity/Mapper/VO/Service/Controller；repairrequest查询与历史投影；附件上传/绑定/鉴权/清理及测试底座；V001—V006和test-local配置。
   - 前端router、MainLayout、auth、公共请求/鉴权图片、workorder API与现有列表/详情/历史组件、AssignmentPanel、图片选择/表单、drafts、useBusinessRead/useBusinessAction/useMutationConfirmation及相关测试。
   - 按真实依赖继续阅读后再修改，不把规划接口或已有表结构当作功能已实现。

使用Superpowers执行已有计划：关键逻辑先运行有效失败测试，再最小实现和相关回归；遇到失败先查根因。已有明确计划无需重启整套需求讨论/计划审批流程。Vue修改使用vue-best-practices，Vue测试使用vue-testing-best-practices；仅在确实触及渲染逻辑时才读取相关Three.js技能，本批通常无需改渲染器。

## 二、必须继承的B04结果和环境边界

- B04最新规则：正常审核首次派单只需管理员主动选择合格维修人员，不要求匹配设备，不依赖台账接口读取成功。`work_order.equipment_id`、申请matchedEquipment及审核事件设备依据可以为空；工单响应`equipment:null`是合法结果。原申请位置、名称、描述、联系人和图片独立保留。禁止在B05重新增加设备必选条件、自动猜设备、自动补录或把空值关联到任意台账。
- 无台账和原有明确关联台账两类工单，都必须能完成接单、拒单/重派、维修、两轮返工和验收。已建工单不因台账停用而被阻断；台账历史只展示真实有关联的工单，不虚构关联。旧显式设备首次派单操作的校验/摘要和UNKNOWN恢复保留；重复关闭仍要求具体设备及原因。
- dev/test已应用V001—V006，V006为`V006__optional_work_order_equipment.sql`，不是性能索引。V005已建立工单、分派、维修轮次、进度、事件及相关外键/CHECK/唯一约束。优先复用；确需结构变更时先记录原因、备份、核对真实Flyway历史，只追加下一未用版本。不得回改V001—V006、baseline、flyway clean或重建数据库。
- B04最新交付记录为后端test-local verify 106项、前端132项、typecheck/build通过。这些是历史回归基线，不能代替B05当前验证，也不把数量当作必须凑齐的目标。
- B04续接记录中dev有9账号、1817设备、3申请、3正式图片及1张待接单工单；只是当时快照。开始时必须重查并备份当前合法数据，不恢复旧快照、不使用用户现有工单造测试、不改用户账号和验收数据。
- 已有环境基线：JDK21.0.11、Spring Boot4.1.1、MyBatis-Plus3.5.17、MySQL8.4.11仅监听127.0.0.1:3307；Vue3/TypeScript/Vite，依赖精确版本以锁文件为准。日常5173/5174均代理dev 8080；5174已经不是旧隔离测试入口。保留现有账号和数据，不切demo。
- 重新核对监听端口、PID、可执行路径和完整进程命令；不信任旧PID、旧运行文件或“页面能打开”。必要时依次检查3307→8080→5173/5174及直连/代理CSRF、真实会话、业务权限。
- Maven先在项目PowerShell点加载`scripts/use-jdk.ps1`，避免mavenrc_pre.cmd覆盖为JDK17；不改全局JAVA_HOME。重新打包前只停止经实时端口/路径/命令匹配的当前项目target JAR进程；不批量停止java/node/mysqld。后台启动隐藏窗口。
- MySQL程序通过`C:\Users\29675\.graduation-project\project` junction访问；受限配置/秘密在`C:\Users\29675\.graduation-project`。不打印、复制到证据或截图密码、Cookie、Token。沿用HttpOnly Cookie、CSRF及默认拒绝策略，不在Pinia/Web Storage保存登录Token。

## 三、B05范围与连续推进顺序

### 内部检查点A：T030—T031，接单、拒单与人工重派

- 当前分派的合格维修人员在待接单状态查看原文/图片后接单，进入IN_PROGRESS，固定实际维修者和首次acceptedAt，开启round1，round1.startedAt与该转换使用同一时点。
- 当前人员拒单必须填写原因；结束当前分派，清除当前负责人，原工单进入REASSIGN_PENDING，原人员不再出现在当前任务中，仅保留有权历史。
- 管理员只可对PENDING_ACCEPT带原因换不同合格人员，或对REASSIGN_PENDING带原因重新安排。沿用原workOrderId/编号，结束旧分派、记录当时可见材料、新分派及事件；最终事务复核资格。
- 完整实时人工名单支持搜索和分页；扩展既有assignment-eligible的CHANGE_BEFORE_ACCEPT/REASSIGN上下文，保留FIRST_ASSIGN。曾拒单者如仍合格可以再选，但明确展示原拒单记录并要求confirmPreviouslyRefused确认；不得自动选择或实现B08推荐排序。
- 接单后负责人固定，处理中、待验收及返工均禁止改派；待接单同人改派拒绝。接单/拒单/改派竞态只产生一个合法结果。

### 内部检查点B：T032—T033，进度、每轮结果及原报修人验收

- 当前实际维修者仅在IN_PROGRESS独立追加进度；进度和提交本轮维修结果是两个操作，均关联真实工单/轮次。
- 提交结果须有本轮处理结果和1—5张本轮新维修凭证，进入PENDING_ACCEPTANCE；提交不等于完成，不写completedAt。旧轮结果/图只读，不能充作新轮凭证。
- 仅原报修用户可验收；先核对当前轮结果/图片再明确确认。通过才进入COMPLETED并写最终completedAt；不通过原因必填，原工单、原实际维修者不变，保存当轮意见后round+1并回IN_PROGRESS。
- 首次acceptedAt不因返工重写；每轮startedAt/submittedAt、进度、结果、图片、验收意见/时间完整保留。同事务保存业务、事件、附件归属和操作回执；绑定失败整体回滚。
- 本批仅保存后续统计所需真实归属和时间，不实现推荐/统计页面或B10聚合。

### 内部检查点C：T034—T036，三角色页面和真实闭环

- 维修人员：当前任务/本人历史、接单/带原因拒单、独立进度、本轮结果/新凭证；当前轮优先、历史折叠。旧分派历史仅按后端投影展示，无写入口。
- 报修用户：本人待验收优先、真实详情/历轮材料；从详情核对并确认验收，不能列表一键直接验收。失败保留原因，第一轮退回与第二轮通过均能操作。
- 管理员：待接单/待重派优先、拒单/改派原因与历史、完整合格名单及重派确认；接单后的维修/验收资料只读，不能代验收。
- 复用共用详情、鉴权图片、AssignmentPanel、确认/待确认机制；维护申请/工单/台账之间真实返回路径及筛选/页码。设备关联为空时不显示虚假台账链接，仍显示原报修名称/位置/图片。
- 保留已验收fixed顶栏/侧栏、3D下方当前位置面板、两处管理员待审红标及原登录/退出/身份恢复行为。必要导航仅增量接线，不重做整体布局。

只做T030—T036。不得提前接B06空间汇总/故障灯/事项面板/10秒轮询，不扩建3D模型，不做B08推荐、B09完整账号管理、B10统计或部署。

## 四、必须落实的权限、并发、附件和草稿规则

1. JSON/TypeScript的Id保持十进制字符串，version/round按契约校验；服务端产生身份、状态、时间和快照。allowedActions来自实际资格/归属/状态，前端隐藏按钮不替代后端授权。
2. 新增写路径保持Cookie/CSRF及默认拒绝，不能整体放开/api。匿名、错角色、其他维修人员、非原报修人、旧分派或旧轮次必须得到真实错误和零副作用；管理员不能代接单、代维修或代验收。
3. 复用BusinessAccessPolicy：当前实际维修者及原报修人/管理员按FULL范围读取；曾拒单或接单前被改派者只能读取本人分派时可见材料/事件，不能看到后续维修图片、进度、结果或意见。列表/count/详情/附件/操作结果采用同一权限边界，不能为方便查询扩大canReadRequest快捷权限。
4. 按统一锁序协调参与用户→工单→分派/当前轮次→附件；重查当前分派/状态/version/round，条件更新及唯一约束兜底。真实双连接验证竞态，不能只做先查后写。避免旧REPEATABLE READ快照误拒绝已提交同标识重放；沿用B04修复经验，但不盲目全局改隔离级别。
5. operationId按操作者+操作作用域+完整内容摘要幂等，包含资源、version/round及业务字段。同标识同内容返回原回执、不新增记录；异内容409。合法原操作不能因其成功后状态/轮次改变而被误当新操作拒绝，仍须复核当前身份及原结果查看权限。
6. 复用并补齐OperationResultService对本批操作的持久结果查询；新增工单事件中的operation/digest/result/version/round必须真正入库，不能仅返回内存成功回执。前端正确核对本操作的resource、resultVersion及resultRound，不能把其他操作/旧轮回执当成功。
7. 响应丢失/超时/断网/5xx/非法成功回执均按UNKNOWN保存原内容及原operationId，冻结该提交并提供原结果核对或原标识重试。NOT_CONFIRMED不证明事务失败；不能自动换标识重发、重复创建进度/轮次/分派。不能因一次只读刷新而丢弃仍待核对的提交。
8. 维修凭证复用真实附件链路，补齐B04对REPAIR_VOUCHER尚未开放的校验/绑定路径：上传及最终绑定均核对当前实际维修者、IN_PROGRESS、workOrderId、当前round及用途互斥。不能相信客户端负责人；跨人/跨工单/跨轮/已绑定/过期附件全部拒绝。
9. 仅JPG/JPEG/PNG，1—5,000,000字节，扩展名/MIME/可解码内容一致；每轮结果1—5张新图。临时上传、正式绑定、鉴权二进制读取、24小时清理互斥及失败补偿沿用B04，历史正式图不删；不放frontend/public、不暴露磁盘路径或绕过权限的静态URL。
10. 草稿仅同次登录内存；维修编辑键为userId+workOrderId+operation+round，验收原因也按round隔离。新轮结果/File从空开始，不能复制旧轮成果；只读刷新不覆盖本轮输入。失败留输入，确认成功只清本份；退出/身份变化/失权清理相应草稿、File引用及Object URL。不写Web Storage/IndexedDB/URL/日志。

## 五、修改边界和工程要求

- 以T030—T036白名单最小修改，不重构已验收模块。必要共享增量先记录职责、接口、原因并补入当前任务边界，再实现，不因例行共享接线反复等待许可。
- 预计必要共享增量：workorder Mapper的分派/轮次/进度/验收/事件读写及锁；WorkOrderQueryService及VO的allowedActions/轮次投影；OperationResultService的本批回执/权限；SecurityConfig精确路径；AttachmentService/BusinessAccessPolicy的本轮凭证链路；前端workorder API、公共回执/确认、drafts、router/MainLayout及现有共用工单详情。按实际需要使用，不预先扩大接口或新建并行体系。
- V005已有实体/轮次结构，当前代码未实现的Mapper方法或操作不是“已有功能”；需要补齐真实SQL。若现有字段足够，不为本批另建重复表；如确需迁移，只在实际核对、备份后追加下一未用版本，不占用已应用V006。
- 使用已批准工具/依赖，不重复安装Three.js、浏览器驱动或测试框架。查库API先核对锁定版本和本地源码，再按需查询官方文档/Context7。安装新工具、全局设置、服务栈切换须按现有规则说明精确用途与影响，不能当成本批授权附带允许。
- 当前没有Git仓库的记录只作线索，执行时重查；仍无仓库则用修改前备份、hash、实际文件及独立审阅，不git init/提交/推送/部署，不伪称OCR成功。若当前已有仓库，遵守实际用户指令和仓库约定，不擅改历史。

## 六、测试与真实证据

- 每个关键规则先跑有效RED，再实现GREEN；编译错误、夹具错误或Mock返回不是有效业务RED。保留任务固定断言，增加本批边界用例，不删断言绕过失败。
- 固定测试：
  - `AcceptRefuseIT.wrong_technician_cannot_accept`：其他维修人员接单403。
  - `ReassignmentIT.reassignment_keeps_order_identity`：重派前后同workOrderId。
  - `RepairProcessingIT.progress_retry_is_not_duplicated`：同标识重试进度count=1。
  - `AcceptanceIT.old_round_cannot_accept_new_round`：旧round对新轮验收409。
  - `repair-draft.test.ts: new_round_needs_new_result`：新轮result为空，旧图不带入。
  - `acceptance.test.ts: confirmation_precedes_acceptance`：确认前请求数为0。
- 真实MySQL定向覆盖：接/拒/改派竞态；同标识并发/丢响应重试；同人/接单后改派拒绝；曾拒单确认；错角色/错人/旧分派；旧version及旧round即使状态再次相同仍拒绝；无本轮结果/新图、绑定回滚；非原报修人验收；两验收分支仅一成功；两轮同工单、同实际维修者、首次接单时间不变、最终验收才完成。
- 有台账和无台账工单都跑完整闭环；核对数据库真实工单/分派/轮次/进度/事件/操作结果以及附件归属、实际文件和摘要，不以模型检查、Mock或截图替代。正式图片按不同真实账号验证权限；同时回归B04旧显式设备操作和无设备派单路径。
- test-local与真实独立绝对附件目录；执行前核对profile、实际数据库/账号、UTC和路径。共享测试库写入测试及浏览器夹具不并行，失败不退回dev/demo。只按本次登记ID/目录精确清理；不清未知数据、不为夹具扫全库或覆盖用户验收数据。
- 实际Cookie/CSRF浏览器验证至少一名管理员、一名原报修用户、两名不同合格维修人员及他人账号：正常一轮完成；拒单→重派；接单前改派→原人当前任务移除但有限历史保留；第一轮退回→原人第二轮新结果/新图→通过；管理员无代验收入口；无台账详情/图片正常；网络失败/UNKNOWN保留；返回筛选/页码、320px和键盘入口。
- 隔离服务选空闲端口；避免localhost测试账号与127.0.0.1日常dev共用Cookie，不因为换端口就认为Cookie已隔离。可沿用localhost/127.0.0.2等已验证隔离方式，但启动前重新核对当前浏览器能力和服务代理。不要占用或改配日常5174到test。
- 复用或新增浏览器夹具前先读当前生命周期/失败清理实现；ready前失败也必须精确清理。浏览器交互完成及时发送结束信号并等待退出，临时网络阻断/viewport恢复，关闭本次测试页和服务，避免重演长时间中断后只剩超时日志。若确有中断，保留事实并独立重查清理状态，不能把超时说成通过。
- 批末后端完整test-local verify；前端全套测试/typecheck/build；保留B01—B04相关回归、登录/退出、目录/草稿/两类红标及权限行为。只在实际影响3D时做对应真实WebGL回归，不把本批普通业务验证夸大为B06闭环或全校园性能达标。

## 七、交付与停止

维护`tests/evidence/B05-progress.md`及命名不冲突的B05 RED/GREEN、DB/文件/浏览器/清理/运行证据。区分源码检查、自动测试、真实DB/文件、浏览器、用户验收与未测项，不覆盖B04历史证据或既有失败日志。

完成独立审阅和必要修复/复测后，更新T030—T036、B05批次、README操作入口与当前迁移说明，交付：

- `B05-verification.json`：实际命令/退出码、范围和证据索引。
- `B05-runtime.json`：重新核对的服务/代理/进程、真实库/附件结果及隔离清理状态，不存凭据。
- `B05-code-review.md`：独立发现、修复与复测、工具限制。
- `B05-acceptance.md`：三角色可操作入口、正常/拒重派/两轮返工/有限历史/越权/失败重试的步骤和真实预期。

按Changed、Verified、请我测试、Remaining Risk交付，状态为“B05待用户验收”；内部通过不等于我已验收。停止等待我，不自动开始B06，不安装/推送/部署。

请先简短说明B05范围、关键假设、B04验收与最新无台账规则、当前迁移和服务状态，然后直接开始。
