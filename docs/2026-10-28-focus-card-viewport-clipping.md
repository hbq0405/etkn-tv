# 首页焦点卡片视口边缘裁剪修复

## 根因

- Android `ScrollView` / `HorizontalScrollView` 的焦点定位（`requestChildFocus → scrollToChild → computeScrollDeltaToGetChildRectOnScreen`）只按满视口 `[scrollY, scrollY+height]` 计算滚动量，把聚焦卡片边沿精确对齐到视口边缘，不为焦点放大预留任何余量（AOSP android-8.0.0_r1 与当前主线源码一致，`getPaddingTop` 等不参与该可见带）。
- 卡片聚焦按 1.08 倍缩放，竖版 248dp 高卡片单侧溢出约 9.9dp、横版 264dp 宽卡片约 10.6dp。行内裁剪已由上次 12dp 行内边距与 `clipChildren=false` 处理，但行间滚动到视口顶部/底部边缘时，缩放溢出仍被滚动容器视口裁掉，表现为“焦点卡片上面被切掉”；行首/行尾卡片横向同理。
- 焦点定位的单一收口是 `protected computeScrollDeltaToGetChildRectOnScreen(Rect)`（`requestChildRectangleOnScreen` 与 `requestChildFocus` 两条路径都经过它）。

## 实际变更

- 新增 `FocusScroll`：`Vertical extends ScrollView`、`Row extends HorizontalScrollView`，重写 `computeScrollDeltaToGetChildRectOnScreen`，把目标矩形按 12dp 余量外扩后再交给框架原实现，使焦点滚动落位在余量内，放大卡片不再贴视口边缘。
- 首页 `homeScroll` 改用 `FocusScroll.Vertical`；三处首页横行改用 `FocusScroll.Row`，行内左右各加 12dp 边距（`clipToPadding=false` 保持），行首/行尾卡片缩放溢出获得滚动范围与绘制空间（`getScrollRange` 含 padding）。
- `TvActivity.screen()` 的通用竖向滚动容器同样替换为 `FocusScroll.Vertical`，影视探索等网格页获得一致行为；纯按钮页仅滚动落位略提前，无视觉差异。
- `TvActivity` 焦点缩放比例提为常量 `FOCUS_SCALE`，与 `FocusScroll.MARGIN_DP` 形成显式配对约束；新增 `FocusScrollGeometryTest` 锁定“全部卡片尺寸的单侧缩放溢出 ≤ 12dp 余量”，未来调大缩放或卡片尺寸会测试失败。
- 版本 0.1.7（versionCode 8）。

## 验证

- `assembleRelease`、`lintRelease`、`testReleaseUnitTest`（含新增几何不变量测试）全部通过；apksigner 校验签名证书 SHA-256 `e02d42443875c3631be48199e0df419b0570fcc421be7ff2b620f4c643e67bd7`，与既有发行一致。
- 构建脚本在 Windows PowerShell 5.1 下因 `utf8NoBOM` 编码参数中断（该参数需 PowerShell 7）；APK 与签名验证已完成，update.json 与 SHA256SUMS.txt 按脚本同格式（UTF-8 无 BOM）另行生成。本机未安装 PowerShell 7。
- 本机模拟器验证未完成：已安装 emulator 37.2.12 与 android-36 android-tv x86_64/arm64、android-33 android-tv x86 镜像并创建 AVD；x86_64 需硬件加速（AEHD 未安装、WHPX 未启用，均需管理员权限），arm64 镜像被 QEMU2 拒绝（不支持 x86_64 宿主模拟 ARM），32 位 x86 纯软件模拟引导 20 分钟无进展（QEMU 工作集冻结在 113MB，adb 持续 offline）。焦点行为验收待电视实测。

## 未覆盖

- 真实电视上的焦点滚动落位、行首/行尾卡片完整显示仍需实测；模拟器路径需先启用 WHPX（管理员 + 可能重启）或安装 AEHD 驱动。
