# 0.1.113 四项评分布局、透明 TMDb 图标与 Guava 隔离

日期：2026-11-12。用户授权本地提交、正式签名构建与 ADB 保留数据覆盖安装；未推送、未创建 GitHub Release。

## 最终行为

- 横幅、竖海报的豆瓣、IMDb、TMDb、烂番茄评分按此顺序在海报底部同一行显示。移除独立烂番茄角标；按实际文字宽度统一缩放字号、图标与间距，适配五档卡片尺寸。无烂番茄评分时隐藏，0% 保留。
- 卡片和详情改用用户提供的 TMDb 图案。移除 JPG 外围白色背景和白色抗锯齿边缘，保存为带 Alpha 的 PNG；与 Web 使用相同字节。TMDb 和豆瓣之间放 IMDb。
- 用户反馈的 CTH811_ALI7G_A / Android 8.0 / arm64-v8a 播放初始化闪退，截图指向厂商系统旧 Guava 遮蔽应用 Guava，缺少 ImmutableMap.Builder.buildOrThrow。现有 Media3 1.8.0 仍保持；在 release 构建中将 Guava 隔离到 xyz.etkn.tv.internal.guava。
- R8 仅用于依赖包名隔离，关闭 shrink/optimize；保留应用、Media3 与 JNI 入口原类名/成员名。R8 生成的合成辅助类可重命名。未改解码器或播放传输链路。

## 验证

- 单元测试 116 项、失败/错误 0；Lint 0 errors、105 个既有警告；正式签名 assembleRelease 成功。
- 使用本轮独立 applicationId xyz.etkn.tv.omdbverify 的正式签名测试包，在模拟器执行仪器测试，正文 OK (3 tests)：
  - 横竖海报及五档尺寸，检查四项顺序、单行、边界、公共文字基线、无文字裁切和底部位置；同时校验 IMDb/烂番茄排序请求。
  - 反射校验 Media3 的 Guava 字段类型已属于私有包，初始化 AudioCapabilities，并创建/释放增强字幕开关两种播放器。
  - 内嵌 PGS 工作线程、外挂 SUP 及其他字幕格式回归。
- 已回看六张卡片截图，透明 TMDb 无白色四角，四项评分并排在底部。
- 逐个 DEX 类型表检查旧 Lcom/google/common/ 类型引用为 0；私有 Guava 类型引用 3886。应用与 Media3 源类名保留。20 个原生库与已安装 0.1.112 的原始字节一致。
- APK 0.1.113/code 114，最低 API 24；正式证书 SHA-256：e02d42443875c3631be48199e0df419b0570fcc421be7ff2b620f4c643e67bd7。
- APK SHA-256：c885abdf162da28bf71b4d8d7a72b5c86563cc89d81160587bc91a36c44d17cc。
- L055FCN 的 adb install -r 返回 Success；未卸载或清除数据。回读安装版本/code、拉回 base.apk 的 SHA-256 与受测包相同；firstInstallTime 保持原安装时间。通过导出 SetupActivity 启动，Status ok、进程和前台入口存在。
- 可交付 APK：outputs/etkn-tv-0.1.113/etkn-tv-0.1.113.apk。结构化结果见同目录文档 2026-11-12-ratings-guava-0.1.113-verification.json。

## 适用边界

可连接的实机是 L055FCN，用户反馈闪退的是另一台海尔/卡萨帝电视。依赖隔离已通过包结构和播放器初始化验证，但故障机是否恢复仍待用户安装新版回测；未将该推断表述为实机已修复。当前实机仅验证覆盖安装/启动，真实账号下四项评分的最终观感和真实影片播放未重新验收。

仪器测试只用合成数据和回环服务，未操作正式包的账号。测试 APK 的编译期注解警告仅在外部测试规则中准确抑制，没有向正式应用加入宽泛 dontwarn。部分本轮临时检视目录清理被执行策略拒绝，保留 D:/ETKvNext-agent-tmp/20261112-omdb-ratings/media3-inspect，未绕过策略。
