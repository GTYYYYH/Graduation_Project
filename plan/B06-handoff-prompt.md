# B06执行提示词：文博1首层3D业务、提示与同步联调

你是我的毕业项目工程伙伴，请用中文，接续现有项目执行B06（T037—T040）。

项目目录：`D:\快速访问\Desktop\毕业项目文件\Graduation_Project`

本文件及其指定的当前项目依据是执行指令的组成部分。本提示词只有在我于新对话发送“开始B06”或明确要求按本文件执行时才生效；本轮生成、保存提示词不代表B06已经开始。

## 1. 授权、验收与边界

- B01—B05已通过我的验收。B05的最新直接反馈为2026-10-18“B05验收通过，我要新开一个对话，请你给我执行B06的提示词”，记录见`tests/evidence/B05-user-acceptance.json`及`B05-acceptance.md`。不要沿用旧handoff中的B05未交付或等待验收状态。
- 新对话收到本执行指令后，我明确授权整批B06的剩余开发、测试、真实浏览器联调、独立审阅和交付。按下述内部检查点连续推进，不逐T或逐检查点等待批准；完成后统一等待我验收，不自动开始B07、B08或其他批次。
- 仅做文博1首层3D业务联调。现有五层外观及其他目录保留，不扩建楼层／楼栋／全校园内景，不做推荐算法、统计看板、完整人员管理、部署或技术栈切换。
- 保留当前修改和已验收实现，不重新初始化，不重建数据库，不覆盖账号、工单、申请、图片或校园数据，不回改已应用V001—V006。当前无Git仓库的记录须再核对；仍无仓库就用备份／hash／实际文件审阅，不为工具自行git init、提交、推送。
- 使用Superpowers执行已有计划，采用TDD、系统查错、独立审阅、完成前验证；Vue使用vue-best-practices和vue-testing-best-practices。确需修改锚点／渲染生命周期时读取相关Three.js技能。无需重开整套项目需求讨论或审批。

## 2. 开始前完整读取

1. `plan/requirements.md`：完整读取，重点角色／位置／状态／附件／权限、§22—24、§27；注意旧首次派单必须匹配设备已被用户直接修订覆盖。
2. `plan/architecture.md`：完整读取，重点§7、10—12、15、18—20，特别是§19.1—19.4和§20.2。保留历史技术结论，不为同步状态重写需求／架构。
3. `plan/implementation-batches.md`：Global Constraints、Review Focus、前置关系、B06全段与后续批次边界；`plan/tasks.md`：§0、Global Constraints、Review Focus、§2.1—2.3和T037—T040全部14字段、白名单、固定失败测试、验收标准。
4. `plan/data-dictionary.md`、`plan/api-contracts.md`（尤其§6.2）、`plan/dependency-lock.md`、`plan/environment.md`、`README.md`、`config/campus.json`、`frontend/public/campus/manifest.json`及样板资产／许可清单。不要把规划接口当已实现。
5. B01—B04验收记录；B05的`B05-acceptance.md`、`B05-progress.md`、`B05-verification.json`、`B05-code-review.md`，以及最新`B05-order-count-*`和`B05-admin-order-count-*`验证／审阅／运行／数据保护记录。旧B05 handoff只用于追溯，当前验收与本文件优先。
6. 当前实际源码：后端CurrentUser／BusinessAccessPolicy／SecurityConfig、campus目录、申请／工单查询及权限投影、轮次／事件／操作回执、附件、Clock及测试底座；前端CampusView、CampusRenderer及manifest绑定／observation／LoadCoordinator、BusinessPanel／LocationSelector、router/businessNavigation、auth／drafts、client／business／account／workorder API、useBusinessRead／useBusinessAction／useMutationConfirmation／useReviewNotifications及实际共用业务组件／测试。

大文件分块读取，不能因输出截断宣称完整读过。先报告实际已实现／待实现、最新测试覆盖时点和服务状态，再直接继续。

## 3. 必须继承的现状

### B05已验收的业务与最新验证

- 普通页面已有真实接单／带原因拒单、接前改派／待重派人工重派、独立进度、本轮结果及新凭证、原报修人验收与同工单返工、有限历史、UNKNOWN持久回执恢复。直接复用，不另造3D业务状态机或写API。
- 正常首次审核派单只需主动选择合格维修人员，不要求设备台账；`equipment:null`合法。已关联台账与无台账工单均须进入空间闭环，不能以设备模型／台账匹配作为故障提示条件。重复关闭及旧显式设备API仍按既有规则校验。
- 接单后负责人固定，返工沿用原工单、实际维修者和首次acceptedAt；每轮新结果／新图；只有原报修人验收通过才COMPLETED。不能回退这些约束。
- 最新生产源码完整回归为`B05-admin-order-count-backend-verify.log`的**132项、0失败／错误／跳过、verify打包成功**；前端`B05-admin-order-count-frontend-tests.log`为**21文件159项**，typecheck/build退出0，独立审阅无发现。128／143、131／155、旧114／136等均是较早时点，不替代最新结果，更不能代替B06最终回归。
- 已有真实B05五账号业务闭环；最新管理员数量浏览器验证待接3→待验收仍3→完成一单2→全部完成0隐藏。本轮三个夹具标签虽含“拒单重派／两轮返工”，该数量增量浏览器只走三张一轮完成，勿据标签冒领流程。拒重派／两轮返工的浏览器证据在原B05，真实HTTP/MySQL边界仍由全套回归覆盖。

### 已验收的导航数量规则必须保留

| 工作入口 | 数量口径 |
| --- | --- |
| 维修人员“我的任务” | 本人当前待接单＋处理中，含返工；待验收不计 |
| 报修用户“我的工单” | 本人待验收 |
| 管理员“工单管理” | 全局全部未完成工单，含待接单／待重派／处理中含返工／待验收 |

已完成不计，0隐藏，进入列表不清零；原管理员申请审核／人员管理徽标保留。现有导航采用可见30秒核对、route／focus／恢复可见立即核对、已确认写入成功重读。**B06空间10秒刷新与导航30秒机制是不同职责**，不能把本人待处理数量直接当空间可见数量（例如维修人员的待验收仍可在空间只读显示）。

### 当前代码接点

- 校园目录已在`frontend/src/api/account.ts`的fetchCatalog；`frontend/src/api/campus.ts`尚无实现，不要重复搬迁目录体系。
- `CampusRenderer.projectAnchor(locationId)`、onFrame、身份／目标代次、后台暂停绘制、dispose等接点已存在，应增量利用；几何对象不放Pinia。Canvas初始化失败不妨碍普通业务入口。
- `BusinessPanel.vue`已复用RequestForm支持当前位置新报修；CampusView已有位置观察面板、目录选择和受控重试。位置汇总／事项面板／useCampusRefresh及后端CampusBusiness查询尚待B06实施，以实际文件再核对。
- useBusinessRead已有数据／stale／lastSuccessAt，useBusinessAction已有确认成功及原结果恢复，drafts已分用户／资源／操作／轮次并清理File/ObjectURL。扩展时保持其现有调用者兼容，不能为刷新大改已验收流程。

## 4. 内部检查点A：T037，后端权限汇总和事项

- [ ] 先建立有效RED：固定`CampusSummaryIT.same_location_only_counts_owned_items`，断言报修人visibleTotal=1；覆盖同位置两报修人各看本人、管理员全局、维修员仅当前任务／无资格无任务提示、拒单后原人移除、申请转工单只计一次、不可见事项不影响颜色／数量。
- [ ] 按`api-contracts.md §6.2`实现`GET /api/campus/summaries`和`GET /api/campus/locations/{id}/items`，不增加写接口。汇总可按buildingId／floorId限定；事项按既定page／pageSize及category过滤，Id保持十进制字符串，查询条件／归属由后端校验，不能由客户端覆盖身份。
- [ ] 先过滤权限与未完结结构化记录，再聚合／分页；REPEATABLE READ只读事务内使用一致快照与同一服务端queriedAt。排除LEGACY_TEXT和终态、APPROVED申请；普通inProgress与rework互斥，七计数之和为visibleTotal。仅返回visibleTotal>0的位置；同响应queriedAt一致，无N+1初检，不建立红灯表／镜像状态表。
- [ ] 固定类型按现有契约：category为FAULT_PROCESSING／PENDING_ACCEPTANCE／NEEDS_SUPPLEMENT／PENDING_REVIEW／NONE；counts为pendingAccept／reassignPending／inProgress／rework／pendingAcceptance／needsSupplement／pendingReview。LocationItemVo含resourceType、id、businessNo、equipmentName、status、stage、createdAt及allowedActions；空位置事项成功返回0／空列表，不泄露历史或他人事项。

权限：报修人本人申请／关联工单；管理员全部允许范围；合格维修员仅当前本人任务，曾拒单／被替换者的有限历史不混当前空间。未合格维修员仍能浏览校园目录，空间业务返回约定空范围，不借汇总泄露匿名故障。附件与操作结果依旧独立鉴权，不扩大BusinessAccessPolicy快捷权限。

## 5. 内部检查点B：T038，DOM提示、事项和共用业务

- [ ] 固定RED `summary-view.test.ts: marker_click_only_opens_items`，断言mutationRequestsAfterMarkerClick=0；验证一位置一个提示、无权／已完结空列表的准确文案、键盘可用，点击不提交／不上传／不默认处理第一条。
- [ ] 依锚点投影实现静态DOM按钮／文字／数量和图例，随相机、视口、目标变化更新并适当隐藏；颜色不是唯一信息，不闪烁。主类别固定**故障处理→待验收→待补充→待审核**；红色主标题统一“故障处理中”，各故障阶段数量分别保留。灰蓝待审核、琥珀待补充、红故障、蓝待验收；正常空位置不画红绿“无故障”灯。
- [ ] 点击提示只打开该位置当前有权未完结事项，显示编号／名称／阶段／时间，再由用户选择进入共用申请或工单详情。合法空结果原文为“当前没有可查看的未完结记录”，不推断他人是否报修。保留原位置、楼层、观察目标和返回上下文，防止嵌套returnTo循环或非本地返回路径。
- [ ] 复用B04/B05表单／详情／鉴权图片／业务API和同次登录草稿。公共空间只观察；投影仪或无设备模型但合法结构化事项不能消失；无台账工单同样提示。3D失败／加载中仍能使用普通入口，保留fixed顶栏／侧栏、3D下方当前位置面板、320px和减少动态效果行为。

## 6. 内部检查点C：T039，10秒读取、编辑保护和结果核对

- [ ] 固定RED `campus-refresh.test.ts: failed_read_does_not_advance_success_time`，断言lastSuccessAt仍为previousSuccessAt；fake-clock覆盖前台每10秒、单次只读8秒超时、后台暂停／恢复立即、同目标请求不堆叠、身份／目标／读取代次丢弃旧响应、卸载释放监听与计时器。
- [ ] 只读刷新限当前汇总、已打开位置列表、详情状态和allowedActions；独立目标可并行。本人经服务端确认成功后立即使相关汇总／事项／详情／普通列表失效重读；NOT_CONFIRMED／UNKNOWN不伪报成功。推荐尚属B08，任务提到推荐失效只保留未来接点，不提前实现推荐查询／算法。
- [ ] 网络／5xx／只读超时保留上次成功且仍有权数据并标过期、上次成功时间、重试；首次失败显示失败，不伪造全0／无故障，不推进成功时间。401清身份与草稿；403按稳定code区分资格／记录失权和CSRF，不能一律退出，也不能保留已确认无权内容。
- [ ] 只读刷新不写编辑字段，不自动换维修人员，不移搬位置草稿／File。仍可查看但原动作失效：保留输入、禁旧提交并提示；失去查看权：清相应编辑、File和ObjectURL；退出／换身份清旧账号内容。待确认原操作按B05既定生命周期保留核对所需冻结内容，不能因一次读取或新轮响应误清／重发。
- [ ] 写丢响应必须保留原operationId和完整payload，核对／原标识重试后事件数仍1；成功只清本次对应用户／资源／操作／轮次草稿，迟到旧轮成功不清新轮编辑。保留后端version／round和权限最终复核，不以按钮禁用替代。

## 7. 内部检查点D：T040，真实浏览器闭环和计时

- [ ] 建立`tests/cases/sample-floor.md`及`tests/evidence/T040/`，隔离fixture登记账号／业务／附件／控制目录；不使用dev已有用户工单造测试，不访问学校网站提交申请。
- [ ] 报修→灰蓝待审→管理员人工首次派单→红待接→维修接单／独立进度／本轮新图结果→蓝待验收→原报修人通过→仅移该事项。有关联台账与无台账均跑真实链路，核对目录ID→manifest→API→DB同一身份，实际图片解码／摘要和访问权限。
- [ ] 退回补充改位置：未提交草稿不迁移提示，成功重提才迁移，旧位置其他事项仍在；另跑撤回／重复关闭、拒单待重派／人工重派、接前改派、两轮返工，逐状态核对三角色可见性，原分派者只保留有限历史。
- [ ] 同位置两报修人、同房不同设备／多事项：各账号数量／颜色／事项／按钮／图片均遵循后端范围；一项完成不清另一项。管理员不能代验收，维修人员不看他人匿名故障，未合格人员仍可观察校园。
- [ ] 使用独立浏览器配置／Cookie主机支持至少管理员、原报修人、当前维修人实时相互观察，补第二报修人／维修人覆盖隔离；不得用同主机不同端口冒充Cookie隔离。用真实提交完成时间和另一端画面变化时间，实测正常联网前台≤30秒、本人确认成功立即重读。fake-clock和HTTP测试不能替代跨端计时证据；分别记录缓存／网络／可见性条件及每次结果。
- [ ] 在隔离环境验证8秒读超时、断网恢复、后台恢复、状态变化时编辑保留／旧操作禁用、换账号晚响应丢弃及200响应丢失后的UNKNOWN恢复。故障注入、网络拦截和viewport结束及时复原；320px／键盘及真实Canvas画面取证，未测实体触屏／全校园FPS不得冒领。

## 8. 修改白名单和工程要求

- 以T037—T040原白名单最小增量：campus/controller/CampusBusinessController、service/CampusSummaryService、mapper/CampusBusinessMapper、对应XML／VO与CampusSummaryIT；前端LocationSummaryMarkers／LocationItemsPanel／BusinessPanel、api/campus、CampusRenderer、useCampusRefresh、useMutationConfirmation、drafts、client、CampusView及命名测试；T040只允许联调发现的必要既有文件修正并逐项登记。
- 必要共享接线（例如SecurityConfig两条GET精确开放、BusinessAccessPolicy当前空间投影、useBusinessRead／useBusinessAction成功刷新、共用详情／router返回位置上下文）先记录职责、前后接口、原因和验证到B06-progress及任务边界，再实施；不为例行接线反复等待许可，不扩大到无关模块。
- 保持Controller／Service／Mapper／DTO／VO分层、默认拒绝、Cookie＋CSRF、私有业务no-store；不得全量返回前端再隐藏、把秘密／草稿／个人资料写manifest、public、Web Storage或日志。B03既有仅几何重试意图与业务草稿不同，不借此持久化业务。
- 复用V001—V006现有字段与索引，先读实际SQL与约束；无证据不建迁移、不提前实施T067。确需结构变化须先说明／备份／查Flyway，只追加下一未用版本；V006是可选设备关联，不是索引迁移。

## 9. 环境、保护及测试清理

- 当前批准栈：JDK21（scripts/use-jdk.ps1）、Boot4.1.1、MyBatis-Plus3.5.17、MySQL8.4.11／127.0.0.1:3307、Vue3／TypeScript／Vite；具体依赖以pom／package锁定为准。不安装新工具／驱动或改全局JAVA_HOME，不用可能下载新包的临时npx替代现有工具。
- 本次交接只读核对时3307／dev8080／5173／5174运行，8081／5175空闲；完整路径／命令和DB快照见`tests/evidence/B06-handoff-baseline.json`，**仅为生成时点，不是新对话的运行证明**。旧B05浏览器收尾曾发生后台进程退出，原因未确定；新对话须核对listener＋PID＋可执行路径＋完整命令＋实际HTTP，不信旧PID或页面能打开。
- dev外部配置／秘密在`C:\Users\29675\.graduation-project`，不输出密码／Cookie／Token。MySQL通过该目录project junction运行；不编辑data/mysql84业务文件。先备份当前源文件／hash及合法dev数据／正式图片，再测试；新对话用户可能已新增记录，保留全部当前数据，不恢复旧快照。
- 后端测试先点加载`. ./scripts/use-jdk.ps1`，从根执行`mvn.cmd -f backend/pom.xml -Ptest-local ...`；仅campus_maintenance_test与绝对`data/attachments/test`，真实账号／UTC／目录守卫，写库测试和浏览器fixture串行。只按本次登记IDs／精确目录清理，不清未知记录，不改dev/demo造证据。
- 日常5173／5174始终代理dev8080，不复用5174为test。隔离可沿用127.0.0.2:5175→8081，但先查空闲和Cookie隔离；浏览器只用本会话可用的cua_repl文档API，保留用户原有页。以实际可用浏览器配置安排同步计时，不伪造三个独立会话。
- 新建B06专用fixture前完整读B05BrowserHarness／B04HttpTestSupport的生命周期和精确恢复，使用B06专用控制文件／证据前缀，禁止直接运行旧B05夹具覆盖控制文件。ready前失败也清理，联调后立即发done并等待退出；若中断，先保存DB／附件实际证据再按登记恢复，不称超时／缺退出码为正常通过。
- 打包前只停止经实时端口／路径／命令匹配的本项目target JAR，不批量停java/node/mysqld；后台启动隐藏窗口。完成后恢复最新dev JAR和日常前端，依次核验3307→8080→5173／5174、直连／代理CSRF及真实会话权限，不打印令牌。

## 10. 最终验证、交付和停止

关键规则先有效RED→最小GREEN，保留失败证据；编译错误、夹具错误、Mock结果不能作为业务RED。保留固定断言和B01—B05回归，真实MySQL覆盖权限／一致快照／去重／分页／状态变化，真实浏览器覆盖Canvas和跨端同步。

最终源码修改后重新执行后端完整test-local verify，前端全套test／typecheck／build；独立审阅全部B06改动及共享回归，解决Critical／Important并复测。不用历史132／159替代本批回归，不因数量减少而删断言。无Git时按源备份／hash／实际文件独立审阅，OCR不可用如实记录。

交付至少包括：

- `tests/evidence/B06-progress.md`：实施顺序、必要共享边界、RED/GREEN、发现／修复／未测；
- `B06-verification.json`：命令／退出码／覆盖时点／源码摘要／真实SQL与图片证据索引；
- `B06-runtime.json`：实时listener／路径／代理／实际库／附件／隔离清理，不含秘密；
- `B06-code-review.md`：独立发现、修复／复测和工具限制；
- `B06-acceptance.md`及`tests/cases/sample-floor.md`：用户三角色步骤、主异常分支、同步计时及明确预期；
- T037—T040、README和批次计划按真实完成状态更新，requirements／architecture不改成进度台账。

区分源码检查、自动测试、真实DB／图片、真实浏览器、用户验收和未测项。按Changed／Verified／请我测试／Remaining Risk统一交付，状态为“B06待用户验收”并停止；不自动开始B07或B08。

请先简短报告本批范围、B05验收事实、实际源码缺口、最新测试覆盖时点和实时服务状态，然后直接开始整批执行。
