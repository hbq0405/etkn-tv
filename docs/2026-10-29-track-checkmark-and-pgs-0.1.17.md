# v0.1.17 音轨字幕当前流标记与图形特效字幕卡顿修复

## 实际变更

- 音轨和字幕选择列表在当前 Media3 实际选中的流后追加 `✓`，字幕关闭时在“关闭字幕”后追加 `✓`。
- 保留内嵌 PGS 独立字幕解码路径，避免图形特效字幕经过 PNG 往返造成播放卡顿；外挂 SUP 和 ASS 路径继续按各自解析方式处理。

## 验证

- Release 单元测试 32 项通过。
- Release Lint 和签名构建通过。
- Debug instrumentation 测试源码编译通过。
- APK 使用既有 ETKN TV 发布证书签名，包名 `xyz.etkn.tv`，versionCode 18，versionName 0.1.17，minSdk 24，targetSdk 35。
- 真实电视已验证图形特效字幕不再卡顿；本轮音轨/字幕勾选列表尚未由本机执行电视操作验收。
