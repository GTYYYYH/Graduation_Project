# B01 本机环境核查与隔离配置

核查日期：2026-10-12，Asia/Shanghai。对应任务：T001、T003—T006；用户已批准A01—A04，B01已通过用户验收。

项目根目录：`D:\快速访问\Desktop\毕业项目文件\Graduation_Project`，下文简称R。

## 1. 本轮授权与当前事实

- 用户本次直接要求根据implementation-batches.md开始项目搭建，按B01（T001—T006）执行；批内连续验证，批末等待验收。
- 这次指令用于启动B01，不将历史文档中的“本轮仅写计划”当成当前请求，也不追溯修改需求、架构的历史批准状态。
- 初次检查R只有plan及其备份，没有backend、frontend、Git仓库或可运行工程。`git rev-parse --show-toplevel`退出128；没有执行git init。
- §2—6保留初次只读核查事实；版本查询、元数据和WebGL2检测均不替代项目验证。批准后的T004—T006结果见§7—9及实际证据。
- 用户已明确批准[dependency-lock.md](dependency-lock.md)A01—A04，随后实际配置和搭建；没有将历史计划文档当作安装授权。

## 2. 已有环境及真实路径

| 项目 | 本轮实际结果 | 状态与处理 |
| --- | --- | --- |
| JDK21 | `C:\Users\29675\Java\jdk-21`；java/javac均21.0.11；Oracle、x86_64 | 现有可复用；以后项目脚本显式指定此路径 |
| 默认java入口 | `C:\Program Files\Common Files\Oracle\Java\javapath\java.exe`；java.home为上述JDK21 | 已核实默认java可调用，但不能由此推定Maven用21 |
| JAVA_HOME | `C:\Program Files\Java\jdk-17.0.5` | 当前值为17；尚未修改 |
| Maven | `D:\Maven\apache-maven-3.9.15-bin\apache-maven-3.9.15\bin\mvn.cmd`；3.9.15；实际Java17.0.5 | 存在JDK不一致；T004仅在项目进程中指定JDK21 |
| 另一套JDK | `D:\JDK\bin\java.exe`、javac均25.0.3，Temurin | 现有，但不选为项目目标 |
| Node/npm | `D:\Node.js\node.exe` v24.11.0；`D:\Node.js\npm.cmd` 11.20.0 | 已有可复用；不需升级Node/npm |
| MySQL PATH客户端/服务端程序 | `D:\PHP\phpstudy_pro\Extensions\MySQL5.7.26\bin\mysql.exe`、mysqld.exe | phpStudy5.7程序；未运行数据库连接，不作为8.4服务端证据 |
| 独立MySQL8.4 | 当前服务、mysqld进程、卸载注册项及常见目录检查未发现 | 未确认存在；拟安装独立8.4.11，必须批准后实施 |
| phpStudy服务 | `phpStudySrv`，Stopped，Manual；`D:\PHP\phpstudy_pro\COM\phpStudyServer.exe -SCM` | 已发现，未启动/停止/配置或改动 |
| VC++ x64运行库 | 2022 X64 Minimum/Additional Runtime 14.51.36247 | 现有；安装MySQL后仍需实际运行验证；不预先重装 |
| Chrome | `C:\Program Files\Google\Chrome\Application\chrome.exe`；153.0.8010.48 | 文件及版本已确认；该Chrome实例的硬件加速未实测 |
| Edge | `C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe`；155.0.4283.45 | 文件及版本已确认；该Edge实例的硬件加速未实测 |
| Git | `D:\Git\Git\cmd\git.exe`；2.55.0.windows.3 | 可调用；R没有仓库，不自动建仓 |
| Python | `D:\Python\python.exe`；3.11.8 | 可调用；后续文档静态核对可用标准库 |

MySQL发现范围：Win32_Service中Name/PathName匹配mysql、maria、mysqld；mysqld进程；HKLM/HKCU常用卸载注册项；`C:\Program Files\MySQL`、`C:\Program Files (x86)\MySQL`、`C:\ProgramData\MySQL`、`D:\mysql`、D盘根目录及`D:\Tools`。这是有限范围检查，不能证明全盘绝无便携实例；如已有其他位置的独立8.4，可提供路径后复核复用。

## 3. 端口及磁盘

| 端口 | 本轮Get-NetTCPConnection -State Listen结果 | 拟用途 |
| --- | --- | --- |
| 3306 | 无监听 | 保留现有数据库使用习惯，不占用 |
| 3307 | 无监听 | B01独立MySQL，仅127.0.0.1 |
| 8080 | 无监听 | Spring Boot开发入口 |
| 5173 | 无监听 | Vite开发入口 |

此结果只说明核查时无监听，启动前须重新检查。端口与目录尚未配置。

核查时C盘可用40,138,002,432字节（约37.38GiB）；D盘56,308,006,912字节（约52.44GiB）。不是安装空间承诺；磁盘状态会变化。

## 4. 浏览器实际核查

使用当前已连接的Codex内置浏览器打开[Khronos WebGL2检测页](https://get.webgl.org/webgl2/)。页面实际显示“Your browser supports WebGL2”，截图可见立方体。已核实此内置浏览器能创建WebGL2并呈现检测页。

浏览器工具当前仅暴露内置浏览器和MCP Apps；没有已连接的独立Chrome/Edge控制会话。因此不把内置浏览器结果记为Chrome/Edge硬件加速验证，也不把检测页当作Three.js校园、门动画、FPS或项目性能证明。Chrome/Edge实机渲染在工程就绪及T016/T066补证。

## 5. 素材只读清单

| 目录 | 文件数/逻辑字节数 | 当前格式 |
| --- | --- | --- |
| `D:\快速访问\Desktop\毕业项目文件\文件材料\学校建筑3D` | 42个，77,354,424字节 | max×1、vrmesh×4、jpg×36、hdr×1 |
| `D:\快速访问\Desktop\毕业项目文件\文件材料\学校多建筑3D` | 179个，570,936,102字节 | max×1、vrmesh×52、jpg×93、png×5、tif×23、bmp×2、tga×1、hdr×1、txt×1 |

首目录源文件`3d66.com_1160697.max`为26,669,056字节；多建筑源文件`3d66.com_23526227.max`为111,598,282字节。两目录本次未发现GLB/glTF/FBX/OBJ文件；未打开max/vrmesh、转换、复制或修改素材，未核实许可与可分离内景。完整用途/许可/hash及副本路线留到T015，不以本次文件盘点作模型合格声明。

## 6. skills与MCP

| 能力 | 当前状态 | B01选择 |
| --- | --- | --- |
| Superpowers | 本会话可用；已读using-superpowers、brainstorming、executing-plans、using-git-worktrees、TDD、verification-before-completion | 使用既有计划执行与验证流程；用户指定目录、禁止自动建仓的约定优先，不创建Git工作树 |
| find-skills | 本会话技能已读取；已只读查看skills.sh及候选Three.js仓库 | 优先已有能力；未运行会自动下载CLI的npx命令 |
| Open Code Review | 插件技能可读；`ocr review --preview --audience agent --background ...`实际报R不是Git仓库 | CLI可以被调用，但Git评审前提不满足；未运行模型评审，未安装/升级或写入Key |
| 浏览器工具 | mcp__cua_repl实际可调用 | 已完成内置浏览器WebGL2检测；无需另装Playwright MCP |
| Context7/Playwright MCP/Blender MCP/Three.js调试MCP | 当前暴露工具中未发现相应可调用工具 | 未确认连接；不将“未暴露”写成全机未安装；官方文档和现有浏览器替代 |
| CloudAI-X/threejs-skills | 本次未在当前可用技能清单发现 | 可选，B01跳过；仓库元数据与许可问题见dependency-lock.md |
| Blender/3ds Max | 未发现可确认的相关卸载注册项；未进行全盘/GUI查找 | 未确认可用；B01不安装，T015按副本路线复核 |

`ocr.cmd`调用曾因不存在该扩展名失败；随后按技能规定直接调用`ocr`，真实失败原因是无Git仓库。两者不混为“OCR未安装”。

## 7. T004隔离配置（已批准并实际验证）

| 对象 | 拟定路径/名称 | 边界 |
| --- | --- | --- |
| JDK脚本 | `R\scripts\use-jdk.ps1`→`C:\Users\29675\Java\jdk-21` | 仅当前项目进程JAVA_HOME/PATH；不改用户/系统环境变量 |
| MySQL程序 | `R\tools\mysql-8.4.11-winx64` | 官方ZIP，独立于phpStudy；tools忽略Git |
| MySQL配置/数据 | `R\config\mysql\my.ini`、`R\data\mysql84` | 仅回环地址3307；不指向既有库或数据目录 |
| 运行方式 | 本地独立mysqld进程，后台隐藏窗口，显式启动/停止 | B01提案不注册Windows服务、不设开机自启、不改防火墙；需要常驻服务时另提具体变更 |
| 数据库 | `campus_maintenance_dev`、`campus_maintenance_demo`、`campus_maintenance_test` | dev/demo/test分离；不自动清理或接管已有库 |
| 应用账号 | `campus_dev`、`campus_demo`、`campus_test`，限127.0.0.1 | 每个账号仅对应库权限；不授权全局ALL、GRANT OPTION或其他库 |
| 业务附件 | `R\data\attachments\dev`、`demo`、`test` | test目录绝对路径独立，拒绝dev/demo、父目录及逃逸路径 |
| 本机秘密 | `C:\Users\29675\.graduation-project\local-dev.yml`、`local-demo.yml`、`local-test.yml` | 项目外专用配置，仅本机；不写计划、README、终端输出或截图 |

用户已批准A01—A04。T004实际安装8.4.11并安全初始化，密码仅受限项目外本机配置保存。实际三个库/账号及UTC会话匹配，权限只有USAGE及各自库SELECT/INSERT/UPDATE/DELETE/CREATE/REFERENCES/INDEX/ALTER，无DROP或跨库权限，证据见[environment-result.json](../tests/evidence/T004/environment-result.json)。MySQL只监听127.0.0.1:3307，无服务注册。

项目脚本设置当前进程MAVEN_SKIP_RC=true：既有mavenrc_pre.cmd会将Maven覆盖为JDK17，原文件未改；实际Maven报告21.0.11。中文路径初始化遇到Windows原生MySQL路径限制，使用受限本机目录`C:/Users/29675/.graduation-project/project`指向R的NTFS junction；my.ini通过该ASCII路径启动，程序/数据/日志仍在R。移动项目需更新此映射并复核，不能递归删除映射。此实现选择及影响记录在[B01执行记录](../tests/evidence/B01-progress.md)，背景见[MySQL Windows路径限制](https://dev.mysql.com/doc/refman/8.4/en/windows-restrictions.html)。

## 8. 本轮命令与证据索引

| 索引 | 已执行检查 | 实际结果 |
| --- | --- | --- |
| ENV-01 | Get-Command java.exe/javac.exe/mvn.cmd/node.exe/npm.cmd/mysql.exe/mysqld.exe/git.exe/python.exe -All；java/javac/mvn/node/npm/git/python版本 | §2路径与版本；正常版本命令退出0 |
| ENV-02 | java -XshowSettings:properties -version；直接调用jdk-21及D:\JDK的java/javac | 真实java.home=用户目录jdk-21；21与25区分 |
| ENV-03 | Get-CimInstance Win32_Service、Get-Process mysqld、常用卸载注册项与安装目录 | §2所述发现范围；未发现独立8.4，不使用凭据连接phpStudy |
| ENV-04 | Get-NetTCPConnection -State Listen，逐项输出3306/3307/8080/5173；Get-PSDrive C,D | §3，逐项监听列表为空 |
| ENV-05 | 两素材目录Get-ChildItem -File -Recurse，按Extension分组与Measure-Object Length | §5，仅目录/格式/逻辑体积 |
| ENV-06 | 内置浏览器导航检测页、读取AX和截图 | §4，仅该浏览器WebGL2检测成功 |
| ENV-07 | git rev-parse --show-toplevel；ocr review --preview | R无Git仓库，Git/OCR前置失败；不伪称评审完成 |

T004已验证SELECT VERSION/DATABASE/CURRENT_USER、隔离权限、端口、UTC和项目Maven使用21。T005/T006实际验证Flyway、BootstrapIT、Wrapper、构建和真实项目入口，见下节；独立Chrome/Edge硬件加速及校园性能仍未测。

## 9. 批内实际工程验证

T005实际Wrapper3.3.4/Maven3.9.15使用JDK21.0.11；最终BootstrapIT15/15、dependency:tree、verify均退出0，dev/test库V001成功各四业务表，无账号/会话/审核/事件行。demo库连接与权限已测，尚未启动演示应用/迁移。生产jar不含测试端点。错误配置和无DDL失败SQL验证启动失败；测试仅移除自身失败标记/临时文件，无DROP/clean。独立审阅3项Important已RED→GREEN修复；Windows JAR锁引发的一次打包失败在停止专用后端后解决，重新打包并以JDK21启动，重复迁移0项。

T006实际npm ci、14/14请求测试、严格typecheck/build通过；13直接依赖与package-lock/SRI匹配。Vite5173代理后端8080的真实401含requestId；浏览器验证按钮重读、/list刷新、窄屏导航与无横向溢出，最终重启后页面验证仍通过，warn/error日志空。证据见[T005最终构建](../tests/evidence/T005/final-verify.log)、[T006最终测试](../tests/evidence/T006/client-final.log)、[汇总](../tests/evidence/B01-verification.json)、[运行进程](../tests/evidence/B01-runtime.json)。当前本机启动方式见[README](../README.md)，仅独立进程，无开机自启。B01内部验证/审阅修复完成；用户2026-10-12反馈“B01已经验证完成”，已通过用户验收，未开始B02。
