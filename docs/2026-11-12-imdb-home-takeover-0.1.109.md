# ETKN-TV 0.1.109：IMDb 评分、工作室文字与 FengOS 接管

日期：2026-11-12

## 交付

- 版本 0.1.109 / code 110，正式签名 APK 已覆盖安装到 `192.168.31.188:5555`，保留原有应用数据。
- APK：`outputs/imdb-home-20261112/etkn-tv-0.1.109.apk`。
- SHA-256：`67d1021026d20ef37ff2e6efd2d04e0c78aca06945a820d787acf2e70cf0bc35`。
- 发行证书 SHA-256：`e02d42443875c3631be48199e0df419b0570fcc421be7ff2b620f4c643e67bd7`。
- 0.1.108 回滚包保存在 `outputs/imdb-home-20261112/previous/etkn-tv-0.1.108.apk`，SHA-256：`1f138495c9003d305d12ef9909c5d3a8411899ceb2a6aded9a965c63b88ce617`。

## 功能变更

- 首页媒体卡片、详情页和焦点背景读取真实 `IMDbRating`；首页媒体库与 facet/collection 菜单可按 IMDb 评分升序或降序排序，排序参数沿用 TV `/api/tv` 语义。
- 详情页工作室/平台始终显示可聚焦文字按钮，点击仍打开对应媒体库筛选；不再请求或等待工作室图标。
- FengOS 接管服务只监听 `com.tvfengmi.launcher/com.gala.video.app.epg.HomeActivity` 的窗口切换，`canRetrieveWindowContent=false`；开机接收器仅在服务被启用时启动 ETKN-TV。原厂桌面保留，可能短暂闪现。

## 验证

- Android 单元测试：116 项通过，0 失败，0 错误；Lint 0 errors。
- 6 项签名隔离实机测试通过：IMDb 卡片五档横竖布局、IMDb 升降序请求、工作室文字与筛选跳转、详情事件原位更新、演员/分类详情。
- 电视实测：服务绑定成功；按 Home 后日志先启动原厂 Home，再由 `uid 10081` 启动 `xyz.etkn.tv/.SetupActivity`；重启后 `sys.boot_completed=1`，日志显示原厂 Home 后自动启动 `SetupActivity`，服务仍绑定。
- 覆盖安装后版本回读为 `versionCode=110`, `versionName=0.1.109`。
- 20 个 JNI `.so` 文件与 0.1.108 逐字节一致。

## 范围与回滚

- 这台 L055FCN 的固件过滤第三方 HOME 候选，因此系统默认桌面仍是原厂桌面；接管服务是本机已验证的实现。Home/开机可能先出现原厂桌面。
- 关闭接管：从 `enabled_accessibility_services` 移除 `xyz.etkn.tv/.LauncherTakeoverService`；若没有其他无障碍服务，将 `accessibility_enabled` 设为 0。
- 回滚应用：覆盖安装 `previous/etkn-tv-0.1.108.apk`，然后按需关闭接管服务。

本轮未推送 GitHub、未发布 Release。
