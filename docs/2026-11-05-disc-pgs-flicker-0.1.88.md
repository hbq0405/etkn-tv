# ISO 原盘 PGS 字幕闪烁修复 0.1.88

用户反馈 ETKN-TV 播放原盘特效字幕闪烁，后续明确为《阿凡达：水之道》ISO 原盘版，片头约 20 秒出现“二十世纪”标志时的 PGS 字幕。本轮修复已复现的 ISO/PGS 分包清屏问题；独立回归覆盖了分包清屏问题；0.1.88 覆盖指定电视后，用户实测反馈“可以了”，确认所报片段恢复正常。

## 原因与最小修改

- 原盘原生端通过 FFmpeg av_read_frame 返回数据包，DiscExtractor 原先逐包直接发布。一个 PGS 显示集可以分为 PCS、窗口、调色板、对象和 END 多个包。
- TvPgsParser 只有收到 END 才构建字幕；其他包仍返回空 Cue 列表。将这些中间包当作独立字幕样本，会替换正在显示的上一帧。原先还使用 END 所在包的时间，未保留 PCS 的实际呈现时间。
- 新增 DiscPgsSampleAssembler：每条原盘 PGS 轨独立合并显示集，到 END 才发布，保存 PCS 时间戳及标志。单个显示集限制 16 MiB；缺失、损坏或超限的未完成集丢弃，下一 PCS 可重新同步；正常零对象清屏集继续发布。
- DiscExtractor 重建轨道、跳转和释放时清理未完成集合。音视频、普通内封字幕、外挂 SUP 和 ASS 沿用现有链路。未修改解析器、服务端、固定依赖或 JNI。

## 验证

- 复刻原先逐包发布行为的三项单测全部失败；合并后全部通过，覆盖动画中间包不清屏、PCS 时间戳、明确清屏、完整包字节、多轨隔离及跳转/损坏集重同步。
- 正式发行构建通过：99 项单元测试，0 失败/错误/跳过；Lint 0 错误；APK 正式签名验证通过。
- 独立模拟器包 xyz.etkn.tv.pgsflicker（正式发行证书），8 项字幕兼容仪器测试全部通过。新增测试实际解码 24 帧双对象 PGS 调色板透明度动画，逐像素验证 alpha，检查旧位图不被新帧修改，末尾明确清屏。其他用例覆盖内嵌工作线程、外挂 SUP 时间、分片/复用、多对象和错误隔离。
- 另外 4 项真实播放器回归全部通过（120.76 秒）：合成 ISO 的 PID 音轨/PGS 选择和连续跳转、纯索引 SeekMap、外挂 PGS 跳转后字幕错误恢复、遥控快进及控制栏导航。仅使用模拟器 loopback 合成服务，不连接生产数据库。
- APK 内 20 个 JNI 与 0.1.87 逐一 SHA-256 相同。包名 xyz.etkn.tv、versionCode 89、versionName 0.1.88、minSdk 24、targetSdk 35。证书 SHA-256：e02d42443875c3631be48199e0df419b0570fcc421be7ff2b620f4c643e67bd7。

## 交付与边界

- 正式签名 APK：outputs/etkn-tv-0.1.88/etkn-tv-0.1.88.apk。
- APK SHA-256：7447fa0364e74697c81ac18db4381715bcc0b460891d2da7383af743fa668e8a。
- 证据：outputs/etkn-tv-0.1.88/verification，含旧行为失败、仪器测试结果、构建与 JNI 核验、已查看的合成 ISO 截图。截图与 Cue 断言不能替代用户实片连续闪烁的观感验收。
- 两个独立测试包已从本轮模拟器卸载，模拟器已退出；本轮 tests/pgs_flicker_20261105 已核对绝对路径、归属及无重解析点后清理。保留正式回归源码及修复包证据。
- 初次交付时功能本地提交 01bc17f，未推送或创建 Release；本轮未热更新服务器。用户明确要求通过 ADB 更新 192.168.31.188 后，于 2026-11-05 20:55:21（Asia/Shanghai）以 install -r 覆盖电视正式应用，保留应用及账号数据。安装回执 Success，回读 versionName=0.1.88 / versionCode=89，正式入口 SetupActivity 启动 Status=ok，当前进程启动检查无 AndroidRuntime 错误。
- 安装前已从电视提取旧版 0.1.87 到 outputs/etkn-tv-0.1.88/rollback-tv-0.1.87.apk；旧包与修复包经 apksigner 验证，均为同一正式发行证书。安装核对记录位于 verification/tv-install-verification.json。用户自行重测已确认的 ISO/PGS 片段后反馈“可以了，推送发版”，本次实片问题验收通过并明确授权 ETKN-TV 推送与发布。ASS、普通 MKV 或其他原因不在本修复的已验证结论范围。
## 正式发布补充

用户在指定电视实测后确认“可以了，推送发版”。2026-11-05 21:03:12（Asia/Shanghai），ETKN-TV v0.1.88 正式发布为 latest： https://github.com/hbq0405/etkn-tv/releases/tag/v0.1.88 。发行标签固定指向 fa5970d90518f420ca8ef25ea15d0b9af659b0f2，main 与标签已推送；本次发行也包含此前本地 0.1.87 的媒体库分组导航功能，服务端分组功能需要已有对应支持，本轮没有推送或热更新后端。

沿用 20:55:21 已安装并由用户确认正常的同一 APK，没有重新生成。草稿校验正文、四资产大小和 SHA-256、更新清单及远端源码身份通过；发布后匿名下载 etkn-tv-0.1.88.apk、RELEASE_NOTES.md、SHA256SUMS.txt、update.json 全部与本地逐字节摘要一致，公开 latest/download/update.json 同样一致。验证分别保存在 verification/draft-verification.json、release-verification.json。

Telegram 工作流 42032342993 在本次发行提交上 completed/success；日志唯一真实投递回执 message_id=63867，未重跑通知，见 verification/notification-verification.json。原包回滚备份与正式验证证据保留。本文档发布记录单独提交，不移动已发布标签，不替换发行资产。
