# ETKN TV 0.1.50：版本与音轨预览、选择框信息

## 显示规则

- 详情页和播放控件的版本按钮只显示槽位名；打开选择框显示服务端完整版本名称与视频参数。
- 两处音轨按钮继续优先显示 `Title`（副标题、简略信息）；音轨选择框及自动选择项改为优先显示 `DisplayTitle`（主标题、完整参数）。例如预览“国语”，选项“国语 · AC-3 5.1”。缺失字段时沿用另一标题兜底，移除默认标记。
- 字幕命名、服务端音轨 Index、本季音轨列表序号锁定、实际选中轨道判断与版本 ID 映射保持原有逻辑。

## 验证

- 81 项 Release 单元测试通过，包含主副标题区分、参数保留、缺失标题回退和默认音轨解析。
- `lintRelease`、`assembleRelease`、Release 仪器测试 APK 构建通过。
- Android TV 模拟器 Release 回归 5 项全部通过：两处预览与选择框、真实 Media3 切源切轨、字幕名称及无法匹配轨道保护、本季音轨锁定、版本原地更新与迟到分集响应保护。
- 截图已回读，确认预览简略、弹窗显示主标题或完整版本参数；证据位于 `outputs/etkn-tv-0.1.50/evidence/`。
- 测试仅连接模拟器内合成服务；未进行用户电视实机验收。

## 本地交付

- 版本 0.1.50 / code51，minSdk24、targetSdk35。
- APK：`outputs/etkn-tv-0.1.50/etkn-tv-0.1.50.apk`。
- APK SHA-256：`036e5cc837a2125ee11b0b8f81cfdd5bc02ff0350d17492e136f67d1ed18ac1c`。
- 正式证书 SHA-256：`e02d42443875c3631be48199e0df419b0570fcc421be7ff2b620f4c643e67bd7`，与既有发行包一致。
- 用户后续授权推送发版，已完成正式发布与匿名下载验证，见下方记录。ETKN 后端无修改。

## 正式发行验收

- 功能提交 `7830f6e95a47c096768104f9efa933d782adba87` 已推送 main；带注释标签 `v0.1.50` 固定指向该受测提交，发行后文档另行提交，不移动标签或替换资产。
- Release `430485171` 于北京时间 2026-11-01 17:46:39 正式发布，非草稿、非预发行，匿名 API 确认 latest 为 0.1.50。发行页：https://github.com/hbq0405/etkn-tv/releases/tag/v0.1.50 。
- 草稿阶段确认完整发行说明、四份资产名称、大小与 GitHub 摘要，核对远端 main 和标签提交。正式发布后四份资产匿名下载均为 HTTP 200，SHA-256 与本地一致；客户端使用的 `releases/latest/download/update.json` 同样 HTTP 200、原始字节一致、版本 0.1.50/code51。
- APK 保持上一轮受测包原字节与正式签名。与 0.1.49 比较，APK 中实际 20 个原生库逐项 SHA-256 一致；播放器源码包保留 19 份依赖归档及其原始摘要，仅更新包内 README 的版本指向，ZIP 完整性通过。
- Telegram release 工作流 `41294581853` 成功，源码提交一致，真实投递回执 `message_id=63034`，未重跑通知。
- 仅发布 APK、更新清单、播放器源码包和校验清单；本地验证证据及私有签名配置未上传。

| 发行资产 | SHA-256 |
| --- | --- |
| etkn-tv-0.1.50.apk | 036e5cc837a2125ee11b0b8f81cfdd5bc02ff0350d17492e136f67d1ed18ac1c |
| update.json | 436d529dfb51601eacb46f61cf18f7c90ca75ab5a751361212a20db5b08fb6cd |
| etkn-tv-player-sources-0.1.50.zip | b4694f2d1f6caa13ba86458974353e6ce6bd8c5fa6d1d385498b84edce8eac42 |
| SHA256SUMS.txt | d47fbbe4af7b58ec6112d6698c5021258e276167574fc8a72efe52360d584275 |

验收回执保存于 `outputs/etkn-tv-0.1.50/evidence/release-draft-audit.json`、`release-public-verification.json` 与 `release-telegram.json`。
