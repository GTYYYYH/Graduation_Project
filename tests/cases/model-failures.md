# T062 加载与资源验证（2026-10-28）

环境：本机Windows、应用内真实Chromium/WebGL2；Intel Iris Xe/ANGLE D3D11；test后端8081、localhost:5175前端，独立test附件目录。夹具为B11BrowserHarness，真实Cookie登录合成报修用户，完整10栋/41层/296位置目录。减少动态开启以隔离资源生命周期；不作5秒、FPS或B12性能达标结论。

| 场景 | 方法与预期 | 实际证据 |
|---|---|---|
| 整体失败 | CDP仅阻断manifest.json；目录和普通入口保留，允许重试 | manifest-failed.png；清除阻断并重试成功 |
| 局部失败 | 仅阻断CafeteriaFloor.ts；其他场景保留，明确局部失败 | interior-failed.png；清除阻断后重试，interior-retry-restored.png显示原食堂位置 |
| 15秒反馈 | Fetch仅暂停Classroom脚本请求，保持超15秒 | interior-timeout.png；放行同代次后进入原教室，interior-timeout-recovery.json记录28.123秒含操作等待 |
| 旧目标迟到 | 暂停Office脚本，切至102室并进入后再放行 | 预期与实际目标ID均2115427807897952271，未被办公室覆盖；load-race.test.ts进一步覆盖旧重试代次 |
| WebGL丢失恢复 | WEBGL_lose_context真实扩展触发；保留目录及普通入口，恢复后重建唯一Canvas | context-lost.png/context-restored.png；恢复contextLost=false、running=true、Canvas=1 |
| 连续切换 | 101—110循环30次，逐次采样并断言内景状态、缓存≤2 | browser-30-switches.json；资源引用首尾120、geometries=8、textures=1 |
| 离页重进 | 离页读取dispose后诊断，重进核对Canvas并采样自然GC堆 | browser-dispose.json、browser-leave-reenter.json；5轮cache/entries/references/bytes均0、running=false，重进Canvas=1 |
| 引用与定时器 | 真实几何模板30次构造；最后scope释放；单元断言监听、observer、rAF、timer清理 | lifecycle-current.log 22/22；typecheck-current.log通过 |

局限：当前模板为本机参数化几何，没有外部图片贴图下载路径；因此以实际局部内景脚本失败覆盖资源失败，不伪造不存在的坏图测试。浏览器堆未强制GC，30次切换约25.3—30.5MB，5次离页采样约26.9—34.3MB有自然回落；仅支持本次场景未见持续累积或卡死，不证明任意长期运行绝无泄漏。renderer.info在dispose后仍有1个内部纹理计数，资源池引用已归零、context已丢失；该计数不等于仍存活的业务纹理或精确显存。重建不恢复旧业务权限的完整账号矩阵属T061/T064，不能由本场景替代。

所有CDP阻断与暂停均已清除，诊断临时window引用已删除，测试用户已退出；测试后端正常结束、5175前端已退出。保护检查见T062/protection-comparison.json：test业务表0、附件0，dev除login_session外业务表/图片未变，demo及保护源码未变。dev会话从63到65，未删除或推断其归属。
