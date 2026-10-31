# ETKN TV 0.1.39 管理员详情操作

## 目标与行为

管理员账号在媒体详情页增加“刷新元数据”和“删除”，普通账号不显示。权限来自当前 `/api/tv/Users/Me` 的 `Policy.IsAdministrator`；不以账号令牌持久化方式推断管理员。

- 刷新弹窗提供“替换所有元数据”（默认开）和“替换现有图片”（默认关），两项都关时不能提交；分别调用 replace_metadata、replace_images、replace_metadata_images。
- 遥控器可切换两个开关、取消或提交，按钮有明确的焦点边框。
- 操作目标始终为详情页媒体 ID，剧集不会误用推荐播放的单集 ID。
- 删除确认明确说明全部版本、整剧或整季范围与关联文件影响，默认聚焦取消。复用现有联动删除流程，成功响应后退出详情；失败保留详情并恢复操作。
- 删除后重新读取上级列表；媒体库保持当前库及排序，人物关联媒体列表重新创建并读取。请求进行时禁用两个入口，避免重复点击。
- 只为本地标准 item-* 详情显示管理入口；虚拟资源不增加删除能力。

## 服务端

ETKvNext `api/tv.py` 增加 POST `/api/tv/Items/{item_id}/Refresh` 与 DELETE `/api/tv/Items/{item_id}`。刷新先鉴权及验证管理员，再复用 media_server.refresh_item；删除复用已有管理员校验与 DeepDeleteService。第三方协议处理函数、查询与 DTO 未变。

服务端已热更新到 etkn，备份 `/tmp/etkn-hotfix-backup/20261031-100216-tv-admin-detail/tv.py`。运行文件 SHA256 为 `27fc45835b410e53602aea634670ae5d20093b372abaa660a580ceced999a7ba`，两套权限已修复，容器 healthy。

## 验证

- 58 项服务端离线回归通过，包括新增 10 项权限、三种刷新动作、联动删除适配和不存在目标测试；连接池创建前硬阻断真实数据库。
- 60 项 release 单元测试、Lint、Debug/Release 构建与固定证书签名校验通过。
- API36 TV 模拟器新增管理操作与删除后保持媒体库导航共 7 项通过；既有详情、人物/类似、章节/空简介、播放选择、分集矩阵与媒体库排序/分页 7 项通过，共 14 项定向仪器回归。
- 已提取并查看刷新、删除弹窗截图：`build/admin-detail-20261031/admin-detail-0.1.39/`；删除取消焦点清晰可见。测试日志位于 build/admin-detail-final-tests.txt 与 build/admin-detail-existing-regression.txt。
- 真实 HTTP 只验鉴权和预先核实不存在的目标：未登录401、普通用户403、管理员404；健康200，前端 JS 200/text/javascript，模块 import 正常。临时验收会话残留0。
- 未对真实媒体提交刷新或删除，未覆盖真实电视操作与后台刷新任务的自然完成周期。
- 未改原生播放库，最终 APK 中16个四ABI原生库与0.1.38逐字节一致。

## 安装包与交付状态

- `D:\work\ETKN-TV\outputs\etkn-tv-0.1.39\etkn-tv-0.1.39.apk`
- 版本0.1.39/code40，包名xyz.etkn.tv，minSdk24/targetSdk35，沿用既有证书。
- SHA256：`769ce4e0ede661519448651539ba23d99c664ed37403635a5f451786337942b8`。
- 两仓库仅本地提交，未推送、未发布 GitHub Release；安装新 APK 后才有 TV 管理入口。
