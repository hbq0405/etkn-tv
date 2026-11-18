# 音乐视频顺序调整 0.1.134

歌手、专辑或文件夹详情先展示音乐内容，附带 MV 区块放在列表末尾；音乐视频独立标签页和播放行为保持现有实现。

- 本地提交：`628e80e`；音乐视频基础功能提交 `74485de`。
- 版本：0.1.134 / versionCode 135。
- 正式包：`D:\ETKN-TV-Releases\v0.1.134\etkn-tv-0.1.134.apk`。
- SHA-256：`8c1950e8c6f525e2bc2b7a668b900e168414d3545bf67f4adbc78018387dfe7f`。
- `Build-Release.ps1` 的 release 单测、Lint、assembleRelease 与 apksigner 均通过，沿用固定发行证书。
- 用户授权后，L055FCN 通过 `adb install -r` 从 0.1.132 / code 133 覆盖到 0.1.134 / code 135，返回 Success；设备 base.apk 的 SHA-256 与交付包一致。
- 更新前后开机辅助服务 `xyz.etkn.tv/.BootStartupService`、`accessibility_enabled=1`、StartupReceiver 启用状态全部保留，系统实际绑定“ETKN TV 开机启动”。
- 正常 Leanback 入口启动 Status ok，前台为 SetupActivity；检查期间未见该应用 AndroidRuntime 致命异常。MV 播放与遥控器交互仍待用户验收。
- 只提交本地；未推送、未发布 GitHub。
