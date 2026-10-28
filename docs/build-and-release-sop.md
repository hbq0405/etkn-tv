# 构建与发布操作规范

- Windows 的 rg 目录搜索使用 `rg 关键词 目录 -g '*.java'`，不把通配符拼进路径。路径错误先核对已返回命中及真实目录，不反复盲试。
- `local.properties` 的 Windows 盘符冒号需要转义，如 `sdk.dir=C\:/Android/sdk`。本地属性不入仓库。
- 当前 Google SDK 工具使用 `android --sdk <目录> sdk list --all` 确认实际包名，安装使用 `platforms/android-36` 等斜杠名称。安装返回码须结合目录与安装清单核验，不能仅以 exit 0 判断包已安装。
- Media3 非稳定接口在调用类显式 OptIn，固定依赖版本并跑 Lint，不通过关闭错误来放行。
- 补丁首次失败先检查 diff 确认无部分写入，再用锚点唯一的确定性文本替换。不得连换 heredoc/管道/编码尝试。
- 发行前运行单元测试、Lint、assembleRelease；核验 API 24、包名、递增版本和固定签名。
- 发行私钥与密码只存 `.private/` 或指定私有目录，限制目录 ACL。不得输出密码、提交私钥或将凭据放入 Release。
- GitHub CLI 未认证时使用官方设备授权；账号主人在浏览器确认。授权完成必须通过 API 读取账号确认，再创建仓库/推送/发布；不得把设备码已输入当作登录成功。
- 只发布本轮校验过的 APK、update.json、校验清单和发行说明；同一仓库保存源码和 Releases。发布后匿名下载比对 SHA-256。
- 没有连接 adb 设备时明确记录未实机验收，不把构建通过说成电视操作和真实播放已通过。

- 编辑含中文的 UTF-8 源码时，Python `read_text`/`write_text` 显式指定 `encoding="utf-8"`；默认 GBK 解码失败前可能已有早先替换落盘，先检查 diff，再只续作未完成部分。
- Android 本地测试编译路径不提供 `com.sun.net.httpserver`，网络契约测试使用 `java.net.ServerSocket` 的本地模拟服务；6 项测试、Lint 与发行构建已验证通过。
- 自动发现必须读取实际媒体地址，不能把管理端口当成媒体入口。发送登录信息前，先匿名确认管理/媒体入口返回同一个实例 ID；媒体地址不可达或身份不同必须拒绝。升级旧会话后要求重新登录。
- 首页导航仅在上边界按 ↑ 展开，不拦截列表之间正常的上下移动；菜单按 ↓ 或返回隐藏并恢复原焦点。无电视连接时构建和接口验证不能作为遥控器实测证据。

## APK 更新签名读取兼容（2026-10-28）

- 触发：旧版应用内更新显示“更新包签名不符”，反馈设备为 Fengmi-L055FCN-TV；未获知系统版本，不根据型号推断 Android 版本。
- 先用官方 apksigner 分别核验已发行及候选 APK。v0.1.0、v0.1.1、v0.1.2 的证书 SHA-256 完全一致，禁止重新生成密钥或移除校验。
- Android 9/10 官方 PackageManager.getPackageArchiveInfo 实现仅在 GET_SIGNATURES 标志存在时收集证书。API 28+ 同时传 GET_SIGNATURES 与 GET_SIGNING_CERTIFICATES，优先 signingInfo，缺失时回退 signatures；空证书必须拒绝，区别无法读取与证书不同。
- 回归在模拟 PackageManager 的实际调用中重现此采集条件；修复前 7 项有 5 项失败，修复后全部通过。另保留不同签名、空签名、多签名与包名/版本拒绝用例。模拟结果不等于电视实测。
- 旧版校验器在安装新版前无法修复自身。发布修复版本后明确提供 APK 手动覆盖安装一次的路径，不要求卸载或清除数据；设备安装及下一次应用内更新仍须实机验证。
- 官方网页读取返回空内容时，停止相同盲试，使用 Python urllib 读取 android.googlesource.com 的同版原始源码并本地保存，已核验 Android 9/10；不把空搜索结果当作引用依据。
- 本轮 aapt2 dump badging 首次检查因错误匹配 sdkVersion 标签而断言失败。先检查实际输出及部分写入：只保存了源码、证书证据，未更新发行说明或文档；确认实际标签是 minSdkVersion 后再续作，实际 minSdk=24 / targetSdk=35 校验通过。检查输出字段名称，不把验证脚本断言失败等同于 APK 构建失败。
