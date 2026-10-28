# v0.1.8 TV API 隔离与焦点卡片修复

- 首页裁切的遗漏位于 `FocusScroll.Row`：GridLayout 允许越界绘制后，外层 HorizontalScrollView 仍开启 clipChildren。关闭本层子裁剪，保留 v0.1.7 的 12dp 焦点滚动余量。
- 登录、媒体库、图片、详情、剧集、播放和播放进度迁至 `/api/tv`；探索迁至 `/api/tv/discover`，服务端继续检查会员及模板权限。未来 TV 特有字段与排序在 TV API 内实现。
- v0.1.8 需要先升级 ETKN 服务端以提供独立 TV API。发现和登录会检查 TV API 版本；不支持时提示先升级服务端。
- 版本 0.1.8，versionCode 9；最低 Android 7/API 24，target API 35。

验证：Android 16 TV 模拟器运行真实 HomeActivity，使用模拟器本地合成媒体服务，90 张卡片、37 个焦点位置（上下片架、横排行首及行尾）全通过；同时断言首页请求均使用 `/api/tv`。修复前 initial 步骤因图片可见区域缺少顶部及左边沿失败，修复后仪器测试通过。该结果覆盖模拟器焦点行为，真实电视播放及遥控器操作仍待实测。

发行单元测试、Lint（0 errors）、assembleRelease 通过。候选 APK 与 v0.1.7 的签名证书 SHA-256 均为 `e02d42443875c3631be48199e0df419b0570fcc421be7ff2b620f4c643e67bd7`。

本轮不实现 TMDb/豆瓣双评分显示或排序。
