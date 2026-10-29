# 2026-10-29 影视探索实测修复 0.1.22

用户实测 0.1.21 反馈两问题 + 卡片样式建议：

- **海报墙卡片改网页版样式**：片名与年份压在海报底部渐变区（加文字阴影），不再占用卡片下方文本行——年份不再被裁剪；卡片高度即海报高度（198dp），行间距由 12dp 提升到 20dp。
- **闪退到首页**：启动链为 SetupActivity（有会话自动转 HomeActivity），进程崩溃重启后即"回到首页"。本地拿不到电视 adb（5555 拒绝），无法直接取证，本轮三管齐下：
  1. 海报墙卡片图改用 TMDb w185（解码位图约为 w342 的 1/3.4）——无限滚动的网格 ImageView 持有全部已加载位图，长刷累积是 OOM 头号嫌疑；
  2. 清单开启 `android:largeHeap`；
  3. 新增 CrashLog：未捕获异常（含 OOM Error）写入 filesDir/last-crash.txt 并交回系统默认处理，下次启动首个 Activity 弹窗显示异常摘要（前几行）后删除——用户可把弹窗内容反馈回来精确定位。
- 顺带修复：详情页滚动误触无限加载（browseView 未挂载时不触发 append）、分级筛选值防御解析（非整数不崩溃）。

## 验证

- 46 项单测、lintRelease、签名构建通过（Lint 曾拦下 CrashLog 的 java.nio.file.Files API 26 用法，已改为 FileOutputStream，minSdk 24 兼容）；证书 SHA-256 e02d4244… 与历史一致。
- 产物 `build/verify-release-0.1.22/`：etkn-tv-0.1.22.apk（SHA-256 3468ab13c4dab0594fec0917f404d6c3309cf5431b54528e876e0f187ead6334）、update.json、SHA256SUMS。
- 闪退根因仍未实证；若 0.1.22 仍闪退，弹窗内容即为定位线索。服务端无改动。
