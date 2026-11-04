# 0.1.80 原盘切轨与用户主题锁定

## 目标与实现

- 修复 ISO 的音轨、字幕数量与服务器不一致时，正确轨道被显示为“尚未解析”的问题。TV PlaybackInfo 增加 `BlurayPlaylist` 和 `MediaStreams.TransportStreamId`，不修改 Emby/Jellyfin 共用 DTO。
- libbluray 优先选择服务器已探测的播放列表；Java Format.id 使用 TS PID＋编码。TrueHD 与同 PID 的 AC3 兼容流分别对应；普通文件、外挂字幕和整季音轨序号锁定保留原行为。
- 每次 seek 重新解封装后，用稳定身份重新绑定 FFmpeg 流下标和 TrackOutput，保留已公布的轨道。
- 原 SeekMap 查询错误地调用 `bd_seek_time`，会移动读取游标并从播放线程触发读取。新增固定 libbluray 1.3.4 源码附加函数，仅查询已加载的 CLPI 索引；实际 seek 由加载线程执行。原先观察到的 StackOverflowError 不能仅凭调用栈归因于此，连续跳转回归用于验证修复后的行为。
- 设置→显示新增“锁定主题”，按本电视的服务器＋账号持久保存明暗、主题色、背景、模糊、透明度和阴影。背景原图另存应用私有目录，加载时限制解码尺寸。锁定后停止外观轮询，拒绝在途更新；解锁立即恢复服务器同步。切账号和保存失败时保护快照归属与开关状态。详情和播放器继续固定暗色。

## 验证

- 89 项 Java 单测通过；发行构建、Lint 0 errors，使用既有正式签名。
- 四 ABI 原盘 JNI 已重建；ELF 仅依赖系统库和既有 libc++，未引入 FFmpeg 动态库。APK 共20个 JNI，只有4个 libetkn_bluray.so 改变。
- 新增20秒自制 AVC＋TrueHD＋两条AC3＋PGS 原盘，播放列表801。FFmpeg 将 TrueHD 兼容流计为额外音轨，真实复现本地4条、服务器3条的数量差异；测试元数据另保留缺席字幕，验证不能靠序号猜测。
- 8项模拟器回归通过：原盘PID匹配/音频实际输出/PGS实际显示/连续跳转、SeekMap不移动游标、用户锁定与壁纸保留、普通文件轨道、动态服务器外观、HEVC及5种音频输出、PGS字幕恢复、长按媒体信息。
- 保存失败回滚补齐后，最终发行包重新构建、89单测/Lint和主题锁定仪器回归再次通过。
- 后端50项离线定向回归通过。生产《星际旅行2：可汗怒吼》PlaybackInfo HTTP200，播放列表801、9音轨/35字幕全部带PID；匿名请求401，临时验收会话已删除。

## 测试素材

`scripts/Generate-DiscTestFixture.py` 使用 FFmpeg（libx264、AC3、TrueHD编码器）和tsMuxer2.7.0，从测试图、正弦波和PGS矩形生成 `app/src/androidTest/assets/disc-tracks.iso`，不包含影视内容。TrueHD 在tsMuxer中使用检测到的 `A_MLP` 类型。构建测试使用独立applicationId后缀 `.disctrackfix` 和正式发行签名。

## 交付边界

版本0.1.80/code81，APK SHA256 `54c645359595078afc2ac05d17605c8fd7262d9c27eb8fb600788f4b3faa5cea`。2026-11-05 02:18:53已ADB覆盖安装192.168.31.188，签名与0.1.79一致，回读版本81/0.1.80，启动SetupActivity，近期AndroidRuntime错误输出为空。未自动登录或恢复影片，目标影片现场切轨仍待用户确认。只读uiautomator返回null root，未获得界面树，不将启动状态冒充页面验收。

本地提交，不推送、不发布GitHub Release。验证日志和模拟器截图保留在 `outputs/etkn-tv-0.1.80/verification/`。本轮临时构建目录递归清理被执行策略拒绝；已只读确认未部分执行，目录保留，未更换工具绕过。
