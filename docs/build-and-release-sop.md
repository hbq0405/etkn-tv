# 构建与发布操作规范

- Windows 的 rg 目录搜索使用 `rg 关键词 目录 -g '*.java'`，不把通配符拼进路径。路径错误先核对已返回命中及真实目录，不反复盲试。
- `local.properties` 的 Windows 盘符冒号需要转义，如 `sdk.dir=C\:/Android/sdk`。本地属性不入仓库。
- 当前 Google SDK 工具使用 `android --sdk <目录> sdk list --all` 确认实际包名，安装使用 `platforms/android-36` 等斜杠名称。安装返回码须结合目录与安装清单核验，不能仅以 exit 0 判断包已安装。
- Media3 非稳定接口在调用类显式 OptIn，固定依赖版本并跑 Lint，不通过关闭错误来放行。
- 补丁首次失败先检查 diff 确认无部分写入，再用锚点唯一的确定性文本替换。不得连换 heredoc/管道/编码尝试。
- 发行前运行单元测试、Lint、assembleRelease；核验 API 24、包名、递增版本和固定签名。
- 发行私钥与密码只存 `.private/` 或指定私有目录，限制目录 ACL。不得输出密码、提交私钥或将凭据放入 Release。
- GitHub CLI 未认证时使用官方设备授权；账号主人在浏览器确认。授权完成必须通过 API 读取账号确认，再创建仓库/推送/发布；不得把设备码已输入当作登录成功。
- 只发布本轮校验过的 APK、update.json、校验清单和发行说明；同一仓库保存源码和 Releases。发布后匿名下载比对 SHA-256。
- 没有连接 adb 设备时明确记录未实机验收，不把构建通过说成电视操作和真实播放已通过。
