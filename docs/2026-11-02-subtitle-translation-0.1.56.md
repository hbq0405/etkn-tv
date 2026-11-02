# 0.1.56 文本字幕实时翻译

客户端实现与 ETKvNext 本轮字幕服务配套，versionCode 57。未发布、未推送，正式 APK 位于 `build/verify-release-0.1.56/etkn-tv-0.1.56.apk`，SHA-256 为 `e6e27f9929e024042a07262eb17a2137bfbba8db3d3dba379d179900ae622654`。

服务端无优先级匹配字幕时，PlaybackInfo 提供 `SubtitleTranslation` 计划；客户端在自动选择状态采集指定内封文本轨道的缓冲 Cue，分批上传文本和原始毫秒时间轴，经 `/api/tv/Items/{id}/{source}/SubtitleTranslation` 取得译文。缺少译文时沿用原字幕，手动选择或关闭字幕立即退出覆盖显示，切源和停止播放清理请求及视图。字幕按钮显示实时翻译/繁简转换及目标简繁。

提取器保持已有 P7 格式规则，只旁路选定字幕轨道。生成期间使用普通文本字幕解析，已有匹配字幕的 libass/PGS 路径不变。译文为纯文本，不复制 ASS 动画样式。实时采集接入 Progressive ExtractorsFactory，已验证 MKV 的 SRT/ASS；HLS/DASH 分段内封字幕未接入该采集器。PGS、VobSub 不执行 OCR。

AI 开关、模型、中文/双语输出、繁简优先级与持久外挂字幕由服务端控制。服务器缓存完整下一集后可先提取整轨翻译；实时分段保存到独立字幕目录，取得完整源并全部生成后才发布完整外挂字幕。

验证：81 项 Release 单测、Lint、签名构建、v2 签名验证通过；2 项新增播放器模拟器测试及 7 项原兼容测试通过。新增测试验证缓冲后续文本提前上传、每条原始时间轴、双语实际显示、迟到译文回退、手动切轨、退出清理和 ASS 跳转。验证包为独立 `xyz.etkn.tv.subtitlecheck`，没有清除正式应用数据。截图在 `build/subtitle-translation-20261102/`。

使用合成字幕服务验收客户端，未验证真实 AI 服务速度和翻译质量，未在用户电视真机验收。完整后端说明见 `D:\work\ETKvNext\docs\reports\2026-11-02-tv-subtitle-translation.md`。新增功能需要配套更新服务端及其 OpenCC/pysubs2 依赖。
