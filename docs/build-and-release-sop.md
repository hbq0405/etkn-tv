# 构建与发布操作规范

- Windows 的 rg 目录搜索使用 `rg 关键词 目录 -g '*.java'`，不把通配符拼进路径。路径错误先核对已返回命中及真实目录，不反复盲试。
- `local.properties` 的 Windows 盘符冒号需要转义，如 `sdk.dir=C\:/Android/sdk`。本地属性不入仓库。
- 当前 Google SDK 工具使用 `android --sdk <目录> sdk list --all` 确认实际包名，安装使用 `platforms/android-36` 等斜杠名称。安装返回码须结合目录与安装清单核验，不能仅以 exit 0 判断包已安装。
- Media3 非稳定接口在调用类显式 OptIn，固定依赖版本并跑 Lint，不通过关闭错误来放行。
- 补丁首次失败先检查 diff 确认无部分写入，再用锚点唯一的确定性文本替换。不得连换 heredoc/管道/编码尝试。
- 发行前运行单元测试、Lint、assembleRelease；核验 API 24、包名、递增版本和固定签名。
- 发行私钥与密码只存 `.private/` 或指定私有目录，限制目录 ACL。不得输出密码、提交私钥或将凭据放入 Release。
- Windows 新启动的 Gradle 终端如果未设置 `ETKN_TV_SIGNING_PROPERTIES`，单独运行 `assembleRelease` 仍可能成功，但只生成 `app-release-unsigned.apk`。先检查实际产物；设置签名配置后使用 `scripts/Build-Release.ps1`，并确认 `apksigner` 通过。v0.1.3 已验证该流程生成签名包且证书与此前发行版一致；Gradle 成功本身不代表 APK 已签名。
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

## 首页头像账号切换与媒体库最新分区（2026-10-28）

- 首页头像应为遥控器可聚焦、可确认的入口。已登录账号凭据沿用 Android Keystore AES-GCM 加密格式保存在本机，账号列表只暴露用户 ID 与名称；切换时只改活动账号。新账号登录前先比对已保存的唯一媒体服务器地址，不匹配则在发送账号密码之前拒绝。此功能不引入多服务器账号列表。
- 首次访问账号列表时迁移原有单账号 token；退出/令牌失效只移除当前账号，保留同服务器的其它保存账号。登录新账号时清除任务栈并重新创建首页，使各 API/通知使用新活动账号。
- 只在所有创建的 ImageView 上设置圆角背景轮廓并开启 clipToOutline，覆盖媒体卡片、轮播背景和 Logo、设置页头像、菜单头像；头像使用圆形裁切。新增 ImageView 时也要补该处理。
- ETKN 的 `/emby/Users/{id}/Items/Latest` 已支持按 `ParentId=library-*` 查询，且后端已根据媒体库 `definition_json.show_in_latest` 对关闭的库返回空列表，并继续做用户媒体库权限过滤。客户端逐库请求该路由，非空分区标题为“最新+媒体库名称”，不新增后台开关映射或绕过服务器过滤。
- 首页焦点背景底部用垂直渐变过渡至首页 `INK` 底色，使下一区域继承同一底色；确保文字/焦点框仍清晰并保持渐变高度不遮挡主要内容。
- 每个新的 `exec_command` 都会启动独立 PowerShell，不保证继承其它会话设置的 `ANDROID_HOME`、`JAVA_HOME` 或 `PATH`。aapt2 与 apksigner 检查曾因这些变量缺失而未能完成；确认 APK/manifest/checksum 已生成后，必须在同一命令中从 `work/android-toolchain/paths.json` 读取 SDK/JDK 路径、设置环境并运行工具。捕获 apksigner 输出时先检查 `$LASTEXITCODE`，再筛选证书摘要；不要把 Select-String 无匹配误判为签名失败，也不要因环境缺失重建或替换发行资产。
- PowerShell 不要把长输出的原生工具直接管道到 `Select-Object -First`；提前关闭 stdout 可能使工具返回非零，即使已打印有效头部。先完整捕获输出，检查 `$LASTEXITCODE`，再从变量截取显示内容。
