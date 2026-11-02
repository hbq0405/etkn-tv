# TV 起播计时 0.1.60

用户授权服务端与电视两端联调，只优化 TV 路由，第三方不处理。0.1.60/code61 保留现有缓冲/解码/轨道及片尾行为，新增起播诊断日志 EtknPlaybackTiming，不记录 URL、令牌或影片名。

PlayerActivity 记录 Activity 建立、PlaybackInfo 入队/请求/返回/UI回调、source 开始、播放器构建、prepare、状态、解码器初始化与首帧。TvPlayerFactory 给本次播放的数据源注入 TransferListener，记录每次请求的文件偏移、open/首块/前1MiB及结束字节数，覆盖 MP4/MKV 头尾及续播读取。监听器不消费或改写媒体数据。

Build-Release.ps1 构建、82 Release 单测、Lint 和 APK v2 固定签名通过；20 JNI 与 0.1.59 逐一哈希一致。APK：build/verify-release-0.1.60/etkn-tv-0.1.60.apk。已通过 ADB 覆盖安装到 192.168.31.188 并确认版本 0.1.60/code61。

服务端 etkn 已安装 /api/tv 请求阶段及 ASGI 首块计时，健康通过。两端持续采集已开启；电视日志环仅64KiB，使用逐行即时落盘。请用户新播完整缓存，再测纯本地；新一次完整首帧样本仍待采集，尚未实施起播性能优化，不能将准备完成写为已消除转圈。

后端报告：D:/work/ETKvNext/docs/reports/2026-11-02-tv-playback-startup-timing.md。默认仅本地提交，未推送/Release。
