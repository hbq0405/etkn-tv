# L055FCN 杜比 P8 画面发灰：ADB 对照诊断

日期：2026-10-31，Asia/Shanghai。先完成只读对照诊断；随后经用户授权修复并覆盖安装 0.1.43。以下原诊断保留当时事实，修复验收见末节。

## 结论

已确认 ETKN-TV 和 AFU 在这台设备上使用不同的视频输出路径。ETKN-TV 的 HDR 视频进入 TextureView，并被合成进普通 sRGB 应用图层；AFU 使用独立 SurfaceView 视频层，实际保留 BT2020 / SMPTE 2084（PQ）/ Limited range。结合用户确认同机同一 P8 文件在 AFU 正常、ETKN-TV 发灰，问题高度指向 ETKN-TV 的 HDR 输出兼容，而非这次走了软件解码。

0.1.42 源码对 L055FCN/API 28 及以下一律启用 TextureView；这原本是 0.1.28 的旋转兼容处理，当前无旋转的 HDR 电影也被纳入。在最初诊断阶段尚未安装候选包；后续修复已获用户同片观感确认，见末节。

## 设备与版本

- ADB 连接用户指定的局域网电视；型号 L055FCN，Android 9/API 28，平台 tl1。
- ETKN-TV：0.1.42/code 43，进程 PID 5605；本地源码 HEAD b9c82aa，构建版本一致。
- AFU（com.attempt.afusektv）：3.2.0/code 10737，进程 PID 7298。
- SurfaceFlinger 报告 HDR10=1、HLG=1、DolbyVision1=0。这表示本次系统上报能力，不能将 AFU 色彩正常表述成已证明完整杜比视界输出。
- 用户明确确认两次对照是同一台峰米、同一个《火柴盒大电影》杜比 P8 文件。

## 运行证据

| 项目 | ETKN-TV | AFU |
| --- | --- | --- |
| View hierarchy | android.view.TextureView，1920×804 | android.view.SurfaceView，1920×804 |
| 解码器 | OMX.amlogic.hevc.decoder.awesome | OMX.amlogic.hevc.decoder.awesome |
| 视频层 | 没有独立 SurfaceView，内容合成进应用 RGBA_8888 层 | 独立 SurfaceView 视频层 |
| 视频输出颜色空间 | 应用层 dataspace=(deprecated) sRGB Linear Full range | 视频层 dataspace=BT2020 SMPTE 2084 Limited range |
| 视频尺寸 | 源 3840×1608；厂商日志输出降为 1920×804 | activeBuffer 3840×1608；显示区域 1920×804 |
| 用户观感 | 灰蒙蒙、饱和度低 | 通透、色彩正常 |

ETKN-TV 在 16:17:48 初始化系统 HEVC 解码器，日志包括：

```text
MediaCodec: [OMX.amlogic.hevc.decoder.awesome] setting surface generation
OmxVideoDecoder: Use OSD layer if don't use HWC.
OmxVideoDecoder: mMustOsdDisplay is true
SurfaceUtils: set up nativeWindow ... for 3840x1608, color 0x11, rotation 0, usage 0x402b00
AmlogicVideoDecoderAwesome: omx output size changed from 3840*1608 to 1920*804
```

media.resource_manager 记录 ETKN-TV 在 16:17:48 占用硬解、16:20:18 释放；AFU 在 16:20:33 占用 OMX.amlogic.hevc.decoder.awesome。AFU 活跃资源和实时 View/视频层同时核对，未仅凭历史 metrics 推断当前解码器。

## 源码关联与修复方向

- app/src/main/java/xyz/etkn/tv/PlayerActivity.java:114：useTextureVideo 对 softwareVideo 或 L055FCN/API≤28 返回 true。
- 同文件 :85、:185：初建播放器和播放源创建时使用该决策；因此这台设备普通无旋转 HDR 电影也固定 TextureView。
- 调整方向：缩小旋转兼容范围，让适用的 HDR 电影在创建解码器之前选择 SurfaceView，并回归既有 90°/270° 竖屏行为。不能恢复已发生厂商 usage 不兼容异常的运行中跨表面切换方案。
- 本轮不改软件解码；软件链路的 HDR 映射缺口仍属另外的覆盖范围，不能拿它解释这次已证实的系统硬解现场。

## 验证边界

已执行用户控制下的同机同片 ADB 对照，读取版本、View hierarchy、SurfaceFlinger、media.metrics、media.resource_manager 与脱敏解码日志。media.codec 服务在本机不存在，未重复调用；改用实际存在的资源管理服务核对。未 root、未改系统属性、未清应用数据、未清 logcat、未安装候选包、未切换解码选项或修改电视画质。AFU 保持用户最后开启的播放状态。

本轮独立日志监听已停止。必要脱敏事实已摘录到本报告。清理命令在进程启动前被自动审批策略 blocked by policy 拒绝，未执行删除，未更换工具绕过。tests/tv_hdr_adb_20261031 下 etkn-snapshot.json、afu-snapshot.json、switch-sanitized.log 三份本轮脱敏临时文件保留；既有 tests、outputs 和其他任务内容保持。
## 0.1.43 修复与实机验收

- 用户明确要求修复后，将 L055FCN/API≤28 的输出决策缩小为：所选源视频轨的 VideoRange、ExtendedVideoType 或 ColorTransfer 明确 HDR/DolbyVision/HLG/PQ 时，硬解在创建播放器前选择 SurfaceView；SDR 与元数据缺失保留 TextureView 旋转兼容，软件兼容保持原路径。
- 将所选 MediaSource 的赋值提前到视图选择之前。切源和切解码模式沿用先释放旧播放器、再选新视图的顺序，没有对运行中的 MediaCodec 跨表面切换。
- 用户截图已有 DolbyVision HDR10、bt2020、smpte2084 信息；客户端直接使用已有媒体信息，不依据文件名判断，不改后端或媒体传输。
- Release 70 项单元测试、Lint、签名构建通过。新三项单元测试覆盖 HDR/HLG/Dolby/PQ、SDR/缺字段及设备和软件模式边界，含截图中的 DolbyVision HDR10 值。
- 独立诊断包 xyz.etkn.tv.diagnostic 在该 L055FCN 真机执行三项仪器测试：HDR 输出选择及硬→软→硬重建、90°/270° 实际四象限像素方向和比例、返回退出及停止进度回报，全部通过（OK，3 tests）。合成素材只证明选择与生命周期，不宣称其为真实 HDR 视觉验收。
- 沿用原签名覆盖安装 0.1.43/code44，未卸载或清除正式应用数据。用户在同一部杜比 P8 文件上明确反馈“已播放，颜色正常了”。
- 首次实际播放采到独立 SurfaceView、3840×1608、BT2020 SMPTE_170M Limited range；后续采到 3840×1600、BT2020 SMPTE 2084 Limited range，硬解仍为 OMX.amlogic.hevc.decoder.awesome。后续尺寸不同，不能把它与原诊断当作完全同一版本的 PQ 对照；修复结论由用户同片观感与独立视频输出路径共同支持。完整杜比视界未验证。
- APK 四 ABI 共 20 个原生库与 0.1.42 及实际受测 Debug APK 逐一 SHA-256 相同，签名证书一致。保留上一版供回退；本轮只调整 Java 输出决策、测试、版本和相应规范。
- 用户随后明确授权推送发版。发行资产包含签名 APK、更新清单、SHA256SUMS 和固定依赖源码包；诊断日志与截图留本地，不上传为发行资产。
