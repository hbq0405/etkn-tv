# ETKN TV 0.1.25 首页、竖屏与结束退出修复

日期：2026-10-30。用户授权本地提交、正式签名 APK 构建与 TV 模拟器验证；未推送或发布。

## 改动

- 首页删除冷启动自动续播。默认焦点等待继续观看返回，再选择第一张卡片；没有继续观看时保留媒体库回退。保留从媒体库返回时的焦点恢复，后台刷新不会抢焦点。
- 系统解码沿用 SurfaceView，由 MediaCodec 处理旋转。软件解码使用 TextureView，根据当前视频 Format 旋转原始帧，同时按旋转后的尺寸与像素比例计算画面比例。删除提取器的全局旋转与尺寸缓存，避免重复旋转和跨播放项状态残留。
- 修正 FFmpeg 原生输出在窗口报告零行跨度时的亮度拷贝宽度，使用已有的备用跨度；原问题会使软解丢失亮度、白色变黑。使用固定 FFmpeg 静态缓存重新链接四 ABI，保持原有解码配置。
- 电影或单集收到 STATE_ENDED 后 finish，沿用停止生命周期保存最终进度与回报停止一次，返回原页面。
- 版本 0.1.25 / versionCode 26。同步原生重建入口文档与项目行为规则。

## 验证

- Release 单元测试 46 项通过；lintRelease、assembleRelease 通过。
- API 36 Android TV x86_64 模拟器：26 项既有回归全部通过，覆盖首页焦点/缓存/刷新/返回媒体库、遥控器播放暂停/返回/快进退、首帧恢复、HEVC、AAC/AC3/EAC3/DTS/TrueHD、4K 10-bit 软解、ASS 与 PGS。
- 最终正式签名 APK 覆盖安装成功，5 项目标验收全部通过：首个继续观看仅聚焦、分区延迟顺序、手机竖屏样本、90°/270° 旋转样本、电影及单集结束返回并只回报一次最终停止进度。
- 竖屏样本分别执行系统解码→软件解码→系统解码，核对实际画面比例；旋转素材还验证四象限像素方向、颜色与黑边。用户示例为 720×1280、无旋转标记；模拟器基线的系统解码本就正常，不能据此宣称复现了用户电视上的全部症状。带旋转标记的软解问题有修复前失败与修复后通过的像素证据。
- 四 ABI JNI 的 ELF 依赖保持静态 FFmpeg，不依赖独立 libavcodec/libswscale；Release 与受测 Debug 的 16 个原生库逐个哈希一致。
- apksigner 通过；证书 SHA-256 与 0.1.24 相同：e02d42443875c3631be48199e0df419b0570fcc421be7ff2b620f4c643e67bd7。
- 包名 xyz.etkn.tv，minSdk 24、targetSdk 35，包含 arm64-v8a/armeabi-v7a/x86/x86_64。git diff --check 通过。

## 本地产物与证据

- APK：`D:\work\ETKN-TV\build\verify-release-0.1.25\etkn-tv-0.1.25.apk`
- APK 大小：46,010,632 字节。
- APK SHA-256：a25940d115f4aaa017e1a18699d385e341a885b7967645dbf7528cf55e634ccc。
- 本轮日志：`outputs/portrait-focus-20261030/`；主要为 `native-static-relink.txt`、`regression-test.txt`、`release-final-build.txt`、`release-acceptance-test.txt` 与 `final-images/`。
- 合成旋转素材仅进入 androidTest；个人示例及截图留在本地，未提交或打入发行 APK。

## 适用范围

模拟器与构建验证已完成。用户实际电视的芯片、系统解码与 HDR/Dolby Vision 输出仍待用户安装实测；未进行服务器代码修改或生产操作。
