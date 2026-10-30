# B01 精确依赖与安装批准清单 v1

日期：2026-10-12，Asia/Shanghai；T003/B01。项目R=`D:\快速访问\Desktop\毕业项目文件\Graduation_Project`。

**状态：A01—A04已批准并实施，B01已通过用户验收。** 用户2026-10-12直接批准“批准 A01—A04，继续 B01”。四项已按下列精确版本安装/配置并实际验证；传递依赖、审阅修复和工程证据在T004—T006记录。

## 1. 本轮申请批准的必需范围

只申请下面A01—A04，不申请可选工具、Git仓库/提交、Windows服务、开机自启、防火墙、全局JAVA_HOME、Node升级或模型处理。

| 批准项 | 精确范围 | 输出/配置位置 | 当前状态 |
| --- | --- | --- | --- |
| A01 项目JDK配置 | 复用Oracle JDK21.0.11；脚本在当前项目进程设JAVA_HOME/PATH，统一Maven与运行Java21 | R/scripts/use-jdk.ps1；JDK仍在C:/Users/29675/Java/jdk-21 | 已批准；Maven/Wrapper实际使用21.0.11 |
| A02 独立MySQL | 官方社区版8.4.11 Windows x64 ZIP；127.0.0.1:3307独立后台进程；初始化专用数据目录、三个库及三套受限账号 | R/tools/mysql-8.4.11-winx64；R/data/mysql84；R/config/mysql/my.ini | 已批准；实际版本/三库权限/UTC/目录验证通过 |
| A03 后端项目工具/依赖 | Maven3.9.15、Wrapper3.3.4 only-script；Boot4.1.1、MP3.5.17及§3必要依赖；配置测试底座/执行V001 | R/backend；R/.cache/maven（构建工具及依赖缓存） | 已批准；实际依赖树/BootstrapIT/verify通过 |
| A04 前端项目依赖 | §4全部13个直接依赖，精确版本；npm本地安装，生成package-lock并运行请求测试/类型/构建 | R/frontend/node_modules及源码/锁文件；R/.cache/npm | 已批准；锁文件/ci/typecheck/build/请求测试通过 |

批准A02包括显式创建`campus_maintenance_dev`/`campus_maintenance_demo`/`campus_maintenance_test`，以及只对应各自数据库、限127.0.0.1的`campus_dev`/`campus_demo`/`campus_test`。初始化管理员凭据和应用密码在获批实施时生成/设置，不要求把任何密码发到聊天。

本机秘密保存在`C:\Users\29675\.graduation-project\local-dev.yml`、`local-demo.yml`、`local-test.yml`及必要的数据库管理员客户端配置；仅本机访问，目录/文件权限限制到本机维护用户。项目只保存`config/local.example.yml`占位示例，秘密不进README、计划、普通日志、截图或Git。实例程序/数据/日志独立于phpStudy，停止/配置范围只含新实例。

## 2. 环境E项逐项决策

| ID | 版本/来源 | Windows/目录/影响 | 成本、验证与替代 |
| --- | --- | --- | --- |
| E01 JDK | 现有21.0.11，C:/Users/29675/Java/jdk-21 | x86_64实际java/javac已核对；A01仅项目进程变量 | 不新增采购/账号/Key；Wrapper实际报告21并编译才算完成；替代为IDE项目JDK设21 |
| E02 Wrapper/Maven | Wrapper3.3.4；Maven3.9.15；Apache/Maven Central | backend/mvnw、mvnw.cmd、.mvn/wrapper；MAVEN_USER_HOME=R/.cache/maven，maven.repo.local=其repository | Apache开源工具，无账号/Key；仅缓存落盘，不改全局Maven；验证-v、校验和、dependency:tree/verify |
| E03 Node/npm | 现有24.11.0/11.20.0 | D:/Node.js；复用，不升级；npm缓存显式项目路径 | 无新增安装/Key；typecheck/build/test实证；不装pnpm/yarn/bun |
| E04 MySQL | 8.4.11社区版，Oracle官方ZIP，见§5 | Windows x64；已有VC++ X64 Runtime14.51.36247；独立回环3307，A02范围 | 官方社区版无业务API Key/云账号；实际版本/权限/迁移验证；若已有可用独立8.4可改为复用复核；不能用phpStudy5.7代替 |
| E05 Chrome/Edge | 现有153.0.8010.48/155.0.4283.45 | 可执行文件已找到，不升级；工具当前连接内置浏览器 | B01已测内置浏览器WebGL2；独立Chrome/Edge硬件加速在实际工程补测；无需新浏览器下载 |
| E06 Git | 现有2.55.0.windows.3 | 不建仓/工作树，不提交；文件交付位置仍R | 版本可调用不等于已有仓库；OCR Git评审待以后获得建仓授权 |
| E07 Python | 现有3.11.8 | D:/Python；仅标准库静态核对，无pip安装 | 无账号/Key；可改用既有Node/PowerShell完成核对 |
| E08 Blender | 本批不选版本、不安装；条件工具 | T015选副本路线再核对GUI/版本 | 程序化Three.js示意几何可替代；不阻塞B01 |
| E09 3ds Max/导出器 | 本批不选版本、不安装；原始max/vrmesh用途待核查 | 必须合法匹配源软件及插件，T015再提具体范围 | 成本/授权尚未核实，不在本次批准中；不强制购买，不能用改扩展名或Blender伪装转换 |
| E10 glTF验证工具 | 本批不安装额外CLI | T015按实际GLB副本及浏览器检查能力选择 | 现有文件只读核对不是渲染/许可合格；不向在线工具上传私有素材 |

## 3. 后端P01精确基线及BOM核对

| 坐标/工具 | 固定或管理版本 | 用途/核查结果 |
| --- | --- | --- |
| org.springframework.boot:spring-boot-starter-parent | 4.1.1 | parent POM官方HEAD200；Java release=21 |
| org.springframework.boot:spring-boot-starter-webmvc | 4.1.1由parent管理 | Boot4 MVC模块；官方POM HEAD200 |
| org.springframework.boot:spring-boot-starter-security | 4.1.1由parent管理 | 默认拒绝、统一安全错误；官方POM HEAD200 |
| org.springframework.boot:spring-boot-starter-validation | 4.1.1由parent管理 | DTO校验；官方POM HEAD200 |
| org.springframework.boot:spring-boot-starter-flyway | 4.1.1由parent管理 | Boot4迁移自动配置；官方POM HEAD200，不用第二套自动建表 |
| org.flywaydb:flyway-mysql | Boot BOM 12.4.0 | 显式MySQL支持；flyway-core由starter引入/BOM同版本 |
| com.mysql:mysql-connector-j | Boot BOM 9.7.0 | JDBC驱动；服务端仍8.4.11，两者版本不同正常 |
| com.baomidou:mybatis-plus-bom | 3.5.17 | MP模块同版本管理 |
| com.baomidou:mybatis-plus-spring-boot4-starter | 3.5.17 | 官方POM可获取；不叠加Boot3或另一MyBatis starter |
| com.baomidou:mybatis-plus-jsqlparser | 3.5.17 | 分页支持；官方POM HEAD200 |
| org.springframework.boot:spring-boot-starter-test | 4.1.1由parent管理 | 共同测试依赖；官方POM HEAD200 |
| org.springframework.boot:spring-boot-starter-webmvc-test | 4.1.1由parent管理 | Boot4 MockMvc配套；官方POM HEAD200 |
| org.springframework.boot:spring-boot-starter-security-test | 4.1.1由parent管理 | 安全测试配套；官方POM HEAD200 |
| org.apache.maven.plugins:maven-wrapper-plugin | 3.3.4 | only-script分发；POM与官方命令模板可获取 |
| org.apache.maven:apache-maven ZIP | 3.9.15 | 9,348,861字节；在内存核对厂商SHA512并计算SHA256，未写盘/安装 |

本轮读取精确[Boot4.1.1依赖BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom)：Spring Framework7.0.9、Security7.1.1、Flyway12.4.0、Connector/J9.7.0、JUnit Jupiter6.0.3、Mockito5.23.0。按parent管理，不在pom分别硬覆盖这些版本。

MP starter POM内部导入Boot4.0.1 BOM、声明mybatis-spring4.0.0。本项目根Boot4.1.1依赖管理应决定最终Spring/Boot解析版本；T005必须用实际dependency:tree验证，元数据不能证明组合构建已通过。发现非预期混用时先查解析来源，不静默换已选基线。

Wrapper提案属性如下（尚未生成工程文件）：

```properties
wrapperVersion=3.3.4
distributionType=only-script
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.15/apache-maven-3.9.15-bin.zip
distributionSha256Sum=b0d9292f06c5faded31ddcb6cb69099f316d6fea40a7778624b259bad9fed18a
```

厂商SHA512为`251c676eb88684d6dd86c666817979d256a335c31ad34ab0e1df17eeca1cd504a6c890e906a46d04e51cc7a4fb02f33a75971ef750558034e0a81c257be02267`，与内存下载结果一致。官方ZIP.sha256地址返回404，所以不是抄一个不存在的厂商SHA256文件；上面的SHA256从已核对SHA512的实际字节计算。3.3.4的[官方Windows脚本](https://raw.githubusercontent.com/apache/maven-wrapper/maven-wrapper-3.3.4/maven-wrapper-distribution/src/resources/only-mvnw.cmd)已核对支持MAVEN_USER_HOME及distributionSha256Sum。

## 4. 前端P02/P03/P04精确直接依赖

下面13项构成A04当前安装范围。来源均为npm官方registry；每个版本元数据已读取，T006写package.json精确值并生成package-lock.json，不使用范围版本或预览版本。

| 名称 | 版本 | 类型/必要性 | Node/peer核查 |
| --- | --- | --- | --- |
| vue | 3.5.43 | dependencies，页面运行 | peer TypeScript可选 |
| vue-router | 4.6.4 | dependencies，路由 | Vue ^3.5.0，所选Vue满足 |
| pinia | 4.0.3 | dependencies，少量共享状态 | Vue ^3.5.11、TS≥5.6；@vue/devtools-api ^8.1.5为必需peer |
| @vue/devtools-api | 8.1.5 | dependencies，满足Pinia必需peer | 官方metadata已核对；不安装浏览器DevTools扩展 |
| element-plus | 2.14.7 | dependencies，表单列表组件 | Vue ^3.3.7，所选Vue满足 |
| echarts | 6.1.0 | dependencies，后续统计图 | registry可获取；本批不造示例统计业务 |
| three | 0.186.1 | dependencies，后续3D | 官方包，OrbitControls/GLTFLoader使用同包addons，不另装控制器/加载器包 |
| vite | 8.3.4 | devDependencies，开发构建 | Node ^20.19.0或≥22.12.0，现有24.11.0满足 |
| @vitejs/plugin-vue | 6.0.9 | devDependencies，SFC | 同Node条件；peer Vite支持8、Vue ^3.2.25 |
| typescript | 5.9.3 | devDependencies，严格类型 | Node≥14.17；满足Pinia/vue-tsc声明要求 |
| vue-tsc | 3.3.13 | devDependencies，Vue类型检查 | peer TS≥5.0；@volar/typescript2.4.28；选择JS编译器TS5.9.3作兼容基线，未采用本次查询到的TS7.0.2原生分发 |
| @types/node | 24.10.1 | devDependencies，Vite配置类型 | 同Node24主版本；符合Vite/Vitest类型peer条件 |
| vitest | 5.0.3 | devDependencies，必要公共请求逻辑测试 | Node ^22.12.0或^24.0.0或≥26；Vite支持8；现有Node满足 |

Node/peer声明满足是静态结果，不是构建通过。Pinia的peerDependenciesMeta明确@vue/devtools-api optional=false；Router自身依赖的6系DevTools API与根8系可能并存，由锁文件与npm ls确认，不通过删peer或强制legacy-peer-deps掩盖问题。

### 4.1 下载完整性记录

| 名称/版本 | 官方dist.integrity（SRI） |
| --- | --- |
| vue 3.5.43 | `sha512-o5qZoksdnjIKvW1srZ3ab7pcDNYAerBjRe54D0LBLfRdCYFrSgBHVXokMas35czQc0//lmx4/tuY4ZNQ+Rf2Ng==` |
| vue-router 4.6.4 | `sha512-Hz9q5sa33Yhduglwz6g9skT8OBPii+4bFn88w6J+J4MfEo4KRRpmiNG/hHHkdbRFlLBOqxN8y8gf2Fb0MTUgVg==` |
| pinia 4.0.3 | `sha512-XMQqpvjgG7LMqVhhFUzKLT4KEbsYbfZZ0CZU9PgdFm1O2VKBmIkykL8+SfNhOaT7sR0wT6BEgKT0YNDYWgmVRA==` |
| @vue/devtools-api 8.1.5 | `sha512-YJipMVAKe5wT5CWf5kTYCaNV7NMNjFVxJkIkJaJ4W/nCxEBzlZzrOsYKeCymdCrFZmBS/+wTWFoUs3Jf/Q6XSQ==` |
| element-plus 2.14.7 | `sha512-lrG7pZyCS+mpCNVpzxxyu5r35+T6V0hzkAeIwn7HA6to772XSTHKuYCZG51OfzlqaxGbNegLxs650zAF1j43gA==` |
| echarts 6.1.0 | `sha512-q0yaFPggC9FUdsWH4blavRWFmxdrIodbkoKNAjJudAI6CA9gNPxHtV2RcZNEepZVlk4yvBYkOkbk6HIVpIyHZA==` |
| three 0.186.1 | `sha512-blFeqb49wRCSGUGj7gtpfnSGHy2lwDk94RhUmS1c/hTby70kvChbWpkJ4Pm1390LqzzvTmzgXKHPEafJwCb8jA==` |
| vite 8.3.4 | `sha512-IftLE8phJhdNRIGNEV4JCCvfNScoZQVfcXaN8TQropWGJtOHcu+iAB9pHcuGvmEb/kddw0LBe/jS8ztdC6xdiQ==` |
| @vitejs/plugin-vue 6.0.9 | `sha512-rD/MORlhaZMlXWW0rEn4FB4wMinWC0z/D7Ye160S5+Rs1mC8ZDAdcDp4SjKfUhG5t8q1ktcPVw4xuTkPRlzrSA==` |
| typescript 5.9.3 | `sha512-jl1vZzPDinLr9eUt3J/t7V6FgNEw9QjvBPdysz9KfQDD41fQrC2Y4vKQdiaUpFT4bXlb1RHhLpp8wtm6M5TgSw==` |
| vue-tsc 3.3.13 | `sha512-jE4/bXdOMSHVxEGgQCqNllJy+9N3Gx6tfSAsg0yhFK/MAN6eXqISihupnDEKSCt12VVnjN2gI3AqLYcqoh5x/A==` |
| @types/node 24.10.1 | `sha512-GNWcUTRBgIRJD5zj+Tq0fKOJ5XZajIiBroOF0yvj2bSU1WvNdYS/dn9UxwsujGW4JX06dnHyjV2y9rRaybH0iQ==` |
| vitest 5.0.3 | `sha512-xMw97S3rjdtj5dkVat7jCsqWBpvchs3RlpQctUqwJD0KkERk40vz2fJ77lDwW/Vzh/pk18eItYAzkodhSes3jQ==` |

实际安装时npm按package-lock/SRI核对；本次仅读元数据，不是所有tarball已经下载并校验。版本源为`https://registry.npmjs.org/<包名>/<精确版本>`（scope包名URL编码）；包SRI后续与该源和锁文件复核。

### 4.2 当前不安装的条件测试/类型包

| 包 | 已核实候选 | 本批决定/替代 |
| --- | --- | --- |
| @types/three | 0.186.0 | three0.186.1元数据无types/typings；T016真实导入Three类型时采用匹配r186声明并另纳入所需范围；B01请求客户端不导入Three API，暂不安装 |
| @vue/test-utils | 2.5.1 | client.test.ts可用Vitest node环境验证，不需要挂载组件；将来真正组件测试再采用 |
| jsdom | 26.1.0兼容现有Node；本次最新查询30.1.2要求Node≥24.15.0（24系） | B01不需DOM模拟，跳过；不为测试工具升级Node；后续需DOM时提出兼容版本 |
| @playwright/test | 1.64.0，Node≥20，playwright同版本 | P05条件候选，B01跳过；T064前再复核/批准，不默认下载浏览器；现有CUA可取证 |

所有A04包为本地项目依赖，无业务API Key/外部账户要求；公开包元数据所列MIT/Apache许可按各包保留。开发辅助包不进入业务派单链；不加Axios、Vue专用3D框架、React/R3F、动画或物理运行引擎。

## 5. 独立MySQL安装/配置方案

官方来源：[MySQL8.4 Windows安装手册](https://dev.mysql.com/doc/refman/8.4/en/windows-installation.html)；[8.4.11 Windows x64 ZIP](https://cdn.mysql.com/Downloads/MySQL-8.4/mysql-8.4.11-winx64.zip)。本轮HEAD200、Content-Length=281,191,914字节（约268.2MiB）；不是解压后空间或已下载结果。

官方[MD5文件](https://cdn.mysql.com/Downloads/MySQL-8.4/mysql-8.4.11-winx64.zip.md5)记录：`2e833921898a9a030ea6bfe81bd811bc`。批准后下载必须核对该值、检查解压程序及可用签名，记录本地SHA256。不能以URL可获取或校验和存在代替mysqld运行/SELECT VERSION证据。

批准后的实施顺序：

1. 重新检查3307及R/tools、R/data/mysql84是否存在；只使用明确新实例路径，已有未知数据目录不清理/接管。
2. 下载官方ZIP到R/.cache/downloads，校验后解压到R/tools。已有VC++先复用，若实际运行提示缺运行库，再提出具体安装，不能自动全局更新。
3. 写独立my.ini：basedir/datadir/log路径均R下绝对路径，port=3307，bind-address=127.0.0.1，default-time-zone=+00:00；不加载phpStudy配置，不注册Windows服务。
4. 在新数据目录执行安全初始化；初始化凭据仅本机处理，立即设置安全管理员凭据并保存受限外部配置，不将临时/正式密码抄到本材料或普通输出。
5. 用后台隐藏窗口启动专用mysqld进程，记录PID和监听；按显式批准创建三个专用库及受限账号。建议仅SELECT/INSERT/UPDATE/DELETE/CREATE/ALTER/INDEX/REFERENCES，逐库授权，不授全局权限、其他库、GRANT OPTION或DROP。
6. 通过本地外部配置连接每个库，查询VERSION/DATABASE/CURRENT_USER和SHOW GRANTS并脱敏；确认测试账号不能访问dev/demo（用授权范围核对，不对其他库执行破坏性DDL）。
7. 配置附件绝对路径R/data/attachments/dev、demo、test。Bootstrap测试先核对test库/账号/active profile和规范化test目录，不接受dev/demo、父目录、相邻前缀或逃逸路径；不回退H2/Mock数据库，不调用flyway clean。

上面为批准前拟定步骤；批准后已实际安全初始化、建专用库/账号/授权、外置秘密及启动实例，实证见[环境结果](../tests/evidence/T004/environment-result.json)。无Windows服务、全局环境或phpStudy变更。

回退范围：停止本次专用实例，恢复本次项目配置；不改现有phpStudy/数据库/全局环境。删除新数据/缓存另作精确路径预览和明确批准，不在本次批准中夹带删除。

## 6. 获批后的项目命令与实际验收

以下命令现有工程可执行；Wrapper已经生成，不必重复生成。完整启动/停止方式见[README](../README.md)。项目脚本须点加载，测试显式使用test-local。

```powershell
# R目录：项目进程变量；不得使用setx或修改用户/系统JAVA_HOME
. .\scripts\use-jdk.ps1 -JdkHome 'C:\Users\29675\Java\jdk-21'

# R/backend：Wrapper生成时使用已批准固定插件/版本
mvn.cmd -N 'org.apache.maven.plugins:maven-wrapper-plugin:3.3.4:wrapper' '-Dmaven=3.9.15' '-Dtype=only-script'
.\mvnw.cmd -version
.\mvnw.cmd dependency:tree
.\mvnw.cmd -Ptest-local '-Dtest=BootstrapIT' test
.\mvnw.cmd -Ptest-local verify

# R/frontend：package.json和首次锁文件由T006按本清单生成后
npm.cmd ci --cache 'D:\快速访问\Desktop\毕业项目文件\Graduation_Project\.cache\npm'
npm.cmd ls --depth=0
npm.cmd run test -- src/api/client.test.ts
npm.cmd run typecheck
npm.cmd run build
```

生成Wrapper时同样将maven.repo.local指向R/.cache/maven/repository，不因全局缓存/镜像未列入本次范围而写入新全局配置。package-lock记录全部传递包、来源和integrity；Node24.11实际构建失败时先查根因，不使用--force/--legacy-peer-deps掩盖冲突。

| T项 | 必须实际证据 |
| --- | --- |
| T004 | Wrapper/实际java均21；MySQL实际8.4.11；三个库/受限账号/目录；phpStudy未被改动 |
| T005 | BootstrapIT.target_jdk_is_21；错误库/profile/目录守卫拒绝；V001迁移与重复启动；依赖树、verify退出0；错误JSON含requestId |
| T006 | client.test.ts的403保留身份、写超时UNKNOWN；锁文件/npm ci；严格typecheck/build；浏览器代理/刷新有真实反馈 |

批次全部通过上述内部验证后交付README启动步骤，状态待用户验收；不开始B02、不提交Git。

## 7. skills/MCP S/M项及开发工具决策

| ID | 核查/精确候选 | 当前决定、影响与替代 |
| --- | --- | --- |
| S01 Superpowers | 本会话插件6.4.2，已有技能路径可读 | 复用；执行、TDD、查错、完成前验证，不重复安装；项目文件/阶段规则优先 |
| S02 Visualize | 当前会话已有 | 本批不需要讨论原型；原型不能代替实际工程 |
| S03 Three.js skills | CloudAI-X/threejs-skills，commit b1c623076c661fc9b03dac19292e825a5d106823；本轮API stars=3532 | 本批跳过；GitHub license元数据null，未核实明确使用条件，不提出安装批准；官方Three文档替代 |
| S04 文档技能 | 会话已有documents/pdf/pptx等 | B13/B14按真实材料格式使用，本批不批量装渲染软件 |
| S05 find-skills/Skills CLI | 本会话find-skills可读；已查看skills.sh；CLI缓存/安装本轮未验证 | 不运行可能下载CLI的npx命令，不新增全局包；有实际选中的技能再固定CLI版本和项目作用域 |
| M01 Context7 | 当前工具未暴露可调用连接 | 本批跳过，不写未知客户端配置/Key；精确官方版本文档与registry/BOM直接读取替代 |
| M02 Playwright MCP | 当前未暴露连接；CUA浏览器实际可用 | 不安装MCP；现有浏览器取证；P05开发依赖与MCP分开 |
| M03 Three.js调试MCP | 当前未暴露连接 | T016/T062真实观测有缺口再提具体版本/权限；DevTools/renderer.info替代，不在发布包加桥 |
| M04 Blender MCP | 当前未暴露连接；模型路线未选 | T015按实际副本路线再提；不连接外部素材搜索/上传/付费功能 |
| Open Code Review | ocr命令实际可调用，预览报R不是Git仓库 | 本轮不能完成Git模型评审；静态契约检查照常；不为此自动git init或配外部LLM凭据 |

S03仓库真实存在的候选技能名：threejs-fundamentals、threejs-geometry、threejs-interaction、threejs-animation、threejs-loaders；还存在lighting/materials等。本次只检查仓库树及公开元数据，尚未审核每个SKILL全文、安装量或许可文本；因此不把候选记录写成安装推荐。参见[仓库](https://github.com/CloudAI-X/threejs-skills)和[技能目录](https://skills.sh/)。

## 8. 本轮验证记录与交付边界

- T001：只读环境记录在[environment.md](environment.md)，Java/Maven不一致、独立8.4缺口已具体化。
- T002：21张表、142项类型、52条HTTP契约、tasks全部38个DTO/VO引用及REQ-01—20已静态检查；发生的DTO/VO命名不一致已对齐原文件白名单，未改业务状态或范围。
- T003：官方registry/BOM/POM/下载HEAD/Wrapper模板校验如上；用户随后直接批准A01—A04，实际结果与批准前元数据区分记录。
- T004：8.4.11 ZIP 281,191,914字节，MD5通过，实际SHA256 A492371D687D2BAB088B0062581144A0044B8964BAEFDF4FAA579292B423D25C，Oracle签名有效；专用实例/三库/权限/UTC实证通过。中文路径使用受限本机junction→R，项目Maven跳过现有RC覆盖JDK17；见[实施裁定](../tests/evidence/B01-progress.md)。
- T005：实际Wrapper使用21.0.11；依赖树Boot4.1.1、Framework7.0.9、Security7.1.1、Flyway12.4.0、Connector9.7.0、MP3.5.17、Jackson3.1.5，未解析Boot4.0。独立审阅3项Important经RED→GREEN修复后最终BootstrapIT15/15及verify退出0，真实dev/test各V001成功，无账号；demo库已连通但未启动应用/迁移，不冒称已应用V001。Windows打包文件占用已通过停止专用进程后打包/重启解决，记录见README与审阅报告。
- T006：npm锁定127项传递包条目，13直接项精确版本/SRI匹配；ci/typecheck/build、14/14请求测试及浏览器代理/按钮/路由/刷新/窄屏通过。只包含当前引用的基础资源，不提前加载Three/ECharts或业务页面。
- B01内部验证及独立审阅修复完成，用户2026-10-12验收通过；原requirements/architecture哈希未变，未建仓/提交或开始B02。事实汇总见[B01-verification.json](../tests/evidence/B01-verification.json)、[审阅记录](../tests/evidence/B01-code-review.md)。
