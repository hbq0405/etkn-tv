# TV 模板媒体管理权限 · 0.1.44

日期：2026-10-31。首次本地实现与构建记录；后续正式签名、配套热更新和 GitHub 发布见文末。

## 行为

- 详情页分别读取 `/api/tv/Users/Me` 的 `TvPermissions.CanRefreshMetadata` 与 `CanDeleteMedia` 控制刷新、删除入口。
- 服务端模板开关默认关闭；普通账号只获得开启的操作，仍保持普通账号身份和原有加密登录令牌持久化。
- 管理员原有按钮和旧服务端管理员策略兼容保留；私密账号仍要求重新登录。
- 删除复用现有确认弹窗（默认取消）和删除后返回刷新链路。刷新动作与空选择保护保持原样。
- 需要配套 ETKvNext 迁移 0178、模板配置与 TV 服务端鉴权变更。

## 验证

- Release 单元测试 70 项，失败/错误 0；Lint、Release 签名构建通过。Lint 存在既有警告。
- 模拟器 `emulator-5554` 安装最终 Release APK，使用同一发行证书签名的测试 APK 执行 AdminDetailFlowTest 和 PrivateAccountFlowTest，正文 `OK (16 tests)`。
- 覆盖普通账号仅刷新、仅删除、持久登录、管理员刷新删除确认与失败恢复、私密账号原有登录行为。
- 逐项比较 0.1.43 与本版 APK 的 20 个 `lib/*.so`，SHA-256 全部一致。
- APK 签名证书 SHA-256：`e02d42443875c3631be48199e0df419b0570fcc421be7ff2b620f4c643e67bd7`。
- APK SHA-256：`0d5855b16b2fcfece910980163add56a27779b6b1f87e783721e2801b2c6d923`。

## 交付与边界

- 版本 `0.1.44`，versionCode `45`。
- 本地资产：`outputs/etkn-tv-0.1.44/etkn-tv-0.1.44.apk`、`update.json`、`SHA256SUMS.txt`。
- 本轮测试仅使用本地合成服务和模拟器，未访问真实媒体执行刷新或删除，未安装/测试真实电视。
- 未热更新 ETKvNext、未应用生产迁移；需两端一起上线后再做真实业务验收。


## 正式密钥重新构建复核（2026-10-31）

用户补充安装失败并要求用正式密钥构建，随后明确原因为“上传错了”。按要求重新使用现有`.private/signing.properties`运行Build-Release.ps1，未修改功能源码或密钥。Release构建、Lint和签名核验通过，70项Release单测复用已通过缓存。

- 应用包名 `xyz.etkn.tv`，版本 `0.1.44`，versionCode `45`，最低API24。
- 签名仍为正式发行证书，SHA256 `e02d42443875c3631be48199e0df419b0570fcc421be7ff2b620f4c643e67bd7`，v2签名校验通过。
- 当前APK SHA256 `d509c280e315c7a40300ca366419e733cf91d2f2804debe601866c8473c42f99`。首次构建的哈希保留为历史记录；重打包携带当前提交构建元信息。
- 输出仍为 `outputs/etkn-tv-0.1.44/etkn-tv-0.1.44.apk`，已同步update.json与SHA256SUMS，20个原生库与0.1.43完全一致。
- 配套ETKvNext服务端已热更新：迁移0178、模板开关与紧凑布局、独立授权/即时撤权/旧客户端保存兼容的真实HTTP验收通过；既有模板开关保持关闭。
- 未发布、未安装真实电视；用户纠正上传文件错误不代表本轮完成实机安装验收。

## GitHub 正式发布（2026-10-31）

用户本轮明确要求“etkn-tv推送发版”。仅推送 ETKN-TV，未推送 ETKvNext。

- 源码 main 与带注释标签 `v0.1.44` 已推送；标签固定指向 `e30c614b6c9a66507b6b4355d27b453f2b07633c`，发布后不移动标签。
- Release `429984120` 于北京时间 **2026-10-31 22:27:14** 正式发布为 latest，非草稿、非预发布。
- 发布页：https://github.com/hbq0405/etkn-tv/releases/tag/v0.1.44 。
- 发布前重新检查实际 APK：包名 xyz.etkn.tv、0.1.44/code45、minSdk24、targetSdk35；正式证书与上文相同，v2签名通过。70项单测和Lint报告重新回读，失败/错误0、41条既有Lint警告；功能源码未改，沿用上轮已通过的16项最终Release模拟器回归。
- 20个原生库与0.1.43逐项哈希一致；复用同一上游源文件，并在本版源码包补齐已固定的libbluray1.3.4、libudfread1.1.2归档与重建说明。19个上游源文件逐项校验，ZIP完整性通过，旧版源码包未改。
- 草稿四资产名称、大小、SHA256、正文、main/标签与通知工作流一致后才正式发布。
- 正式发布后四资产全部匿名下载，哈希与本地一致；应用实际使用的 latest/download/update.json HTTP200，字节与已发布更新清单一致。
- Telegram发布通知工作流 `41210191196` completed/success，日志确认 delivery，未重复重跑。

| 发行资产 | SHA256 |
| --- | --- |
| etkn-tv-0.1.44.apk | d509c280e315c7a40300ca366419e733cf91d2f2804debe601866c8473c42f99 |
| update.json | 8ea275a163247af63ca163e1d1d26fc559c1477ffe9a81ce585a1c3863425ecf |
| etkn-tv-player-sources-0.1.44.zip | 44c9328c9203747a6b9752d3d1ab7ac0ff40fc10aec33525ed36858815e0d4fb |
| SHA256SUMS.txt | adb248c31ce6ea691e21303c069d4a0d062633c9bd5f7a44db173e805c9af9e8 |

发布资产和脱敏验证回执保存在 `outputs/etkn-tv-0.1.44/`；本轮匿名下载仅流式校验，不额外落地临时下载目录。真实电视安装、真实媒体刷新/删除及后台自然完成仍未验收。本轮未修改应用功能源码、签名密钥、服务端代码或生产环境。
