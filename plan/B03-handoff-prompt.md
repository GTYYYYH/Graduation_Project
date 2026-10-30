你是我的毕业项目工程伙伴，请接续现有项目开发，使用中文回答。

项目目录：D:\快速访问\Desktop\毕业项目文件\Graduation_Project

B01和B02均已通过我的验收，B02验收日期为2026-10-13。
本次明确授权：开始B03（T015—T018），按当前实施批次计划完成整批开发、测试和交付；完成后等待我验收，不继续B04。不要重新搭建或重新初始化B01/B02，不把历史未开发、安装待批准或每T暂停记录当作当前状态。

一、先读取当前文件
- plan/requirements.md，重点§21.2、21.7、22及相关性能/素材规则。
- plan/architecture.md，重点空间身份、3D生命周期、资源缓存/释放、失败处理与素材许可。
- plan/implementation-batches.md，重点B03。
- plan/tasks.md，重点T015—T018、§2.2渲染接口与文件白名单。
- plan/data-dictionary.md、plan/api-contracts.md、plan/dependency-lock.md、plan/environment.md。
- config/campus.json、plan/campus-inventory.md、README.md。
- tests/evidence/B02-acceptance.md、B02-progress.md、B02-code-review.md、B02-verification.json、B02-runtime.json、B02-startup-diagnosis-2026-10-13.json。
- 当前frontend的CampusView、router、auth store、真实目录客户端、公共请求客户端、package.json及锁文件。
- 按T015实际定位“学校建筑3D／学校多建筑3D”、现有素材与检查报告；不存在或许可未知时如实记录，不能沿用历史684个GLB检查结果冒充本次验证。

当前文件、我的直接授权和验收记录优先于历史记忆。历史测试与运行快照不等于当前运行状态，不能信任旧PID。

二、已有基础
- JDK21.0.11、Spring Boot4.1.1、MyBatis-Plus3.5.17；独立MySQL8.4.11，127.0.0.1:3307，原phpStudy保留。
- dev/test已应用V001和V002；dev已显式初始化校园和管理员；demo仅已验证连接，尚未运行应用迁移/初始化。
- 初始配置10栋、41楼层、296处内景覆盖单位、257个报修位置、1817项台账。用户验收后的账号等数据必须保留，不能重置为测试快照。
- B02最终后端42/42、前端33/33，构建/类型检查与真实Cookie、CSRF、角色导航、刷新恢复、审核闭环均有证据。
- 登录采用随机Token＋HttpOnly Cookie＋MySQL摘要，固定8小时不续期；Pinia/Web Storage不存登录Token，写操作验证CSRF。
- Three.js0.186.1及现有Vue/TypeScript/Vite依赖已批准并安装。复用同版本OrbitControls/GLTFLoader，不重复安装或升级。
- 正式入口前端5173、后端8080。2026-10-13曾因后端未启动使csrf代理502，已恢复；进入新对话后仍须实时核对服务及真实目录，不把前端页面能打开当作后端已运行。

三、B03严格范围
1. T015：只读原素材，核对来源、格式、贴图、扩展、许可、hash和允许副本输出路径。默认采用可维护的参数化几何；许可素材仅补充必要构件。未知许可不分发，不修改原始素材，不上传模型，不假定.max已打开，不伪装扩展名转换。
2. T016：文博1一栋5层简化外观，名称、旋转/平移/缩放/复位；单渲染实例、加载代次、最小缓存/释放及失败反馈。WebGL2失败仍保留可用的位置选择和普通列表入口。
3. T017：文博1首层101宿舍样板，真实可进入内景。左右各1组上床下桌，共2床2桌面；独立卫浴马桶/淋浴/洗手盆各1，空调1，宿舍区及卫浴灯各1，左右桌面插座各1。门向室内开启，独立门轴、门前/室内锚点；选择只定位，用户主动开门进入。
4. T018：扩为首层10间宿舍101—110＋门厅＋宿管值班室，共12处可进入观察。中央走廊两侧各5间，一侧101—105、另一侧106—110；门厅在中部入口、值班室邻门厅、两端楼梯贯穿5层。可剖开观察，全部实例的身份/门状态/锚点独立，公共空间仅观察、不报修。

只制作文博1外观及完整首层内景，不扩其余楼层内景、楼栋或全校园。不实现报修/工单、业务故障提示、自动派单或B06完整3D业务闭环，不用假业务成功反馈。展示用门窗/走廊/楼梯不新增台账；值班室只展示1桌1椅。审美沿用当前需求的暖灰外墙、低饱和蓝灰屋顶、浅木家具和清楚标识，红色不用于正常建筑分类。

四、工程与验证要求
- 使用Superpowers执行已有计划，关键逻辑先写失败测试、再实现、完成前核验；批内依赖就绪连续执行，不逐T询问是否继续，不重新制定整套项目计划。
- 严格按T015—T018文件边界最小修改，保留B02账号、安全与目录行为；必要共享文件增量说明原因并记录，不擅自扩业务或变更契约。
- manifest的configVersion须与真实目录一致，以稳定code映射后端字符串ID/显式floor关系，不硬编码数据库ID，不解析房号推断位置，不把Three.js对象或业务数据放入Pinia/模型缓存。
- 沿用计划的CampusRenderer接口：mount、selectTarget(target,generation)、enterLocation(id,generation)、returnToFloor、resetOverview、dispose。
- 验证last_target_wins、门状态按实例隔离、首层12个唯一位置；旧加载/相机动画/重试不能覆盖新目标；换身份或离开页面后迟到结果不能恢复旧场景。
- 验证资源引用和释放：离开页面停止循环、控制器及监听，释放无共享引用的geometry/material/texture；共享材质不能使门状态互改或卸载一个实例破坏其他实例。缓存遵守当前架构预算，不只凭调用dispose宣称无泄漏。
- 实际WebGL2浏览器验证外观、所有12处进入/返回、鼠标/触控/键盘等价入口、表单焦点不驱动相机、快速切换、复位、重复离开再进入、减少动态和窄屏。
- 热目标定位与开门进入分别≤1秒；减少动态时直接定位；冷内景计时从用户点击进入起，区分预取/冷/热条件，遵守当前5秒目标。不能把结构检查、数量正确或静态截图当作交互/性能通过。
- 运行计划要求的renderer-generation、door-state、floor-mapping测试及前端全套/typecheck/build；按实际影响做B02回归。每项证据区分源码、自动测试、真实渲染/计时、用户验收和未验证限制。

五、环境与授权边界
- Maven需要时在项目PowerShell点加载scripts/use-jdk.ps1；保持项目进程跳过mavenrc_pre.cmd的JDK17覆盖，不修改全局JAVA_HOME。
- MySQL通过C:\Users\29675\.graduation-project\project这个junction访问，实际程序/数据仍在D盘。秘密配置及客户端文件位于C:\Users\29675\.graduation-project；可按任务需要使用，不将密码/登录Token输出到聊天、源码、截图或证据。不要重发管理员初始密码。
- 重新打包前实时核对并只停止运行当前target JAR的项目后端；不要批量停止java/node/mysqld。
- 测试数据库使用真实MySQL和test-local，先验证profile、库、账号、权限与目录；不回退dev，不执行flyway clean，不清未知或用户验收数据，不修改已应用V001/V002。本批通常无新增数据库迁移需求。
- 如果复用B02BrowserHarness，先检查独立审阅记录的超时清理Minor并在使用前修复和验证；不要清空test库绕过问题。无需使用该fixture时不扩展本批去处理它。
- find-skills仅按实际缺口使用，不擅自安装技能或工具。Blender、3ds Max、相关MCP及未知新增依赖需先列具体用途/版本/安装位置/影响并获得批准；参数化路线不因缺这些工具停工。
- 当前无Git仓库，不自行git init、提交、推送或部署。OCR因无Git不可用时记录限制并完成独立审阅。

六、交付与停止
更新T015—T018状态、B03执行账本、tests/evidence/B03-*、素材许可与模型检查报告，以及README操作说明。提供真实渲染截图和可操作入口，记录原素材hash未变与示意标识。
最后报告Changed、Verified、请我测试、Remaining Risk；交付可实际操作的B03后停止等待我的验收，不启动B04、不自行把内部验证标成用户验收。

请先简短说明B03范围、关键假设和素材路线，然后直接开始。
