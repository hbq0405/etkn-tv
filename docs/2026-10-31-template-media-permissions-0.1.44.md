# TV 模板媒体管理权限 · 0.1.44

日期：2026-10-31。仅本地完成，未推送、未发布，未操作生产或真实电视。

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
