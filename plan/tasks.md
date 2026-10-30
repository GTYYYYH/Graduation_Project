# 基于SpringBoot的设备维护管理系统的设计与实现 — Implementation Plan

2026-10-26最新状态：B01—B09已通过用户验收；用户直接反馈“B09已经验收成功，请你开始执行B10”，来源tests/evidence/B09-user-acceptance.json。B10（T058—T059）整批已授权，待绑定项目的新对话通过交接门禁后实施；B11及后续未授权。下方旧日期、待测描述及测试数字保留原时点和AI验证边界，不覆盖最新用户验收。

2026-10-15 B04用户直接修订：正常审核首次派单只选合格维修人员，无需台账；新增V006放宽工单equipment_id可空，不改已应用迁移或旧数据。T028/T029原设备必选断言改用于重复关闭/旧显式设备API兼容路径，正常页面不发equipmentId；T025/T027适配空关联查询。以下旧首次派单设备前提以本修订及api-contracts/data-dictionary为准。T067未来索引使用执行时下一未用版本，不复用V006。B04已于2026-10-15通过用户验收；B05已于2026-10-18通过用户验收（含三端导航数量增量）；B06（T037—T040）已交付，待用户统一验收。

> **For agentic workers:** REQUIRED SUB-SKILL: 后续按 `superpowers:executing-plans` 连续执行用户明确授权批次内的关联任务；若用户另选子代理方式，使用 `superpowers:subagent-driven-development`。步骤使用 `- [ ]` 跟踪。执行与用户手测单位按[实施批次计划](implementation-batches.md)改为B01—B14；批内验证后继续，批末统一等待用户验收，Git提交仍须明确授权。

**Goal:** 在用户本机交付完整10栋示意校园、三角色设备维护闭环、按权限的空间提示、可解释的人工派单推荐及可复现验收证据。  
**Architecture:** Vue3＋TypeScript＋Three.js提供观察和业务入口；Spring Boot单体、MyBatis-Plus与独立MySQL8.4保存身份、空间及业务。3D与普通列表共用接口和表单；后端掌握权限、状态、历史、事务、附件及统计。  
**Tech Stack:** Java21、Spring Boot4.1.1、MyBatis-Plus3.5.17 Boot4 starter、Boot管理的Security/Flyway/JDBC/测试依赖；Vue3.5.43、Router4.6.4、Pinia4.0.3、Element Plus2.14.7、ECharts6.1.0、Vite8.3.4、plugin-vue6.0.9；Three.js、TypeScript及vue-tsc在T003核对固定。  
**Spec:** [requirements.md](requirements.md) v2.1、[architecture.md](architecture.md) v2.1。历史[3D协作提示词](../../提示词_3D校园报修与维修推荐.txt)仅用于理解流程，不覆盖当前正式文档或本轮直接授权。  
**日期与状态:** 2026-10-26，Asia/Shanghai。B01—B09已通过用户验收；B10（T058—T059）已获整批授权，将在新对话完成项目绑定交接后实施；B11—B14未授权。验收来源见tests/evidence/B09-user-acceptance.json。历史授权、测试范围与凭据UI证据保留原记录，不追溯声称旧需求／架构已正式整体审阅。

## 0. 依据、事实与授权边界

1. 当前需求和架构均保留“新版待整体审阅”历史状态。计划编写时用户授权重写tasks.md；随后直接要求开始搭建并批准A01—A04、继续B01，已授权本批实施，但不追溯声称两份基线完成整体正式审阅。
2. 本文件保留72项技术明细；[实施批次计划](implementation-batches.md)负责14批执行顺序和统一验收。以当前需求中的业务规则为准，架构提供实现边界；涉及业务或核心选型变更时报告差异，不能靠计划静默改变需求。
3. 旧根目录tasks.md已删除。编号迁移参考 `plan/.backups/20261011-210554-需求文档整体修订/tasks.md` 的48项标题；只用来追溯拆分，不恢复旧规则、不继承完成状态。旧任务记录不证明新任务已实现。
4. 初次环境核对时只有plan及其备份，没有工程。批准后B01已创建backend/frontend、配置获批工具/专用数据库并完成基础验证；B02已接入账号、目录和资格审核；B03已接入文博1五层参数化外观和完整首层12处内景。未重新初始化B01/B02或覆盖用户数据。实际状态以任务证据和批次记录为准。
5. 当前源文件SHA-256：requirements.md＝`7D386F785C7218F31197537FC4F06C68B73C86756622BAE624B99A1B3836D325`；architecture.md＝`D98AD3ABF9028518427F224B827890234D043EB9ED2295CAF7AFCEECFD5E1400`。执行任务前重读当前版本；文件变化时先检查受影响契约。
6. 启动条件：用户审阅基线和[实施批次计划](implementation-batches.md)，明确“计划通过，开始Bxx”。本批内前置实际验证通过即可连续推进；跨批前置须对应批次已验收。整批交付后状态“待用户验收”并停止；仅说“通过”只记录该批验收，“Bxx通过，继续下一批”才推进依赖就绪的下一批。用户2026-10-12的新要求覆盖此前每T项单独等待验收的约定。
7. 缺环境或凭据时明确说明设置位置、用途与验证办法。安装、升级、修改全局配置或数据库实例须依据已批准的具体清单执行；本计划中的命令不是当前安装授权。已有可调用工具可在授权任务中使用。
8. 不自动初始化Git、提交、推送或部署。若以后明确授权提交，说明提交文件、原因和本地仓库位置；禁止force push、修改远程历史、删除未知分支。
9. 授权来源记录：用户先直接要求“根据implementation-batches.md，开始项目搭建”，输出R，随后“批准A01—A04，继续B01”；B01/B02随后均获验收。2026-10-13用户明确授权“开始B03（T015—T018）”，以B03-handoff-prompt.md为指令组成部分，整批完成后等待验收，不开始B04。随后用户明确反馈“B03验收通过，我需要新开一个对话，给我执行B04的提示词。”，据此记录B03整批验收并准备B04交接，不在本对话开始B04。未知新安装仍须具体授权，不追溯标记旧需求/架构整体审阅通过。

## Global Constraints

- 正式题名保持“基于SpringBoot的设备维护管理系统的设计与实现”；资源、账号和业务记录均标为演示，不宣称学校实测。
- 单体后端、本机浏览器演示；保留phpStudy；独立MySQL8.4 LTS，不使用其5.7实例代替。Java编译和运行统一21。
- 10栋、41楼层、296处内景覆盖单位、257个可报修位置、1817项初始台账。初始数量不是后续台账上限。
- 宿舍174×8＝1392；教室57×6＝342；办公室15×4＝60；食堂9×2＝18；体育馆首层3＋二层2＝5。教学空调72台；投影仪有业务台账、无需网格。
- 不解析房号、地址、模型名或mesh UUID推断位置。楼栋／楼层／位置身份显式配置；模板仅共享几何资源。
- 新申请强制合法可报修locationId，保留原文和联系快照；类别为可选自由文本。内部设备设施种类另行限于已确认空间范围。
- 申请五状态：待审核、退回待补充、已审核通过、已撤回、重复关闭。工单五状态：待接单、待重派、处理中、待验收、已完成。返工为轮次阶段，不增枚举。
- 一申请最多一工单、同设备最多一未完结工单；审核与首次派单同事务。接单后负责人固定，仅原报修人验收，返工沿用原工单。
- 随机Token＋HttpOnly Cookie＋MySQL摘要；固定8小时、不续期；写请求CSRF。密码变更／停用撤销全部旧会话，恢复不复活会话。
- JPG／JPEG／PNG，单文件≤5 MB（本计划详设按5,000,000字节），每次提交≤5张；首次故障图片及每轮新凭证至少1张。临时附件默认24小时，正式历史保留。
- 先按权限过滤再聚合、分页及计数；曾拒单／接单前被改派者只能看本人分派期间材料，不能读取关系解除后的新维修材料。
- 时间DATETIME(3)按UTC读写；接口ISO8601带时区；统计月和今日推荐使用Asia/Shanghai半开区间。
- 位置主类别：故障处理→待验收→退回待补充→待审核。申请转工单仅计一次，普通处理中与返工互斥。
- 推荐L＝W＋2I＋3R＋0.5A；排序L升序→今日D升序→最近成功分派时间升序（从未分派优先）→人员ID升序。推荐不自动选人提交。
- 前台10秒周期读取、单次读取初始8秒超时、不堆叠请求；正常联网他人变化≤30秒，本人确认成功立即刷新，恢复前台立即读。
- 同次登录内存草稿按用户＋位置／申请／操作轮次隔离；不写Web Storage、IndexedDB、URL、日志；结果待确认保留原标识和输入。
- Three.js单一渲染实例，WebGL2＋OrbitControls＋GLTFLoader；本机资源、不依赖CDN。缓存最多两个内景模板条目，共享资源按引用释放。
- 普通列表独立完成业务；失败由用户选择列表。未合格但启用的维修人员可浏览全部允许几何，不能获得任务提示或报修权限。
- 禁止默认引入JWT、Redis、SSE/WebSocket、消息队列、微服务、Docker/Testcontainers、AI推荐、IoT、小程序、对象存储和校园编辑器。
- 模型处理只用副本；下载、格式可读、用途合格、浏览器渲染、性能达标分别取证。

## Review Focus

以下五类跨模块风险必须由所属任务写进测试，不能只在最终回归补测：

| 风险输入／条件 | 应有结果 | 首次锁定任务与复核 |
| --- | --- | --- |
| 同位置不同用户；曾分派者关系解除后猜测附件ID | 数量、颜色、详情和图片均不泄露；当前提示移除，有限历史只读 | T019、T025、T037；T061、T064 |
| 写入已提交但响应丢失；同状态跨轮次旧页面 | 原标识重试返回原结果，不重复事件；旧version／round拒绝 | T022、T028、T032、T033、T039；T060 |
| 旧文字申请匹配了有位置设备；有未完结工单时移动设备 | 旧工单仍无3D位置；移动和审核共同加锁，只产生允许结果 | T028、T026、T056；T060 |
| 新旧加载、刷新、账号切换、晚到重试交错 | 最后一次有效目标／身份生效；有权旧数据标过期，失权清除；草稿不被只读覆盖 | T016、T024、T039、T062 |
| 跨上海日／月、多轮返工及多附件JOIN | 推荐I/R互斥、完成量按工单去重，统计边界和维修时长可复算 | T047、T058；T064、T067 |

## 1. 工具、MCP、skills、环境与安装顺序

### 1.1 本轮只读核查结果

2026-10-12检查命令与PATH，不代表工程已构建：

| 项目 | 本轮证据 | 需要处理 |
| --- | --- | --- |
| java／javac | 21.0.11 | JDK已可调用；T001重新确认真实JDK目录 |
| Maven | 3.9.15，但运行JDK＝17.0.5 | T004用项目启动脚本／IDE项目配置指定JDK21；不直接全局改JAVA_HOME |
| Node／npm | v24.11.0／11.20.0 | T003核对目标包engines和peer；可用时无需重装 |
| MySQL | PATH客户端来自phpStudy MySQL5.7.26；服务列表未发现可确认的独立8.4 | “8.4未确认”，不能据此断言完全未安装；T001检查安装位置／实例／端口，T004必要时安装独立实例 |
| Git／Python | Git2.55.0可调用；Python路径可发现，未核对版本 | Git不等于项目已建仓库；Python只在所选脚本需要时核查 |
| Chrome／Edge | Chrome153.0.8010.48、Edge155.0.4283.45可执行文件存在 | 尚未验证WebGL2、硬件加速或实际页面性能 |
| Superpowers／Visualize | 本会话技能可用，writing-plans文件已读取 | 已有工具可用，不重复安装 |
| Three.js专项skills | 未在会话技能目录及本机两处SKILL.md清单发现 | 仅记“本轮未发现”；可选安装见下表，不作为开工硬条件 |
| Context7／Playwright MCP／Three.js调试MCP／Blender MCP | 本会话未暴露相应可调用工具，未审计全局秘密配置 | 记“未确认已连接”，不写成已安装或全机没有 |
| Blender／3ds Max | Blender未在PATH发现；未检查GUI安装和源软件版本 | T015检查条件；PATH缺失不证明未安装 |

本轮只读访问Maven Central／npm registry，Boot4.1.1、MyBatis-Plus3.5.17、Vue3.5.43、Router4.6.4、Pinia4.0.3、Element Plus2.14.7、ECharts6.1.0、Vite8.3.4、plugin-vue6.0.9对应元数据可取得。Vite与plugin-vue的engines均为 `^20.19.0 || >=22.12.0`，本机Node24.11.0满足这个版本条件；这仅证明包存在及部分声明匹配，不证明安装、类型检查或整套构建通过。T003／T005／T006仍须核对传递依赖和实构建。

### 1.2 必需环境与项目依赖

| ID | 名称／来源 | 必要性与安装位置 | 安装或配置时机 | 验证／费用／替代 |
| --- | --- | --- | --- | --- |
| E01 | JDK21，[OpenJDK](https://jdk.java.net/java-se-ri/21) | 系统运行必需；优先已有JDK21，项目指定绝对目录 | T001只读，T004批准后配置；缺失才另选发行版安装 | java、javac、Wrapper均报告21，实际编译；不同发行版使用条件安装前核对 |
| E02 | [Maven Wrapper](https://maven.apache.org/wrapper/) | 后端构建必需，文件在backend/.mvn和mvnw.cmd；无需新增全局Maven | T003确定发行版URL／版本，T005生成；首次运行会下载构建工具和依赖 | Wrapper版本与校验信息固定，不能沿用JDK17；无账号，下载需网络 |
| E03 | [Node.js](https://nodejs.org/en/download)＋npm | 前端构建必需，优先已有安装 | T003核对engines，T004仅按批准清单处理缺口 | node／npm与实际构建；不额外安装pnpm、yarn或bun |
| E04 | [MySQL8.4社区版与Windows安装说明](https://dev.mysql.com/doc/refman/8.4/en/windows-installation.html) | 必需独立实例；独立数据目录、服务名、端口，保留phpStudy；安装器要求的VC++运行库按条件核对 | T001确认已有实例，T004批准后安装／配置及建专用库 | SELECT VERSION()和实际连接证明8.4；开发／演示／测试库及受限账号分离；MySQL客户端版本不能替代服务端证据 |
| E05 | [Chrome](https://www.google.com/chrome/)或[Edge](https://www.microsoft.com/edge) | 浏览器与WebGL2必需；已有浏览器优先 | T001／T016核查，不默认升级 | 实际WebGL2和硬件加速；不同浏览器配置演示不同角色 |
| E06 | [Git](https://git-scm.com/downloads/win) | 版本管理推荐，非业务运行必需；本机已有 | 仅用户授权建仓库／提交时使用 | 无远程账号也可本地管理；未授权时保留文件交付，不自动git init |
| P01 | [Spring Boot](https://docs.spring.io/spring-boot/)、[MyBatis-Plus](https://baomidou.com/getting-started/install/) | pom.xml固定Boot4.1.1及MP3.5.17 Boot4 starter／同版本分页支持；Security、Validation、Flyway核心与MySQL模块、Connector/J、JUnit／Mockito／MockMvc由Boot管理；旧讨论值Security7.1.1/Flyway12.4.0/Connector9.7.0仅核对记录，不单独覆盖BOM | T003列依赖树，T005安装构建；后续任务只补批准清单内的必要模块 | dependency:tree、实际MySQL迁移与测试；不叠加其他MyBatis starter，不自行覆盖BOM版本 |
| P02 | Vue／Router／Pinia／Element Plus／ECharts／Vite／plugin-vue／TypeScript／vue-tsc | 前端package.json精确版本＋package-lock.json；npm本地项目安装 | T003确认TS兼容组合，T006批准后安装 | npm ci、typecheck、build；无API Key，不按latest浮动安装 |
| P03 | [Three.js](https://threejs.org/manual/#en/installation)＋同包OrbitControls／GLTFLoader | 3D运行必需；frontend项目依赖。声明包仅在真实类型需求下加入匹配版本 | T003固定精确版本；T006安装，T016实际使用 | WebGL2、GLB及生命周期；不加React/R3F或Vue专用3D框架 |
| P04 | [Vitest](https://vitest.dev/guide/)＋必要时Vue Test Utils／jsdom | 推荐项目开发依赖，用于草稿、代次、刷新与推荐选择等有意义单元测试；不做布局快照堆积 | T003提出精确兼容清单，T006经批准安装 | npm run test；如用户不装，须提供同等可复现逻辑验证并记偏差，不能跳过关键规则验收 |
| P05 | [Playwright Test](https://playwright.dev/docs/intro) | 推荐浏览器回归开发依赖，区别于Playwright MCP；可复用已安装浏览器channel | T003规划，T064执行前授权安装缺项 | 不默认下载额外浏览器；需下载时报告路径和体积，先获准；无API Key |
| E07 | Python3 | 条件可选，配置核对、取证和性能脚本可用；优先Node／Java和已有Python标准库 | T065选择脚本实现时核对，不新增全局Python包 | python --version；不用pandas等解决简单记录 |
| E08 | [Blender](https://www.blender.org/download/) | 只在GLB／FBX等副本需要人工整理时使用，非系统运行依赖 | T015选定副本路线并获准后安装或复用 | 实际导入导出再GLTFLoader渲染；程序化几何路线可不安装 |
| E09 | 3ds Max及匹配导出器／V-Ray代理依赖 | 仅确实复用.max／.vrmesh时条件必需；合法授权、匹配版本由维护者核对 | T015明确成本和格式后单独授权 | Blender与GLTFLoader不能直接把.max／.vrmesh转为可用校园；格式不可用可用参数化示意几何，不要求购买软件 |
| E10 | glTF结构验证／浏览器DevTools | 使用GLB时条件需要格式检查；优先已有验证能力 | T015／T016检查副本；额外CLI包需批准 | 格式通过不代替可进入内景、许可或性能；不自动上传私有素材到在线验证器 |

### 1.3 skills与MCP选用清单

**系统运行不需要安装任何MCP。** MCP和skills只服务开发，不进入发布包或派单链路。

| ID | 工具、来源与用途 | 当前状态／必要性 | 安装范围与时机 | 账号、费用、Key及验证／替代 |
| --- | --- | --- | --- | --- |
| S01 | Superpowers：writing-plans、executing-plans、TDD、systematic-debugging、verification-before-completion | 本会话已可用；计划／批内连续开发／查错／取证流程 | 不重复安装；按任务加载；子代理需用户选定且工具真实可用 | 不引入新业务服务；本项目“批内连续验证、批末统一验收”优先 |
| S02 | Visualize | 已可用，可选讨论原型 | 仅需要澄清界面时使用，原型与运行证据分开 | 无需为已有技能另装工具；不把示例图当真实Three.js测试 |
| S03 | [CloudAI-X/threejs-skills](https://github.com/CloudAI-X/threejs-skills) | 本轮未发现安装；推荐但非必需 | T003审核内容，批准后项目范围安装fundamentals／geometry／interaction／animation／loaders；materials／lighting仅按需 | 社区内容需按所选Three.js官方API复核；源码使用条件检查LICENSE；替代为官方文档 |
| S04 | documents／docx、pdf、pptx或presentations、spreadsheets | 当前会话有相关技能；最终原生材料按所需格式选择，不全部调用 | T069—T072先检查技能、可用编译／渲染工具及工作区依赖；只装真实缺项 | 原生Word／PDF／PPT须分别打开或渲染核查；Windows Office／渲染运行时并非读取SKILL即可认定具备 |
| M01 | [Context7](https://github.com/upstash/context7)：版本对应文档检索 | 未确认已连接；首选可选MCP | T003给出具体连接方案，批准后在客户端配置；不写前端运行依赖 | 官方提供CLI＋skills与MCP两种模式；API Key建议用于更高额度，可能涉及OAuth／服务额度，实施时核对；直接官方文档可替代 |
| M02 | [Playwright MCP](https://github.com/microsoft/playwright-mcp)：真实浏览器读操作、流程取证 | 未确认连接；已有mcp__cua_repl浏览器能力优先；可选 | T003或T064只有现有能力不足才批准安装；Node运行、隔离浏览器会话 | 不需业务API Key；Canvas选取不能仅靠DOM树，必须看实际画面和门／内景；替代为现有浏览器工具或Playwright Test |
| M03 | [threejs-devtools-mcp](https://github.com/DmitriyGolub/threejs-devtools-mcp)：场景与资源调试 | 未确认连接；可选诊断工具 | T016／T062遇真实观测缺口再申请；只连本机开发浏览器，桥接不可进入发布包 | Vue场景发现能力必须实测，README宣传不作保证；替代为DevTools、helpers、renderer.info |
| M04 | [mcp-for-blender](https://github.com/ahujasid/mcp-for-blender)：Blender对象整理 | 未确认连接；仅选Blender路线且确有控制需求时可选 | T015在Blender／运行时／插件版本明确后单独批准；仅修改副本 | 第三方集成，非Blender官方；不代替.max源软件；不自动开启外部模型搜索、上传、AI或付费服务；人工／本地脚本可替代 |
| S05 | [Skills CLI](https://github.com/vercel-labs/skills)／find-skills | find-skills本会话可用；CLI历史缓存不作当前已安装证明 | 仅S03等确要安装时先查缓存／固定CLI版本／作用域 | PowerShell用npx.cmd；npx可能联网下载CLI，须算入安装批准；不用--all，不重复全局安装 |

所有新增工具的安装提案必须写清：**工具名、精确版本／提交、下载来源、安装目录、前置运行时、Windows兼容、成本／账号／Key、配置变更、验证步骤、无安装替代和回退范围**。秘密通过客户端安全配置或本机外部配置填写，不贴入tasks.md、终端历史、URL或截图。

批准S03后可使用以下**候选形式**；T003必须先核对所选CLI版本的help、仓库技能名和项目作用域，实际命令将CLI固定为核实版本，不照抄未固定命令：

```powershell
# 仅说明形式，当前不执行；不加 --all 或全局安装参数
npx.cmd skills add CloudAI-X/threejs-skills --agent codex --skill threejs-fundamentals threejs-geometry threejs-interaction threejs-animation threejs-loaders
```

**安装顺序：** T001只读清单 → T002具体契约 → T003生成可批准的安装／版本清单 → 用户批准所需项 → T004环境／数据库 → T005后端依赖与Wrapper → T006前端依赖与测试能力。S03/M01/M02缺失不阻塞核心代码；M03/M04只有明确使用需要才安装。T015处理模型路线；T064前检查浏览器回归工具；T069—T072前检查文档渲染条件。

## 2. 文件地图、契约与验证约定

### 2.1 路径与命名

根目录 **R**＝`D:/快速访问/Desktop/毕业项目文件/Graduation_Project`。下述文件均相对R；执行者必须展开为绝对路径，不在素材源目录写业务代码。

- **J**＝`backend/src/main/java/cn/wzbc/maintenance`；**JT**＝`backend/src/test/java/cn/wzbc/maintenance`；**JR**＝`backend/src/main/resources`；**M**＝`JR/db/migration`；**F**＝`frontend`。
- `cn.wzbc.maintenance`、类名、HTTP路径、枚举编码和端口为本计划提出的实施约定，属于详细实现，不冒称用户此前逐项确认。T002冻结这些契约；业务语义不变。必要调整同步受影响任务后再执行。
- 主配置按架构分业务，Controller／Service／Mapper／Entity／DTO／VO分别在业务包内。`plan/api-contracts.md`、`plan/data-dictionary.md`、`plan/environment.md`、`plan/dependency-lock.md`是专题证据，不复制第二套requirements／architecture。
- 优先拟定后端8080、Vite5173、独立MySQL3307；T001检测端口，T004按批准值记录，不占用／改动phpStudy。最终README给实际值。
- 基础身份CurrentUser和默认拒绝的SecurityConfig由T005定义；BusinessAccessPolicy由T019定义临时范围，T022/T025扩展正式业务投影。设备历史的只读工单前端在T027先建立，避免页面依赖尚不存在的详情组件。基础迁移顺序：T005的V001账号／会话／注册审核；T007的V002空间／设备／位置历史；T019的V003附件；T022的V004申请／提交快照／事件；T025的V005工单／分派／轮次／进度／事件；T067仅必要时新增V006性能索引。已应用脚本只新增迁移，不回改。
- 设备初始化配置与空间固定配置在`config/campus.json`；渲染清单在`F/public/campus/manifest.json`。资源文件仅允许T015中许可清单明确列出的副本路径；不使用模糊“全素材目录可改”白名单。
- 任务白名单列核心文件和对应测试；不得借路径简写修改其他业务。需要新增一个必要文件时先说明职责及前后接口，将白名单补入当前任务，不顺便重构后续任务。

B04共享增量边界（2026-10-13，按直接授权执行）：允许SecurityConfig按方法/路径/角色增量开放本批API，GlobalExceptionHandler/application.yml支持multipart和24小时清理；common/OperationResultService内嵌回执与结果查询Controller；JT/support/B04HttpTestSupport、B04BrowserHarness提供真实隔离fixture/失败精确清理，BootstrapIT增量核对V003—V005与仍默认拒绝的B05操作。前端client/common支持取消头与鉴权二进制/本批回执；auth/main接身份草稿清理，router/MainLayout/CampusView只接本批真实页面及locationId共用入口。对应职责、衔接与理由先记录于tests/evidence/B04-progress.md；不修改既有认证规则、渲染算法或引入后续业务。证据与修改前备份纳入B04-*。

T020必要测试增量：backend/src/test/resources/application-test.yml关闭测试上下文的后台定时清理，测试仍直接调用真实cleanupExpired；防止不同可控Clock上下文后台任务误碰正在运行的隔离fixture。不关闭dev清理，不改变24小时规则。

B05续接共享边界补记（2026-10-16，非前一轮修改前登记）：workorder MutationSupport/Mapper/VO/Query及Controller、common/OperationResultService、security/SecurityConfig、attachment/AttachmentService；前端api/business/workorder/attachment、useBusinessAction/useMutationConfirmation/useImageSelection、drafts、WorkOrderHistoryContent及TechnicianWorkPanel/AcceptancePanel/ReassignmentPanel、业务详情和router/MainLayout均限本批事务/权限/回执/草稿与最小导航接线。router/businessNavigation及申请/工单/设备详情只维护受控返回上下文。JT/support/B05BrowserHarness及tests/evidence/B05-audit.py仅提供隔离测试生命周期和只读核对。JT/workorder/WorkOrderLockTest及六个写入口补齐锁后必要参与者映射保护，原合法回执优先，详见账本收尾补记。详细职责、接口和测试见tests/evidence/B05-progress.md；无迁移、模型扩建或B06轮询。当前续接修复AssignmentService合格名单在计数/分页前排除待接单当前负责人，保留首次/待重派资格规则。

2026-10-18用户追加B05导航数量红标：维修端待接单＋处理中（含返工），报修端待验收；0隐藏，不作进入列表即清零的未读标记。最小共享边界为WorkOrderQueryService／Controller的本人navigation-count只读查询、api/workorder、useReviewNotifications、useBusinessAction确认成功刷新及MainLayout两个徽标；JT/workorder/WorkOrderNavigationCountIT和既有通知／布局／维修组件测试。浏览器夹具仅增加可选证据前缀，不覆盖原B05记录。最新验证见tests/evidence/B05-order-count-verification.md及B05-order-count-progress.md；旧128／143条保留为增量前时点，未启动B06。

2026-10-18用户继续追加管理员“工单管理”数量红标：全局全部未完成状态，含待重派、待验收及返工，完成后排除、0隐藏。沿用同一只读接口及生命周期，扩展现有WorkOrderQueryService/API类型、useReviewNotifications和MainLayout必要接线；保留维修／报修口径及原管理员两个徽标。备份与测试证据使用B05-admin-order-count前缀；交付待验收，不进入B06。

### 2.2 统一接口契约

ID在Java为Long，JSON与TypeScript均用十进制字符串，避免大整数精度丢失；version与round为非负／正整数，具体校验T002固定。服务端生成操作者、时间、状态、快照，客户端不传它们作为事实。

| 契约 | 约定及定义任务 |
| --- | --- |
| 通用 | T002／T005：ApiResponse<T>＝code/message/data/requestId，可附errors；PageResult<T>＝records/total/page/pageSize，默认20、最大100，page从1起。错误按架构12.2节 |
| 状态 | T002：申请PENDING_REVIEW/NEEDS_SUPPLEMENT/APPROVED/WITHDRAWN/DUPLICATE_CLOSED；工单PENDING_ACCEPT/REASSIGN_PENDING/IN_PROGRESS/PENDING_ACCEPTANCE/COMPLETED；注册PENDING/REJECTED/APPROVED；资格与启停用独立 |
| 认证 | T011：GET /api/auth/csrf→{token,headerName}；POST /api/auth/login；GET /api/auth/me；POST /api/auth/logout。会话Cookie拟名CAMPUS_SESSION，HttpOnly、SameSite=Lax、Path=/api，本机HTTP适配，HTTPS另加Secure；CSRF头X-CSRF-TOKEN，由正式安全实现验证。登录及退出后刷新CSRF，不在前端读会话Token |
| 目录 | T007：GET /api/campus/catalog→CatalogVo{configVersion,buildings,floors,locations}；位置含id/code/floorId/type/use/name/reportable/canCreateRequest，两种权限分开 |
| 写操作 | T002：MutationMeta{operationId,version?,round?}；新建operationId，同操作重试内容摘要一致才返回原结果；GET /api/operations/{operationId}?operation=...只查当前操作者已授权结果，未确认不伪装业务失败 |
| 新报修 | T022：CreateRepairRequestDto{locationId,equipmentName,equipmentCategory?,addressText,description,contactName,contactPhone,attachmentIds,operationId}；首次需1—5张故障图，不能客户端指定LEGACY_TEXT |
| 附件 | T019：POST /api/attachments/temp，用途／batchId／目标及round；GET /api/attachments/{id}/content；DELETE /api/attachments/{id}/temp；响应为AttachmentVo，无磁盘路径。bindTemporary(ids,AttachmentTarget,CurrentUser)在业务事务执行 |
| 审核派单 | T028：POST /api/repair-requests/{id}/approve-and-assign，DTO含equipmentId/technicianId/version/operationId、必要的曾拒单确认；申请、工单、分派、事件一次提交 |
| 工单变更 | T030—T033：/api/work-orders/{id}/accept、refuse、assign、progress、submit-result、acceptance；具体DTO携带操作标识及适用version／round，处理方来自认证上下文 |
| 只读详情 | T022／T025：RepairRequestDetailVo、WorkOrderDetailVo；含allowedActions及当前version／round；后端按关系投影历史，不返回整表Entity。跨申请／工单／设备／人员的链接依照允许范围 |
| 空间提示 | T037：GET /api/campus/summaries、/api/campus/locations/{id}/items；LocationSummaryVo{locationId,category,counts,visibleTotal,queriedAt}，counts含待接单／待重派／普通处理中／返工／待验收／待补充／待审核 |
| 推荐 | T047：GET /api/work-orders/assignment-candidates?context=...&requestId=...或workOrderId=...；同快照推荐前5及完整合格名单分页，AssignmentCandidateVo含W/I/R/A/load/todayCompleted/lastAssignedAt/refusal/eligible/reason；queriedAt、businessDate明确 |
| 草稿 | T024：draftStore按userId＋locationId或requestId保存；T034—T039扩展业务ID＋操作＋round。api client的read与mutation区分，未知写入结果保留operationId |
| 前端渲染 | T016：CampusRenderer.mount(container,manifest)、selectTarget(target,generation)、enterLocation(id,generation)、returnToFloor()、resetOverview()、dispose()；几何对象不放Pinia。T038使用projectAnchor(locationId)绘制DOM标记 |
| 维护命令 | T008／T009／T055／T056：campus-init、admin-init、admin-reset、correct-location-name；仅本地显式命令，无公开HTTP接口，正常启动不调用 |

T002须把上述VO与DTO字段、空值、错误code、幂等作用域、状态允许表和锁顺序全部写进契约文档，后续任务引用已冻结名字。详细命名调整只能改实现名称，不能增加角色／状态／报修范围。

### 2.3 验证命令与证据

以下都是**未来任务的运行方式**，本轮没有工程可执行：

```powershell
# 后端：工作目录 R/backend；T005配置test-local及JUnit包含*Test与*IT
.\mvnw.cmd -Ptest-local '-Dtest=具体测试类' test
.\mvnw.cmd -Ptest-local verify
.\mvnw.cmd dependency:tree

# 前端：工作目录 R/frontend；T006定义脚本
npm.cmd ci
npm.cmd run typecheck
npm.cmd run build
npm.cmd run test -- src/具体测试文件.test.ts
# T064在批准工具就绪后定义test:e2e，并使用实际后端／测试库
npm.cmd run test:e2e
```

- 每项“AI验证方式”中的BT(X)指backend目录第一条命令将具体测试类替换为X；FT(path)指frontend目录的run test；FV指typecheck＋build各退出0。目标测试通过后不无故重复全套，新增跨模块变化时才执行相关回归。
- 任务中的固定测试名与代码断言使用该测试描述的隔离数据；断言变量为测试本地结果，不是新增业务字段。后端关键规则按“写命名测试及确定断言→运行确认失败原因是目标功能→最小实现→同命令测试成功”执行。文档、安装和低风险布局任务用读取／构建／浏览器证据，不写照抄实现的无意义测试。
- 集成测试必须使用真实MySQL8.4、受限测试账号和独立附件目录；测试启动先验证数据库名称、账号权限、profile、目录。清理只限已经确认的测试库／目录，禁止回退dev／demo库，禁止flyway clean。
- MockMvc模拟身份只证明局部权限；Cookie／CSRF最终使用真实HTTP和浏览器登录。可控Clock测试8小时及上海日／月边界，不实际等待。
- `tests/evidence/Txxx/`保存该任务命令退出码、测试报告、接口requestId、脱敏数据库／文件核对及浏览器截图／操作；`plan/tasks.md`只填证据索引、结论和用户反馈。未跑的检查写原因。
- 每项保留技术状态和实际验证记录；整批统一报告**Changed/Verified/请我测试/Remaining Risk**，并按[实施批次计划](implementation-batches.md)等待批次用户验收。
- 论文日期按任务书2026.12.28—2027.02.28初稿／第一次查重阶段；开发未估算承诺日程，不编造工期或以材料日期跳过前置验收。

## 3. 阶段与任务索引

| 阶段 | 任务 | 可独立审阅的阶段成果 |
| --- | --- | --- |
| A 基础与账号 | T001—T014 | 工具和环境清单、契约、可构建工程、固定目录初始化、注册登录与资格审核 |
| B 样板与申请基础 | T015—T029 | 文博1外观→单房间→首层；附件、草稿、台账、新申请及管理员首次派单 |
| C 维修闭环与早期空间联调 | T030—T040 | 拒单／重派／进度／验收、按权限汇总、同步、首层真实三角色闭环 |
| D 完整校园与推荐 | T041—T048 | 样板全楼→三宿舍→三教学楼→三食堂→体育馆→10栋总览；规则推荐及人工选择 |
| E 完整管理与统计 | T049—T059 | 资料密码、人员分类历史、旧记录和移动兼容、待办工作台、真实统计 |
| F 综合验收 | T060—T067 | 并发、安全、加载释放、可访问性、浏览器回归及前后端性能实测 |
| G 软件交付 | T068—T069 | 本机统一入口、配置复现、数据设计／操作／测试／许可材料 |
| H 论文与归档 | T070—T072 | 需求设计稿、实现测试稿、证据核对与完整归档 |

五步几何链固定：T015素材条件→T016单栋外观→T017单房间→T018完整首层→T040真实业务联调→T041其余楼层→T042其他宿舍；教学、食堂、体育馆各按确认模板独立验收。T028派单依赖空间目录、台账、附件、资格及工单模型，不能只在原业务末尾添加3D任务。阶段表是功能索引；实际执行范围由用户授权的B批次确定，批内按依赖连续完成。

## 4. 任务明细

每项有14个字段。任务内标注R§为requirements章节、A§为architecture章节；验收编号REQ-xx来自R§27.1。状态和实际验证记录必须在真正执行后更新。 单项前置的同批/跨批验收边界按[实施批次计划](implementation-batches.md)解释，任务状态和证据继续逐项保留。

<a id="t001"></a>

### T001：只读核对环境、素材目录和工具状态

1. **编号：** T001
2. **目标：** R§0、20、26；A§2、14—15：形成真实缺口清单。
3. **前置任务：** 无；在B01授权范围内执行。
4. **本次范围：** 核对JDK/Maven、Node、MySQL实例与端口、WebGL2、技能/MCP和素材位置；只读。
5. **明确不做：** 安装、全局配置、建库、转换模型、建工程。
6. **允许修改文件：** plan/environment.md；plan/tasks.md的本项记录。
7. **接口与衔接：** 输出环境路径、版本、可调用状态、未确认项和缺口；交给T003/T004/T015。
8. **实施步骤：**

   - [x] 步骤1：运行java/javac/mvn/node/npm版本命令并记录真实路径，核对Maven所用JDK。
   - [x] 步骤2：检查独立8.4安装/实例及拟用端口，不把PATH客户端当服务端。
   - [x] 步骤3：检查浏览器WebGL2、素材格式及现有skills/MCP可调用性，生成缺口表。

9. **验收标准：** 明确现有、未发现、未连接、未验证四类；有可批准的下一步清单。
10. **AI验证方式：** 只读输出与文件逐项比对；不得把版本查询写成构建通过。
11. **我的手测步骤：** 打开environment.md核对路径；确认未改phpStudy和全局环境。
12. **所需配置或外部依赖：** 已有Shell/浏览器；E01—E10只核对，无安装。
13. **状态：** 已通过所属B01的用户验收（2026-10-12）；内部验证与证据保留。
14. **实际验证记录：** 2026-10-12；证据[environment.md](environment.md) ENV-01—ENV-07。版本命令退出0；JDK21.0.11可复用但Maven用17.0.5，Node24.11.0/npm11.20.0可用；常见安装/服务/进程范围未发现独立8.4；3307/8080/5173无监听；两素材目录42/179个文件，未转换；内置浏览器WebGL2检测页有实际画面，独立Chrome/Edge硬件加速未测。Git退出128且OCR预览因无仓库失败，未建仓或配置模型。文档静态核对退出0，输出T001_ENVIRONMENT_DOCUMENT_CHECKS_PASSED；requirements/architecture/batches原哈希未变。未执行构建、数据库连接或业务测试；用户尚未验收。 后续用户验收记录：2026-10-12用户直接反馈“B01已经验证完成”，所属B01已通过用户验收。

---

<a id="t002"></a>

### T002：冻结数据字典、状态与接口衔接

1. **编号：** T002
2. **目标：** R§1—28；A§8—13、16.2：补齐详细契约，保持业务语义。
3. **前置任务：** T001。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 表字段、枚举、DTO/VO、API、幂等作用域、错误code、锁顺序及配置版本。
5. **明确不做：** DDL执行、工程创建或新业务状态。
6. **允许修改文件：** plan/data-dictionary.md；plan/api-contracts.md；plan/tasks.md接口约定。
7. **接口与衔接：** 冻结本计划§2.2；锁顺序按用户ID升序→设备ID→申请→工单→轮次→附件ID升序，所有相关路径同序；只涉及子集时不逆序。
8. **实施步骤：**

   - [x] 步骤1：列全部表、外键/唯一/生成列、UTC字段及STRUCTURED/LEGACY_TEXT服务端兼容规则。
   - [x] 步骤2：列每个接口完整请求/响应、空值、权限、allowedActions和提交标识摘要/重试规则。
   - [x] 步骤3：画出事务及锁顺序，静态检查后续任务引用的DTO/VO/字段均有定义。

9. **验收标准：** 账号2—30字符、密码8—32、手机号规则一致；5 MB固定5,000,000字节并测试上限/+1；ID字符串/UTC/分页明确；R§27的20组用例均有支撑。
10. **AI验证方式：** 逐章覆盖及类型/方法名检查；精确DDL仅为待执行设计，不声明MySQL验证。
11. **我的手测步骤：** 按一条正常闭环和一条旧记录流程阅读接口，核对没有第二套状态。
12. **所需配置或外部依赖：** 现有writing-plans；官方资料或可用M01；不存在也用官方文档。
13. **状态：** 已通过所属B01的用户验收（2026-10-12）；内部验证与证据保留。
14. **实际验证记录：** 2026-10-12；[data-dictionary.md](data-dictionary.md)及[api-contracts.md](api-contracts.md)。Python3.11.8标准库从stdin检查类型定义/引用、HTTP路由重复、FK目标、幂等承载字段、REQ覆盖及tasks全部DTO/VO引用；首次跨任务检查退出1，发现10个原任务文件名尚未定义，按原白名单统一命名后同检查退出0，最终输出T002_CONTRACT_CHECKS_PASSED（142类型、52路由、21表、38个跨任务DTO/VO引用、20组REQ）。事务/锁顺序和MySQL非延迟FK构造已静态核对；未执行DDL/数据库/权限或业务测试。实施选择：账号英文大小写不区分及非既定文本上限由助手定稿，成本是后续调整须同步迁移/接口；queriedAt统一示意字段名。用户尚未验收。 后续用户验收记录：2026-10-12用户直接反馈“B01已经验证完成”，所属B01已通过用户验收。

---

<a id="t003"></a>

### T003：制定可批准的安装与版本锁定清单

1. **编号：** T003
2. **目标：** R§0.2、26.3；A§2：把安装范围具体化。
3. **前置任务：** T001、T002。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 固定Wrapper、Three.js、TS/vue-tsc、测试依赖；核对BOM、peer/engines；列必需与可选安装。
5. **明确不做：** 实际安装、自动更换已选基线、密钥记录。
6. **允许修改文件：** plan/dependency-lock.md；plan/environment.md；plan/tasks.md工具清单。
7. **接口与衔接：** 输出E/P/S/M逐项版本、来源、目录、影响、成本、批准状态；T004—T006只消费获批项。
8. **实施步骤：**

   - [x] 步骤1：读取精确包元数据和Boot BOM，记录所选可获取版本及冲突。
   - [x] 步骤2：对每个缺项列具体安装/配置/验证命令；新MCP先读源文档与权限，不抄其他客户端配置。
   - [x] 步骤3：保存安装提案供用户批准；可选工具拒绝/跳过时写替代和验收办法。

9. **验收标准：** 不得出现浮动latest/SNAPSHOT；所有拟安装项可审阅，工具不混入业务运行链。
10. **AI验证方式：** 比较元数据、下载URL、版本校验和技能名称；只读查询成功不等于安装成功。
11. **我的手测步骤：** 核对必须安装清单、可选清单和磁盘位置，再批准所需项。
12. **所需配置或外部依赖：** 网络读取官方源；npx执行可能下载，未批准不运行安装。
13. **状态：** 已通过所属B01的用户验收（2026-10-12）；内部验证与证据保留。
14. **实际验证记录：** 2026-10-12；[dependency-lock.md](dependency-lock.md)为完整E/P/S/M提案。Invoke-RestMethod读取精确registry版本并区分engines/peer/peerDependenciesMeta；Boot4.1.1 BOM和MP3.5.17 POM核对；必要starter/POM及MySQL8.4.11 ZIP HEAD200。逐项复核13个拟装npm包版本/SRI退出0，输出T003_EXACT_PACKAGE_METADATA_AND_INTEGRITY_PASSED；Maven ZIP在内存核对厂商SHA512后计算Wrapper SHA256，未写盘/安装；MySQL MD5仅读官方校验文件。静态文档检查退出0（4个文档、类型围栏/相对链接/基线哈希/未建工程）。Pinia必需peer补@vue/devtools-api8.1.5；Node不升级，TS5.9.3/vue-tsc3.3.13/Three0.186.1/Vitest5.0.3固定。可选MCP/skills/DOM测试工具跳过并写替代；OCR预览因无Git仓库未能模型评审。所有实际构建/安装/数据库验证未执行；用户批准/验收待记录。 后续用户验收记录：2026-10-12用户直接反馈“B01已经验证完成”，所属B01已通过用户验收。

---

<a id="t004"></a>

### T004：配置JDK21、独立MySQL与隔离运行环境

1. **编号：** T004
2. **目标：** R§0.2；A§14—15：解决环境缺口。
3. **前置任务：** T003。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 仅获批环境项；项目JDK脚本；独立MySQL8.4，dev/demo/test库、受限账号、数据和附件目录。
5. **明确不做：** 迁移业务表、修改phpStudy、清未知库、记录凭据。
6. **允许修改文件：** scripts/use-jdk.ps1；config/local.example.yml；.gitignore；plan/environment.md；批准清单指定的外部配置路径。
7. **接口与衔接：** 提供实际DB端口/库名/配置加载路径；测试身份不能操作dev/demo；后续BT从明确test配置启动。
8. **实施步骤：**

   - [x] 步骤1：在已批准目录安装/复用独立8.4及条件运行库，项目级固定JDK21。
   - [x] 步骤2：由维护者显式创建专用库与受限账号，外部配置填秘密，区分附件目录。
   - [x] 步骤3：验证SELECT VERSION()/DATABASE()/CURRENT_USER()及权限、端口；不执行危险越权DDL试探。

9. **验收标准：** JDK和实际服务端版本符合基线；测试范围可验证，重启配置不改phpStudy。
10. **AI验证方式：** 命令退出码、数据库只读查询与SHOW GRANTS脱敏；确认目录绝对路径。
11. **我的手测步骤：** 按environment.md连接三个专用库，检查其他实例仍正常。
12. **所需配置或外部依赖：** 仅T003中已获批准E01/E03/E04及系统安装权限；未授权环境变更则等待。
13. **状态：** 已通过所属B01的用户验收（2026-10-12）；内部验证与证据保留。
14. **实际验证记录：** 2026-10-12；MySQL8.4.11官方下载/MD5/Oracle签名有效，回环3307进程启动。三个库分别以campus_dev/demo/test连接，VERSION/DATABASE/CURRENT_USER/time_zone及SHOW GRANTS退出0，账号仅各自库8项批准权限；见[环境实证](../tests/evidence/T004/environment-result.json)。项目脚本跳过原mavenrc_pre.cmd覆盖，Maven实际JDK21.0.11；MySQL中文路径兼容采用受限配置目录junction，实际数据/程序仍在R，见[执行裁定](../tests/evidence/B01-progress.md)。无服务注册/phpStudy/全局变量更改，无越权DDL或危险清理。 后续用户验收记录：2026-10-12用户直接反馈“B01已经验证完成”，所属B01已通过用户验收。

---

<a id="t005"></a>

### T005：建立后端、统一响应和真实数据库测试底座

1. **编号：** T005
2. **目标：** A§2—4、8、12、15；REQ-01基础。
3. **前置任务：** T002、T004。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** Boot/MP/Wrapper、异常、Clock、测试库守卫和V001账号结构。
5. **明确不做：** 注册登录业务、全部后续业务表。
6. **允许修改文件：** backend/pom.xml；backend/mvnw、mvnw.cmd、.mvn/wrapper/maven-wrapper.properties；J/Application.java；J/common/ApiResponse.java、PageResult.java、GlobalExceptionHandler.java、BusinessException.java；J/config/ClockConfig.java、TestDatabaseGuard.java；J/security/CurrentUser.java、SecurityConfig.java、SecurityErrorHandler.java；JR/application.yml；M/V001__account_session_review.sql；backend/src/test/resources/application-test.yml；JT/common/BootstrapIT.java、MutableClock.java；README.md。
7. **接口与衔接：** ApiResponse/PageResult、Clock、test-local；Surefire包含*Test/*IT，隔离数据库和目录验证失败即停止。CurrentUser基础类型在此定义；初始SecurityConfig无默认生成用户、默认拒绝业务，T011接真实会话过滤器。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `BootstrapIT.target_jdk_is_21`，断言 `assertEquals(21, Runtime.version().feature())`。写BootstrapIT：assertEquals(21,targetJava)；错误库名/目录守卫必须拒绝。
   - [x] 步骤2：建立项目Wrapper和pom、UTC/配置加载、响应与Security错误协同入口。
   - [x] 步骤3：运行V001迁移；验证重复启动不重复建表、迁移失败停止、secret未进仓库。

9. **验收标准：** Wrapper使用21；真实MySQL迁移与守卫可运行，api错误含requestId且不含内部路径。
10. **AI验证方式：** BT(BootstrapIT)、dependency:tree及后端verify；记录迁移历史。
11. **我的手测步骤：** 按README启动后端；核对实际JDK及安全错误JSON。
12. **所需配置或外部依赖：** 获批P01/E02；首次Wrapper下载明确计入批准；test-local外部配置。
13. **状态：** 已通过所属B01的用户验收（2026-10-12）；内部验证与证据保留。
14. **实际验证记录：** 2026-10-12；Wrapper3.3.4/Maven3.9.15/SHA256，实际JDK21.0.11。守卫RED 5项中3项失败，GREEN5/5；初始BootstrapIT13/13。独立审阅修复VALIDATION_FAILED/未知JSON字段拒绝/测试目录接管，对应3项RED失败后最终15/15及-Ptest-local verify退出0，jar已打包并重启。实际MySQL8.4.11/V001四表、重复启动无新迁移、错误配置拒绝、无DDL失败SQL停止启动且仅清自身test失败标记、真实HTTP401/403、默认拒绝、无默认生成账号/Servlet会话均通过。依赖树版本一致，无Boot4.0混用；见[BootstrapIT](../tests/evidence/T005/BootstrapIT.xml)、[最终构建](../tests/evidence/T005/final-verify.log)、[依赖树](../tests/evidence/T005/dependency-tree.txt)、[审阅修复](../tests/evidence/B01-code-review.md)。Windows打包前须停止当前JAR进程，已记录README。B01已通过用户验收。 后续用户验收记录：2026-10-12用户直接反馈“B01已经验证完成”，所属B01已通过用户验收。

---

<a id="t006"></a>

### T006：建立Vue TypeScript工程与公共请求能力

1. **编号：** T006
2. **目标：** R§10、19；A§2、5、12：前端基础。
3. **前置任务：** T002、T003、T004、T005。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 精确依赖、路由骨架、Pinia、API类型、read/mutation错误区分和测试脚本。
5. **明确不做：** 业务页面、硬编码身份/成功反馈、复制第二套3D应用。
6. **允许修改文件：** F/package.json、package-lock.json、vite.config.ts、tsconfig.json、tsconfig.app.json、index.html；F/src/main.ts、App.vue、api/client.ts、api/common.ts、router/index.ts、stores/auth.ts、layouts/MainLayout.vue；F/src/api/client.test.ts；F/vitest.config.ts；README.md。
7. **接口与衔接：** apiRead<T>/apiMutation<T>返回统一类型；401、403、409、网络异常分别处理；写超时返回结果待确认，身份Token永不进入Pinia。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `client.test.ts: permission_error_keeps_identity`，断言 `expect(authenticatedAfter403).toBe(true)`。写client.test.ts：expect(403后身份保留).toBe(true)，写超时expect(result.kind).toBe('UNKNOWN')。
   - [x] 步骤2：安装获批P02—P04/P03，锁文件、Vite /api代理和项目脚本。
   - [x] 步骤3：实现公共请求与布局骨架，窄屏有列表入口，后续页面接真实认证。

9. **验收标准：** 严格TS通过；网络异常不被当过期，写操作不按读请求自动重试；没有localStorage Token。
10. **AI验证方式：** npm ci、FV、FT(src/api/client.test.ts)；浏览器代理/刷新检查。
11. **我的手测步骤：** 打开开发页，核对基础布局及真实API错误反馈。
12. **所需配置或外部依赖：** 已获批前端与测试依赖；Three精确版本来自T003。
13. **状态：** 已通过所属B01的用户验收（2026-10-12）；内部验证与证据保留。
14. **实际验证记录：** 2026-10-12；A04精确13直接版本已安装/package-lock锁定。空客户端RED14项中12失败，实现后GREEN14/14，整批修复后再次14/14；npm ci/typecheck/build/npm ls退出0。401/403/409、网络读取失败、写超时/5xx/非法成功UNKNOWN、CSRF和大字符串ID/multipart均验证，无自动写重试/Token存储。浏览器实际/api代理401、requestId重读变化、/list与刷新、390×844窄屏入口/clientWidth=scrollWidth=375无溢出通过，默认视口已恢复，warn/error日志空；最终后端重启后刷新验证仍通过。见[最终测试](../tests/evidence/T006/client-final.log)、[类型](../tests/evidence/T006/typecheck.log)、[构建](../tests/evidence/T006/build.log)、[真实代理](../tests/evidence/T006/http-proxy.json)、[截图](../tests/evidence/T006/desktop.png)。账号/业务/3D留对应批次；B01已通过用户验收。 后续用户验收记录：2026-10-12用户直接反馈“B01已经验证完成”，所属B01已通过用户验收。

---

<a id="t007"></a>

### T007：建立空间和设备结构与目录接口

1. **编号：** T007
2. **目标：** R§21、24；A§8、17；REQ-03/06。
3. **前置任务：** T002、T005。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** V002建筑/楼层/位置/设备/名称及移动历史；目录查询。
5. **明确不做：** 制作模型、自动初始化、任意空间编辑后台。
6. **允许修改文件：** M/V002__campus_equipment.sql；J/campus/controller/CampusCatalogController.java、service/CampusCatalogService.java、mapper/CampusMapper.java、vo/CatalogVo.java；J/equipment/entity/EquipmentEntity.java、mapper/EquipmentMapper.java；JT/campus/CampusCatalogIT.java。J/campus/entity/CampusBuildingEntity.java、CampusFloorEntity.java、CampusLocationEntity.java。本批必要增量白名单：JT/common/BootstrapIT.java（只增量调整V002迁移/表清单期望）。
7. **接口与衔接：** GET /api/campus/catalog；CatalogVo定义§2.2；身份/几何权限和canCreateRequest分开；T008填配置。
8. **实施步骤：**

   - [x] 步骤1：写CampusCatalogIT：同名位置ID不等、floorId显式；reportable=false不等于不能观察。
   - [x] 步骤2：迁移结构与约束，完成按启用账号授权的目录读取。
   - [x] 步骤3：验证无数据时返回真空目录，不用硬编码配置伪装DB。

9. **验收标准：** 关联与唯一约束有效；ID字符串，房号只作标签。
10. **AI验证方式：** BT(CampusCatalogIT)；真实迁移和无越权字段检查。
11. **我的手测步骤：** 核对同房号可区分，维修资格不足账号仍能观察目录。
12. **所需配置或外部依赖：** 真实test库；认证接通前仅局部接口测试，T012后补真实登录。
13. **状态：** 已通过用户验收（B02，2026-10-13）。
14. **实际验证记录：** 2026-10-12，Asia/Shanghai；先失败测试再实现。CampusCatalogIT 3/3；真实V002迁移、FK/唯一、字符串ID、空目录与观察/报修权限；dev Cookie目录及浏览器补证通过。最终`mvnw.cmd -Ptest-local verify`退出0、常规42/42；`npm.cmd run test`退出0、33/33，typecheck/build退出0。命令、RED/GREEN、实际运行及限制见[B02执行记录](../tests/evidence/B02-progress.md)、[核验汇总](../tests/evidence/B02-verification.json)、[浏览器](../tests/evidence/B02-browser.json)；用户2026-10-13明确确认“B02验收通过”，见[B02验收记录](../tests/evidence/B02-acceptance.md)。

---

<a id="t008"></a>

### T008：固定校园配置与幂等本地初始化

1. **编号：** T008
2. **目标：** R§21；A§14.5、17；REQ-03—05。
3. **前置任务：** T007。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 完整10/41/296/257配置与1817初始台账，显式维护命令。
5. **明确不做：** 启动自动重建、公开初始化、覆盖已维护资料、将物理部件各自建账。
6. **允许修改文件：** config/campus.json；J/maintenance/CampusInitCommand.java、MaintenanceRunner.java、LocalMaintenanceCommand.java；J/campus/service/CampusInitializationService.java；JT/campus/CampusInitializationIT.java；scripts/campus-init.ps1；plan/campus-inventory.md。
7. **接口与衔接：** campus-init按稳定空间code与equipment初始化键导入；配置版本供API和渲染manifest核对。LocalMaintenanceCommand.name()/execute()与MaintenanceRunner在此定义；maintenance.command显式选择、禁用Web，普通启动不调用。T009/T055/T056仅扩展各自命令。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `CampusInitializationIT.initial_counts_are_exact`，断言 `assertEquals(1817, equipmentCount)`。写初始化测试：assertEquals(10/41/296/257/1817,各计数)，分项1392/402/18/5。
   - [x] 步骤2：逐位置列首层/标准层目录和范围内设施种类，导入同事务并保存初始快照。
   - [x] 步骤3：再次执行：assertEquals(1817,count)，修改过名称/启停用不得被覆盖；不兼容报告冲突。

9. **验收标准：** 174/57/15/9/2可报修分项、教学72空调；公共空间0台账；重复执行无新业务身份。
10. **AI验证方式：** BT(CampusInitializationIT)，逐项配置与DB对照，不仅总数。
11. **我的手测步骤：** 显式执行一次初始化，再执行一次检查跳过；看文博1首层10、标准层12。
12. **所需配置或外部依赖：** 实际dev/demo配置显式选择；演示数据标识；无GUI后台。
13. **状态：** 已通过用户验收（B02，2026-10-13）。
14. **实际验证记录：** 2026-10-12，Asia/Shanghai；先失败测试再实现。CampusInitializationIT 3/3；逐位置配置/DB核对10/41/296/257/1817及1392/402/18/5、72教学空调；dev首次changed=true、重复false，保留维护后字段。最终`mvnw.cmd -Ptest-local verify`退出0、常规42/42；`npm.cmd run test`退出0、33/33，typecheck/build退出0。命令、RED/GREEN、实际运行及限制见[B02执行记录](../tests/evidence/B02-progress.md)、[核验汇总](../tests/evidence/B02-verification.json)、[浏览器](../tests/evidence/B02-browser.json)；用户2026-10-13明确确认“B02验收通过”，见[B02验收记录](../tests/evidence/B02-acceptance.md)。

---

<a id="t009"></a>

### T009：预置管理员本地幂等初始化

1. **编号：** T009
2. **目标：** R§4；A§14.4；REQ-01。
3. **前置任务：** T005、T008。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 本地admin-init、密码随机生成与安全哈希。
5. **明确不做：** HTTP初始化、默认明文密码、重复执行覆盖管理员。
6. **允许修改文件：** J/maintenance/MaintenanceRunner.java、AdminInitCommand.java；J/account/entity/UserEntity.java、mapper/UserMapper.java；J/account/service/AdminInitializationService.java；J/security/PasswordSupport.java；JT/account/AdminInitializationIT.java；scripts/admin-init.ps1；README.md。
7. **接口与衔接：** PasswordSupport.randomValidPassword()/hash()/matches()供T010/49/52/55；初始密码只在本地成功操作展示一次。MaintenanceRunner显式maintenance.command选择，禁用Web启动；普通启动不执行维护命令。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `AdminInitializationIT.second_init_preserves_identity`，断言 `assertEquals(firstUserId, secondUserId); assertEquals(firstHash, secondHash)`。写AdminInitializationIT：重复初始化用户ID及哈希不变；随机密码满足8—32位、字母和数字。
   - [x] 步骤2：以本地命令事务保存管理员及哈希，成功后一次性显示。
   - [x] 步骤3：验证正常启动不初始化；日志/SQL/README无明文默认密码。

9. **验收标准：** 保留原身份；没有公开管理员注册或初始化入口。
10. **AI验证方式：** BT(AdminInitializationIT)，DB重查与日志脱敏检查。
11. **我的手测步骤：** 执行初始化并用一次性密码登录（待T011/T012）；再执行核对不覆盖。
12. **所需配置或外部依赖：** 维护者本地权限、明确目标库；生成密码不进入截图/证据。
13. **状态：** 已通过用户验收（B02，2026-10-13）。
14. **实际验证记录：** 2026-10-12，Asia/Shanghai；先失败测试再实现。AdminInitializationIT 4/4；重复ID/哈希不变、随机合规密码、32码点Unicode、non-Web回归通过；dev首次创建、重复跳过且不再输出密码。初始密码仅项目外DPAPI维护记录。最终`mvnw.cmd -Ptest-local verify`退出0、常规42/42；`npm.cmd run test`退出0、33/33，typecheck/build退出0。命令、RED/GREEN、实际运行及限制见[B02执行记录](../tests/evidence/B02-progress.md)、[核验汇总](../tests/evidence/B02-verification.json)、[浏览器](../tests/evidence/B02-browser.json)；用户2026-10-13明确确认“B02验收通过”，见[B02验收记录](../tests/evidence/B02-acceptance.md)。

---

<a id="t010"></a>

### T010：公开注册与全局唯一性

1. **编号：** T010
2. **目标：** R§1—3、10；A§7—8、10；REQ-01。
3. **前置任务：** T008、T009。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 报修用户和维修人员注册；维修申请快照；唯一约束和参数校验。
5. **明确不做：** 自动登录、管理员代建账号/角色切换、短信。
6. **允许修改文件：** J/account/controller/RegistrationController.java、service/RegistrationService.java、dto/RegisterDto.java、vo/RegistrationVo.java、mapper/RegistrationApplicationMapper.java、entity/RegistrationApplicationEntity.java；JT/account/RegistrationIT.java。
7. **接口与衔接：** POST /api/auth/register→RegistrationVo；REPORTER/TECHNICIAN固定，维修审核PENDING。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `RegistrationIT.duplicate_phone_is_rejected`，断言 `assertEquals(409, duplicatePhoneResponse.statusCode())`。写RegistrationIT：账号2/30边界、密码8/32、重复手机/账号409，管理员角色注册拒绝。
   - [x] 步骤2：实现保存用户及首次申请快照事务，确认密码不存库。
   - [x] 步骤3：并发注册同手机号仅一成功；失败不留孤立用户/审核申请。

9. **验收标准：** 手机号大陆11位仅格式，不宣称归属验证；注册成功不发登录Cookie。
10. **AI验证方式：** BT(RegistrationIT)；真实DB哈希和全局唯一性重查。
11. **我的手测步骤：** 分别注册两角色，测试重复手机号、未选角色和前往登录。
12. **所需配置或外部依赖：** P01已安装；账号及姓名用演示数据。
13. **状态：** 已通过用户验收（B02，2026-10-13）。
14. **实际验证记录：** 2026-10-12，Asia/Shanghai；先失败测试再实现。RegistrationIT 5/5；参数边界、跨角色/大小写/停用全局唯一、并发同手机号恰好一成功、审核快照事务、不自动登录；真实浏览器公开注册通过。最终`mvnw.cmd -Ptest-local verify`退出0、常规42/42；`npm.cmd run test`退出0、33/33，typecheck/build退出0。命令、RED/GREEN、实际运行及限制见[B02执行记录](../tests/evidence/B02-progress.md)、[核验汇总](../tests/evidence/B02-verification.json)、[浏览器](../tests/evidence/B02-browser.json)；用户2026-10-13明确确认“B02验收通过”，见[B02验收记录](../tests/evidence/B02-acceptance.md)。

---

<a id="t011"></a>

### T011：Cookie会话、CSRF和认证权限入口

1. **编号：** T011
2. **目标：** R§3、8、13；A§6—7；REQ-01/11。
3. **前置任务：** T010。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 随机Token摘要、8小时固定到期、Cookie/CSRF、身份上下文及统一安全错误。
5. **明确不做：** JWT、Token Web Storage、仅靠SameSite。
6. **允许修改文件：** J/security/SecurityConfig.java、SessionAuthenticationFilter.java、CurrentUser.java、SecurityErrorHandler.java；J/account/controller/AuthController.java、service/SessionService.java、mapper/SessionMapper.java、entity/SessionEntity.java、dto/LoginDto.java、vo/CurrentUserVo.java；JT/security/SessionSecurityIT.java。本批必要增量白名单：J/account/vo/AuthSessionVo.java；JT/support/B02HttpTestSupport.java（真实HTTP账号登记与精确清理）。
7. **接口与衔接：** §2.2认证API与Cookie/头名；CurrentUser由后端生成；revokeCurrent()/revokeAll(userId)供管理用例。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `SessionSecurityIT.fixed_eight_hour_expiry`，断言 `assertEquals(401, meAtEightHours.statusCode()); assertEquals(beforeExpiry, expiryAfterMe)`。写真实HTTP测试：8小时边界401；刷新me不延expiresAt；缺/错CSRF返回403。
   - [x] 步骤2：完成会话摘要校验、账号启用复核、默认拒绝及登录/退出后CSRF刷新。登录创建会话与改密/停用共同锁用户并再次核对哈希/状态。
   - [x] 步骤3：用可控Clock和真实Cookie验证退出仅撤当前会话，403保留有效身份。

9. **验收标准：** 认证/业务权限分开；明文Token不在库和日志；HTTP Cookie可用，Secure策略与实际协议一致。
10. **AI验证方式：** BT(SessionSecurityIT)真实HTTP；模拟身份测试不替代这项。
11. **我的手测步骤：** 浏览器登录、刷新、退出；不同配置分别登录不同角色。
12. **所需配置或外部依赖：** 本机同源Vite代理；Clock可控；P01 Security已由Boot管理。
13. **状态：** 已通过用户验收（B02，2026-10-13）。
14. **实际验证记录：** 2026-10-12，Asia/Shanghai；先失败测试再实现。SessionSecurityIT 7/7真实HTTP；8小时精确边界、不续期、摘要/Cookie、CSRF轮换、退出当前/全会话撤销、停用恢复不复活；dev 5173代理登录/me/目录/缺CSRF/退出已重查。密码变更仅同事务撤销支持测试，HTTP在B09。最终`mvnw.cmd -Ptest-local verify`退出0、常规42/42；`npm.cmd run test`退出0、33/33，typecheck/build退出0。命令、RED/GREEN、实际运行及限制见[B02执行记录](../tests/evidence/B02-progress.md)、[核验汇总](../tests/evidence/B02-verification.json)、[浏览器](../tests/evidence/B02-browser.json)；用户2026-10-13明确确认“B02验收通过”，见[B02验收记录](../tests/evidence/B02-acceptance.md)。

---

<a id="t012"></a>

### T012：注册登录页、角色导航和会话恢复

1. **编号：** T012
2. **目标：** R§10、19、22；A§5—6；REQ-01/19。
3. **前置任务：** T006、T011。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 居中登录注册、审核资格入口、所有启用账号校园默认入口及恢复门禁。
5. **明确不做：** 未审核人员维修工作台、注册自动登录、网络失败清空有效会话。
6. **允许修改文件：** F/src/views/account/LoginView.vue、RegisterView.vue；F/src/router/index.ts、stores/auth.ts、api/account.ts；F/src/views/campus/CampusView.vue；F/src/stores/auth.test.ts。本批必要增量白名单：F/src/layouts/MainLayout.vue、src/App.vue、src/api/common.ts、src/api/client.ts；F/src/views/account/LoginView.test.ts、vitest.config.ts（复用已有Vue编译插件作页面回归）；F/vite.config.ts、JT/support/B02BrowserHarness.java（隔离取证入口，非生产）。
7. **接口与衔接：** consume AuthController；后续校园视图在现有CampusView增量接入；审核不合格者导航保留审核页。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `auth.test.ts: network_failure_does_not_grant_business_access`，断言 `expect(businessRouteGranted).toBe(false)`。写auth.test.ts：网络恢复失败不放行业务且能重试；401清理，403保留身份。
   - [x] 步骤2：接真实注册/登录/CSRF，注册结果带账号、密码留空。
   - [x] 步骤3：浏览器测试三角色、待审核人员、停用提示及窄屏表单。

9. **验收标准：** 登录页不选角色；现阶段校园显示目录和明确3D未接入，不能假画面宣称完成。
10. **AI验证方式：** FV、FT(src/stores/auth.test.ts)，真实Cookie浏览器流程。
11. **我的手测步骤：** 注册→前往登录→刷新→退出；检查密码帮助和角色菜单。
12. **所需配置或外部依赖：** 现有浏览器工具；无需新MCP。
13. **状态：** 已通过用户验收（B02，2026-10-13）。
14. **实际验证记录：** 2026-10-12，Asia/Shanghai；先失败测试再实现。auth 12项、LoginView页面1项、B01 client14项通过；三角色/刷新/真实注册登录/320px已取证；独立审阅3项认证问题新增5项RED→GREEN，UNKNOWN业务阻断、同账号新会话代次及401后重试已修复。最终`mvnw.cmd -Ptest-local verify`退出0、常规42/42；`npm.cmd run test`退出0、33/33，typecheck/build退出0。命令、RED/GREEN、实际运行及限制见[B02执行记录](../tests/evidence/B02-progress.md)、[核验汇总](../tests/evidence/B02-verification.json)、[浏览器](../tests/evidence/B02-browser.json)；用户2026-10-13明确确认“B02验收通过”，见[B02验收记录](../tests/evidence/B02-acceptance.md)。

---

<a id="t013"></a>

### T013：维修人员审核、拒绝与原账号重新申请

1. **编号：** T013
2. **目标：** R§3.3、11、12.5—6；A§7、10；REQ-01。
3. **前置任务：** T011。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 本人状态/快照历史、管理员审核、原账号重提与版本冲突。
5. **明确不做：** 待审核资料修改、停用账号自动启用、重提删除原因。
6. **允许修改文件：** J/account/controller/TechnicianReviewController.java、service/TechnicianReviewService.java、dto/ReviewDecisionDto.java、ResubmitReviewDto.java、vo/TechnicianReviewVo.java；JT/account/TechnicianReviewIT.java。本批必要增量白名单：J/account/entity/AccountEventEntity.java、mapper/AccountEventMapper.java、vo/AccountReferenceVo.java；最小GET待审核分页入口复用当前Controller/Service/Mapper，不实现完整人员统计。
7. **接口与衔接：** GET /api/account/review；POST /api/account/review/resubmit；GET/POST /api/admin/accounts/{id}/registration-review；当前提交次数/version必检。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `TechnicianReviewIT.stale_application_cannot_be_reviewed`，断言 `assertEquals(409, staleReviewResponse.statusCode())`。写TechnicianReviewIT：停用审核403/409；空白拒绝原因400；旧申请版本409。
   - [x] 步骤2：事务保存历次资料快照、审核人/时间/原因，重提原账号次数+1。
   - [x] 步骤3：审核通过不自动派单，已通过改资料不改变资格。

9. **验收标准：** 审核、启停用、工单状态独立；失败不改原资料或历史。
10. **AI验证方式：** BT(TechnicianReviewIT)与真实me资格更新重查。
11. **我的手测步骤：** 管理员拒绝→本人改手机重提→通过；先停用再尝试审核。
12. **所需配置或外部依赖：** 演示管理员和维修账号；不需要短信服务。
13. **状态：** 已通过用户验收（B02，2026-10-13）。
14. **实际验证记录：** 2026-10-12，Asia/Shanghai；先失败测试再实现。TechnicianReviewIT 5/5真实HTTP；拒绝→原账号改姓名/手机→第二轮通过、旧版本409、停用只读、原因400、手机号冲突回滚、同标识回执及不同内容409；浏览器重查两轮快照与3事件。最终`mvnw.cmd -Ptest-local verify`退出0、常规42/42；`npm.cmd run test`退出0、33/33，typecheck/build退出0。命令、RED/GREEN、实际运行及限制见[B02执行记录](../tests/evidence/B02-progress.md)、[核验汇总](../tests/evidence/B02-verification.json)、[浏览器](../tests/evidence/B02-browser.json)；用户2026-10-13明确确认“B02验收通过”，见[B02验收记录](../tests/evidence/B02-acceptance.md)。

---

<a id="t014"></a>

### T014：本人审核状态和管理员审核详情页面

1. **编号：** T014
2. **目标：** R§11、12.3—6；REQ-01/19。
3. **前置任务：** T012、T013。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 审核历史展开、资料重提核对、管理员待审核入口及决定确认。
5. **明确不做：** 完整人员统计（T051/T053）、模拟审核成功。
6. **允许修改文件：** F/src/views/account/ReviewStatusView.vue；F/src/views/account/RegistrationReviewView.vue；F/src/components/account/ReviewHistory.vue；F/src/api/account.ts、router/index.ts；F/src/views/account/review-flow.test.ts。本批必要增量白名单：F/src/views/account/review-flow.ts（页面直接使用的确认、版本与UNKNOWN保护逻辑）。
7. **接口与衔接：** 消费T013；人员列表临时基础待审核入口后由T053合并，不创建第二份人员页面。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `review-flow.test.ts: confirmation_precedes_submission`，断言 `expect(reviewRequestsBeforeConfirmation).toBe(0)`。写review-flow.test.ts：取消保留原状态、旧版本禁提交、返回修改保留原因。
   - [x] 步骤2：实现核对后提交、停用只读和历次快照，成功读最新状态。
   - [x] 步骤3：浏览器测试重提/审核/返回筛选位置及320px反馈。

9. **验收标准：** 历史最新在前，提交中防连点，失败保留原因；审核通过只提供任务入口。
10. **AI验证方式：** FV、FT(src/views/account/review-flow.test.ts)，真实接口页面证据。
11. **我的手测步骤：** 完成拒绝→重提→通过链，查看每轮姓名/手机号快照。
12. **所需配置或外部依赖：** 已有工具即可；Visualize如需讨论仅做示例。
13. **状态：** 已通过用户验收（B02，2026-10-13）。
14. **实际验证记录：** 2026-10-12，Asia/Shanghai；先失败测试再实现。review-flow 6/6；确认前0请求、取消/返回保留原因、旧版禁止写、UNKNOWN同标识核对；浏览器历史/筛选返回/停用只读/320px无横向溢出通过。最终`mvnw.cmd -Ptest-local verify`退出0、常规42/42；`npm.cmd run test`退出0、33/33，typecheck/build退出0。命令、RED/GREEN、实际运行及限制见[B02执行记录](../tests/evidence/B02-progress.md)、[核验汇总](../tests/evidence/B02-verification.json)、[浏览器](../tests/evidence/B02-browser.json)；用户2026-10-13明确确认“B02验收通过”，见[B02验收记录](../tests/evidence/B02-acceptance.md)。

---

<a id="t015"></a>

### T015：核查素材、许可和最小副本处理路线

1. **编号：** T015
2. **目标：** R§26.3；A§18、22.4；REQ-20。
3. **前置任务：** T003、T008。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 只读原素材，选择可用GLB/参数化/需转换副本；锁定分发许可与输出路径。
5. **明确不做：** 购买软件、修改原始文件、上传模型、假定.max已打开、制作全部内景。
6. **允许修改文件：** config/asset-license-manifest.json；plan/model-inspection.md；经本项记录的assets/campus-source-copies/单项副本（仅已授权复制/处理）。
7. **接口与衔接：** 清单含原路径/hash、来源/作者/日期/许可、副本路径、格式/用途/检查；T016只消费允许分发项。
8. **实施步骤：**

   - [x] 步骤1：读取学校建筑3D/学校多建筑3D及现有检查报告，确认格式/贴图/压缩扩展，历史结果单列；MAX/VRmesh内部未读入，未知项明确记录。
   - [x] 步骤2：默认用可维护参数化几何构造示意结构；可用许可素材仅补充构件，需源软件时给具体条件。
   - [x] 步骤3：记录允许副本路径及所需条件工具；本批无转换/外部模型副本，不把格式检查当场景通过。

9. **验收标准：** 未核实许可图片只作参考；程序化路线不因缺Blender阻塞；无伪装扩展名转换。
10. **AI验证方式：** 文件/hash和清单核对；实际处理则分别记录读取/导出结果。
11. **我的手测步骤：** 打开清单核对原始文件未变、每个拟分发资源有来源许可。
12. **所需配置或外部依赖：** E08/E09/M04仅条件选用且单独批准；S03可选；原素材目录只读。
13. **状态：** 已通过B03用户验收（2026-10-13）；反馈见tests/evidence/B03-acceptance.md。
14. **实际验证记录：** 2026-10-13：只读盘点221源文件及当前许可/候选GLB；audit与verify退出0，原路径/hash未变，无未知许可素材分发。自建参数化路线、输出及依赖MIT通知见config/asset-license-manifest.json与model-inspection.md；MAX内部、源软件及转换未验证。见tests/evidence/B03-progress.md、B03-source-hashes-after.json。

---

<a id="t016"></a>

### T016：文博1单栋外观与渲染控制器

1. **编号：** T016
2. **目标：** R§21.7、22、26；A§18；REQ-18/20样板。
3. **前置任务：** T006、T008、T015。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 一栋5层简化外观、名称、旋转平移缩放/复位、初始化失败及dispose。
5. **明确不做：** 全校园、全部内景、业务提示或自动报修。
6. **允许修改文件：** F/src/campus/CampusRenderer.ts、types.ts、DormitoryExterior.ts；F/src/views/campus/CampusView.vue；F/public/campus/manifest.json；T015许可清单列明的F/public/campus资源；F/src/campus/renderer-generation.test.ts。F/src/campus/ResourceCache.ts、LoadCoordinator.ts（最小加载与释放能力，T062专项回归）。B03必要增量记录：GeometryKit.ts共享构件/资源scope、three-r186.d.ts已装版本API子集、observation.ts响应式入口与观察意图代次；MainLayout.vue仅侧栏说明；router/index.ts仅在非校园/失效会话时清一次性3D重试记录，不改变认证、权限或跳转；B03证据和README操作说明。职责见B03-progress.md，不改变B02安全/导航契约。
7. **接口与衔接：** §2.2 CampusRenderer接口；manifest以稳定code关联目录，configVersion一致，后续T017增量扩展。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `renderer-generation.test.ts: last_target_wins`，断言 `expect(displayedTargetId).toBe(lastSelectedTargetId)`。写代次测试：expect(旧select回调覆盖新目标).toBe(false)。
   - [x] 步骤2：创建文博1外观和单渲染实例、配置映射、DevTools仅开发期观测。
   - [x] 步骤3：真实浏览器检查外观/名称/观察/复位、WebGL2失败和卸载循环/监听释放。

9. **验收标准：** 来源和示意标识明确；3D失败仍能用位置选择/列表；没有CDN/业务数据嵌模。
10. **AI验证方式：** FV、FT(src/campus/renderer-generation.test.ts)，实际画面、控制台及资源统计。
11. **我的手测步骤：** 观察/缩放/复位、离开再进入，确认外观完整且可重新加载。
12. **所需配置或外部依赖：** P03已安装；S03可选；M03仅观测缺口再批准。
13. **状态：** 已通过B03用户验收（2026-10-13）；反馈见tests/evidence/B03-acceptance.md。
14. **实际验证记录：** 2026-10-13：关键加载/共享资源/入口与观察意图均RED→GREEN；renderer-generation 17/17，全套60/60、typecheck/build退出0。实际WebGL2外观与复位、WebGL失败/恢复、初始化/内景15秒反馈、迟到结果、一次重载会话/期限/摘要竞态和六轮卸载有B03-*证据。资源池/geometry归零且旧上下文关闭，内部texture缓存计数1，不据此声称精确GPU无泄漏。独立审阅全部Important关闭，标签重叠Minor及限制见B03-code-review.md与B03-verification.json。

---

<a id="t017"></a>

### T017：文博1首层101宿舍可进入样板

1. **编号：** T017
2. **目标：** R§21.2、22.2；A§18；REQ-04/06/19。
3. **前置任务：** T016。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 单房墙/地面/独立门轴、两床桌、卫浴、空调、灯/插座；门口与室内观察。
5. **明确不做：** 投影仪等范围外设施、真实业务模拟提交、复制整楼。
6. **允许修改文件：** F/src/campus/templates/DormRoom.ts；F/src/campus/CameraTransition.ts；F/src/campus/CampusRenderer.ts、types.ts；F/src/components/campus/LocationSelector.vue；F/public/campus/manifest.json；F/src/campus/door-state.test.ts。
7. **接口与衔接：** 位置文博1/首层/101映射同一locationId/code；选择定位后用户主动开门进入；选择器与几何共享目标。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `door-state.test.ts: doors_are_instance_specific`，断言 `expect(doorB.open).toBe(false)`。写door-state.test.ts：门状态按实例隔离，减少动态时直接定位，旧动画不覆盖新目标。
   - [x] 步骤2：建立门轴/门口/室内锚点及设施数量，镜头与门动作各≤1秒。
   - [x] 步骤3：浏览器鼠标/触控/选择器进入返回，表单焦点不驱动相机。

9. **验收标准：** 能实际看见内景；8项台账对应分组但不按物件强制报修；示意比例和位置一致。
10. **AI验证方式：** FV、FT(src/campus/door-state.test.ts)，截图＋进入/返回操作；冷加载计时从点击进入。
11. **我的手测步骤：** 选择101→定位门口→开门→室内观察→返回楼层，快速选择不串门。
12. **所需配置或外部依赖：** 参数化模型及许可构件；不需新增动画库。
13. **状态：** 已通过B03用户验收（2026-10-13）；反馈见tests/evidence/B03-acceptance.md。
14. **实际验证记录：** 2026-10-13：door-state RED→GREEN 6/6；101设施和剖开观察、独立门轴、模型门/鼠标/键盘/触控模拟、进入/返回有真实WebGL证据。10组热定位/进入分别最大约664ms/664ms；10次新页面、HTTP缓存关闭、无预取冷进入674—692ms（外观/首层已就绪，从进入点击开始）。实体触屏、完整FPS及其他模型未测。见B03-input-controls.json、B03-cold-entries.json、B03-hot-entries.json及B03-101-interior.png。

---

<a id="t018"></a>

### T018：文博1完整首层与空间实例隔离

1. **编号：** T018
2. **目标：** R§21.2/7；A§17—18；REQ-03/06。
3. **前置任务：** T017。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 10宿舍＋门厅＋值班室、连续走廊/两端楼梯、标签与剖开观察。
5. **明确不做：** 其他楼层/楼栋、公共空间报修。
6. **允许修改文件：** F/src/campus/templates/DormFloor.ts、DormCommon.ts；F/src/campus/CampusRenderer.ts；F/public/campus/manifest.json；F/src/campus/floor-mapping.test.ts。
7. **接口与衔接：** 使用T008首层12处观察/10报修目录；模板实例与门/锚点独立；T040在此接真实业务。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `floor-mapping.test.ts: first_floor_has_twelve_unique_locations`，断言 `expect(new Set(locationIds).size).toBe(12)`。写floor-mapping.test.ts：assert首层10+2；各code/ID不重复，public.canCreateRequest=false。
   - [x] 步骤2：配置两侧各5间、房号101—110、两端楼梯及公共空间内景，隐藏遮挡。
   - [x] 步骤3：逐目标进入观察、快速切换与返回，核对共享材质/门不互改。

9. **验收标准：** 不是仅几间的复制展示；所有12处可进入，公共空间提示仅供观察。
10. **AI验证方式：** FV、FT(src/campus/floor-mapping.test.ts)；首层逐位置画面核对。
11. **我的手测步骤：** 抽两侧宿舍和全部公共内景，确认名称、入口、返回、禁止公共空间报修。
12. **所需配置或外部依赖：** 单房间模板已内部验证通过；失败先修样板，不扩范围。
13. **状态：** 已通过B03用户验收（2026-10-13）；反馈见tests/evidence/B03-acceptance.md。
14. **实际验证记录：** 2026-10-13：floor-mapping RED4→GREEN4；真实目录campus-v1/code/字符串ID/显式floor关系，12处全部进入/返回，两侧各5房、公共空间仅观察。30次快速切换最后目标生效，内景缓存最多2，320px无横向溢出；离页及身份代次模拟迟到结果未恢复旧场景。见B03-browser-locations.json、B03-rapid-switch.json、B03-narrow.json、B03-late-after-epoch.json；未验证实体第二账号切换。比例/标签重叠和审阅裁定见B03-code-review.md；随后用户确认B03整批验收通过，详细反馈见B03-acceptance.md。

---

<a id="t019"></a>

### T019：临时附件上传、取消、正式绑定及鉴权读取

1. **编号：** T019
2. **目标：** R§17；A§11；REQ-17/11。
3. **前置任务：** T011。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** V003附件、内容校验、随机路径、临时归属/用途/上下文和正式访问接口。
5. **明确不做：** 公开图片路径、PDF、绑定时搬文件、删除正式历史。
6. **允许修改文件：** M/V003__attachment.sql；J/attachment/controller/AttachmentController.java、service/AttachmentService.java、AttachmentStorage.java、mapper/AttachmentMapper.java、entity/AttachmentEntity.java、vo/AttachmentVo.java、AttachmentTarget.java；JT/attachment/AttachmentIT.java。J/security/BusinessAccessPolicy.java。
7. **接口与衔接：** §2.2附件API/bindTemporary；目标引用在V004/V005补对应FK，跨业务/round绑定拒绝；读取复用业务历史投影。BusinessAccessPolicy.canReadAttachment(CurrentUser,AttachmentTarget)此项定义：临时限本人；尚未接入的正式归属一律拒绝，T022/T025补真实业务投影。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `AttachmentIT.disguised_image_is_rejected`，断言 `assertEquals(400, disguisedImageResponse.statusCode())`。写AttachmentIT：假PNG/超5MB/第6张拒绝；他人临时附件403；路径穿越无法指定。
   - [x] 步骤2：实现主动上传、DB登记与失败文件补偿、private/no-store读取、CSRF取消。
   - [x] 步骤3：验证绑定仅在调用方事务内生效，回滚后临时仍可重试；无物理搬动。

9. **验收标准：** 图内容验证及数量服务端实施；用途/操作者/目标/轮次正确；不得记录明文Token/磁盘路径。
10. **AI验证方式：** BT(AttachmentIT)，真实文件＋MySQL失败场景；正式业务访问在T025/T061复核。
11. **我的手测步骤：** 上传/预览/取消、假扩展和大图失败，猜附件ID不能看他人图片。
12. **所需配置或外部依赖：** test独立绝对目录；5 MB=5,000,000字节，multipart请求上限容纳5张及协议开销；无对象存储。
13. **状态：** 已通过用户验收（2026-10-15，用户原文“B04审核通过”）；未启动B05。
14. **实际验证记录：** AttachmentIT；真实图片/文件/归属/绑定/鉴权与全量103项回归，B04-attachment-*、B04-final-backend-verify.log。 最新增量后端verify 106项、前端132项及typecheck/build退出0；环境、限制及真实DB/文件/浏览器见tests/evidence/B04-verification.json、B04-runtime.json、B04-person-only-review.md。2026-10-15用户直接反馈“B04审核通过”，验收来源见B04-acceptance.md。

---

<a id="t020"></a>

### T020：临时附件过期清理和绑定互斥

1. **编号：** T020
2. **目标：** A§10.3、11.2、15；REQ-17。
3. **前置任务：** T019。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 默认24小时、可重试清理状态、取消/绑定/清理共同锁。
5. **明确不做：** 清理正式附件、原始素材、未知文件目录。
6. **允许修改文件：** J/attachment/service/TemporaryAttachmentCleanup.java；J/attachment/mapper/AttachmentMapper.java；JT/attachment/AttachmentCleanupIT.java；JR/application.yml。
7. **接口与衔接：** cleanupExpired(now)只处理过期且未关联记录；物理删除前使该记录不可再绑定，失败保留重试记录。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `AttachmentCleanupIT.bind_and_cleanup_are_mutually_exclusive`，断言 `assertEquals(1, successfulBindCount + successfulCleanupClaimCount)`。写双事务测试：bind与cleanup竞态不能同时成功；正式记录永不删除。
   - [x] 步骤2：实现锁内状态变更与锁外删除/补偿，显式检查存储根目录。
   - [x] 步骤3：注入文件删除失败、重复清理、在写文件/登记中断，验证有界恢复。

9. **验收标准：** 24小时边界可控Clock验证；无数据库回滚会自动恢复文件的假设。
10. **AI验证方式：** BT(AttachmentCleanupIT)，独立连接/真实文件竞态与失败重试。
11. **我的手测步骤：** 看临时/正式区分；用测试过期数据确认清理后历史图仍可访问。
12. **所需配置或外部依赖：** 受限test目录；清理仅批准目录，普通启动不清业务数据。
13. **状态：** 已通过用户验收（2026-10-15，用户原文“B04审核通过”）；未启动B05。
14. **实际验证记录：** AttachmentCleanupIT真实互斥/过期/失败补偿；B04-cleanup-create-*、B04-final-backend-verify.log。 最新增量后端verify 106项、前端132项及typecheck/build退出0；环境、限制及真实DB/文件/浏览器见tests/evidence/B04-verification.json、B04-runtime.json、B04-person-only-review.md。2026-10-15用户直接反馈“B04审核通过”，验收来源见B04-acceptance.md。

---

<a id="t021"></a>

### T021：图片选择、预览与明确上传步骤

1. **编号：** T021
2. **目标：** R§17、24.3；A§20；REQ-13/17。
3. **前置任务：** T006、T019。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** File内存选择/预览、移除、主动上传与过期引用反馈。
5. **明确不做：** 选择/切换位置自动上传、历史附件删除、3D截图代故障图。
6. **允许修改文件：** F/src/components/attachment/ImagePicker.vue、ProtectedImage.vue；F/src/api/attachment.ts；F/src/composables/useImageSelection.ts；F/src/composables/image-selection.test.ts。
7. **接口与衔接：** File选择交给草稿；ensureTemporaryUploaded(selection,context)仅明确提交步骤调用；正式图只读。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `image-selection.test.ts: selection_does_not_upload`，断言 `expect(uploadCallsAfterSelection).toBe(0)`。写image-selection.test.ts：选择/路由切换上传次数0；移除/失权释放Object URL。
   - [x] 步骤2：实现≤5张、格式/大小反馈、上传状态和无权图错误；真实鉴权读取不暴露登录Token。
   - [x] 步骤3：验证部分上传失败仍保留File与成功临时引用，重试不重复已确认上传。

9. **验收标准：** 预览和上传/绑定分别显示；关闭渲染不清File；失效附件要求重传。
10. **AI验证方式：** FV、FT(src/composables/image-selection.test.ts)，实际上传/受保护图浏览器检查。
11. **我的手测步骤：** 选图后切页，再主动提交；检查历史图片无删除入口。
12. **所需配置或外部依赖：** 已有浏览器；本机文件均用演示图。
13. **状态：** 已通过用户验收（2026-10-15，用户原文“B04审核通过”）；未启动B05。
14. **实际验证记录：** image-selection与真实浏览器选图/上传/取消/鉴权图；B04-a-browser-cancel.json、B04-c-browser.json。 最新增量后端verify 106项、前端132项及typecheck/build退出0；环境、限制及真实DB/文件/浏览器见tests/evidence/B04-verification.json、B04-runtime.json、B04-person-only-review.md。2026-10-15用户直接反馈“B04审核通过”，验收来源见B04-acceptance.md。

---

<a id="t022"></a>

### T022：首次报修、申请模型及本人查询

1. **编号：** T022
2. **目标：** R§15.1、24.1；A§8—10；REQ-06/07/13。
3. **前置任务：** T008、T019。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** V004申请/快照/事件；结构化首次报修、列表详情与操作结果查询。
5. **明确不做：** 首次生成工单、用户选台账、客户端伪造旧模式。
6. **允许修改文件：** M/V004__repair_request.sql；J/repairrequest/controller/RepairRequestController.java、service/RepairRequestService.java、mapper/RepairRequestMapper.java、dto/CreateRepairRequestDto.java、vo/RepairRequestDetailVo.java；J/common/OperationResultService.java；JT/repairrequest/CreateRepairRequestIT.java。J/repairrequest/entity/RepairRequestEntity.java、RequestSubmissionEntity.java、RequestEventEntity.java；J/security/BusinessAccessPolicy.java。
7. **接口与衔接：** §2.2新报修契约；GET /api/repair-requests、/{id}及本人操作结果；联系人电话为快照，类别可null。补BusinessAccessPolicy的正式申请/提交快照访问规则，T019图片读取复用。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `CreateRepairRequestIT.retry_returns_original_result`，断言 `assertEquals(firstId, retryId); assertEquals(1, requestCount)`。写CreateRepairRequestIT：无图/展示位置400；类别空成功；同标识同内容返回相同ID且count=1。
   - [x] 步骤2：同事务保存申请、联系/位置/原文快照、图片与事件；同标识异内容409。
   - [x] 步骤3：验证他人详情/操作标识不泄露；新建期间与账号停用共同锁用户记录。

9. **验收标准：** 状态PENDING_REVIEW；未创建工单；地址文字不改locationId；账号手机号不被申请覆盖。
10. **AI验证方式：** BT(CreateRepairRequestIT)，DB/图片关联重查，跨账号ID/operationId测试。
11. **我的手测步骤：** 从普通入口报修两处；核对历史原文和当前资料分离。
12. **所需配置或外部依赖：** 真实报修用户登录；隔离测试种子；P01已就绪。
13. **状态：** 已通过用户验收（2026-10-15，用户原文“B04审核通过”）；未启动B05。
14. **实际验证记录：** CreateRepairRequestIT、RequestDetailProjectionIT，真实Cookie/CSRF、DB/文件及权限；B04-final-backend-verify.log、B04-c-real-bindings.tsv。 最新增量后端verify 106项、前端132项及typecheck/build退出0；环境、限制及真实DB/文件/浏览器见tests/evidence/B04-verification.json、B04-runtime.json、B04-person-only-review.md。2026-10-15用户直接反馈“B04审核通过”，验收来源见B04-acceptance.md。

---

<a id="t023"></a>

### T023：退回补充、原申请重提、撤回与历史

1. **编号：** T023
2. **目标：** R§15.2/4、24；A§9—10；REQ-07/14。
3. **前置任务：** T022。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 带原因退回、原用户改位置重提、合法撤回；范围外使用退回流程。
5. **明确不做：** 管理员代撤回、驳回关闭新状态、删除历史图、范围外补账。
6. **允许修改文件：** J/repairrequest/service/RepairRequestService.java；J/repairrequest/controller/RepairRequestController.java；J/repairrequest/dto/SupplementRequestDto.java、ReturnRequestDto.java、WithdrawRequestDto.java；JT/repairrequest/RepairRequestTransitionIT.java。
7. **接口与衔接：** POST /api/repair-requests/{id}/return、supplement、withdraw；version/operationId，旧文字申请重提仍保留无位置属性。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `RepairRequestTransitionIT.resubmit_preserves_first_submission`，断言 `assertEquals(firstSubmittedAt, afterResubmitFirstSubmittedAt)`。写TransitionIT：重提ID/firstSubmittedAt不变，历史图不删；旧版本409。
   - [x] 步骤2：实现原申请状态转换及同事务新材料/快照/事件；位置仅成功重提才更新。
   - [x] 步骤3：范围外写不受理范围原因并保留未完结，用户可纠正/撤回；失败回滚。

9. **验收标准：** 待审核/待补充可撤回；通过后不行；旧记录不通过重提偷偷绑定3D。
10. **AI验证方式：** BT(RepairRequestTransitionIT)；状态、事件及图关联真实重查。
11. **我的手测步骤：** 退回→改位置重提→再次退回→撤回，核对原编号和全部意见。
12. **所需配置或外部依赖：** 管理员和原报修人；文字模式仅服务端测试/迁移数据生成。
13. **状态：** 已通过用户验收（2026-10-15，用户原文“B04审核通过”）；未启动B05。
14. **实际验证记录：** TransitionIT/TransitionReplayIT，真实并发重放RED→GREEN；原申请零新增图重提/退回/撤回浏览器及历史；B04-review-backend-repair.md、B04-c-browser.json。 最新增量后端verify 106项、前端132项及typecheck/build退出0；环境、限制及真实DB/文件/浏览器见tests/evidence/B04-verification.json、B04-runtime.json、B04-person-only-review.md。2026-10-15用户直接反馈“B04审核通过”，验收来源见B04-acceptance.md。

---

<a id="t024"></a>

### T024：报修表单、我的申请及内存草稿

1. **编号：** T024
2. **目标：** R§19.2、24.3；A§20；REQ-06/07/13。
3. **前置任务：** T012、T018、T021、T023。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 普通列表/3D共用新报修与补充表单、详情/撤回、用户位置隔离草稿。
5. **明确不做：** 跨刷新持久化、切位置搬照片、模型物品选择清单。
6. **允许修改文件：** F/src/stores/drafts.ts；F/src/views/request/RequestListView.vue、RequestDetailView.vue、RequestForm.vue、SupplementView.vue；F/src/api/repair-request.ts；F/src/stores/drafts.test.ts；F/src/components/campus/BusinessPanel.vue。
7. **接口与衔接：** DraftKey(userId,locationId)或(userId,requestId)；T034/T035/T039扩展操作轮次键；T017入口传locationId不传台账ID。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `drafts.test.ts: editing_location_a_does_not_change_b`，断言 `expect(draftB.description).toBe('B')`。写drafts.test.ts：位置A/B及用户A/B不串，补充改位置仍同requestId，成功仅清本份。
   - [x] 步骤2：接真实表单/图片/提交/详情；未保存取消按页面约定，刷新关闭只提示不恢复。
   - [x] 步骤3：测试两入口切换、历史图只读、失败/UNKNOWN留输入、不因读刷新覆盖草稿。

9. **验收标准：** 新报修必选位置；联系默认本人且可独立补充；类别自由可空；仅展示空间无报修入口。
10. **AI验证方式：** FV、FT(src/stores/drafts.test.ts)，真实接口＋页面操作；普通列表完整可用。
11. **我的手测步骤：** 两个位置各填草稿→3D/列表切换→提交一处→另一处仍在；退出后清除。
12. **所需配置或外部依赖：** 内存File与Object URL；不需存储SDK或新MCP。
13. **状态：** 已通过用户验收（2026-10-15，用户原文“B04审核通过”）；未启动B05。
14. **实际验证记录：** 草稿/共用表单关键RED→GREEN及真实两位置/两账号/3D入口；本批前端129项，B04-progress.md、B04-final-frontend-tests.log。 最新增量后端verify 106项、前端132项及typecheck/build退出0；环境、限制及真实DB/文件/浏览器见tests/evidence/B04-verification.json、B04-runtime.json、B04-person-only-review.md。2026-10-15用户直接反馈“B04审核通过”，验收来源见B04-acceptance.md。

---

<a id="t025"></a>

### T025：工单结构、查询及历史权限投影

1. **编号：** T025
2. **目标：** R§7、13、16；A§8、12；REQ-09/11。
3. **前置任务：** T007、T011、T019、T022。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** V005工单/分派/轮次/进度/事件、未完结设备唯一生成列和详情投影。设备历史查询此时可用，供T027页面直接验收。
5. **明确不做：** 状态变更、任意历史分派放行所有材料、前端全量过滤。
6. **允许修改文件：** M/V005__work_order.sql；J/workorder/controller/WorkOrderController.java、service/WorkOrderQueryService.java、mapper/WorkOrderMapper.java、vo/WorkOrderDetailVo.java；J/security/BusinessAccessPolicy.java；JT/workorder/WorkOrderQueryIT.java；J/equipment/service/EquipmentQueryService.java。J/workorder/entity/WorkOrderEntity.java、AssignmentEntity.java、RepairRoundEntity.java、ProgressEntity.java、WorkOrderEventEntity.java。
7. **接口与衔接：** GET /api/work-orders/{id}/列表及设备历史；WorkOrderDetailVo含version/round/allowedActions；附件读取复用BusinessAccessPolicy。EquipmentQueryService.currentAndHistory(equipmentId,query,currentUser)供T026台账Service复用。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `WorkOrderQueryIT.former_assignee_cannot_read_later_voucher`，断言 `assertEquals(403, laterVoucherResponse.statusCode())`。写QueryIT：设备未完结unique；仅曾拒单者读取后续他人结果/图片403，实际维修者完结可只读。
   - [x] 步骤2：迁移表/关联/生成列，补申请和附件目标FK，查询先权限再分页/count。
   - [x] 步骤3：补设备未完结/历史查询，验证多轮/多图不使列表数膨胀。

9. **验收标准：** 一申请最多一工单、历史不级联删；旧文字工单locationId可空，未完成约束真实有效。
10. **AI验证方式：** BT(WorkOrderQueryIT)与AttachmentIT相关范围回归、迁移历史重查。
11. **我的手测步骤：** 用不同关联人员打开同工单，核对材料范围；设备详情显示当前/历史。
12. **所需配置或外部依赖：** 真实MySQL支持生成列；后续任务复用范围策略，不另写粗粒度放行。
13. **状态：** 已通过用户验收（2026-10-15，用户原文“B04审核通过”）；未启动B05。
14. **实际验证记录：** WorkOrderQueryIT/RequestDetailProjectionIT，真实不同Cookie、有限历史和后续图片403；B04-c-limited-history.jpg、B04-final-backend-suites.json。 最新增量后端verify 106项、前端132项及typecheck/build退出0；环境、限制及真实DB/文件/浏览器见tests/evidence/B04-verification.json、B04-runtime.json、B04-person-only-review.md。2026-10-15用户直接反馈“B04审核通过”，验收来源见B04-acceptance.md。

---

<a id="t026"></a>

### T026：设备台账维护、范围与位置变更限制

1. **编号：** T026
2. **目标：** R§14、21.6、24.2/4；A§8、17；REQ-08/14。
3. **前置任务：** T007、T011、T025。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 管理台账编号/名称/结构化位置/设施种类、启停用、查询；未完结设备禁止移动。
5. **明确不做：** 设备故障状态、开放范围外补账、编辑隐式启用、自动解析旧文字。
6. **允许修改文件：** J/equipment/controller/EquipmentController.java、service/EquipmentService.java、dto/EquipmentSaveDto.java、EquipmentStatusDto.java、vo/EquipmentVo.java；J/equipment/mapper/EquipmentMapper.java；JT/equipment/EquipmentMaintenanceIT.java。
7. **接口与衔接：** /api/equipment CRUD与独立enable/disable；分页设备当前未完结情况由T025补查询投影；后续审核复用lockAndValidateEquipment。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `EquipmentMaintenanceIT.unfinished_device_cannot_move`，断言 `assertEquals(409, moveUnfinishedDeviceResponse.statusCode())`。写EquipmentMaintenanceIT：范围外类型拒绝；停用编辑仍停用；在非空未完结集合下改位置409。
   - [x] 步骤2：实现编号自动生成、version、设备共同锁、位置/设施类型范围及变更历史。
   - [x] 步骤3：维护旧文字台账须管理员明确选位置且保留原文，新增同名合法设备不因名称唯一被拦。

9. **验收标准：** 新工单停用设备不受理，既有工单继续；姓名/台账文字不覆盖申请原文。
10. **AI验证方式：** BT(EquipmentMaintenanceIT)，真实未完结工单移动阻断、设备状态及历史重查。
11. **我的手测步骤：** 新增/编辑/停用设备；尝试公共空间或非法类别补录，检查被阻止。
12. **所需配置或外部依赖：** 管理员资格；设备/空间已初始化；完整并发在T060。
13. **状态：** 已通过用户验收（2026-10-15，用户原文“B04审核通过”）；未启动B05。
14. **实际验证记录：** EquipmentMaintenanceIT，未完结阻止移动/停用编辑仍停用/范围和同名补录；真实页面启停用、维护及DB，B04-c-browser.json。 最新增量后端verify 106项、前端132项及typecheck/build退出0；环境、限制及真实DB/文件/浏览器见tests/evidence/B04-verification.json、B04-runtime.json、B04-person-only-review.md。2026-10-15用户直接反馈“B04审核通过”，验收来源见B04-acceptance.md。

---

<a id="t027"></a>

### T027：设备列表、表单、详情与维修历史入口

1. **编号：** T027
2. **目标：** R§14、19.4；REQ-08/17。
3. **前置任务：** T026、T025。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 搜索筛选、维护/启停用确认、先当前后历史及审核补录返回。
5. **明确不做：** 删除台账、修改编号、把资料编辑视为启用。
6. **允许修改文件：** F/src/views/equipment/EquipmentListView.vue、EquipmentFormView.vue、EquipmentDetailView.vue；F/src/api/equipment.ts；F/src/router/index.ts。F/src/views/workorder/WorkOrderDetailView.vue、components/workorder/WorkOrderHistoryContent.vue、api/workorder.ts（本项先提供共用只读详情，T034/T035扩展操作）。
7. **接口与衔接：** 调用T026和T025；工单详情链接按允许范围；审核返回保存requestId/列表查询，不自动派单。共用只读工单详情在此先落地，保证历史链接可运行，后续页面复用，不保留无目标链接。
8. **实施步骤：**

   - [x] 步骤1：实现编号只读和位置选择，保存失败留输入，停用确认说明新旧工单影响。
   - [x] 步骤2：接T025真实工单投影，实现当前工单/历次工单分区与分页，编号入口按允许范围。
   - [x] 步骤3：浏览器检查审核补录→回原申请、筛选/返回、当前与历史分区、窄屏及无记录状态。

9. **验收标准：** 停用设备历史可查；当前/历史来自真实接口，申请补录返回不自动派单。
10. **AI验证方式：** FV；真实台账/历史接口与窄屏浏览器证据。
11. **我的手测步骤：** 搜索/维护/启停用/详情；从审核补录并回到原申请。
12. **所需配置或外部依赖：** 已有工具；不为布局写重复实现测试。
13. **状态：** 已通过用户验收（2026-10-15，用户原文“B04审核通过”）；未启动B05。
14. **实际验证记录：** 真实补录返回原申请、设备启停用/维护/当前只读工单/有限历史入口、320px键盘；B04-c-browser.json、B04-final-browser.json。 最新增量后端verify 106项、前端132项及typecheck/build退出0；环境、限制及真实DB/文件/浏览器见tests/evidence/B04-verification.json、B04-runtime.json、B04-person-only-review.md。2026-10-15用户直接反馈“B04审核通过”，验收来源见B04-acceptance.md。

---

<a id="t028"></a>

### T028：审核并首次派单和重复关闭事务

1. **编号：** T028
2. **目标：** R§15.3、24.2；A§9—10；REQ-08/13/14。
3. **前置任务：** T013、T020、T023、T026、T025。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 匹配启用设备、位置一致、合格人员、原申请通过及首次派单原子提交；重复关闭。
5. **明确不做：** 先通过后首次待派、范围外补账、旧申请由设备偷偷补位置。
6. **允许修改文件：** J/repairrequest/service/RequestReviewService.java、controller/RequestReviewController.java、dto/ApproveAndAssignDto.java、DuplicateCloseDto.java；J/workorder/service/AssignmentService.java；JT/repairrequest/ApproveAndAssignIT.java。
7. **接口与衔接：** §2.2 approve-and-assign；同快照资格名单供T029/T047；设备/用户/申请共同锁，operationId同事务保存原结果。GET /api/admin/assignment-eligible按上下文返回实时完整合格名单，供T029人工选择；T047在该名单规则上派生推荐。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `ApproveAndAssignIT.legacy_order_keeps_null_location`，断言 `assertNull(legacyOrderLocationId); assertEquals(1, orderCountAfterRetry)`。写ApproveAndAssignIT：位置差异/缺位置新申请409；旧文字申请成功但工单locationId=null。
   - [x] 步骤2：实现设施范围、人员资格、设备启停用/未完结、版本复核与通过/工单/分派/事件原子写。
   - [x] 步骤3：验证同标识重试不新增；并行同设备两申请最多一成功；重复关闭带非空原因不生成工单。

9. **验收标准：** 任何失败原申请仍待审核；无合法人员不通过；模型缺投影仪不影响合法派单。
10. **AI验证方式：** BT(ApproveAndAssignIT)，真实事务回滚与生成列冲突。
11. **我的手测步骤：** 一申请选错位置被阻止；正常派单生成一工单；重复申请按原因关闭。
12. **所需配置或外部依赖：** 合格人员与启用设备；推荐尚未加入也能人工选人。
13. **状态：** 已通过用户验收（2026-10-15，用户原文“B04审核通过”）；未启动B05。
14. **实际验证记录：** ApproveAndAssignIT20项；真实事务回滚/并发/幂等/完整合格名单/LEGACY；浏览器生成1张PENDING_ACCEPT工单及round0，B04-c-real-bindings.tsv。 最新增量后端verify 106项、前端132项及typecheck/build退出0；环境、限制及真实DB/文件/浏览器见tests/evidence/B04-verification.json、B04-runtime.json、B04-person-only-review.md。2026-10-15用户直接反馈“B04审核通过”，验收来源见B04-acceptance.md。

---

<a id="t029"></a>

### T029：管理员申请审核与人工首次派单页面

1. **编号：** T029
2. **目标：** R§19.4、24—25；REQ-07/08。
3. **前置任务：** T014、T024、T027、T028。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 待审核列表、原文/台账对比、匹配补录、位置阻断、退回/重复/派单确认。
5. **明确不做：** 自动选人、推荐排序（T048）、失败提前亮红灯。
6. **允许修改文件：** F/src/views/request/RequestReviewListView.vue、RequestReviewView.vue；F/src/components/workorder/AssignmentPanel.vue；F/src/api/repair-request.ts、workorder.ts；F/src/views/request/review.test.ts。
7. **接口与衔接：** AssignmentPanel此时使用完整合格名单；T048在同组件加推荐；所有写调用T028。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `review.test.ts: mismatched_location_blocks_submit`，断言 `expect(approveRequestsWithMismatchedLocation).toBe(0)`。写review.test.ts：位置不一致/无资格人无法提交；failed mutation保留设备和人员选择。
   - [x] 步骤2：实现原文/位置/图与台账分别显示、补录返回、带原因退回和重复关闭。
   - [x] 步骤3：真实浏览器确认通过与工单生成，失败只反馈不改变业务。

9. **验收标准：** 仍只有五种申请状态；审核成功后链接工单，返回保持列表筛选。
10. **AI验证方式：** FV、FT(src/views/request/review.test.ts)，真实申请审核各分支。
11. **我的手测步骤：** 审核一条正常、一条范围外、一条重复；检查台账和用户原文分别保存。
12. **所需配置或外部依赖：** 已有工具即可；推荐工具不是派单必需。
13. **状态：** 已通过用户验收（2026-10-15，用户原文“B04审核通过”）；未启动B05。
14. **实际验证记录：** 审核阻断RED→GREEN、页面恢复/分页回归，真实人工选人/失败留选择/重复关闭/范围外退回；两类红标及fixed栏实测，B04-code-review.md、B04-final-browser.json。 最新增量后端verify 106项、前端132项及typecheck/build退出0；环境、限制及真实DB/文件/浏览器见tests/evidence/B04-verification.json、B04-runtime.json、B04-person-only-review.md。2026-10-15用户直接反馈“B04审核通过”，验收来源见B04-acceptance.md。

---

<a id="t030"></a>

### T030：接单、带原因拒单及责任固定

1. **编号：** T030
2. **目标：** R§16.1；A§9.2、10；REQ-09/11。
3. **前置任务：** T028。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 当前人员接单/拒单，首轮开始、实际维修者、拒单待重派历史。
5. **明确不做：** 接单后改派、拒单后仍保留当前责任、拒单算完成。
6. **允许修改文件：** J/workorder/service/WorkOrderTransitionService.java；J/workorder/controller/WorkOrderController.java；J/workorder/dto/AcceptWorkOrderDto.java、RefuseWorkOrderDto.java；JT/workorder/AcceptRefuseIT.java。
7. **接口与衔接：** /accept、/refuse；CurrentUser/version/operationId；acceptedAt首次固定，round1.startedAt同转换时间。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `AcceptRefuseIT.wrong_technician_cannot_accept`，断言 `assertEquals(403, otherTechnicianAcceptResponse.statusCode())`。写AcceptRefuseIT：错人员403、空原因400、过期分派409；重复operationId事件数不变。
   - [x] 步骤2：实现条件更新及接单首轮/拒单分派结束记录，状态和时间同事务。
   - [x] 步骤3：验证拒单当前任务为0、仅本人历史分派材料只读；接单成果归实际人员。

9. **验收标准：** 仅一个分支成功；负责人接单后固定，拒单后待重派不计原人员负荷。
10. **AI验证方式：** BT(AcceptRefuseIT)，DB分派/轮次/事件/时间重查。
11. **我的手测步骤：** 维修人员先看故障图再接单或拒单，管理员查看拒单原因。
12. **所需配置或外部依赖：** 合格维修账号；实际Cookie身份。
13. **状态：** 已通过用户验收（2026-10-18，用户原文“B05验收通过”）；B06当前进展见T037—T040（本段为B05验收记录）。
14. **实际验证记录：** AcceptRefuseIT、接／拒并发及两设备模式完整闭环，其他维修人员403；固定接单时间／round1断言。 最终后端verify128/128、前端21文件143/143及typecheck/build退出0；完整命令、源码SHA256、DB／图片、浏览器、清理与未测项见tests/evidence/B05-verification.json、B05-runtime.json、B05-code-review.md、B05-acceptance.md。无Git／OCR非Git失败如实保留；2026-10-18用户已确认B05验收通过，含数量增量；最新132／159条及反馈来源见B05-admin-order-count-verification.json、B05-user-acceptance.json。

---

<a id="t031"></a>

### T031：接单前改派与待重派重新分派

1. **编号：** T031
2. **目标：** R§16.1、25.2；A§9、21；REQ-09/16。
3. **前置任务：** T030。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 复用AssignmentService，接单前带原因改派、待重派安排新人员、曾拒单确认。
5. **明确不做：** 处理中/待验收/返工改派、当前负责人同人改派。
6. **允许修改文件：** J/workorder/service/AssignmentService.java；J/workorder/controller/WorkOrderController.java；J/workorder/dto/AssignWorkOrderDto.java；JT/workorder/ReassignmentIT.java。
7. **接口与衔接：** POST /api/work-orders/{id}/assign；version/operationId/reason/technicianId/confirmPreviouslyRefused；资格在最终事务复核。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `ReassignmentIT.reassignment_keeps_order_identity`，断言 `assertEquals(originalWorkOrderId, reassignedWorkOrderId)`。写ReassignmentIT：接单后409、同人409、曾拒单未确认409；合法重派仍同workOrderId。
   - [x] 步骤2：结束旧分派、保留当次可见材料引用，写新分派与事件。
   - [x] 步骤3：同时接单/改派仅一个有效结果；旧人员当前详情/附件按有限历史投影。

9. **验收标准：** 无新申请/工单；曾拒单者仍有资格但需确认；失败保留待重派。
10. **AI验证方式：** BT(ReassignmentIT)真实双连接竞态及历史/附件回归。
11. **我的手测步骤：** 改派未接工单，原人再点接单被阻止；拒单后重派原编号不变。
12. **所需配置或外部依赖：** 管理员及至少两个合格人员。
13. **状态：** 已通过用户验收（2026-10-18，用户原文“B05验收通过”）；B06当前进展见T037—T040（本段为B05验收记录）。
14. **实际验证记录：** ReassignmentIT、B05BoundaryIT、拒单原人确认和接前换人浏览器；当前名单计数分页排除原人、同工单和有限历史。 最终后端verify128/128、前端21文件143/143及typecheck/build退出0；完整命令、源码SHA256、DB／图片、浏览器、清理与未测项见tests/evidence/B05-verification.json、B05-runtime.json、B05-code-review.md、B05-acceptance.md。无Git／OCR非Git失败如实保留；2026-10-18用户已确认B05验收通过，含数量增量；最新132／159条及反馈来源见B05-admin-order-count-verification.json、B05-user-acceptance.json。

---

<a id="t032"></a>

### T032：独立追加进度与本轮结果/凭证提交

1. **编号：** T032
2. **目标：** R§16.2、17；A§8—11；REQ-09/13/17。
3. **前置任务：** T030、T019。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 独立进度、本轮处理结果、新凭证、待验收转换及幂等。
5. **明确不做：** 旧轮图充新凭证、提交进度自动验收、后端相信客户端负责人。
6. **允许修改文件：** J/workorder/service/RepairProcessingService.java；J/workorder/controller/WorkOrderController.java；J/workorder/dto/ProgressDto.java、SubmitRepairResultDto.java；J/workorder/vo/RepairRoundVo.java；JT/workorder/RepairProcessingIT.java。
7. **接口与衔接：** /progress与/submit-result；version/round/operationId；RepairRoundVo供详情/统计，结果和附件同事务。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `RepairProcessingIT.progress_retry_is_not_duplicated`，断言 `assertEquals(1, progressCountAfterRetry)`。写RepairProcessingIT：本轮结果空/无新凭证400，错轮次409；同标识进度count=1。
   - [x] 步骤2：只在IN_PROGRESS按CurrentUser追加进度或提交结果；关联附件上下文及轮次复核。
   - [x] 步骤3：注入绑定失败验证结果/状态/事件回滚；提交时间写轮次但不写completedAt。

9. **验收标准：** 进度与凭证分别关联工单及轮次；旧材料只读，本轮1—5张合格凭证。
10. **AI验证方式：** BT(RepairProcessingIT)，DB/文件关联及响应丢失重试断言。
11. **我的手测步骤：** 追加一次进度，再提交本轮结果，检查待验收并未完成。
12. **所需配置或外部依赖：** 真实临时附件；工单由该人员已接单。
13. **状态：** 已通过用户验收（2026-10-18，用户原文“B05验收通过”）；B06当前进展见T037—T040（本段为B05验收记录）。
14. **实际验证记录：** RepairProcessingIT、真实新凭证绑定回滚／过期／跨人跨单跨轮403或409、原标识并发进度唯一；浏览器200响应丢失后核对成功。 最终后端verify128/128、前端21文件143/143及typecheck/build退出0；完整命令、源码SHA256、DB／图片、浏览器、清理与未测项见tests/evidence/B05-verification.json、B05-runtime.json、B05-code-review.md、B05-acceptance.md。无Git／OCR非Git失败如实保留；2026-10-18用户已确认B05验收通过，含数量增量；最新132／159条及反馈来源见B05-admin-order-count-verification.json、B05-user-acceptance.json。

---

<a id="t033"></a>

### T033：原报修人验收与原工单返工

1. **编号：** T033
2. **目标：** R§16.3；A§9—10、13；REQ-09/13。
3. **前置任务：** T032。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 通过完成/不通过带原因开下一轮，原负责人和编号不变。
5. **明确不做：** 管理员代验收、新建返工工单、跨轮旧页再次验收。
6. **允许修改文件：** J/workorder/service/AcceptanceService.java；J/workorder/controller/WorkOrderController.java；J/workorder/dto/AcceptanceDto.java；JT/workorder/AcceptanceIT.java。
7. **接口与衔接：** POST /api/work-orders/{id}/acceptance，passed/reason/version/round/operationId；通过才completedAt。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `AcceptanceIT.old_round_cannot_accept_new_round`，断言 `assertEquals(409, oldRoundResponse.statusCode())`。写AcceptanceIT：非原报修人403，空退回原因400；旧round即使状态重回待验收仍409。
   - [x] 步骤2：同事务保存当轮验收、完成时间或下轮startedAt及事件；操作结果可重查。
   - [x] 步骤3：验证多次返工一工单，旧凭证不满足新轮提交；两个验收分支仅一个成功。

9. **验收标准：** 验收结果历史完整；完成成果归实际维修者，返工不增加完成数量。
10. **AI验证方式：** BT(AcceptanceIT)，DB轮次/时长/幂等和权限检查。
11. **我的手测步骤：** 第一轮退回→原人第二轮新图→通过，核对原编号和两轮过程。
12. **所需配置或外部依赖：** 原报修人及同一维修人员；可控测试Clock。
13. **状态：** 已通过用户验收（2026-10-18，用户原文“B05验收通过”）；B06当前进展见T037—T040（本段为B05验收记录）。
14. **实际验证记录：** AcceptanceIT、B05WorkflowIT两种设备模式两轮、双验收分支竞态／旧round和version拒绝／管理员与他人403／passed带reason400。 最终后端verify128/128、前端21文件143/143及typecheck/build退出0；完整命令、源码SHA256、DB／图片、浏览器、清理与未测项见tests/evidence/B05-verification.json、B05-runtime.json、B05-code-review.md、B05-acceptance.md。无Git／OCR非Git失败如实保留；2026-10-18用户已确认B05验收通过，含数量增量；最新132／159条及反馈来源见B05-admin-order-count-verification.json、B05-user-acceptance.json。

---

<a id="t034"></a>

### T034：维修任务、有限历史及维修处理页面

1. **编号：** T034
2. **目标：** R§7、19.3；REQ-09/11/13。
3. **前置任务：** T021、T024、T031、T032。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 当前/本人历史列表、接单拒单、进度、本轮结果和凭证，返回查询位置。
5. **明确不做：** 无权历史结果展示、审核未通过者任务入口、跨轮草稿复用。
6. **允许修改文件：** F/src/views/workorder/TaskListView.vue、TaskDetailView.vue、RepairProcessingView.vue；F/src/api/workorder.ts；F/src/stores/drafts.ts；F/src/views/workorder/repair-draft.test.ts。
7. **接口与衔接：** 共用WorkOrderDetailVo/RepairRoundVo；编辑键userId+workOrderId+operation+round；T038面板复用这些组件。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `repair-draft.test.ts: new_round_needs_new_result`，断言 `expect(nextRoundDraft.result).toBe('')`。写repair-draft.test.ts：换round不带旧结果/图；只读刷新不覆盖本轮输入；失权清相应草稿。
   - [x] 步骤2：实现先看图再接/拒、两块独立维修提交区、当前轮在前历史折叠。
   - [x] 步骤3：浏览器测试拒单/改派失效、失败保留、本人历史只读且不混入当前任务。

9. **验收标准：** 旧分派者只收到后端授权投影；按钮由allowedActions和资格决定，后端仍复核。
10. **AI验证方式：** FV、FT(src/views/workorder/repair-draft.test.ts)，真实任务和附件读取。
11. **我的手测步骤：** 接/拒两单、追加进度、提交结果；换其他人员查看历史范围。
12. **所需配置或外部依赖：** 已有浏览器；File内存草稿不依赖3D渲染生命周期。
13. **状态：** 已通过用户验收（2026-10-18，用户原文“B05验收通过”）；B06当前进展见T037—T040（本段为B05验收记录）。
14. **实际验证记录：** 真实任务第2页及返工排序／有限历史浏览器，草稿在途跨轮send与check、上传锁／UNKNOWN反馈有效RED→GREEN。 最终后端verify128/128、前端21文件143/143及typecheck/build退出0；完整命令、源码SHA256、DB／图片、浏览器、清理与未测项见tests/evidence/B05-verification.json、B05-runtime.json、B05-code-review.md、B05-acceptance.md。无Git／OCR非Git失败如实保留；2026-10-18用户已确认B05验收通过，含数量增量；最新132／159条及反馈来源见B05-admin-order-count-verification.json、B05-user-acceptance.json。

---

<a id="t035"></a>

### T035：我的工单、本轮核对及验收页面

1. **编号：** T035
2. **目标：** R§19.2、16.3；REQ-09/13。
3. **前置任务：** T033、T034。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 待验收优先、详情/历史轮次、通过或退回前确认及失败保留原因。
5. **明确不做：** 列表按钮直接验收、使用旧轮材料、管理员验收入口。
6. **允许修改文件：** F/src/views/workorder/MyWorkOrdersView.vue、WorkOrderDetailView.vue、AcceptancePanel.vue；F/src/api/workorder.ts；F/src/stores/drafts.ts；F/src/views/workorder/acceptance.test.ts。
7. **接口与衔接：** 消费T033及统一详情；键userId+workOrderId+acceptance+round，链接申请/工单互返。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `acceptance.test.ts: confirmation_precedes_acceptance`，断言 `expect(acceptanceRequestsBeforeConfirmation).toBe(0)`。写acceptance.test.ts：旧round禁提交；确认前请求次数0；网络未知结果不清原因。
   - [x] 步骤2：实现本轮结果/图优先和歷次折叠、验收确认、成功后重查。
   - [x] 步骤3：浏览器验证第一轮退回/第二轮通过、无权限及返回筛选页码。

9. **验收标准：** 提交维修不显示已完成；仅原报修人能操作，多个轮次不把旧凭证当当前。
10. **AI验证方式：** FV、FT(src/views/workorder/acceptance.test.ts)，真实验收与DB重查。
11. **我的手测步骤：** 进入待验收核对图→退回→第二轮通过，查看完整历史。
12. **所需配置或外部依赖：** 原报修人实际登录。
13. **状态：** 已通过用户验收（2026-10-18，用户原文“B05验收通过”）；B06当前进展见T037—T040（本段为B05验收记录）。
14. **实际验证记录：** confirmation_precedes_acceptance及确认期锁、旧轮意见回调隔离；浏览器第一轮退回、第二轮新凭证通过和320px键盘取消／确认。 最终后端verify128/128、前端21文件143/143及typecheck/build退出0；完整命令、源码SHA256、DB／图片、浏览器、清理与未测项见tests/evidence/B05-verification.json、B05-runtime.json、B05-code-review.md、B05-acceptance.md。无Git／OCR非Git失败如实保留；2026-10-18用户已确认B05验收通过，含数量增量；最新132／159条及反馈来源见B05-admin-order-count-verification.json、B05-user-acceptance.json。

---

<a id="t036"></a>

### T036：管理员工单管理和重新分派页面

1. **编号：** T036
2. **目标：** R§19.4、16.1；REQ-09/16。
3. **前置任务：** T027、T031、T034、T035。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 待接/待重派优先、原因与分派历史、合法重派确认、接单后只读。
5. **明确不做：** 接单后改派、代验收、独立另建派单页面规则。
6. **允许修改文件：** F/src/views/workorder/WorkOrderManagementView.vue、WorkOrderManagementDetailView.vue；F/src/components/workorder/AssignmentPanel.vue；F/src/api/workorder.ts；F/src/router/index.ts。
7. **接口与衔接：** 复用T029 AssignmentPanel与T031 assign；详情按统一WorkOrderDetailVo；T027设备历史此时补真实联调。
8. **实施步骤：**

   - [x] 步骤1：接真实列表、人员搜索/分页/状态、原负责人/拒单/改派历史。
   - [x] 步骤2：仅合法状态显示分派表单，曾拒单明确确认，失败保留选择。
   - [x] 步骤3：验证申请/工单/设备之间返回路径、真实当前/历史排序以及接单后只读。

9. **验收标准：** 重派原编号不变；接单后所有维修/验收记录只读；设备历史不再是未接入占位。
10. **AI验证方式：** FV，真实浏览器重派/失败/历史链接与台账历史复核。
11. **我的手测步骤：** 从设备和申请进入工单，改派后原人任务移除，历史保留。
12. **所需配置或外部依赖：** 真实管理员及至少两个维修人员；推荐尚未加入可人工选。
13. **状态：** 已通过用户验收（2026-10-18，用户原文“B05验收通过”）；B06当前进展见T037—T040（本段为B05验收记录）。
14. **实际验证记录：** 管理员人员搜索／完整名单／曾拒单确认／接后只读；申请—工单—台账往返复用原路由及返回上下文真实浏览器。 最终后端verify128/128、前端21文件143/143及typecheck/build退出0；完整命令、源码SHA256、DB／图片、浏览器、清理与未测项见tests/evidence/B05-verification.json、B05-runtime.json、B05-code-review.md、B05-acceptance.md。无Git／OCR非Git失败如实保留；2026-10-18用户已确认B05验收通过，含数量增量；最新132／159条及反馈来源见B05-admin-order-count-verification.json、B05-user-acceptance.json。

---

<a id="t037"></a>

### T037：按权限的位置汇总与事项列表

1. **编号：** T037
2. **目标：** R§23；A§7、19；REQ-10/11。
3. **前置任务：** T033。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 一致快照查询未完结结构化事项、阶段/主类别/数量、位置列表与分页。
5. **明确不做：** 红灯独立表、无权匿名数量、按显示名合并、历史任务混当前。
6. **允许修改文件：** J/campus/controller/CampusBusinessController.java、service/CampusSummaryService.java、mapper/CampusBusinessMapper.java、vo/LocationSummaryVo.java、LocationItemVo.java；JR/mapper/campus/CampusBusinessMapper.xml；JT/campus/CampusSummaryIT.java。
7. **接口与衔接：** §2.2 summaries/items；先权限后分组，counts中普通处理中与返工互斥；queryAt一致。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `CampusSummaryIT.same_location_only_counts_owned_items`，断言 `assertEquals(1, reporterSummary.visibleTotal())`。写SummaryIT：同位置两报修人各count本人；批准申请只算工单一次；不可见项不影响category。
   - [x] 步骤2：用RR只读事务聚合，待重派不归原拒单人；LEGACY_TEXT/终态排除。
   - [x] 步骤3：验证优先级故障→验收→补充→审核，一条完成不清其他项；有限资格人员无任务数据。

9. **验收标准：** 一个位置一个汇总；编号/名称/阶段/时间及allowed入口只在允许范围。
10. **AI验证方式：** BT(CampusSummaryIT)手工固定期望数据＋真实SQL总数/分页；无N+1初检。
11. **我的手测步骤：** 同一房间由两用户报修；分别登录查看数量/颜色，管理员看全部。
12. **所需配置或外部依赖：** 真实业务记录和一致快照；不需要消息中间件。
13. **状态：** 内部验证完成，所属B06待用户验收。
14. **实际验证记录：** 固定权限断言RED403及变异RED→9项真实HTTP/MySQL GREEN；RR只读事务交错提交仍一致，汇总2查询／事项4查询无随条目增加；最终verify141退出0。见T037/sql-snapshot-and-query-count.json及B06-verification.json。

---

<a id="t038"></a>

### T038：DOM空间提示、事项面板与共用业务入口

1. **编号：** T038
2. **目标：** R§22—23；A§18—19；REQ-10/11/19。
3. **前置任务：** T018、T024、T034、T035、T036、T037。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 锚点投影、一位置一DOM标记、文字数量/图例与目录同色；按2026-10-19用户批注，单条有权事项新读唯一后直达共用详情，多条先选择。
5. **明确不做：** 全量业务传前端再隐藏、闪烁、点击提示自动操作。
6. **允许修改文件：** F/src/components/campus/LocationSummaryMarkers.vue、LocationItemsPanel.vue、BusinessPanel.vue；F/src/api/campus.ts；F/src/campus/CampusRenderer.ts；F/src/campus/summary-view.test.ts。
7. **接口与衔接：** T037汇总/事项＋projectAnchor(locationId)；面板复用T024/T034/T035，不另造3D业务API。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `summary-view.test.ts: marker_click_only_opens_items`，断言 `expect(mutationRequestsAfterMarkerClick).toBe(0)`。写summary-view.test.ts：同位置只一提示，空授权列表文案正确，点击不提交请求。
   - [x] 步骤2：实现DOM静态颜色/文字/数量、键盘/触控入口和面板→详情返回当前位置。
   - [x] 步骤3：真实首层多事项/多账号测试，公共空间只观察，投影仪无模型仍显示合法事项。

9. **验收标准：** 不可见记录不影响任何颜色/数量/按钮；完成仅移除单项，历史从列表进。
10. **AI验证方式：** FV、FT(src/campus/summary-view.test.ts)，实际几何锚点/业务权限画面核对。
11. **我的手测步骤：** 点击单条彩色标识直达详情，多条先选择；处理/查看后返回原位置；目录与标识同色，点击不提交业务。
12. **所需配置或外部依赖：** 已有浏览器；Canvas需真实画面取证。
13. **状态：** 内部验证及真实浏览器取证完成，所属B06待用户验收。
14. **实际验证记录：** 固定marker点击0写请求变异RED／组件GREEN；真实Canvas、文字图例、多账号、空列表、键盘320px、无模型投影仪与共用入口已取证。R1普通新报修返回已修，组件3项及108室内真实往返通过；新增标识路由/目录状态10项、三端真实单条直达及多条选择通过。固定测试名保留原断言：0业务写入；最新入口语义按用户批注覆盖原一律打开列表约定。

---

<a id="t039"></a>

### T039：10秒读取、草稿保护与不确定结果核对

1. **编号：** T039
2. **目标：** R§23.3、24.3；A§19—20；REQ-12/13。
3. **前置任务：** T038。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 前后台刷新、8秒只读超时、身份/目标/读取代次、失败/失权、操作标识核对。
5. **明确不做：** SSE/WebSocket、轮询覆盖编辑、网络失败伪造全0、写超时生成新标识重试。
6. **允许修改文件：** F/src/composables/useCampusRefresh.ts、useMutationConfirmation.ts；F/src/stores/drafts.ts；F/src/api/client.ts；F/src/views/campus/CampusView.vue；F/src/composables/campus-refresh.test.ts、mutation-confirmation.test.ts。
7. **接口与衔接：** 读取只更新只读状态/allowedActions；成功invalidate相关列表/汇总/详情/推荐；UNKNOWN冻结当前操作标识直到已授权原结果确认。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `campus-refresh.test.ts: failed_read_does_not_advance_success_time`，断言 `expect(lastSuccessAt).toBe(previousSuccessAt)`。写fake-clock测试：10秒触发不堆叠、恢复前台立即读、失败不改lastSuccess、账号切换旧响应丢弃。
   - [x] 步骤2：实现后台暂停、本人成功立即重读；403区分资格/记录失权与CSRF，401清身份及草稿。
   - [x] 步骤3：写丢响应测试：原标识核对后事件数=1；成功只清本次，仍可看但操作失效保草稿并禁旧提交。

9. **验收标准：** 正常条件≤30秒在T040/T064实测；网络保有权旧数据标过期，初次失败显示加载失败。
10. **AI验证方式：** FV、FT两命名文件；真实延迟/响应丢失/恢复前台，自动化时钟不能替代同步实测。
11. **我的手测步骤：** 两浏览器操作同事项，编辑中让另端改变状态；失网/恢复/切账号观察。
12. **所需配置或外部依赖：** 现有真实HTTP/浏览器；仅隔离验证环境故障注入。
13. **状态：** 内部验证完成，所属B06待用户验收。
14. **实际验证记录：** 固定失败时间RED／GREEN，10秒／8秒／单请求／身份目标代次／卸载／CSRF与失权已自动测；真实8.11秒超时、断网恢复、UNKNOWN及编辑禁旧操作、换身份旧响应取消。受控visibility注入12秒零查询／恢复读取，不冒领实体窗口最小化。DB原结果事件1条。

---

<a id="t040"></a>

### T040：文博1首层真实三角色闭环联调

1. **编号：** T040
2. **目标：** R§27 REQ-06—14；A§15、18—20。
3. **前置任务：** T029、T036、T039。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 首层模型、表单、台账、申请/工单/附件/提示/草稿真实贯通。
5. **明确不做：** 扩大楼层、示例成功反馈代后端、完整系统验收声明。
6. **允许修改文件：** tests/cases/sample-floor.md；tests/evidence/T040/本项证据；仅本项联调发现的既有文件必要修正（逐项记录）。
7. **接口与衔接：** 验证目录ID→manifest→API→DB同一身份；T041扩展须先取得所属B06用户验收。
8. **实施步骤：**

   - [x] 步骤1：真实用户房间报修→管理员审核派单→维修接单/结果→原人验收，逐状态核对提示和DB。
   - [x] 步骤2：补充改位置/拒单重派/返工/重复关闭各走一遍，观察≤30秒与本人立即同步。
   - [x] 步骤3：检查同位置两用户、同房不同设备、列表/3D草稿及有限历史图；形成证据与缺口表。

9. **验收标准：** 正常与异常沿原对象；初始示意画面、真实数据、模型/接口/DB证据分开。
10. **AI验证方式：** 相关BT/FV＋三角色真实浏览器；不对学校网站提交任何申请。
11. **我的手测步骤：** 按sample-floor.md重做主闭环和一次返工，确认后再扩大模板。
12. **所需配置或外部依赖：** 三个独立浏览器配置、真实dev/demo或隔离test及独立附件目录。
13. **状态：** 内部验证及真实浏览器取证完成，所属B06待用户验收。
14. **实际验证记录：** 六身份独立Cookie回环主机闭环，主流程及关联兼容API、补充迁移、撤回重复、拒重派接前改派、两轮返工、有限历史／图片／未合格观察；7组同步最终观察上界≤14.187秒，16图解码／6凭证42HTTP权限／12位置身份一致。原夹具中断无退出码，恢复登记夹具1项退出0并清空test；R1普通入口修复后108室内真实提交往返通过；最新三端单条直达、目录四色及完成去色通过，四组跨端上界1.923／15.413／17.094／13.815秒。新B06-comments夹具1项退出0并精确清空，最终后端141／前端195及类型/构建通过。

---

<a id="t041"></a>

### T041：文博1五层覆盖与首层/标准层差异

1. **编号：** T041
2. **目标：** R§21.2、22；A§18；REQ-03/06/18。
3. **前置任务：** T040。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 首层10+2公共、2—5层各12宿舍、楼梯衔接、跨层定位/资源复用。
5. **明确不做：** 房号推楼层、跨层数据共享、一次加载全部房间。
6. **允许修改文件：** F/src/campus/templates/DormFloor.ts、DormitoryExterior.ts；F/public/campus/manifest.json；F/src/campus/dorm-building.test.ts；plan/campus-inventory.md。
7. **接口与衔接：** 文博1共58宿舍＋2公共＝60观察位置、58可报修、464初始台账；各floorId显式。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `dorm-building.test.ts: complete_building_has_fifty_eight_rooms`，断言 `expect(dormRooms.length).toBe(58)`。写dorm-building.test.ts：5层/58宿舍/60观察，首层与标准层位置键不同。
   - [x] 步骤2：按配置生成4标准层与层高/锚点、遮挡隐藏，门动作实例独立。
   - [x] 步骤3：跨层快速切换、进/退/再进，检查当前位置业务、草稿及缓存有界。

9. **验收标准：** 五层各目标可进入，楼梯连续；本项内部验证通过后可在B07内复用其他楼，整批完成后统一等待用户验收。
10. **AI验证方式：** FV、FT(src/campus/dorm-building.test.ts)，五层抽查与目录逐项映射。
11. **我的手测步骤：** 101及201/312/512等显式位置来回进入，检查楼层选择与业务不串。
12. **所需配置或外部依赖：** 已验收首层模板；不新增模型依赖。
13. **状态：** B07已通过用户统一验收（2026-10-24；直接反馈见tests/evidence/B07-user-acceptance.json）。
14. **实际验证记录：** 2026-10-23，T041专用RED→GREEN与全量前端212项、typecheck/build退出0；JDK21/MySQL8.4.11 test-local后端verify 141项退出0。映射/浏览器/业务/许可/清理证据及未验证边界见tests/evidence/B07-verification.json、B07-progress.md、B07-acceptance.md。用户尚未验收；性能B12正式测，未逐间人工观察全部296内景。

---

<a id="t042"></a>

### T042：三栋宿舍复用与跨楼栋隔离

1. **编号：** T042
2. **目标：** R§21.1/2；REQ-03/04/06。
3. **前置任务：** T041。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 文博1/2/3完整外观和各五层内景，实例变换/锚点/门与数据隔离。
5. **明确不做：** 共享设备/申请/灯/门状态、减为样板交付。
6. **允许修改文件：** F/src/campus/templates/DormitoryExterior.ts、DormFloor.ts；F/public/campus/manifest.json；F/src/campus/dorm-instance.test.ts；plan/campus-inventory.md。
7. **接口与衔接：** 174宿舍+6公共＝180观察，174可报修，1392初始台账；配置code不同。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `dorm-instance.test.ts: same_room_label_has_distinct_ids`，断言 `expect(wenbo1Room101Id).not.toBe(wenbo2Room101Id)`。写dorm-instance.test.ts：文博1/2同号101的ID、门动作和提示互不相等。
   - [x] 步骤2：复用已内部验证通过的几何到两栋新实例，校验世界坐标与各层门口锚点。
   - [x] 步骤3：三个真实位置各报修/查询，逐项核对覆盖不共享业务和资源引用。

9. **验收标准：** 模板复用不共享业务身份；全部公共内景仍仅展示。
10. **AI验证方式：** FV、FT(src/campus/dorm-instance.test.ts)，三栋映射清单和真实跨栋用例。
11. **我的手测步骤：** 三栋101分别进入，提交一栋只在授权该位置提示。
12. **所需配置或外部依赖：** 已有几何及固定目录，不重新复制整套API。
13. **状态：** B07已通过用户统一验收（2026-10-24；直接反馈见tests/evidence/B07-user-acceptance.json）。
14. **实际验证记录：** 2026-10-23，T042专用RED→GREEN与全量前端212项、typecheck/build退出0；JDK21/MySQL8.4.11 test-local后端verify 141项退出0。映射/浏览器/业务/许可/清理证据及未验证边界见tests/evidence/B07-verification.json、B07-progress.md、B07-acceptance.md。用户尚未验收；性能B12正式测，未逐间人工观察全部296内景。

---

<a id="t043"></a>

### T043：三教学楼、教室/办公室及公共内景

1. **编号：** T043
2. **目标：** R§21.3/7；REQ-03/04/06。
3. **前置任务：** T042。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 致用/经世/博爱各五层、57教室15办公室30卫生间3门厅；设施布局。
5. **明确不做：** 投影仪建模要求、门厅/卫生间报修、讲台台账、教学102空调。
6. **允许修改文件：** F/src/campus/templates/TeachingBuilding.ts、Classroom.ts、Office.ts、TeachingCommon.ts；F/public/campus/manifest.json；F/src/campus/teaching-layout.test.ts；plan/campus-inventory.md。
7. **接口与衔接：** 105观察/72可报修/402台账；教室30桌30椅5×6、讲台电脑、1空调6灯4插座；办公室4套/1空调/2灯/4插座。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `teaching-layout.test.ts: classroom_and_office_counts`，断言 `expect(classrooms.length).toBe(57); expect(offices.length).toBe(15)`。写teaching-layout.test.ts：首层3教室标准层4；57/15/30/3总数与72空调。
   - [x] 步骤2：建立教室/办公/公共模板，两端楼梯、显式房号和独立入口/锚点。
   - [x] 步骤3：浏览器逐用途观察；无模型投影仪通过真实申请/匹配/提示，不开放公共报修。

9. **验收标准：** 教室6项/办公室4项按组；全部105处可观察；卫生间门厅无空调。
10. **AI验证方式：** FV、FT(src/campus/teaching-layout.test.ts)，三栋覆盖与投影仪真实闭环。
11. **我的手测步骤：** 看首层/标准层、桌椅和办公室数量，核对卫生间仅观察。
12. **所需配置或外部依赖：** 参数化/许可构件；资源副本按T015清单追加许可后使用。
13. **状态：** B07已通过用户统一验收（2026-10-24；直接反馈见tests/evidence/B07-user-acceptance.json）。
14. **实际验证记录：** 2026-10-23，T043专用RED→GREEN与全量前端212项、typecheck/build退出0；JDK21/MySQL8.4.11 test-local后端verify 141项退出0。映射/浏览器/业务/许可/清理证据及未验证边界见tests/evidence/B07-verification.json、B07-progress.md、B07-acceptance.md。用户尚未验收；性能B12正式测，未逐间人工观察全部296内景。

---

<a id="t044"></a>

### T044：三食堂九层公共区域内景

1. **编号：** T044
2. **目标：** R§21.4/7；REQ-03/05/06。
3. **前置任务：** T043。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 三栋各三层，12四人桌/48椅/4窗口/4立式空调/12灯，封闭后勤标识。
5. **明确不做：** 可进入厨房仓储、窗口/桌椅报修、具体角落必填、虚构房号。
6. **允许修改文件：** F/src/campus/templates/CafeteriaBuilding.ts、CafeteriaFloor.ts；F/public/campus/manifest.json；F/src/campus/cafeteria-layout.test.ts；plan/campus-inventory.md。
7. **接口与衔接：** 9独立楼层AREA，18项台账；每层空调组1+照明组1，36实体空调不建36条。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `cafeteria-layout.test.ts: all_nine_floor_areas`，断言 `expect(floorAreas.length).toBe(9)`。写cafeteria-layout.test.ts：9区域、每层12/48/4/4/12；reportable目标不是厨房。
   - [x] 步骤2：实现3×4餐桌/灯、四角空调、层入口和连续楼梯，单区域选择省无意义一层。
   - [x] 步骤3：真实楼层报修/提示验证不要求灯号或角落，后勤区不能进入。

9. **验收标准：** 全部9层内景可观察；桌椅/窗口只展示，地址明确食堂＋层。
10. **AI验证方式：** FV、FT(src/campus/cafeteria-layout.test.ts)，区域入口及分组台账核对。
11. **我的手测步骤：** 一食堂二层和三食堂三层观察，报修空调组无需单体。
12. **所需配置或外部依赖：** 已有Three包；不新增设施选择清单。
13. **状态：** B07已通过用户统一验收（2026-10-24；直接反馈见tests/evidence/B07-user-acceptance.json）。
14. **实际验证记录：** 2026-10-23，T044专用RED→GREEN与全量前端212项、typecheck/build退出0；JDK21/MySQL8.4.11 test-local后端verify 141项退出0。映射/浏览器/业务/许可/清理证据及未验证边界见tests/evidence/B07-verification.json、B07-progress.md、B07-acceptance.md。用户尚未验收；性能B12正式测，未逐间人工观察全部296内景。

---

<a id="t045"></a>

### T045：体育馆两层挑空、看台与分组设施

1. **编号：** T045
2. **目标：** R§21.5/7；REQ-03/05。
3. **前置任务：** T044。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 首层球场2篮球架12灯4空调，二层32座/回廊6灯2空调、两端楼梯。
5. **明确不做：** 第三层灯区、泳池/乒乓球/礼堂、看台座椅报修。
6. **允许修改文件：** F/src/campus/templates/GymBuilding.ts；F/public/campus/manifest.json；F/src/campus/gym-layout.test.ts；plan/campus-inventory.md。
7. **接口与衔接：** 2 AREA、首层3项/二层2项；18灯6空调，顶部灯归首层，篮球架合1组。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `gym-layout.test.ts: two_floor_areas_only`，断言 `expect(floorAreas.length).toBe(2); expect(standSeats.length).toBe(32)`。写gym-layout.test.ts：两层位置/5台账映射，32座/18灯/6空调，顶部无第三层ID。
   - [x] 步骤2：建立挑空和观赛回廊、两短边楼梯、区域入口与观察锚点。
   - [x] 步骤3：浏览器从两层观察结构/返回，真实报修不要求篮球架端或单灯号。

9. **验收标准：** 层高示意约相当于5层教学楼，两层均可进入；装饰不新增报修。
10. **AI验证方式：** FV、FT(src/campus/gym-layout.test.ts)，挑空/楼梯画面及目录/台账核对。
11. **我的手测步骤：** 球场和看台两处进入，检查32座和两层组台账。
12. **所需配置或外部依赖：** 已批准模板资源，单独内景按需加载。
13. **状态：** B07已通过用户统一验收（2026-10-24；直接反馈见tests/evidence/B07-user-acceptance.json）。
14. **实际验证记录：** 2026-10-23，T045专用RED→GREEN与全量前端212项、typecheck/build退出0；JDK21/MySQL8.4.11 test-local后端verify 141项退出0。映射/浏览器/业务/许可/清理证据及未验证边界见tests/evidence/B07-verification.json、B07-progress.md、B07-acceptance.md。用户尚未验收；性能B12正式测，未逐间人工观察全部296内景。

---

<a id="t046"></a>

### T046：完整10栋总览、环境与配置版本对齐

1. **编号：** T046
2. **目标：** R§21—22、26；A§14、18；REQ-03/18/19。
3. **前置任务：** T045。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 北教/东馆/南食/西宿、中央广场环路、克制装饰，完整定位/观察链。
5. **明确不做：** 实测地理宣称、加载全部296内景、配置不匹配自动重建DB。
6. **允许修改文件：** F/src/campus/CampusLayout.ts；F/src/campus/CampusRenderer.ts；F/src/views/campus/CampusView.vue；F/public/campus/manifest.json；F/src/campus/full-campus.test.ts；config/asset-license-manifest.json。
7. **接口与衔接：** manifest/campus配置版本启动核对；映射全部10/41/296/257；名称来自API/同稳定code，不猜模型名。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `full-campus.test.ts: ten_buildings_and_full_location_coverage`，断言 `expect(buildings.length).toBe(10); expect(locationIds.length).toBe(296)`。写full-campus.test.ts：10栋/41层/296覆盖/257报修映射全部唯一且无悬空目标。
   - [x] 步骤2：组装外观/道路环境、命名与总览复位；仅目标内景加载。
   - [x] 步骤3：模拟配置版本不匹配：3D明确不可用，列表/身份可用且不自动重置；完成覆盖清单。

9. **验收标准：** “示意校园”可见；3D入口不妨碍2秒内位置/列表可用，性能正式测T066。
10. **AI验证方式：** FV、FT(src/campus/full-campus.test.ts)，全栋/层/位置映射及浏览器覆盖。
11. **我的手测步骤：** 十栋总览逐一选楼/层/房间或区域→返回，检查不存在未覆盖楼栋。
12. **所需配置或外部依赖：** 许可清单仅分发可用资源；原素材不打入包。
13. **状态：** B07已通过用户统一验收（2026-10-24；直接反馈见tests/evidence/B07-user-acceptance.json）。
14. **实际验证记录：** 2026-10-23，T046专用RED→GREEN与全量前端212项、typecheck/build退出0；JDK21/MySQL8.4.11 test-local后端verify 141项退出0。映射/浏览器/业务/许可/清理证据及未验证边界见tests/evidence/B07-verification.json、B07-progress.md、B07-acceptance.md。用户尚未验收；性能B12正式测，未逐间人工观察全部296内景。

---

<a id="t047"></a>

### T047：规则推荐、一致快照与完整合格名单

1. **编号：** T047
2. **目标：** R§25；A§13.4、21；REQ-15/16。
3. **前置任务：** T031、T033。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 首次/接单前改派/待重派推荐，W/I/R/A、2L、上海今日D及稳定排序。
5. **明确不做：** AI、自动派单、技能画像、最大接单数硬限制、前5资格上限。
6. **允许修改文件：** J/workorder/controller/AssignmentCandidateController.java、service/AssignmentRecommendationService.java、mapper/AssignmentCandidateMapper.java、vo/AssignmentCandidateVo.java；JR/mapper/workorder/AssignmentCandidateMapper.xml；JT/workorder/AssignmentRecommendationIT.java。
7. **接口与衔接：** §2.2 assignment-candidates；context=FIRST_ASSIGN/CHANGE_BEFORE_ACCEPT/REASSIGN，对应requestId或workOrderId；推荐Top5和完整资格列表同快照。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `AssignmentRecommendationIT.same_load_fewer_completed_first`，断言 `assertEquals(List.of(oneCompletedId, fiveCompletedId), rankedIds)`。写RecommendationIT：assert排序L优先；同L今日5与1先1；2L=2W+4I+6R+A，I/R不重计。
   - [x] 步骤2：RR只读事务同后端时点取数，L→D→lastAssignedAt(null优先)→ID；旧文字工单计入。
   - [x] 步骤3：验证当前负责人改派排除、曾拒单不入优先但完整名单保留、无优先与无资格区分、上海跨日边界。

9. **验收标准：** 负荷高仍合格；最近分派含全部成功记录，拒单不删；状态不合法不能读改派推荐。
10. **AI验证方式：** BT(AssignmentRecommendationIT)，手算表与真实SQL结果逐行比较、分页前全量稳定排序。
11. **我的手测步骤：** 多于5合格人、5/1完成例、无资格/全曾拒单例，看完整列表仍可选。
12. **所需配置或外部依赖：** 同快照业务数据；不需要API Key、模型或外部推荐服务。
13. **状态：** 已通过用户验收（2026-10-25），来源tests/evidence/B08-user-acceptance.json。
14. **实际验证记录：** 见tests/evidence/B08-verification.json、B08-progress.md、B08-acceptance.md与B08-code-review.md；MySQL手算、组件、真实浏览器及用户验收分列。原浏览器夹具暂停期间超时退出1保留，test库/文件已实查清理；正常生命周期补验另记。B08已获用户验收；B09已授权，交接新对话执行。

---

<a id="t048"></a>

### T048：推荐面板、选择保留及资格变化

1. **编号：** T048
2. **目标：** R§25.4；A§19、21.3；REQ-15/16。
3. **前置任务：** T039、T047。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 既有AssignmentPanel增推荐Top5、全部搜索/分页、理由/时间、10秒刷新及失败人工选择。
5. **明确不做：** 刷新换已选人、接单后推荐、推荐失败冒用旧资格。
6. **允许修改文件：** F/src/components/workorder/AssignmentPanel.vue、AssignmentCandidateList.vue；F/src/api/workorder.ts；F/src/composables/useCampusRefresh.ts；F/src/components/workorder/assignment-selection.test.ts。
7. **接口与衔接：** 首次/改派/重派同组件消费T047；参考指标与资格列表错误分别反馈；最终仍调用T028/T031。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `assignment-selection.test.ts: reorder_keeps_valid_selection`，断言 `expect(selectedIdAfterReorder).toBe(previousSelectedId)`。写selection.test.ts：负荷重排保留eligible已选ID；失资格取消选人/禁提交，其余编辑保留。
   - [x] 步骤2：实现排名/字段/时点/展开全部/曾拒单确认，前台周期和跨自然日更新。
   - [x] 步骤3：验证推荐失败但实时资格名单成功可人工选；两者失败不缓存提交；提交再由后端复核。

9. **验收标准：** 无推荐不等于无资格；第6名可选；不同资格反馈准确，≤30秒实测。
10. **AI验证方式：** FV、FT(src/components/workorder/assignment-selection.test.ts)，真实三种上下文和资格变化。
11. **我的手测步骤：** 选一人后改负荷/停用/跨日，查看是否保留合法选择、阻止失效选择。
12. **所需配置或外部依赖：** 现有浏览器/服务端Clock；无额外MCP必需。
13. **状态：** 已通过用户验收（2026-10-25），来源tests/evidence/B08-user-acceptance.json。
14. **实际验证记录：** 见tests/evidence/B08-verification.json、B08-progress.md、B08-acceptance.md与B08-code-review.md；MySQL手算、组件、真实浏览器及用户验收分列。原浏览器夹具暂停期间超时退出1保留，test库/文件已实查清理；正常生命周期补验另记。B08已获用户验收；B09已授权，交接新对话执行。

---

<a id="t049"></a>

### T049：本人资料保存与自行修改密码

1. **编号：** T049
2. **目标：** R§2、3.3、12.10；A§7、10；REQ-01。
3. **前置任务：** T013、T033。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 按资格允许改姓名/手机号、改密校验当前密码、全会话撤销事务。
5. **明确不做：** 改账号/角色、待审核资料写、未通过绕重提、持久保存确认密码。
6. **允许修改文件：** J/account/controller/AccountSettingsController.java、service/AccountSettingsService.java、dto/UpdateProfileDto.java、ChangePasswordDto.java；JT/account/AccountSettingsIT.java。
7. **接口与衔接：** GET/PATCH /api/account/profile；POST /api/account/password；profile version/operationId，密码结果不提供明文查询。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `AccountSettingsIT.approved_profile_change_keeps_review_snapshot`，断言 `assertEquals(originalSnapshotPhone, savedSnapshotPhone)`。写SettingsIT：待审核/未通过资料拒绝，合格人员修改不重审且历史快照不变。
   - [x] 步骤2：保存最新资料，手机全局unique；改密先核对旧密码再更新哈希/revokeAll同事务。
   - [x] 步骤3：验证保存失败原值/会话不变，新密码重登，旧会话全部401。

9. **验收标准：** 真实DB可重新查；联系人快照不被更新；密码格式和确认规则一致。
10. **AI验证方式：** BT(AccountSettingsIT)，真实多会话Cookie/数据库重查及失败回滚。
11. **我的手测步骤：** 改资料后重查，再改密码，另一浏览器旧登录立即失效。
12. **所需配置或外部依赖：** 本人身份和明确外部数据库配置。
13. **状态：** 已通过用户统一验收（2026-10-26）；直接反馈见tests/evidence/B09-user-acceptance.json。
14. **实际验证记录：** 2026-10-25 Asia/Shanghai，JDK21/MySQL8.4.11；最终后端167/167、前端284/284、类型/构建退出0；真实DB/HTTP/失败回滚/会话/并发、本地维护命令及普通业务浏览器证据见[B09核验索引](../tests/evidence/B09-verification.json)、[执行账本](../tests/evidence/B09-progress.md)、[独立审阅](../tests/evidence/B09-code-review.md)。凭据UI输入/提交按工具规则留本人手测，未冒充浏览器通过；统一步骤见[B09验收清单](../tests/evidence/B09-acceptance.md)。dev全表/图片及13项保护源文件与基线一致，test21表/目录清空。2026-10-26用户统一验收通过；最新反馈见tests/evidence/B09-user-acceptance.json，历史凭据UI证据边界保留。

---

<a id="t050"></a>

### T050：账号设置、资料取消与改密确认页面

1. **编号：** T050
2. **目标：** R§12.10；REQ-01/19。
3. **前置任务：** T049。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 个人资料/修改密码标签、资格只读、失败留输入、成功清密码重登。
5. **明确不做：** 改登录账号/角色、密码放URL/缓存、无变化提交。
6. **允许修改文件：** F/src/views/account/AccountSettingsView.vue；F/src/api/account.ts；F/src/views/account/account-settings.test.ts；F/src/layouts/MainLayout.vue。
7. **接口与衔接：** 调用T049；本人菜单三角色可达，未通过资料从审核页重提。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `account-settings.test.ts: unchanged_profile_does_not_submit`，断言 `expect(saveCallsForUnchangedProfile).toBe(0)`。写settings.test.ts：无变化请求0；取消还原；成功清密码并退出旧会话显示重登。
   - [x] 步骤2：实现格式/唯一性错误、密码显示隐藏、影响确认及返回修改保留输入。
   - [ ] 步骤3：真实浏览器三个资格分支/窄屏，失败不伪造成功。

9. **验收标准：** 资料保存后顶部姓名同步；改密成功登录页密码留空，网络未确认不宣称完成。
10. **AI验证方式：** FV、FT(src/views/account/account-settings.test.ts)，真实资料/密码重查。
11. **我的手测步骤：** 改名/改手机、取消、占用错误，再自行改密并重新登录。
12. **所需配置或外部依赖：** 已有工具；不需短信找回。
13. **状态：** 已通过用户统一验收（2026-10-26）；直接反馈见tests/evidence/B09-user-acceptance.json。
14. **实际验证记录：** 2026-10-25 Asia/Shanghai，JDK21/MySQL8.4.11；最终后端167/167、前端284/284、类型/构建退出0；真实DB/HTTP/失败回滚/会话/并发、本地维护命令及普通业务浏览器证据见[B09核验索引](../tests/evidence/B09-verification.json)、[执行账本](../tests/evidence/B09-progress.md)、[独立审阅](../tests/evidence/B09-code-review.md)。凭据UI输入/提交按工具规则留本人手测，未冒充浏览器通过；统一步骤见[B09验收清单](../tests/evidence/B09-acceptance.md)。dev全表/图片及13项保护源文件与基线一致，test21表/目录清空。2026-10-26用户统一验收通过；最新反馈见tests/evidence/B09-user-acceptance.json，历史凭据UI证据边界保留。

---

<a id="t051"></a>

### T051：动态人员分类、数量与完整历史查询

1. **编号：** T051
2. **目标：** R§5—7、12.1/2/7/8；A§13.3；REQ-02/11。
3. **前置任务：** T033、T049。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 全部人员、四类快捷数、组合筛选、角色业务数、完整人员业务摘要/历史投影。
5. **明确不做：** 分类永久身份、分类数相加、把仅曾分派计本人完成。
6. **允许修改文件：** J/account/controller/PersonnelController.java、service/PersonnelQueryService.java、mapper/PersonnelQueryMapper.java、vo/PersonnelVo.java、PersonnelDetailVo.java；JR/mapper/account/PersonnelQueryMapper.xml；JT/account/PersonnelQueryIT.java。
7. **接口与衔接：** GET /api/admin/personnel及/{id}/history；listTotal/allPeopleTotal/categoryCounts分开；详情分类/状态总数不随列表筛选继承。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `PersonnelQueryIT.overlapping_categories_do_not_duplicate_total`，断言 `assertEquals(distinctAccountCount, allPeopleTotal)`。写PersonnelQueryIT：一人同时未完结/已完成，total去重，类别人数不随组合筛选改变。
   - [x] 步骤2：聚合独立申请/工单/实际成果，覆盖无业务、待审核、未通过、停用及管理员。
   - [x] 步骤3：验证详情各分类总数/筛选数/摘要独立；曾拒单/改派与实际维修关系清晰。

9. **验收标准：** 已通过申请按工单算一次；仅历史分派没有当前负荷/本人完成，管理员不套普通数量。
10. **AI验证方式：** BT(PersonnelQueryIT)，固定数据手算＋真实SQL对比分页/总数。
11. **我的手测步骤：** 查看新账号、停用账号和有重叠业务人员的总数/完整历史。
12. **所需配置或外部依赖：** 真实业务样本；管理员全局权限仍不扩大普通角色。
13. **状态：** 已通过用户统一验收（2026-10-26）；直接反馈见tests/evidence/B09-user-acceptance.json。
14. **实际验证记录：** 2026-10-25 Asia/Shanghai，JDK21/MySQL8.4.11；最终后端167/167、前端284/284、类型/构建退出0；真实DB/HTTP/失败回滚/会话/并发、本地维护命令及普通业务浏览器证据见[B09核验索引](../tests/evidence/B09-verification.json)、[执行账本](../tests/evidence/B09-progress.md)、[独立审阅](../tests/evidence/B09-code-review.md)。凭据UI输入/提交按工具规则留本人手测，未冒充浏览器通过；统一步骤见[B09验收清单](../tests/evidence/B09-acceptance.md)。dev全表/图片及13项保护源文件与基线一致，test21表/目录清空。2026-10-26用户统一验收通过；最新反馈见tests/evidence/B09-user-acceptance.json，历史凭据UI证据边界保留。

---

<a id="t052"></a>

### T052：账号启停用限制与普通账号随机重置

1. **编号：** T052
2. **目标：** R§8、12.4/9；A§7.3、10；REQ-01/02。
3. **前置任务：** T051。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 未完结业务阻止停用、恢复不复活旧会话、管理员随机重置普通密码。
5. **明确不做：** 删除账号、自停用、该HTTP重置管理员、默认初登强制改密。
6. **允许修改文件：** J/account/service/AccountAdministrationService.java；J/account/controller/PersonnelController.java；J/account/dto/AccountStatusDto.java、ResetPasswordDto.java；J/account/vo/PasswordResetResultVo.java；JT/account/AccountAdministrationIT.java。
7. **接口与衔接：** POST /api/admin/accounts/{id}/enable、disable、reset-password；最终用户行锁复核全部业务；随机密码仅本次成功响应。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `AccountAdministrationIT.unfinished_business_blocks_disable`，断言 `assertEquals(409, disableWithUnfinishedBusinessResponse.statusCode())`。写AdministrationIT：有未完结阻止并列编号；账号/手机号停用后仍占用；自停用和管理员HTTP重置拒绝。
   - [x] 步骤2：共同锁用户，状态/会话撤销或哈希/revokeAll同事务；新申请/派单共享此锁。
   - [x] 步骤3：模拟响应丢失：不保存可事后查询的明文随机密码，提示结果待确认/本地或再次明确重置处理，不伪造失败。

9. **验收标准：** 无业务才停用；恢复仅登录资格，不审核通过；随机密码符合规则且DB先提交再返回。
10. **AI验证方式：** BT(AccountAdministrationIT)，多会话及真实业务阻止/失败回滚。
11. **我的手测步骤：** 停用空账号、恢复、旧Cookie仍无效；有业务账号停用被阻止。
12. **所需配置或外部依赖：** 管理员真实登录；秘密不写普通日志和操作结果永久存储。
13. **状态：** 已通过用户统一验收（2026-10-26）；直接反馈见tests/evidence/B09-user-acceptance.json。
14. **实际验证记录：** 2026-10-25 Asia/Shanghai，JDK21/MySQL8.4.11；最终后端167/167、前端284/284、类型/构建退出0；真实DB/HTTP/失败回滚/会话/并发、本地维护命令及普通业务浏览器证据见[B09核验索引](../tests/evidence/B09-verification.json)、[执行账本](../tests/evidence/B09-progress.md)、[独立审阅](../tests/evidence/B09-code-review.md)。凭据UI输入/提交按工具规则留本人手测，未冒充浏览器通过；统一步骤见[B09验收清单](../tests/evidence/B09-acceptance.md)。dev全表/图片及13项保护源文件与基线一致，test21表/目录清空。2026-10-26用户统一验收通过；最新反馈见tests/evidence/B09-user-acceptance.json，历史凭据UI证据边界保留。

---

<a id="t053"></a>

### T053：完整人员列表、详情与查询返回

1. **编号：** T053
2. **目标：** R§12.1—8；REQ-01/02/19。
3. **前置任务：** T014、T051、T052。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 全部人员/四类、待审入口、组合筛选、启停用、完整详情分类/摘要/链接返回。
5. **明确不做：** 新增/删除普通账号、详情继承快捷分类缩小范围、统计错用筛选人数。
6. **允许修改文件：** F/src/views/account/PersonnelListView.vue、PersonnelDetailView.vue；F/src/api/account.ts；F/src/router/index.ts；F/src/views/account/personnel-navigation.test.ts。
7. **接口与衔接：** 复用T014审核详情；人员列表查询和详情内查询分开保存，关联详情返回二者各自位置。
8. **实施步骤：**

   - [x] 步骤1：写navigation.test.ts：查看待审清旧过滤；切快捷分类清role/review留启停用/搜索；详情返回不串筛选。
   - [x] 步骤2：实现全部字段/核心数/分类重叠提示/摘要及历史关系，启停用确认与编号阻止。
   - [x] 步骤3：验证无业务/停用/预置管理员、未知人员、加载/无权/空结果和窄屏。

9. **验收标准：** 角色选择不适用审核筛选时清除禁用；状态变化旧操作停止；返回恢复页码且数据最新。
10. **AI验证方式：** FV、FT(src/views/account/personnel-navigation.test.ts)，真实人员分类与历史链接。
11. **我的手测步骤：** 组合筛选→审核/详情→申请/工单→逐层返回，核对原页码和筛选。
12. **所需配置或外部依赖：** 现有浏览器；无需新的管理框架。
13. **状态：** 已通过用户统一验收（2026-10-26）；直接反馈见tests/evidence/B09-user-acceptance.json。
14. **实际验证记录：** 2026-10-25 Asia/Shanghai，JDK21/MySQL8.4.11；最终后端167/167、前端284/284、类型/构建退出0；真实DB/HTTP/失败回滚/会话/并发、本地维护命令及普通业务浏览器证据见[B09核验索引](../tests/evidence/B09-verification.json)、[执行账本](../tests/evidence/B09-progress.md)、[独立审阅](../tests/evidence/B09-code-review.md)。凭据UI输入/提交按工具规则留本人手测，未冒充浏览器通过；统一步骤见[B09验收清单](../tests/evidence/B09-acceptance.md)。dev全表/图片及13项保护源文件与基线一致，test21表/目录清空。2026-10-26用户统一验收通过；最新反馈见tests/evidence/B09-user-acceptance.json，历史凭据UI证据边界保留。

---

<a id="t054"></a>

### T054：普通账号一次性重置密码结果页面

1. **编号：** T054
2. **目标：** R§12.9；REQ-01/02。
3. **前置任务：** T052、T053。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 核对目标/影响、随机结果默认隐藏、复制回退、离开确认清明文。
5. **明确不做：** 事后明文查询、缓存/URL保存、复制失败当重置失败。
6. **允许修改文件：** F/src/views/account/PasswordResetView.vue；F/src/api/account.ts；F/src/views/account/password-reset.test.ts。
7. **接口与衔接：** 仅普通账号详情可达，consume PasswordResetResultVo；成功页面关闭后不可再读此次明文。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `password-reset.test.ts: copy_failure_does_not_reset_again`，断言 `expect(resetRequestsAfterCopyFailure).toBe(1)`。写reset.test.ts：默认隐藏；关闭清空；复制失败不触发第二次重置。
   - [x] 步骤2：实现核对/确认/取消/本次显示复制与关闭清除提示；失败不显示可用新密码。
   - [ ] 步骤3：验证忘保留后由管理员再次明确重置，上次密码失效；UNKNOWN提示核对且无自动再发。

9. **验收标准：** 密码只在本次内存结果；不会写Pinia持久缓存、日志或截图证据。
10. **AI验证方式：** FV、FT(src/views/account/password-reset.test.ts)，实际重置/DB旧会话失效检查。
11. **我的手测步骤：** 重置普通账号→手动复制→关闭→新密码登录；旧密码无效。
12. **所需配置或外部依赖：** 剪贴板权限失败提供手动选择；无短信/邮件服务。
13. **状态：** 已通过用户统一验收（2026-10-26）；直接反馈见tests/evidence/B09-user-acceptance.json。
14. **实际验证记录：** 2026-10-25 Asia/Shanghai，JDK21/MySQL8.4.11；最终后端167/167、前端284/284、类型/构建退出0；真实DB/HTTP/失败回滚/会话/并发、本地维护命令及普通业务浏览器证据见[B09核验索引](../tests/evidence/B09-verification.json)、[执行账本](../tests/evidence/B09-progress.md)、[独立审阅](../tests/evidence/B09-code-review.md)。凭据UI输入/提交按工具规则留本人手测，未冒充浏览器通过；统一步骤见[B09验收清单](../tests/evidence/B09-acceptance.md)。dev全表/图片及13项保护源文件与基线一致，test21表/目录清空。2026-10-26用户统一验收通过；最新反馈见tests/evidence/B09-user-acceptance.json，历史凭据UI证据边界保留。

---

<a id="t055"></a>

### T055：管理员专用本地密码重置

1. **编号：** T055
2. **目标：** R§4；A§14.4；REQ-01。
3. **前置任务：** T049、T052。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** admin-reset本地显式命令，随机新密码、安全哈希和全部旧会话撤销。
5. **明确不做：** 公开HTTP重置、删除重建身份、重复init覆盖密码。
6. **允许修改文件：** J/maintenance/AdminResetCommand.java；J/account/service/AdminPasswordMaintenanceService.java；scripts/admin-reset.ps1；JT/account/AdminResetIT.java；README.md。
7. **接口与衔接：** 复用T009 PasswordSupport及T011 revokeAll；本地runner无web、目标预置管理员ID保留。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `AdminResetIT.local_reset_keeps_original_identity`，断言 `assertEquals(originalAdminId, adminIdAfterReset)`。写AdminResetIT：userId/关联历史不变，旧Cookie全部401，新密码matches为true。
   - [x] 步骤2：仅维护命令允许更新管理员哈希与会话，成功后本次一次性显示。
   - [x] 步骤3：验证普通启动和公开HTTP没有重置入口，失败不改哈希/会话。

9. **验收标准：** init仍幂等跳过原管理员；遗忘走reset；不存在事后查询密码。
10. **AI验证方式：** BT(AdminResetIT)，本地命令实际执行和DB/真实HTTP会话复查。
11. **我的手测步骤：** 本地重置后管理员新密码登录，检查历史和原账号仍在。
12. **所需配置或外部依赖：** 维护者本地权限及明确目标库；日志/测试报告不含密码。
13. **状态：** 已通过用户统一验收（2026-10-26）；直接反馈见tests/evidence/B09-user-acceptance.json。
14. **实际验证记录：** 2026-10-25 Asia/Shanghai，JDK21/MySQL8.4.11；最终后端167/167、前端284/284、类型/构建退出0；真实DB/HTTP/失败回滚/会话/并发、本地维护命令及普通业务浏览器证据见[B09核验索引](../tests/evidence/B09-verification.json)、[执行账本](../tests/evidence/B09-progress.md)、[独立审阅](../tests/evidence/B09-code-review.md)。凭据UI输入/提交按工具规则留本人手测，未冒充浏览器通过；统一步骤见[B09验收清单](../tests/evidence/B09-acceptance.md)。dev全表/图片及13项保护源文件与基线一致，test21表/目录清空。2026-10-26用户统一验收通过；最新反馈见tests/evidence/B09-user-acceptance.json，历史凭据UI证据边界保留。

---

<a id="t056"></a>

### T056：旧文字记录、位置维护与名称修正兼容

1. **编号：** T056
2. **目标：** R§24.4；A§17.3；REQ-14。
3. **前置任务：** T028、T033、T026。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 旧无位置申请补充/审核/维修、旧文字设备显式定位、名称修正/移动历史与派单竞态。
5. **明确不做：** 批量补绑旧申请、删重建空间、移动未完结设备、完成工单随设备迁移。
6. **允许修改文件：** J/equipment/service/EquipmentService.java；J/campus/service/LocationNameMaintenanceService.java；J/maintenance/CorrectLocationNameCommand.java；scripts/correct-location-name.ps1；JT/campus/LocationCompatibilityIT.java；plan/data-dictionary.md。
7. **接口与衔接：** correct-location-name只修同稳定ID名称且留历史；新提交当前名/历史快照原名；工单位置固定取审核时申请。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `LocationCompatibilityIT.legacy_request_does_not_gain_location`，断言 `assertNull(approvedLegacyWorkOrderLocationId)`。写CompatibilityIT：旧申请匹配有位置设备仍无提示；补充不变旧模式，新建伪造旧模式拒绝。
   - [x] 步骤2：完成同设备共同锁及移动/名称修正历史；明确旧台账维护保原地址。
   - [x] 步骤3：并发移动与审核仅允许一致结果；完成后移动设备，旧工单/快照/历史提示不迁。

9. **验收标准：** 旧文字仍正常闭环和参与统计/推荐；无新增空间管理后台或解析地址。
10. **AI验证方式：** BT(LocationCompatibilityIT)、ApproveAndAssignIT相关回归；真实双事务竞态。
11. **我的手测步骤：** 处理旧文字单→台账明确定位→完成后移动→看旧工单原地址/位置。
12. **所需配置或外部依赖：** 文字模式种子仅测试/明确演示初始化；正常新接口不能生成旧例外。
13. **状态：** 已通过用户统一验收（2026-10-26）；直接反馈见tests/evidence/B09-user-acceptance.json。
14. **实际验证记录：** 2026-10-25 Asia/Shanghai，JDK21/MySQL8.4.11；最终后端167/167、前端284/284、类型/构建退出0；真实DB/HTTP/失败回滚/会话/并发、本地维护命令及普通业务浏览器证据见[B09核验索引](../tests/evidence/B09-verification.json)、[执行账本](../tests/evidence/B09-progress.md)、[独立审阅](../tests/evidence/B09-code-review.md)。凭据UI输入/提交按工具规则留本人手测，未冒充浏览器通过；统一步骤见[B09验收清单](../tests/evidence/B09-acceptance.md)。dev全表/图片及13项保护源文件与基线一致，test21表/目录清空。2026-10-26用户统一验收通过；最新反馈见tests/evidence/B09-user-acceptance.json，历史凭据UI证据边界保留。

---

<a id="t057"></a>

### T057：三角色待办工作台与普通入口

1. **编号：** T057
2. **目标：** R§19.1；REQ-09/19。
3. **前置任务：** T048、T053、T056。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 报修待补/待验收、维修待接/返工/处理、管理员待审/待重派；独立列表入口。
5. **明确不做：** 无资格人员维修工作台、同业务重复计数、用校园替代原列表。
6. **允许修改文件：** J/account/service/WorkbenchQueryService.java、controller/WorkbenchController.java；JT/account/WorkbenchQueryIT.java；F/src/views/account/WorkbenchView.vue；F/src/api/workbench.ts；F/src/router/index.ts。
7. **接口与衔接：** GET /api/workbench按身份投影；计数复用原状态/责任口径，菜单由后端确认资格。
8. **实施步骤：**

   - [x] 步骤1：固定测试 `WorkbenchQueryIT.approved_request_not_counted_twice`，断言 `assertEquals(0, pendingRequestCountForApprovedRequest)`。写WorkbenchQueryIT：approved申请不再重复待处理；拒单不计原人任务，返工阶段互斥。
   - [x] 步骤2：实现真实待办摘要→列表→详情，导航保留校园/设备/人员/统计/账号。
   - [x] 步骤3：浏览器3D关闭/失败情况下从工作台独立完成业务，资格未过仍可审核页/校园。

9. **验收标准：** 优先级符合已确认页面；统计入口不挤入待办概览，零数据/失败区分。
10. **AI验证方式：** BT(WorkbenchQueryIT)、FV，三角色真实普通入口测试。
11. **我的手测步骤：** 仅通过普通列表完成一轮报修维修验收，核对待办数随状态变化。
12. **所需配置或外部依赖：** 无新增运行依赖；已有浏览器。
13. **状态：** 已通过用户统一验收（2026-10-26）；直接反馈见tests/evidence/B09-user-acceptance.json。
14. **实际验证记录：** 2026-10-25 Asia/Shanghai，JDK21/MySQL8.4.11；最终后端167/167、前端284/284、类型/构建退出0；真实DB/HTTP/失败回滚/会话/并发、本地维护命令及普通业务浏览器证据见[B09核验索引](../tests/evidence/B09-verification.json)、[执行账本](../tests/evidence/B09-progress.md)、[独立审阅](../tests/evidence/B09-code-review.md)。凭据UI输入/提交按工具规则留本人手测，未冒充浏览器通过；统一步骤见[B09验收清单](../tests/evidence/B09-acceptance.md)。dev全表/图片及13项保护源文件与基线一致，test21表/目录清空。2026-10-26用户统一验收通过；最新反馈见tests/evidence/B09-user-acceptance.json，历史凭据UI证据边界保留。

---

<a id="t058"></a>

### T058：真实统计聚合、时间边界和下钻

1. **编号：** T058
2. **目标：** R§18.1；A§13；REQ-17。
3. **前置任务：** T033、T051、T056。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 上海月完成量/均时、每日分布、设备生成频次、全部当前状态及对应明细。
5. **明确不做：** 统计缓存表、JOIN附件膨胀、按提交结果算完成、无样本均值0。
6. **允许修改文件：** J/statistics/controller/StatisticsController.java、service/StatisticsService.java、mapper/StatisticsMapper.java、vo/StatisticsVo.java；JR/mapper/statistics/StatisticsMapper.xml；JT/statistics/StatisticsIT.java。
7. **接口与衔接：** GET /api/statistics/dashboard?month=YYYY-MM及/details?metric=...；month作用域与ALL_CURRENT_STATE分开；平均允许null，queryAt明确。
8. **实施步骤：**

   - [ ] 步骤1：固定测试 `StatisticsIT.repair_duration_excludes_acceptance_wait`，断言 `assertEquals(7200000L, cumulativeRepairMillis)`。写StatisticsIT：首轮1h+等待3h+返工1h维修=2h；跨月按最终完成；同名设备不合并。
   - [ ] 步骤2：按工单预聚合轮次，RR快照生成同请求指标；明细复用权限/时间字段/样本规则。
   - [ ] 步骤3：验证上海月边界/空样本/缺时或负时异常，异常均值不按0或静默剔除。

9. **验收标准：** 响应acceptedAt-firstSubmittedAt；故障按createdAt；每日合计=月量；当前状态不随month改变。
10. **AI验证方式：** BT(StatisticsIT)手工期望、真实SQL、明细和异常日志脱敏。
11. **我的手测步骤：** 选择跨月和返工月份，逐工单核对指标及状态全量范围。
12. **所需配置或外部依赖：** 可控Clock及UTC DATETIME(3)映射；ECharts只显示结果。
13. **状态：** 已通过用户统一验收（2026-10-28）；见tests/evidence/B10-user-acceptance.json，历史验证限制保留。
14. **实际验证记录：** 后端StatisticsIT6/6及完整174/174，真实DB/HTTP与RR并发快照；B10-backend-regression.log、B10-boundary-hand-calculation.json。 用户尚未验收；本批最终汇总以tests/evidence/B10-verification.json为准。

---

<a id="t059"></a>

### T059：概览/分析看板、图表与返回下钻

1. **编号：** T059
2. **目标：** R§18.2；REQ-17/19。
3. **前置任务：** T058、T036。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 共用月份双标签、无样本—、查询时间、对应工单摘要/详情返回。
5. **明确不做：** 前端重新定义统计、旧月结果显示成新月、当前状态加月份过滤。
6. **允许修改文件：** F/src/views/statistics/StatisticsView.vue、StatisticsDetailsView.vue；F/src/api/statistics.ts；F/src/components/statistics/StatisticsCharts.vue；F/src/views/statistics/statistics-scope.test.ts。
7. **接口与衔接：** consume StatisticsVo；month与metric范围路由独立，返回保月/标签/明细；图表可文字读数。
8. **实施步骤：**

   - [ ] 步骤1：固定测试 `statistics-scope.test.ts: stale_month_response_is_discarded`，断言 `expect(displayedMonth).toBe('2026-10')`。写scope.test.ts：旧month晚到丢弃；null均值显示—；state明细不传month过滤。
   - [ ] 步骤2：实现概览指标、全部当前状态、分析每日/设备频次/样本与点击下钻。
   - [ ] 步骤3：浏览器真实指标核对、刷新失败标旧月份、无权清内容、窄屏数值/入口可用。

9. **验收标准：** 图表与明细数一致；空频次/暂无完成样本分别反馈，统计失效不伪造零。
10. **AI验证方式：** FV、FT(src/views/statistics/statistics-scope.test.ts)，真实看板及关联详情。
11. **我的手测步骤：** 切月份/标签、点指标→工单→返回，核对月和查询范围没变。
12. **所需配置或外部依赖：** P02已有ECharts；不新增BI服务。
13. **状态：** 已通过用户统一验收（2026-10-28）；见tests/evidence/B10-user-acceptance.json，历史验证限制保留。
14. **实际验证记录：** 前端完整295/295、typecheck/build，真实浏览器16案例及生命周期正常停止；B10-browser-verification.json。最终新增显示回归和独立审阅仍在收尾。 用户尚未验收；本批最终汇总以tests/evidence/B10-verification.json为准。

---

<a id="t060"></a>

### T060：跨模块事务、锁顺序与幂等并发回归

1. **编号：** T060
2. **目标：** A§10、15；REQ-08/09/13/14/16。
3. **前置任务：** T048、T052、T056、T058。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 真实独立连接/事务竞态、跨轮旧页、写成功丢响应、账户/设备共同锁。
5. **明确不做：** Mock线程替代DB竞态、锁全体候选、清正常库。
6. **允许修改文件：** JT/integration/ConcurrencyIT.java；tests/cases/concurrency.md；tests/evidence/T060/证据；必要修正限涉及Service/Mapper并逐项列入白名单。
7. **接口与衔接：** T002统一锁顺序；原operationId/content摘要/当前授权结果；所有新增事件和轮次同事务。
8. **实施步骤：**

   - [ ] 步骤1：固定测试 `ConcurrencyIT.only_one_conflicting_transition_commits`，断言 `assertEquals(1, successfulConflictingTransitionCount)`。写ConcurrencyIT：接单vs改派、撤回vs审核、通过vs退回，每对仅1有效分支。
   - [ ] 步骤2：测停用vs新报修/派单、设备停用/移动vs审核、绑定vs清理、改密vs登录，共同锁后二次复核。
   - [ ] 步骤3：模拟已提交丢响应/同标识异内容/新标识旧round；核对业务、事件、轮次及附件数量。

9. **验收标准：** 无重复工单/事件/凭证、无半成功、无旧密码验证后创建可继续用会话；不允许死锁重试重复业务。
10. **AI验证方式：** BT(ConcurrencyIT)，独立连接证据、DB最终行数、冲突HTTP code；verify相关回归。
11. **我的手测步骤：** 按并发案例用两浏览器同时操作，核对最终唯一状态与友好冲突提示。
12. **所需配置或外部依赖：** 受限test库/目录、可控屏障与Clock；不要在学校/未知库做并发测试。
13. **状态：** 未开始。
14. **实际验证记录：** 未执行；执行后填命令/退出码、环境、实际结果、证据索引、用户反馈及未验证原因。

---

<a id="t061"></a>

### T061：会话、CSRF、数据和历史附件安全回归

1. **编号：** T061
2. **目标：** R§13、26.3；A§6—7、11；REQ-01/11/17。
3. **前置任务：** T053、T054、T055、T059、T060。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 真实HTTP矩阵、Cookie/到期/撤销、URL猜测、历史材料截止、日志和缓存隔离。
5. **明确不做：** 凭模拟身份宣称全认证通过、保留敏感截屏、扩大历史范围。
6. **允许修改文件：** JT/security/AuthorizationMatrixIT.java、CookieLifecycleIT.java；tests/cases/authorization-matrix.md；tests/evidence/T061/证据。
7. **接口与衔接：** 角色×资格×归属×状态×轮次×分派期间投影；403资格/记录/CSRF区别，private/no-store。
8. **实施步骤：**

   - [ ] 步骤1：固定测试 `AuthorizationMatrixIT.other_user_cannot_read_attachment`，断言 `assertEquals(403, otherUserAttachmentResponse.statusCode())`。真实Cookie测试8小时到期/不续期/退出当前/改密全部/停用恢复不复活。
   - [ ] 步骤2：枚举他人位置列表/申请/工单/统计/operationId/附件/分页total猜测；不可见数据不得影响汇总颜色/数。
   - [ ] 步骤3：测拒单/改派后新材料403、实际维修者完结只读、账号切换/Object URL清理，检查日志无秘密。

9. **验收标准：** 未合格可观察无任务，公开管理员初始化/注册/重置不存在；错误不返回SQL/路径。
10. **AI验证方式：** BT(AuthorizationMatrixIT/CookieLifecycleIT)，真实HTTP和浏览器缓存/切账号证据。
11. **我的手测步骤：** 三角色加两个普通用户按矩阵核对同位置权限与历史图片。
12. **所需配置或外部依赖：** 隔离演示账号，不收集真实学校个人数据。
13. **状态：** 未开始。
14. **实际验证记录：** 未执行；执行后填命令/退出码、环境、实际结果、证据索引、用户反馈及未验证原因。

---

<a id="t062"></a>

### T062：加载超时、迟到结果与资源释放回归

1. **编号：** T062
2. **目标：** R§26.1；A§18.3/4；REQ-18/20。
3. **前置任务：** T046、T048、T039。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 整体/局部失败、15秒实际加载起算、重试代次、上下文丢失、两条模板缓存/引用释放。
5. **明确不做：** 15秒反馈代5秒性能、迟到覆盖新目标、整体失败强制跳列表。
6. **允许修改文件：** F/src/campus/ResourceCache.ts、LoadCoordinator.ts、CampusRenderer.ts；F/src/views/campus/CampusView.vue；F/src/campus/resource-lifecycle.test.ts、load-race.test.ts；tests/cases/model-failures.md。
7. **接口与衔接：** 按身份/目标/层级/generation验回调；cancel不可实现时丢旧结果且dispose；File草稿独立。
8. **实施步骤：**

   - [ ] 步骤1：固定测试 `resource-lifecycle.test.ts: interior_cache_is_bounded`，断言 `expect(interiorTemplateCacheCount).toBeLessThanOrEqual(2)`。写load-race.test.ts：排队不计15秒但进入5秒含排队；迟到旧/旧重试不覆盖。
   - [ ] 步骤2：写资源测试：最多2内景缓存，引用为0才释放geometry/material/texture，撤页停止监听/循环/计时器。
   - [ ] 步骤3：真实浏览器局部坏图、WebGL上下文丢失/恢复、30次切换/离开/重进，记录资源趋势和仍可列表操作。

9. **验收标准：** 超时后同目标同代次晚成可显示；权限快照不随恢复复活，页面不崩/重复提示。
10. **AI验证方式：** FV、FT两命名文件＋实际renderer.info/资源引用/堆趋势；调用dispose不足证明无泄漏。
11. **我的手测步骤：** 模拟局部失败→重试→换位置→回列表，草稿和位置仍正确。
12. **所需配置或外部依赖：** 浏览器DevTools优先；M03仅需要时批准，调试桥不能留正式包。
13. **状态：** 内部验证完成，待B11统一用户验收；外部图片下载场景不适用于当前参数化模板，长期堆结论有限。
14. **实际验证记录：** 2026-10-28，三个相关测试文件22/22及typecheck通过；真实浏览器30次切换、5轮离页重进、整体/局部失败、15秒及迟到、WebGL丢失恢复实测完成。证据及限制见tests/cases/model-failures.md、tests/evidence/T062/verification.json；用户验收未进行。

---

<a id="t063"></a>

### T063：320px、键盘、触控和减少动态效果

1. **编号：** T063
2. **目标：** R§22.4、19.5；A§18；REQ-19。
3. **前置任务：** T059、T062。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 全角色核心页面与校园的窄屏/标签/焦点/44×44目标/非颜色表达。
5. **明确不做：** 要求键盘控制全部3D几何、用简洁显示减少空间/业务。
6. **允许修改文件：** F/src/assets/main.css；F/src/layouts/MainLayout.vue；F/src/components/campus/LocationSelector.vue、BusinessPanel.vue、LocationSummaryMarkers.vue；必要页面样式逐项列明；tests/cases/accessibility.md。
7. **接口与衔接：** DOM选择器/位置列表提供等价进入；prefers-reduced-motion与显示设置不改变业务API/权限。
8. **实施步骤：**

   - [ ] 步骤1：1366×768及320/375/767px检查导航/选择/面板/图片/错误/确认按钮。
   - [ ] 步骤2：键盘完成注册、选位置、报修、派单、维修、验收，焦点可见；触控关键目标≥44×44。
   - [ ] 步骤3：减少动态直接切换、简洁显示保10栋/提示；颜色旁有文字/数字，输入滚动不牵动相机。

9. **验收标准：** 关键名称和按钮无遮挡，无悬停唯一入口，无闪烁；列表始终一键可达。
10. **AI验证方式：** FV＋真实浏览器/视口检查，必要自动化仅验证操作，不写纯布局重复测试。
11. **我的手测步骤：** 320px或手机窄窗和键盘完整走关键操作，检查文字/焦点。
12. **所需配置或外部依赖：** 现有浏览器；用户实际触控硬件未测则明确未测。
13. **状态：** 未开始。
14. **实际验证记录：** 未执行；执行后填命令/退出码、环境、实际结果、证据索引、用户反馈及未验证原因。

---

<a id="t064"></a>

### T064：真实浏览器端到端REQ-01—20回归

1. **编号：** T064
2. **目标：** R§27；A§15、22.3：端到端证据。
3. **前置任务：** T061、T063。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 真实后端/测试库三角色及异常、权限、草稿、提示、推荐、旧记录、统计回归。
5. **明确不做：** 离线JSON/Mock业务、对学校站点提交、把未测性能自动标过。
6. **允许修改文件：** F/playwright.config.ts；F/tests/e2e/account.spec.ts、repair-loop.spec.ts、campus-permissions.spec.ts、draft-sync.spec.ts、recommendation.spec.ts、legacy-statistics.spec.ts；tests/cases/acceptance-matrix.md；tests/evidence/T064/证据。
7. **接口与衔接：** test:e2e指向明确test后端和独立目录，脚本fixtures守卫test目标；REQ性能和最终交付另引用T066—T069。
8. **实施步骤：**

   - [ ] 步骤1：检查已有浏览器工具/脚本能力；需P05/M02时按具体清单获准后安装，能复用已装Chrome不默认下载。
   - [ ] 步骤2：跑正常闭环/退回/撤回/重复/拒单/改派/两轮验收，同位置双用户及跨楼同号。
   - [ ] 步骤3：验证前台≤30秒/本人立即/恢复前台、丢响应/失网/失权、推荐重排/失资格、旧文字/统计下钻。

9. **验收标准：** 每个REQ记录实际/预期/证据或未测原因；Canvas几何需要真实截图和操作，DOM自动化不足以证明。
10. **AI验证方式：** npm.cmd run test:e2e、FV及真实Cookie/DB/附件证据；REQ-20性能此项保持待T066/T067。
11. **我的手测步骤：** 按acceptance-matrix复核代表流程及失败反馈，测试通过再批准性能任务。
12. **所需配置或外部依赖：** P05推荐项目依赖或已有等价工具；隔离账号/库/浏览器配置，硬件实际记录。
13. **状态：** 未开始。
14. **实际验证记录：** 未执行；执行后填命令/退出码、环境、实际结果、证据索引、用户反馈及未验证原因。

---

<a id="t065"></a>

### T065：性能验收数据与测量脚本

1. **编号：** T065
2. **目标：** R§26.2；A§22.1；REQ-20准备。
3. **前置任务：** T064。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 显式演示/测试数据≥1000业务记录、最多5会话、五种内景代表目标、硬件/缓存/样本记录。
5. **明确不做：** 真实学生数据、给正常库自动清数据、生成数据即宣称性能达标。
6. **允许修改文件：** scripts/prepare-performance-data.ps1；tests/performance/dataset.json、measure-api.mjs、measure-browser.ts；plan/performance-protocol.md；tests/evidence/T065/种子与条件记录。
7. **接口与衔接：** 脚本先校验批准的demo/test库和目录，固定seed/分布/上海时钟；API测量p95用nearest-rank：排序后ceil(0.95*n)第k个。
8. **实施步骤：**

   - [ ] 步骤1：记录CPU/GPU/内存/Windows/浏览器完整版本/视口、服务配置、资源hash和冷/热缓存方法。
   - [ ] 步骤2：显式准备≥1000条业务记录，涵盖状态/责任/旧记录/轮次/多附件，建最多5个有效演示会话。
   - [ ] 步骤3：手算脚本小样本p95/FPS/帧间隔断言，记录失败请求计数；输出完整原始序列不删慢样本。

9. **验收标准：** 数据规模/代表目标/请求类别可复现；五种内景各至少2次，共≥10次，种子可明确辨识。
10. **AI验证方式：** 种子DB计数/分布、脚本小样本assert、测量配置读回；不运行未知目录清理。
11. **我的手测步骤：** 核对硬件/浏览器/数据表，确认演示账号无真实个人信息。
12. **所需配置或外部依赖：** 已有Node优先；E07 Python仅标准库条件可选；硬件加速/WebGL2必须实际确认。
13. **状态：** 未开始。
14. **实际验证记录：** 未执行；执行后填命令/退出码、环境、实际结果、证据索引、用户反馈及未验证原因。

---

<a id="t066"></a>

### T066：3D加载、交互与切换性能实测调优

1. **编号：** T066
2. **目标：** R§22.2、26.2；A§22；REQ-20。
3. **前置任务：** T062、T065。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 首屏/内景/热过渡/FPS/30切换/低FPS提示；按证据进行必要资源调优。
5. **明确不做：** 降10栋/内景范围、把热缓存写冷、列可用代3D性能通过、默认远程解码器。
6. **允许修改文件：** tests/performance/measure-browser.ts；plan/performance-report.md；tests/evidence/T066/原始记录；仅测量定位的F/src/campus和清单资源（列具体文件）；config/asset-license-manifest.json。
7. **接口与衔接：** 单调计时；点击进入→排队/加载/过渡→可观察；预取与点击前停留单列。预算非额外验收硬指标。
8. **实施步骤：**

   - [ ] 步骤1：连续5次业务入口≤2s、5次冷总览10栋≤8s；五类内景≥10次每次≤5s；热定位/进入各≤1s。
   - [ ] 步骤2：校园与代表内景各稳定观察60s：平均≥30FPS、95%帧间隔≤100ms；≥30切换后原位置仍可操作。
   - [ ] 步骤3：连续10s低于20FPS反馈、15s故意延迟另测；瓶颈明确后调draw calls/贴图/像素比/缓存并复测受影响项。

9. **验收标准：** 记录失败/超限而非掩盖；初始预算12MiB/6MiB/30万三角/250draw calls/96MiB估算/1.5DPR可说明调优，不称精确GPU显存。
10. **AI验证方式：** FV＋用户演示电脑真实WebGL2/硬件加速测量；保留每次结果和帧序列，不只截图。
11. **我的手测步骤：** 按protocol重做一轮冷内景和30次切换，观察低性能/列表入口。
12. **所需配置或外部依赖：** Windows10/11、≥8GB、≥1366×768、实际CPU/GPU/浏览器；不可用如实阻塞性能结论。
13. **状态：** 未开始。
14. **实际验证记录：** 未执行；执行后填命令/退出码、环境、实际结果、证据索引、用户反馈及未验证原因。

---

<a id="t067"></a>

### T067：接口性能、聚合SQL与必要索引

1. **编号：** T067
2. **目标：** R§26.2；A§22.2；REQ-20。
3. **前置任务：** T060、T065、T066。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 列表/详情/状态/推荐读各20样本，业务写各20，5并发/1000记录；定位SQL/N+1和索引。
5. **明确不做：** 按人员/房间N+1、删除失败样本、美化p95、随意加缓存服务。
6. **允许修改文件：** tests/performance/measure-api.mjs；M/V006__performance_indexes.sql（仅实际需要）；plan/performance-report.md；相关已定位Mapper/XML；tests/evidence/T067/原始记录。
7. **接口与衔接：** 端到端读p95≤1s，非附件上传写p95≤2s；统计/推荐/空间各一致快照，操作实际落库。
8. **实施步骤：**

   - [ ] 步骤1：使用T065协议测读写各类样本，包含并发条件，记录operationId/requestId和HTTP失败比例。
   - [ ] 步骤2：EXPLAIN/查询次数定位扫描或N+1，必要才加V006或修改具体SQL，不回改已执行迁移。
   - [ ] 步骤3：复测指标/明细/排序/权限和幂等，避免性能优化造成JOIN膨胀/快照丢失。

9. **验收标准：** 满足p95且事务结果正确；未达标写缺口与原因；附件上传时间单列。
10. **AI验证方式：** measure-api原始CSV/JSON、EXPLAIN、BT相关业务集成测试及verify；仅需要时应用V006。
11. **我的手测步骤：** 5会话打开列表/推荐并派单，核对响应及实际DB业务数。
12. **所需配置或外部依赖：** 已批准demo/test样本；不加Redis/消息服务；SQL账号受限。
13. **状态：** 未开始。
14. **实际验证记录：** 未执行；执行后填命令/退出码、环境、实际结果、证据索引、用户反馈及未验证原因。

---

<a id="t068"></a>

### T068：本机统一入口打包、启动与恢复验证

1. **编号：** T068
2. **目标：** A§14；R§20/27.2；REQ-18/20交付。
3. **前置任务：** T067。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 前端产物入Boot包、SPA刷新/资源/API/图片同入口、配置/目录稳定、备份恢复说明。
5. **明确不做：** 云部署、额外Web服务器、自动清库、原素材/秘密打包。
6. **允许修改文件：** backend/pom.xml；J/config/SpaResourceConfig.java；scripts/build-local.ps1、start-local.ps1、backup-demo.ps1；config/demo.example.yml；README.md；JT/integration/PackagedApplicationIT.java；tests/evidence/T068/打包复现记录。
7. **接口与衔接：** 所有/API和受保护图片仍安全过滤；manifest本机版本资源；数据/日志外部明确路径，不依启动cwd改变。
8. **实施步骤：**

   - [ ] 步骤1：固定测试 `PackagedApplicationIT.api_404_never_falls_back_to_spa`，断言 `assertEquals(404, missingApiResponse.statusCode())`。写PackagedApplicationIT：深层SPA刷新200；/api不存在按API404而非index.html；受保护图仍鉴权。
   - [ ] 步骤2：本地构建包并从两个工作目录启动，验证Cookie/CSRF、模型/图、位置配置版本及列表降级。
   - [ ] 步骤3：在明确隔离恢复目录/新专用库复现，记录迁移失败拒启/无历史业务库不自动接管；不覆盖原库。

9. **验收标准：** 无远程CDN/调试桥/密码/Token/原始素材；已有数据不因重构建丢失；启动命令可复制。
10. **AI验证方式：** 前端npm ci/FV、后端verify及实际发布包三角色/刷新/文件/配置测试。
11. **我的手测步骤：** 只按README启动统一入口，换启动目录仍能读图/模型/DB；刷新深页正常。
12. **所需配置或外部依赖：** E01/E04/E05；备份/恢复目标另列明确路径，普通构建不触及数据。
13. **状态：** 未开始。
14. **实际验证记录：** 未执行；执行后填命令/退出码、环境、实际结果、证据索引、用户反馈及未验证原因。

---

<a id="t069"></a>

### T069：软件交付、数据库设计和完整证据材料

1. **编号：** T069
2. **目标：** R§27.2；A§22.4；REQ-01—20交付。
3. **前置任务：** T068。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 源码/迁移/数据字典/配置初始化/操作指南/测试矩阵/性能/许可完整汇总与复现。
5. **明确不做：** 论文内容假造结果、把计划预期抄作实测、提交真实秘密。
6. **允许修改文件：** plan/delivery-checklist.md、user-guide.md、test-report.md、data-dictionary.md、model-inspection.md、performance-report.md；README.md；tests/cases/acceptance-matrix.md；config/asset-license-manifest.json。
7. **接口与衔接：** 每REQ关联任务和实际证据；文档/原型/模型读取/渲染/接口/DB/附件/性能/用户验收分列；遗留项明确。
8. **实施步骤：**

   - [ ] 步骤1：按20组REQ汇总实际环境、数据、步骤、预期/实际、证据路径及未测原因。
   - [ ] 步骤2：从源码/迁移导出真实表关系和状态/轮次说明，完善初始化/备份/恢复/故障处理和三角色演示。
   - [ ] 步骤3：核对许可/原始副本关系/敏感信息/交付包清单，按README进行独立复现并收用户软件验收反馈。

9. **验收标准：** 交付范围包括全部10栋和异常流程；没有未证实“全部通过”；未知许可资源不分发。
10. **AI验证方式：** 真实包复现、链接/清单/hash检查；原生DOCX/PDF若生成须渲染核查；检查工作区文档依赖工具再调用。
11. **我的手测步骤：** 按user-guide完整演示三角色和返工/推荐/旧记录代表用例，核对报告结论。
12. **所需配置或外部依赖：** S04按格式调用；生成原生材料时先load_workspace_dependencies和渲染条件，无条件则报告限制。
13. **状态：** 未开始。
14. **实际验证记录：** 未执行；执行后填命令/退出码、环境、实际结果、证据索引、用户反馈及未验证原因。

---

<a id="t070"></a>

### T070：论文需求分析与系统设计章节稿

1. **编号：** T070
2. **目标：** R§0.1、27.2；正式材料成果要求。
3. **前置任务：** T069。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 按学校当前模板/任务书撰写需求、架构、ER/状态/空间/权限/推荐设计。
5. **明确不做：** 修改正式题名、编造学校真实数据、未读模板直接套论文。
6. **允许修改文件：** 论文/需求与系统设计章节稿.docx；论文/图表源/ER及状态图的明确文件；plan/thesis-evidence-map.md。
7. **接口与衔接：** 章节从实际数据字典/当前架构/实现对照；设计与真实实现差异记录，图与表字段一致。
8. **实施步骤：**

   - [ ] 步骤1：读取用户当前论文模板/格式、任务书日期及现有稿件，保留其内容再补本次章节。
   - [ ] 步骤2：写三角色闭环、空间与台账粒度、会话/权限/事务、推荐公式和选择边界。
   - [ ] 步骤3：核对图表/引用/题名与实际源代码迁移；打开并渲染DOCX逐页检查。

9. **验收标准：** 设计论证可追溯，示意校园/演示数据标明；引用经当前来源核查。
10. **AI验证方式：** 使用S04文档流程、DOCX结构和逐页渲染；只报已完成章节。
11. **我的手测步骤：** 审阅章节逻辑/图/格式和正式模板符合度。
12. **所需配置或外部依赖：** 学校模板与当前论文材料须提供或在材料目录确认；文档运行库缺项先说明再批准。
13. **状态：** 未开始。
14. **实际验证记录：** 未执行；执行后填命令/退出码、环境、实际结果、证据索引、用户反馈及未验证原因。

---

<a id="t071"></a>

### T071：论文系统实现与测试章节稿

1. **编号：** T071
2. **目标：** R§27；软件实际验证证据。
3. **前置任务：** T070。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 具体实现、三角色截图、推荐手算/SQL、权限/并发/统计/性能与限制。
5. **明确不做：** 用原型截图冒充实测、编造样本/指标、论文声称云部署/AI。
6. **允许修改文件：** 论文/实现与测试章节稿.docx；plan/thesis-evidence-map.md；论文/图表源/对应真实证据图表文件。
7. **接口与衔接：** 章节数字来源T060—T069实际报告/原始样本；敏感页面如随机密码必须脱敏且不保留明文。
8. **实施步骤：**

   - [ ] 步骤1：提取真实实现/迁移/接口与实测报告，列截图/图表所对应版本和操作。
   - [ ] 步骤2：写核心流程/异常/推荐L与D/统计时间/性能样本，未达标和未测如实列出。
   - [ ] 步骤3：逐表复算关键数，逐图核对版本/角色/权限，DOCX渲染排版。

9. **验收标准：** 完成数量/时间不重复计轮次，冷/热和目标/实测分列；引用不伪造。
10. **AI验证方式：** 证据索引与数值重算、源码对应、DOCX结构/渲染检查。
11. **我的手测步骤：** 重算一个推荐例、一个返工时长和一张性能表，核对截图说明。
12. **所需配置或外部依赖：** 实际软件证据先已验收；S04及对应文档运行环境。
13. **状态：** 未开始。
14. **实际验证记录：** 未执行；执行后填命令/退出码、环境、实际结果、证据索引、用户反馈及未验证原因。

---

<a id="t072"></a>

### T072：论文整合、交付归档与最终核对

1. **编号：** T072
2. **目标：** R§27.2；正式毕业交付要求。
3. **前置任务：** T071。同批前置须实际验证通过，跨批前置须完成所属批次验收；批次对应见[实施批次计划](implementation-batches.md)。
4. **本次范围：** 合并当前稿、术语/图表/目录/引用统一，交付源码包/软件/迁移/许可/证据清单。
5. **明确不做：** 自动查重提交、对外发布、Git推送、未经请求新增答辩PPT。
6. **允许修改文件：** 论文/毕业论文整合稿.docx；plan/final-archive-checklist.md；交付/明确版本源码与本机运行包；plan/tasks.md验收索引。
7. **接口与衔接：** 归档版本/hash与实际验证版本一致；论文/系统/初始配置/验收报告同一基线。
8. **实施步骤：**

   - [ ] 步骤1：保留原稿内容，合并已完成并内部核对的章节，检查术语/编号/摘要/目录/参考文献及学校格式。
   - [ ] 步骤2：渲染整稿并核对所有REQ证据、源码/迁移/资源/秘密排除；列查重/签字/学校提交待用户办理项。
   - [ ] 步骤3：按清单归档并报告尚未验收项；仅用户明确授权时才另做Git提交或答辩PPT。

9. **验收标准：** 全部归档文件可读、来源可追溯；没有自动上传/部署；最终完成只按真实用户验收记录判断。
10. **AI验证方式：** DOCX结构/逐页渲染、归档hash/文件打开、可运行包复现及最终清单交叉检查。
11. **我的手测步骤：** 打开论文与源码包/软件包，对照清单并验收最后归档。
12. **所需配置或外部依赖：** S04按真实输出格式；官方提交/查重账户操作另行授权，不在本任务假定。
13. **状态：** 未开始。
14. **实际验证记录：** 未执行；执行后填命令/退出码、环境、实际结果、证据索引、用户反馈及未验证原因。

## 5. 覆盖、编号迁移与验收总表

### 5.1 需求逐章覆盖

| R章节 | 覆盖内容 | 负责任务 |
| --- | --- | --- |
| 0 | 正式目标、规模、技术与成果 | [T001](#t001)、[T003](#t003)、[T004](#t004)、[T008](#t008)、[T046](#t046)、[T065](#t065)、[T068](#t068)、[T069](#t069)、[T070](#t070)、[T071](#t071)、[T072](#t072) |
| 1—4 | 账号来源、注册字段、登录审核、管理员 | [T009](#t009)、[T010](#t010)、[T011](#t011)、[T012](#t012)、[T013](#t013)、[T049](#t049)、[T052](#t052)、[T055](#t055) |
| 5—8 | 分类、数量、历史、停用恢复 | [T025](#t025)、[T051](#t051)、[T052](#t052)、[T053](#t053)、[T061](#t061) |
| 9、20、28 | 替换关系、阶段及证据边界 | [T001](#t001)、[T002](#t002)、[T003](#t003)、[T069](#t069)、[T072](#t072) |
| 10—12 | 登录/注册、审核、人员、密码页面 | [T012](#t012)、[T014](#t014)、[T050](#t050)、[T053](#t053)、[T054](#t054) |
| 13 | 角色及数据范围 | [T011](#t011)、[T019](#t019)、[T025](#t025)、[T037](#t037)、[T061](#t061) |
| 14 | 台账/启停用/维修历史 | [T026](#t026)、[T027](#t027)、[T056](#t056) |
| 15 | 申请/补充/撤回/审核/重复 | [T022](#t022)、[T023](#t023)、[T024](#t024)、[T028](#t028)、[T029](#t029) |
| 16 | 维修及验收闭环 | [T030](#t030)、[T031](#t031)、[T032](#t032)、[T033](#t033)、[T034](#t034)、[T035](#t035)、[T036](#t036)、[T040](#t040) |
| 17 | 图片及维修凭证 | [T019](#t019)、[T020](#t020)、[T021](#t021)、[T032](#t032)、[T061](#t061) |
| 18 | 真实统计/概览分析 | [T058](#t058)、[T059](#t059)、[T067](#t067) |
| 19 | 导航、工作台及全部业务页 | [T012](#t012)、[T014](#t014)、[T024](#t024)、[T027](#t027)、[T029](#t029)、[T034](#t034)、[T035](#t035)、[T036](#t036)、[T050](#t050)、[T053](#t053)、[T057](#t057)、[T063](#t063) |
| 21 | 数量/粒度/空间布局 | [T007](#t007)、[T008](#t008)、[T015](#t015)、[T016](#t016)、[T017](#t017)、[T018](#t018)、[T041](#t041)、[T042](#t042)、[T043](#t043)、[T044](#t044)、[T045](#t045)、[T046](#t046) |
| 22 | 入口/选择/观察/角色/可访问 | [T012](#t012)、[T016](#t016)、[T017](#t017)、[T018](#t018)、[T038](#t038)、[T046](#t046)、[T063](#t063) |
| 23 | 提示、聚合与更新 | [T037](#t037)、[T038](#t038)、[T039](#t039)、[T040](#t040)、[T064](#t064) |
| 24 | 位置/原文/草稿/历史 | [T022](#t022)、[T023](#t023)、[T024](#t024)、[T026](#t026)、[T028](#t028)、[T039](#t039)、[T056](#t056) |
| 25 | 推荐规则与人工选择 | [T031](#t031)、[T047](#t047)、[T048](#t048)、[T064](#t064) |
| 26 | 加载降级/性能/许可 | [T015](#t015)、[T016](#t016)、[T046](#t046)、[T062](#t062)、[T065](#t065)、[T066](#t066)、[T067](#t067)、[T068](#t068) |
| 27 | 20组验收及交付 | [T060](#t060)、[T061](#t061)、[T062](#t062)、[T063](#t063)、[T064](#t064)、[T065](#t065)、[T066](#t066)、[T067](#t067)、[T068](#t068)、[T069](#t069)、[T072](#t072) |

### 5.2 架构决策覆盖

| 决策 | 保持的职责 | 负责任务 |
| --- | --- | --- |
| A01—A02 | 业务分包/有限共享状态 | [T002](#t002)、[T005](#t005)、[T006](#t006)、[T024](#t024)、[T039](#t039) |
| A03—A04 | Cookie会话/Security与Service权限 | [T011](#t011)、[T019](#t019)、[T025](#t025)、[T061](#t061) |
| A05—A06 | 主表历史/约束/版本/必要锁 | [T002](#t002)、[T005](#t005)、[T007](#t007)、[T019](#t019)、[T022](#t022)、[T025](#t025)、[T028](#t028)、[T030](#t030)、[T031](#t031)、[T032](#t032)、[T033](#t033)、[T060](#t060) |
| A07—A08 | 临时附件/统一响应 | [T005](#t005)、[T006](#t006)、[T019](#t019)、[T020](#t020)、[T021](#t021) |
| A09 | 业务时间/真实聚合 | [T047](#t047)、[T051](#t051)、[T058](#t058)、[T059](#t059) |
| A10—A11 | 本地/Flyway/隔离MySQL测试 | [T004](#t004)、[T005](#t005)、[T007](#t007)、[T019](#t019)、[T022](#t022)、[T025](#t025)、[T065](#t065)、[T068](#t068) |
| A12—A13 | 版本锁定/TypeScript | [T003](#t003)、[T005](#t005)、[T006](#t006) |
| N01 | 固定目录/DB身份/渲染清单 | [T007](#t007)、[T008](#t008)、[T016](#t016)、[T046](#t046)、[T056](#t056) |
| N02 | Three.js单一实例 | [T016](#t016)、[T017](#t017)、[T018](#t018)、[T041](#t041)、[T042](#t042)、[T043](#t043)、[T044](#t044)、[T045](#t045)、[T046](#t046)、[T062](#t062) |
| N03 | 权限投影/前台10秒读取 | [T037](#t037)、[T038](#t038)、[T039](#t039)、[T064](#t064) |
| N04 | 同次登录草稿 | [T021](#t021)、[T024](#t024)、[T034](#t034)、[T035](#t035)、[T039](#t039) |
| N05 | 快照规则推荐/主动选人 | [T047](#t047)、[T048](#t048)、[T060](#t060) |
| N06 | 分层加载/有界资源/预算证据 | [T015](#t015)、[T016](#t016)、[T046](#t046)、[T062](#t062)、[T065](#t065)、[T066](#t066)、[T067](#t067) |

### 5.3 REQ-01—20验收对应

此表指向实施与取证责任，**不是通过记录**。每条的实际验收状态均为未执行；T064完成业务回归，性能及正式交付仍分别等T066—T069的真实结果。

| 用例 | 验收对象 | 实施/证据任务 |
| --- | --- | --- |
| REQ-01 | 账号、审核、资料密码及会话 | [T009](#t009)、[T010](#t010)、[T011](#t011)、[T012](#t012)、[T013](#t013)、[T014](#t014)、[T049](#t049)、[T050](#t050)、[T052](#t052)、[T053](#t053)、[T054](#t054)、[T055](#t055)、[T061](#t061)、[T064](#t064) |
| REQ-02 | 人员去重、动态分类、历史成果归属 | [T025](#t025)、[T051](#t051)、[T052](#t052)、[T053](#t053)、[T054](#t054)、[T061](#t061)、[T064](#t064) |
| REQ-03 | 10/41/296/257/1817全覆盖 | [T007](#t007)、[T008](#t008)、[T018](#t018)、[T041](#t041)、[T042](#t042)、[T043](#t043)、[T044](#t044)、[T045](#t045)、[T046](#t046)、[T064](#t064) |
| REQ-04 | 174宿舍/57教室/15办公室/72教学空调 | [T008](#t008)、[T017](#t017)、[T042](#t042)、[T043](#t043)、[T064](#t064) |
| REQ-05 | 食堂/体育馆区域及分组 | [T008](#t008)、[T044](#t044)、[T045](#t045)、[T064](#t064) |
| REQ-06 | 显式身份/模板隔离/两入口位置必选 | [T007](#t007)、[T022](#t022)、[T024](#t024)、[T027](#t027)、[T040](#t040)、[T042](#t042)、[T043](#t043)、[T044](#t044)、[T046](#t046)、[T064](#t064) |
| REQ-07 | 五申请状态/补充/撤回/范围外处理 | [T023](#t023)、[T024](#t024)、[T028](#t028)、[T029](#t029)、[T037](#t037)、[T040](#t040)、[T064](#t064) |
| REQ-08 | 台账/位置/资格及未完结唯一 | [T025](#t025)、[T026](#t026)、[T028](#t028)、[T029](#t029)、[T060](#t060)、[T064](#t064) |
| REQ-09 | 接拒单/重派/结果/验收/返工 | [T030](#t030)、[T031](#t031)、[T032](#t032)、[T033](#t033)、[T034](#t034)、[T035](#t035)、[T036](#t036)、[T040](#t040)、[T064](#t064) |
| REQ-10 | 提示阶段/单位置聚合/去重 | [T037](#t037)、[T038](#t038)、[T039](#t039)、[T040](#t040)、[T064](#t064) |
| REQ-11 | 同位置/当前与有限历史/附件权限 | [T019](#t019)、[T025](#t025)、[T031](#t031)、[T037](#t037)、[T038](#t038)、[T061](#t061)、[T064](#t064) |
| REQ-12 | 30秒/本人立即/恢复前台/编辑保护 | [T039](#t039)、[T040](#t040)、[T048](#t048)、[T064](#t064) |
| REQ-13 | 内存草稿/待确认/同标识重试 | [T020](#t020)、[T021](#t021)、[T022](#t022)、[T024](#t024)、[T032](#t032)、[T033](#t033)、[T034](#t034)、[T035](#t035)、[T039](#t039)、[T060](#t060)、[T064](#t064) |
| REQ-14 | 旧文字/名称快照/设备移动 | [T026](#t026)、[T028](#t028)、[T056](#t056)、[T060](#t060)、[T064](#t064) |
| REQ-15 | W/I/R/A/L/D与稳定排序/完整名单 | [T047](#t047)、[T048](#t048)、[T064](#t064) |
| REQ-16 | 推荐选择不变/资格失效/最终复核 | [T028](#t028)、[T031](#t031)、[T047](#t047)、[T048](#t048)、[T060](#t060)、[T064](#t064) |
| REQ-17 | 图片/新轮凭证/时间统计 | [T019](#t019)、[T020](#t020)、[T021](#t021)、[T032](#t032)、[T058](#t058)、[T059](#t059)、[T061](#t061)、[T064](#t064) |
| REQ-18 | 局部/整体失败/15秒/迟到/列表降级 | [T016](#t016)、[T039](#t039)、[T046](#t046)、[T062](#t062)、[T064](#t064)、[T068](#t068) |
| REQ-19 | 320px/44px/键盘/文字/减少动态 | [T012](#t012)、[T038](#t038)、[T046](#t046)、[T050](#t050)、[T053](#t053)、[T059](#t059)、[T063](#t063)、[T064](#t064) |
| REQ-20 | 实测性能/资源许可/分层证据 | [T015](#t015)、[T062](#t062)、[T065](#t065)、[T066](#t066)、[T067](#t067)、[T068](#t068)、[T069](#t069)、[T071](#t071)、[T072](#t072) |

### 5.4 旧48项到新72项的映射

旧编号只来自保留的备份标题，不继续使用原根目录计划。新任务编号、前置条件和范围以本文件为准。以下48行覆盖所有旧任务；合并或拆分不转移“已验收”状态。纯新增空间/几何/刷新/推荐/性能任务已经编入相应依赖链。

| 旧编号 | 新编号 | 处理 |
| --- | --- | --- |
| 旧T001 | [T001](#t001)、[T003](#t003)、[T004](#t004) | 拆分只读核对、安装清单与环境配置 |
| 旧T002 | [T002](#t002)、[T005](#t005) | 补接口/数据契约，保留后端基础 |
| 旧T003 | [T002](#t002)、[T006](#t006) | 类型/请求契约前置 |
| 旧T004 | [T004](#t004)、[T005](#t005)、[T007](#t007)、[T019](#t019)、[T022](#t022)、[T025](#t025) | 按表职责和依赖拆迁移 |
| 旧T005 | [T010](#t010) | 保留注册并强化真实并发唯一性 |
| 旧T006 | [T011](#t011) | 保留会话/CSRF并强化真实证据 |
| 旧T007 | [T009](#t009) | 保留本地管理员初始化 |
| 旧T008 | [T012](#t012) | 校园默认入口和资格导航更新 |
| 旧T009 | [T013](#t013) | 保留审核/原账号重提 |
| 旧T010 | [T014](#t014) | 保留本人审核页 |
| 旧T011 | [T014](#t014)、[T053](#t053) | 审核详情和完整人员列表分阶段接入 |
| 旧T012 | [T026](#t026)、[T056](#t056) | 增加位置/范围/移动兼容 |
| 旧T013 | [T027](#t027) | 保留台账维护与历史 |
| 旧T014 | [T019](#t019)、[T021](#t021) | 拆后端图片与前端选择 |
| 旧T015 | [T022](#t022) | 新建强制结构化位置/快照 |
| 旧T016 | [T024](#t024) | 增加两入口共用内存草稿 |
| 旧T017 | [T023](#t023) | 保留原申请重提和范围外退回 |
| 旧T018 | [T024](#t024)、[T039](#t039) | 补充UI与同步/结果核对 |
| 旧T019 | [T028](#t028) | 新增位置一致性和旧记录例外 |
| 旧T020 | [T029](#t029)、[T048](#t048) | 基础派单与推荐增强分开 |
| 旧T021 | [T030](#t030)、[T031](#t031) | 接拒单与管理员改派分开 |
| 旧T022 | [T034](#t034) | 维修当前/有限历史投影 |
| 旧T023 | [T036](#t036)、[T048](#t048) | 原工单重派与推荐共用面板 |
| 旧T024 | [T032](#t032) | 保留独立进度/本轮新凭证 |
| 旧T025 | [T034](#t034) | 同次编辑/轮次草稿 |
| 旧T026 | [T033](#t033) | 保留原报修人验收/返工 |
| 旧T027 | [T035](#t035) | 本轮验收材料优先 |
| 旧T028 | [T020](#t020) | 保留过期清理与绑定互斥 |
| 旧T029 | [T049](#t049) | 保留资料/密码真实持久化 |
| 旧T030 | [T050](#t050) | 保留账号设置 |
| 旧T031 | [T052](#t052) | 保留随机重置及会话撤销 |
| 旧T032 | [T054](#t054) | 保留一次性结果/复制回退 |
| 旧T033 | [T055](#t055) | 保留管理员本地重置 |
| 旧T034 | [T052](#t052) | 共同锁阻止未完结账号停用 |
| 旧T035 | [T051](#t051) | 强化独立业务与成果归属 |
| 旧T036 | [T053](#t053) | 保留组合筛选/审核/启停用 |
| 旧T037 | [T053](#t053) | 详情完整历史和双层返回 |
| 旧T038 | [T027](#t027) | 历史投影在工单结构后接入 |
| 旧T039 | [T057](#t057) | 保留普通待办工作台 |
| 旧T040 | [T058](#t058) | 统计真实聚合/边界/快照 |
| 旧T041 | [T059](#t059) | 保留双标签看板与下钻 |
| 旧T042 | [T060](#t060)、[T061](#t061) | 拆并发与安全矩阵 |
| 旧T043 | [T063](#t063)、[T064](#t064) | 拆可访问性与E2E |
| 旧T044 | [T068](#t068) | 增加校园静态资源/版本/深页验证 |
| 旧T045 | [T069](#t069) | 加入空间/推荐/性能/许可证据 |
| 旧T046 | [T070](#t070) | 更新需求/设计论文范围 |
| 旧T047 | [T071](#t071) | 真实实现和实测论文材料 |
| 旧T048 | [T072](#t072) | 整合与归档，不自动生成新增成果 |

### 5.5 执行前与任务交付检查

- [ ] 用户已审阅当前需求/架构/批次计划基线并授权本次Bxx；跨批前置已验收，同批前置实际验证通过。
- [ ] 安装清单内实际需要项已批准，实际版本/路径/端口/配置已核对；缺项和替代写明。
- [ ] 已重读当前任务的接口、数据和文件范围，变更未越过业务/架构边界。
- [ ] 有意义规则测试先确认失败再实现；真实MySQL/文件/HTTP/浏览器证据与模拟/文档分开。
- [ ] Changed/Verified/请我测试/Remaining Risk与证据索引已填写；未跑项说明原因。
- [ ] 本批全部关联任务完成内部验证后，统一交付并更新批次为待用户验收、停止；仅收到通过不自动开发下一批，不自动安装未知新工具/提交/推送/部署。

## 6. 历史计划编写检查与当前停止点

计划编写阶段只进行材料阅读、只读环境/包元数据核查、任务编写和计划静态检查。当时校验72项编号连续、每项14字段、前置依赖、REQ责任、旧编号映射和参考基线未改；当时均未开始的状态已由后续B01—B03记录更新。历史静态检查不证明软件、模型、数据库迁移或性能已完成。

**当前停止点（2026-10-19更新）：** B01—B05已验收；B06（T037—T040）开发、最新全量回归、真实浏览器及独立审阅、数据保护和服务恢复已完成，状态B06待用户验收；不开始B07／B08。


B04 T021/T024必要辅助文件白名单（2026-10-13）：F/src/api/business.ts、F/src/composables/useRequestForm.ts、F/src/composables/useMutationConfirmation.ts，及对应关键行为测试。职责与接口先记tests/evidence/B04-progress.md：仅本批严格业务回执/共用表单协调/原操作结果核对；不实现B06轮询、后续维修行为或通用流程引擎。
B04 T024/T027/T029必要共享白名单：F/src/composables/useBusinessRead.ts，仅复用读取代次/身份/离页/失败反馈，无轮询；职责先记B04-progress.md。
B04 T024/T029必要组件白名单：F/src/components/request/RequestMaterialContent.vue，仅复用已鉴权申请原文/快照/事件/图片展示，职责先记B04-progress.md。
B04 T025/T026共享测试白名单：JT/support/B04WorkOrderFixture.java，隔离历史状态SQL材料，登记ID并精确清理，不暴露B05业务操作。
B04 T027/T029共享白名单：F/src/composables/useBusinessAction.ts（待确认写操作协调），F/src/views/workorder/WorkOrderListView.vue（已授权GET只读列表/任务入口）；无轮询或B05动作。
B04 T024/T027/T029确认UI共享白名单：F/src/components/common/InlineConfirmation.vue、F/src/composables/useBusinessConfirmation.ts，以页面内明确二次确认满足既定确认要求及键盘/窄屏验证；仅替换本批新页面的原生确认，不改已验收B02流程或业务契约。

B04服务修复必要白名单（2026-10-14用户直接授权）：scripts/start-frontend-dev.ps1、README.md、tests/evidence/B04-session-*及B04-progress.md。职责：日常5173/5174固定代理dev 8080；启动前检查后端及端口，只启动本项目前端并保留调用者环境，不安装依赖或自动停止未知进程。不改认证业务、已应用迁移、初始化逻辑或B05范围。

B04 T029及用户2026-10-14直接界面要求增量白名单：F/src/layouts/MainLayout.vue、F/src/App.vue（fixed顶栏/侧栏、响应式占位及数量红标）；F/src/composables/useReviewNotifications.ts及对应行为测试（仅已确认管理员、30秒可见页面计数/失权清除）；F/src/router/index.ts（既定审核路由）；F/src/views/request/review-flow.ts（页面使用的审核阻断与确认校验）。职责及后端共享增量先记B04-progress.md；不接B05动作或B06空间轮询。

B04 T029回归白名单：F/src/views/request/RequestReviewView.test.ts，页面生命周期与补录分页返回，职责见B04-progress.md。

B04 T022/T023/T025独立审阅后端修复白名单（2026-10-15）：J/repairrequest/service/RepairRequestService.java（仅并发原标识重放与关联TECHNICIAN详情历史投影）；JT/repairrequest/TransitionReplayIT.java、RequestDetailProjectionIT.java（仅真实事务可控交错、合法账号详情/图片/count隔离）；tests/evidence/B04-review-backend-repair.md及对应RED/GREEN日志。沿用WorkOrderMapper和BusinessAccessPolicy.historyScope，不改迁移、不扩附件快捷访问、不增加B05动作。先等主代理释放共享test库，严禁与浏览器fixture并行写测试库。

2026-10-15用户直接授权增量：RegistrationReviewView.vue仅审核确认成功后刷新人员导航红标；useReviewNotifications/MainLayout及既有测试复用既有待审人员GET，不增加人员管理模型/写操作。



B06必要共享接线／联调修正白名单：SecurityConfig仅两条GET；useBusinessRead及api/business、repair-request、workorder仅signal与详情轮询兼容；RequestDetail／Supplement／RequestReview／WorkOrderDetail及router/businessNavigation保留受控空间返回；TechnicianWorkPanel保留仍有权编辑并禁旧操作；RequestListView仅普通新报修returnTo漏接修复。职责、RED/GREEN与独立复核见B06-progress.md。B06BrowserHarness及B06证据脚本只使用登记test库／精确附件目录，恢复夹具不重新初始化已有数据。

B06用户2026-10-19浏览器批注增量白名单：LocationDirectory.vue（只读目录投影）、campus/location-status.css（标识/目录共享颜色变量）、CampusView.test.ts（实际SFC路由/状态测试）、router/businessNavigation.ts共用campusItemPath；既有Markers/ItemsPanel/CampusView仅只读导航接线。职责先记B06-progress.md，不改后端契约、状态机或迁移。
