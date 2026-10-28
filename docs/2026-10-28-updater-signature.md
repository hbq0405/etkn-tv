# ETKN TV 更新包签名误报修复

用户反馈“更新包签名不符”，设备 Fengmi-L055FCN-TV，系统版本未知。

## 已确认依据

- v0.1.0 / v0.1.1 / v0.1.2 均通过 apksigner verify，单一 RSA3072 证书 SHA-256 为 `e02d42443875c3631be48199e0df419b0570fcc421be7ff2b620f4c643e67bd7`。
- 官方源码：Android 9 `pie-release`、Android 10 `android10-release` 的 `frameworks/base/core/java/android/content/pm/PackageManager.java`，`getPackageArchiveInfo()` 仅在 GET_SIGNATURES 标志存在时调用 collectCertificates。原更新器 API 28+ 仅传 GET_SIGNING_CERTIFICATES，会在此实现中得到无证书信息并误报签名不同。
- 官方原始来源：`https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/core/java/android/content/pm/PackageManager.java` 及同路径 `pie-release` 分支。

## 实际修改

API 28+ 同时请求两种签名信息，签名优先读 signingInfo，缺失时使用 signatures；明确拒绝空证书并分开错误提示。保留准确证书集合比较、包名、递增版本、下载大小/哈希及 HTTPS 来源校验。versionCode=3 / versionName=0.1.2，继续沿用原私钥。

仅增加 SDK 参数的包校验入口供回归模拟；生产调用始终传 Build.VERSION.SDK_INT。Mockito 仅用于测试，不加入 APK。

## 验证及边界

- 新增 7 项包校验回归，原逻辑下 5 项失败，修复后 7 项全部通过。
- 全部 13 项单元测试通过，Lint 0 错误、27 警告，assembleRelease 成功。
- 实际 APK 包名 xyz.etkn.tv，versionCode 3，最低 API 24，target 35。三版证书一致。
- GitHub 发布及匿名下载校验结果以该版 Releases 和对应交付目录 release-verification.json 为准；本文件记录构建前置验收。
- 未连接电视，不宣称覆盖安装、遥控器及真实播放、后续应用内升级已实测。

## 用户安装方式

旧版更新器无法通过下载新版修复自身检查；从 Releases 取得新版 APK，使用电视文件管理器手动覆盖安装一次，保留旧应用与数据。以后更新仍需一次真实升级来验收。

播放全屏、详情背景、片头片尾跳过暂缓，遵守先完成首页的范围约定。
