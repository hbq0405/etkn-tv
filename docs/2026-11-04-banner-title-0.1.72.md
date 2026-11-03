# 媒体库横幅文字片名与背景请求档位 0.1.72

## 目标与结果

按用户最终要求，媒体库横幅取消 Logo 图片，改用打包字体的文字片名；大及以下四档背景请求 360，极大请求 720。字体、留白和黑色柔和阴影按卡片实际宽度等比例变化，左对齐、最多两行、溢出省略，给右上角集数角标留出空间。文字创建即显示，不等待背景加载。

变化只位于 TvActivity 的媒体库横幅卡片。图片尺寸切换时为既有卡片重新选择请求档位，并按新显示尺寸解码，沿用既有鉴权、缓存、背景缺失回退和焦点行为。详情页与首页轮播的 Logo 继续原有行为；竖幅海报与评分标识未修改。客户端版本 0.1.72，versionCode 73。

## 依据与边界

旧媒体库横幅的背景和 Logo 都使用 Images 的四线程 IO 队列，各有一个图片请求；豆瓣和 TMDb 评分标识本地绘制，无网络请求。旧背景按测量像素请求 360 到 720，服务端上取整，测量值超过 360 即可能使用 720。该差异和额外 Logo 请求由源码证实，但没有真实电视请求阶段耗时对照，不能据此断定单一性能根因或声称加载耗时已经达标。

服务端已有背景三档 360/720/1920、Logo 三档 180/360/720，支持这次客户端明确选档。撤销此前未部署的 backdrop 选档阈值实验及其测试改动，ETKvNext 最终工作区无本轮差异；本轮无需服务端热更新。

## 字体来源与许可

使用 atelierAnchor 得意黑 Smiley Sans v2.0.1 的原始 SmileySans-Oblique.otf，2,003,884 字节。只用于媒体库横幅片名，通过 ResourcesCompat 加载，保持原始字库，系统字体继续补足缺字。

- 上游：https://github.com/atelier-anchor/smiley-sans/releases/tag/v2.0.1
- 原字体：app/src/main/res/font/smiley_sans.otf
- 完整版权与 SIL OFL 1.1：app/src/main/assets/licenses/SmileySans-OFL-1.1.txt，应用内开源许可列表自动可见。许可仅清除上游一处行末空格，版权及许可文本不变。
- 字体 SHA-256：139b5dcbe70d6d52de85a62d148784c6af4bd161e38fdb62ba0c1e7e5065fca6
- 原始分发 ZIP SHA-256：299c0be6c960ae37361762eca76f7d0cd516615435bb96c0d4b98a1e70178a07

## 验证

- 正式签名 Release 构建、87 项单元测试通过；Lint 0 错误、82 个既有警告；git diff --check 通过。
- 正式签名的隔离应用 xyz.etkn.tv.bannertitle 在 emulator-5554 运行 HomeFocusTest 两项回归，最终 OK (2 tests)。覆盖五档背景请求、既有卡片切换档位、零 Logo 请求、打包字体、文字立即可见、字号随卡片变大、阴影、短中文/长中文/英文两行及角标避让，以及首屏填满/末行焦点分页、防重复和焦点保留。
- 回读并人工查看极小、中、极大截图；截图保存于 outputs/etkn-tv-0.1.72/screenshots/。
- 发行与隔离 APK 都沿用原正式证书：e02d42443875c3631be48199e0df419b0570fcc421be7ff2b620f4c643e67bd7。
- 实际 APK 包名 xyz.etkn.tv，0.1.72/code73，minSdk24、targetSdk35；20 个 JNI 库逐项与 0.1.71 哈希一致；APK 内字体与许可逐字节匹配源码。Release 资源优化后字体实际路径为 res/NF.otf，验证按内容匹配，不依赖原始资源名。许可格式规范化前后正式 APK 的全部 DEX/JNI 载荷完全一致。
- APK：outputs/etkn-tv-0.1.72/etkn-tv-0.1.72.apk，52,927,694 字节。
- APK SHA-256：4c656011d29f6c2cf69d21fa4391834a1aaa8eee1e34dbdedfee58688f21d1ae。
- ADB 覆盖安装 192.168.31.188:5555 返回 Success；设备 dumpsys 回读版本 0.1.72/code73。直接启动未导出的 HomeActivity 被 Android 正常拒绝，核对 Manifest 后改从正式 MAIN/LEANBACK_LAUNCHER 的 SetupActivity 启动成功；最终已安装 APK 的 SHA-256 与交付文件一致。
- 验证日志保存于 outputs/etkn-tv-0.1.72/verification/；隔离测试应用及测试 APK 已从模拟器卸载。

## 交付与未决

仅本地提交，不推送、不发布 GitHub Release。本轮未修改生产服务端。真实电视背景实际加载耗时和最终观感仍待用户使用确认，不把安装成功或模拟器结果当作实机视觉/性能验收。

本轮临时目录 D:/work/ETKN-TV/tests/banner-title-20261104 的递归删除在进程启动前被执行策略 blocked by policy 拒绝。已回读确认目录和七个本轮文件保留，日志另存正式输出目录；不换工具、Shell 或删除形式绕过策略。其余既有临时目录未触碰。
