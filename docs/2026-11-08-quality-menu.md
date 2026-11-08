# 播放质量菜单

后续已增加60Mbps及自动优先原画，最新交付见 [硬件提前协商与自动网络降码率](2026-11-08-adaptive-transcode.md)。以下保留初次质量菜单交付记录。

2026-11-08，功能提交`94438e8`，仅本地未推送。

“码率”按钮改为“质量”。原画之外提供4K80/40、1080p20/12/8/4、720p2/1Mbps；不会出现1440p选项。尺寸或码率任一项超过所选上限时请求转码，向服务端传递MaxWidth/MaxHeight；两项都未超过时继续原画。H264硬件能力报告加入4K30，原有高帧无画面自动转码、轨道和通知处理保持。

105项单测通过，Lint和正式签名Release构建通过；20个JNI库与前版本相同。APK SHA256 `8c0aa3ab1ac64dfb64e0062a2b2831e85ad5257166931f20ee63246dac98442d`，版本0.1.99/code100。已保留数据覆盖安装到192.168.31.188并回拉哈希匹配，SetupActivity启动正常；旧包位于outputs/quality-supergirl-20261108/before.apk。app/build.gradle既有版本变更保持未提交。

超级少女的服务端修复已配套热更新etkn，详见D:\work\ETKvNext\docs\reports\2026-11-08-supergirl-quality.md。原有高帧/HDR/轨道切换用户已确认正常；本轮菜单和该片重播待用户实测。
