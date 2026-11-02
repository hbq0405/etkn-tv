# 0.1.59 跳片尾直接切集

## 变更

片尾跳过原先 `seekTo(duration)`，再等待 `STATE_ENDED` 推进播放。现在自动与按钮跳过共用停止生命周期，直接释放当前播放器并调用已有下一集/队列推进。

- 保留实际 PositionTicks，Stopped 额外使用已有 `PlayedToCompletion` 字段标记本集完成；不伪造末尾进度。
- 下一集尚在查询时等待结果，期间禁止重建旧播放器；有下一集继续、末集退出。
- 片头仍跳到 IntroEnd；保留自然结束续播、音轨锁定和队列推进。
- 版本 0.1.59 / versionCode 60，沿用发行证书。

## 回归

先在独立模拟器安装 `.creditstest` 调试包复现：片尾跳过检测到额外 seek，断言 `expected:<0> but was:<1>`。

修复后定向仪器测试 `OK (5 tests)`，95.893 秒：

1. 自动/按钮跳片尾均无末尾 seek，下一集正确，停止仅一次且含实际位置和完成标记。
2. 下一集查询延迟时立即释放旧播放器，随后正确继续或末集退出。
3. 片头、片尾开关和模式持久化。
4. 自然播放结束续播及跨季音轨锁定。
5. 既有播放队列自动推进和退出。

`scripts/Build-Release.ps1` 完成 assembleRelease、lintRelease、testReleaseUnitTest：82 单测通过，Lint 无错误（82 个 warning）。发布 APK 的 20 个 JNI 文件与 0.1.58 逐项 SHA-256 相同，覆盖 arm64-v8a、armeabi-v7a、x86、x86_64。

## 构建交付

- APK：`D:\work\ETKN-TV\build\verify-release-0.1.59\etkn-tv-0.1.59.apk`。
- 包名 `xyz.etkn.tv`，minSdk 24，targetSdk 35；51,270,906 字节。
- APK SHA-256：`d3da26950fc02e30bf617dbc27f9734c3b08ba2204649544f3fdbed90d5f0980`。
- 证书 SHA-256：`e02d42443875c3631be48199e0df419b0570fcc421be7ff2b620f4c643e67bd7`；apksigner 校验通过。
- 同目录保存 update.json、SHA256SUMS.txt；尚未上传，update.json 内的发布 URL 只是构建元数据。

## 验收边界

仪器测试使用隔离调试包，发行 APK 完成构建、单测、Lint、签名和静态检查。真实电视待用户安装验收；未测量同设备切集改善的毫秒数。移除末尾 seek 可以省去该路径的额外等待，但下一集的播放器准备仍会发生，不能承诺零转圈。仅本地提交，不推送、Release 或发送发布通知。

隔离应用已卸载，任务专用模拟器已关闭。`tests/credits_skip_20261102_2235` 已核对归属、绝对路径及无重解析点，删除被执行策略拦截；回读确认仍在，未绕过，未纳入提交。
