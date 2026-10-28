# ETKN TV

ETKN 原生 Android TV 客户端。Android 7（API 24）及以上，遥控器操作，内置 AndroidX Media3 播放器。

## 首版功能

- 自动发现局域网默认 5257 端口的 ETKN；也可手动输入服务器地址。
- 用户登录、媒体库浏览、搜索、季集选择、续播、播放进度同步、音轨和字幕选择。
- 首页宽幅轮播使用当前登录账号的个人画像向量推荐；优先 Logo，缺少或加载失败时显示片名。无画像时提示积累观看/收藏，不替换为热门榜。
- 显示 ETKN 本地用户头像；在网页个人中心上传。
- 有效 Plus/Pro 实例及获模板权限的普通用户可使用影视探索、季度订阅、共享池/re0/频道搜索与确认获取；服务端最终鉴权，管理员沿用服务端权限。
- 可选“我的订阅 / 追更入库通知”，默认关闭；只匹配该账号有明确来源归属的订阅，并过滤片库/路径/分级权限。季度订阅按季度匹配，系统全局追剧记录不会冒充个人关注。首次开启建立基线，不补发历史入库。前台约每分钟检查，后台由 Android 调度（15 分钟周期，可能延迟），电视系统可能限制通知展示。
- 从本仓库 Releases 检查、下载、校验并安装更新。校验包名、版本、SHA-256 和当前安装签名，安装由系统确认。

下载：[最新发行版本](https://github.com/hbq0405/etkn-tv/releases/latest)

## 服务端

需要包含个人推荐、用户头像、个人订阅入库通知和来源上下文迁移 0175 的 ETKN 版本。
基础和播放调用 Emby 兼容 API；增强功能调用 ETKN `/api/discover`。非本地媒体使用 ETKN 直链/302，不添加流代理或转码。账号密码不落盘，会话令牌以 Android Keystore 加密。

## 构建与发行

JDK 17 以上、Android SDK 36（Build Tools 35）、Gradle Wrapper 8.13。

```powershell
$env:JAVA_HOME='你的 JDK 目录'
$env:ANDROID_HOME='你的 SDK 目录'
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

发行使用固定签名，在私有目录创建 `signing.properties`，字段为 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`，将绝对路径放入 `ETKN_TV_SIGNING_PROPERTIES` 环境变量。私钥和属性文件不入 Git，须另行安全备份；后续更新必须沿用同一签名，递增 versionCode。

```powershell
$env:ETKN_TV_SIGNING_PROPERTIES='本机私有目录\signing.properties'
.\scripts\Build-Release.ps1 -OutputDirectory '发行输出目录'
```

检查生成的 APK、update.json、SHA256SUMS.txt 和说明，之后通过 `gh release create v<版本> <发行文件> --repo hbq0405/etkn-tv --notes-file <说明文件>` 发布。源码与发行包均在同一公开仓库。

## 验证范围

首版经过后端定向回归、生产回滚验证、Android 单元测试、Lint 和签名构建。未接入电视设备，遥控器焦点、电视硬解码兼容、字幕效果、系统通知及安装更新流程须实机验收。播放器按设备实际支持的格式播放，不承诺所有编码都能硬解。
