# 媒体库横幅年份与评分等比例缩放 0.1.73

用户已实机确认 0.1.72 去掉媒体库横幅 Logo 后加载正常、片名整齐。本轮补齐剩余问题：年份、豆瓣/TMDb 评分、两个本地评分图标、间距和底部渐变区域随卡片实际宽度等比例缩放，与片名共用宽度/192 的比例。切换五档图片大小时，既有卡片立即更新。

改动仅位于 TvActivity.posterCard 的横幅分支，竖幅、首页轮播和详情页保持原行为。背景请求继续为极小/小/中/大 360，极大 720；媒体库横幅仍无 Logo 请求。服务端未修改、未部署。

## 修改与回归

- TvActivity.java：只增加横幅评分区域的实际宽度缩放，文字用像素字号，几何尺寸按比例取整；沿用图片尺寸更新器与宽度变化监听。
- HomeFocusTest.java：扩展既有五档测试，验证片名、年份、评分、图标、评分栏高度和代表性间距的比例，同时保留零 Logo 请求、360/720 请求、角标避让及字体检查。
- app/build.gradle：0.1.73 / versionCode 74。
- 新增断言在旧版 0.1.72 上失败：AssertionError，年份字号必须随卡片变大。修复后的正式签名隔离应用 xyz.etkn.tv.bannertitle 在 emulator-5554 上最终 OK (2 tests)，同时覆盖首屏填满及末行焦点不移动时的分页续载。
- 正式 Release 构建与签名通过；87 项单测通过，0 失败/错误/跳过；Lint 0 错误、82 个既有警告；git diff --check 通过。
- 已回读并查看极小、中、极大截图；文字与图标跟随卡片变化。截图位于 outputs/etkn-tv-0.1.73/screenshots/。评分标识沿用既有本地位图生成，不扩大本轮改动范围。
- APK 中 20 个 JNI 库逐项与 0.1.72 哈希相同，字体与许可匹配源码及上一版。

## 交付

- APK：outputs/etkn-tv-0.1.73/etkn-tv-0.1.73.apk，52,927,694 字节。
- SHA-256：0f7ffa9a21f64bb4e3c85260229794090489211ade3a016d9d211b80ad5699a8。
- 正式证书 SHA-256：e02d42443875c3631be48199e0df419b0570fcc421be7ff2b620f4c643e67bd7。
- 192.168.31.188:5555 覆盖安装返回 Success，dumpsys 回读 0.1.73 / code 74；设备安装 APK 的 SHA-256 与本地产物一致。从正式 MAIN/LEANBACK_LAUNCHER 的 SetupActivity 启动成功，保留原有账号与数据。
- 验证日志、单测 XML、Lint 报告及 verification.json 保存在 outputs/etkn-tv-0.1.73/verification/。
- 仅本地提交，不推送、不发布 GitHub Release。0.1.73 的电视最终观感仍待用户使用确认，安装成功与模拟器测试不代替实机视觉验收。

## 验证过程边界

正式构建首次因 mergeDexRelease/classes.dex 被占用失败。核对唯一 Gradle daemon 为 IDLE、无活动构建后，按既有 SOP 执行 gradlew --stop，再运行 Build-Release.ps1 成功；未删除构建目录。交付统计脚本曾在 ZIP 关闭后访问对象而失败，确认构建与安装已成功后，只修正并重跑只读核验，未更改源码或重建 APK；最终 verification.json 已生成。

此前本任务临时目录清理被执行策略 blocked by policy 拒绝，已核实并保留现场；本轮 tests/banner-ratings-20261104 也保留，不换工具或删除方式绕过策略。其余 tests/、outputs/ 和 turn-records/ 未清理。
