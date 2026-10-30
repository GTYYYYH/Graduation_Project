# B03 开发工具安装记录

日期：2026-10-13。用途：开发辅助工具的安装与验证记录，不改变需求、批次状态或用户验收结论。

## 选择依据

已读取 B03 聊天、对应分享页面、B03-handoff-prompt.md、T015—T018、项目提示词和相关需求/架构条款，核对 frontend/package.json。当前路线是 Three.js 0.186.1 参数化几何、Vue 3.5.43 + TypeScript、Vitest，真实 WebGL 验证已有浏览器能力。

本聊天授权安装适合 B03 的工具；参考文件里的历史开发指令不作为本次开发授权。此次未编辑业务代码、依赖包、数据库或原始素材。

## 已安装

以下 7 个 skills 安装在 `C:\Users\29675\.agents\skills\<名称>`，属于用户级开发辅助知识，不进入项目发布包。

| Skill | B03 用途 | 搜索时安装量快照 |
| --- | --- | --- |
| threejs-fundamentals | Scene、相机、Object3D 层级、门轴变换、渲染器清理 | 14.4K |
| threejs-geometry | 墙、地板、床桌、卫浴和楼层的参数化几何 | 12.1K |
| threejs-interaction | Raycaster、OrbitControls、鼠标/触控、门点击与选取 | 11.8K |
| threejs-materials | 暖灰墙体、浅木家具、材质共享和释放 | 12.0K |
| threejs-lighting | 室内可见度、环境光、灯光成本和阴影取舍 | 10.8K |
| vue-best-practices | 响应式就绪状态、组件契约、Vue 与渲染器生命周期 | 40.8K |
| vue-testing-best-practices | Vitest、异步状态、组件行为与浏览器测试边界 | 12.6K |

来源与固定版本：

- [CloudAI-X/threejs-skills](https://github.com/cloudai-x/threejs-skills)：commit `b1c623076c661fc9b03dac19292e825a5d106823`；GitHub API 核对时 3540 stars。
- [vuejs-ai/skills](https://github.com/vuejs-ai/skills)：commit `c9d355ff23f654309dd02006be671859df0a134c`；GitHub API 核对时 2897 stars。
- 使用 Codex skill-installer 下载固定 commit 的指定目录；未安装整个技能合集。7 个 skills 共 40 个文件，都是 Markdown，无附带可执行脚本。安装量与 stars 是检索快照，不是可靠性保证。

Context7 MCP 已添加到 `C:\Users\29675\.codex\config.toml`：

```toml
[mcp_servers.context7]
url = "https://mcp.context7.com/mcp"
```

连接来源：[Context7 官方客户端配置](https://context7.com/docs/resources/all-clients)。本次采用已实测可用的匿名 HTTP 连接，未保存 API Key、未完成 OAuth 登录、未购买服务。远端服务版本在握手时为 4.3.0；它由服务商更新，不等于本机安装了该版本的软件包。

## Verified

- 官方安装器成功完成两组安装；逐文件 SHA-256 与安装前下载的固定 commit 内容一致，共 40/40 文件。
- 每个 SKILL.md 的 name、description 均存在，文内本地文件引用检查通过；Skills CLI 1.7.2 的用户技能列表识别全部 7 项。
- Codex 本会话已自动更新技能列表，实际出现全部 7 个 skills。
- Context7：initialize、tools/list、resolve-library-id、query-docs 均通过。随后使用本会话实际暴露的 Context7 MCP 工具再次完成 Three.js 库解析与资源释放文档查询，证明 Codex 内可调用。
- TOML 解析和配置比较通过：原有配置值保持一致，只增加 context7。CLI 曾省略 node_repl 的空 args，已恢复原空数组。
- 原配置备份及完整哈希/查询证据保留在 `C:\Users\29675\.codex\tmp\b03-tooling-20261013-102933`；备份不复制进项目，避免将用户级配置纳入发布材料。

## 使用方式

skills 已在本聊天识别，Context7 已可调用，本次不需要重启应用。在 B03 聊天下一轮可输入：

```text
沿用当前 B03 进度，按实际问题使用 $threejs-fundamentals、$threejs-geometry、$threejs-interaction；材质和灯光需要时使用 $threejs-materials、$threejs-lighting；Vue 修改和测试使用 $vue-best-practices、$vue-testing-best-practices。查库 API 时使用 Context7，先核对项目锁定版本和本地源码。保持当前批次范围、文件白名单和测试标准，不重新实现已完成部分，交付后等待我验收。
```

工具按需选用，不要求每轮读取所有 skills。本记录不向 B03 聊天发送消息或追加任务。

## Remaining Risk 与暂不安装项

- 社区 Three.js skills 包含旧版本示例，例如材质文档的 `uv2` 写法；不可直接当作 0.186.1 的准确 API。Context7 返回的版本列表也未证明有 r186 独立快照。版本相关行为以项目 node_modules/three 源码、锁文件、对应官方资料及实际测试为准。
- 匿名 Context7 当前实测可用，可能受服务额度或以后认证策略影响；届时使用官方文档替代，再按实际需要配置账号。查询只发送公共库名与技术问题，不发送项目源码或秘密。
- Blender、3ds Max 和 Blender MCP：参数化路线没有导入/转换缺口；.max 也不能靠 Blender MCP 直接转换，因此此次不安装。
- Playwright MCP：已有浏览器能力正在完成 B03 WebGL 验证，此次不重复安装。Playwright Test 是项目测试依赖，与 MCP 不同，后续有明确回归需求再处理。
- Three.js 调试 MCP：已有 renderer.info、生命周期诊断与真实浏览器工具，没有确认新的观测缺口，因此此次不加场景桥接。
- threejs-animation、loaders、shaders、postprocessing：当前简单门轴运动和参数化房间无需骨骼动画、GLB 加载或额外后期处理，暂不安装。
- 本次验证的是工具安装、识别和文档查询能力，不重新宣称 B03 功能或用户验收通过。
