# ETKN TV 0.1.29：详情页状态按钮与媒体库增强

## 变更

1. 详情页新增「☆ 收藏 / ★ 已收藏」「标记已播放 / ✓ 已播放」切换按钮，调用 `/api/tv` 的 FavoriteItems 与 PlayedItems 路由（POST/DELETE）；响应 UserData 直接合入本地详情，剧集/季切换已播放后整页重载分集矩阵，并发切换按票据丢弃过期响应。
2. 媒体库海报墙头部新增操作行：
   - 「播放」+「模式 · 顺序播放/随机播放」：按当前筛选与排序（随机为客户端打乱）取前 1000 项组成连播队列；电影直接播放，剧集/季解析到分集（首项续播感知推荐、后续项首个未看），播放器播完自动进入下一项，「下一集」按钮在队列尾部扩展为「下一个」；不可播放项自动跳过，队列耗尽退出。单项播放（详情页/继续观看）行为不变。
   - 「视图 · 竖幅/横幅」：竖幅海报与横幅背景图两种卡片，按服务器+账号+库持久保存。
   - 物理库（SourceType=path）追加「类型」（聚合 Genres+Tags，标签来源以（标签）标注，同名取题材）、「合集」（库内合集网格 → 合集成员海报墙，返回键逐级回退）、「收藏」（Filters=IsFavorite 开关）。
3. 合集成员视图默认保持服务端合集顺序，排序/视图/播放仍可用；评分排序在合集父级回退标准查询路径。

## 验证

- `testReleaseUnitTest`：50 项通过、0 失败（新增 PlaybackQueueTest 4 项：首个未看分集排序/回退/空集、题材标签合并去重）。
- `lintRelease`、`assembleRelease` 通过；minSdk 24 / targetSdk 35 / versionCode 30。
- apksigner v2 验签通过，证书 SHA-256 与既有发行一致（e02d4244…67bd7）。
- 产物 `outputs/etkn-tv-0.1.29/`：APK SHA-256 `989cbb77338f59a417c37040498ace07c5e3322315a920bca4543ac6e977e315`；update.json 与 SHA256SUMS.txt 已补齐（Build-Release.ps1 的 update.json 步骤在本机 Windows PowerShell 5.1 上因 `utf8NoBOM` 不支持而未写入，为既有脚本兼容性，非本次代码问题）。
- 未覆盖：真机遥控器操作与真实连播播放；无连接设备，按 SOP 不宣称实测。

## 服务端依赖

需要 ETKvNext `172abfca` 起的 api/tv 路由（收藏/已播放、Items/Filters 适配、库内合集端点）；旧服务端下新入口报「请先更新 ETKN 服务端以支持独立 TV 接口」。
