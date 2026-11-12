# 0.1.112 烂番茄评分展示与排序

日期：2026-11-12。此次为用户授权的本地构建及 ADB 覆盖安装；未推送、未创建 GitHub Release。

## 变更

- 普通媒体库与分类列表提供“烂番茄评分”排序，使用 `/api/tv` 的 `CriticRating`。
- 卡片、焦点背景和详情页展示番茄图标及百分比，支持 0%，无评分隐藏；详情精确更新同步该字段。
- 版本递增为 `0.1.112` / `versionCode=113`，沿用正式发行签名。

## 验证

- `:app:testReleaseUnitTest :app:lintRelease :app:assembleRelease` 全部通过；读取 JUnit XML 共 116 项，失败与错误均为 0。
- 新 APK 与设备原有 0.1.111 的证书 SHA-256 相同：`e02d42443875c3631be48199e0df419b0570fcc421be7ff2b620f4c643e67bd7`。
- 正式 APK：`app/build/outputs/apk/release/app-release.apk`，SHA-256：`570ea5adee7941bd7de0e0d516840ddd655565b17269d2729f176dd610b6f480`。
- 目标设备 L055FCN：`adb install -r` 返回 Success，没有卸载或清除数据。回读安装版本为 0.1.112/code 113；拉回已安装 base.apk，其 SHA-256 与受测 APK 完全一致。
- 通过 MAIN/LEANBACK_LAUNCHER 启动 SetupActivity，返回 Status ok，进程和前台入口存在。
- 配套 ETKN 功能提交 `ebd73f65` 已热更新；真实新 Key 的 OMDb 连接测试 HTTP 200。

## 范围

评分格式和 0% 已由单元测试验证；生产抽样尚无烂番茄评分，未执行全量评分同步，未完成真实账号下的评分界面视觉/遥控器验收。播放、原生依赖及开机逻辑未修改。
