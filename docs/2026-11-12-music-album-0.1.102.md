# TV 专辑直播放与播放队列样式（0.1.102）

日期：2026-11-12

## 变更

- 专辑列表、推荐专辑、歌手专辑及首页传入专辑的入口都会直接打开全屏播放页，并按服务端返回曲序播放专辑首曲。长专辑保留队列现有分页加载。
- 播放队列歌曲行移除独立卡片底板，歌曲时长在行尾对齐显示；当前正在播放时显示动态频谱小图标，暂停时隐藏。
- 新版本为 `0.1.102` / `versionCode 103`。不修改 Web、服务端或数据库。

## 验证与安装

- `Build-Release.ps1` 正式构建、Lint、Release 单元测试及 APK 签名验证通过。
- 使用独立 applicationId 在隔离 Android TV 模拟器运行 `UserPreferencesMusicTest`，`OK (3 tests)`。覆盖专辑列表点击直接播放首曲、队列时长与动态频谱、暂停隐藏、队列样式，以及既有音乐分页、停止行为、偏好和遥控菜单回归。
- 深色播放页截图：`build/music-album-20261112/screenshots/music-player.png`；浅色截图在测试暂停状态：`build/music-album-20261112/screenshots/music-player-light.png`。
- 正式 APK：`build/music-album-20261112/release/etkn-tv-0.1.102.apk`；SHA-256：`33506599bb690e84f9ad47648399fc04a5b43aa71eadd955f3e16ee4c1bfa0b1`。安装后从电视回拉的 APK 哈希一致。
- `192.168.31.188` 已通过 `adb install -r` 更新，保留既有应用数据；`SetupActivity` 启动成功，当前应用 PID 的 `AndroidRuntime` 致命异常计数为 0。电视更新前 APK 保存在 `build/music-album-20261112/device-before.apk`。
- 隔离测试应用已卸载，模拟器已退出；最终 ADB 只剩目标电视在线。真实用户账号下的音乐播放体验尚未实测。

未推送代码，未发布 GitHub Release。
