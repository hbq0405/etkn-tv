# ETKN TV 开发边界

- 原生 Android TV 客户端，最低 Android 7/API 24，内置 Media3 播放器，遥控器全功能操作。
- 新版 TV 功能通过专用 `/api/tv` API 访问；服务端可以复用既有基础媒体逻辑，TV 专属字段、筛选、排序和功能留在 TV 路由。不得把 TV 特例加入第三方 Emby/Jellyfin 共用查询链路。旧版专属路径仅为升级兼容保留。
- 探索、订阅、共享池/re0/频道搜索与确认获取使用 ETKN `/api/tv/discover`，服务端以 `require_discovery` 最终校验会员和模板权限。
- TMDb/豆瓣双评分与独立排序已由 TV 专属 API 支持；新增特色功能继续留在 TV 模块，普通 Emby/Jellyfin 查询不加 TV 分支。
- 局域网自动发现探测媒体8097/8096及管理5257，通过公开连接信息读取真实媒体地址并校验实例身份；手动填写媒体服务器地址，不得把普通Emby服务器误识别为ETKN。
- 更新仓库经用户授权为 `hbq0405/etkn-tv`，源码与 APK/更新信息同仓库公开，Releases 保存发行资产；不上传凭据或签名私钥，发布前验证哈希、版本和签名。
- 发行签名私钥保存在本机私有目录，永不提交；所有后续版本必须沿用同一私钥。
- ExoPlayer 的 NextLib/libass 原生扩展固定版本；TrueHD 修复使用 third_party/nextlib_compat 和四 ABI JNI 库。发布遵循 GPL-3.0，附原始许可、对应源码与重建信息；不能以支持列表代替真实解码输出验证。
- 最小修改、真实构建验证；明确区分源码完成、APK构建、设备实测和GitHub发布。
- 默认仅本地提交；不得顺带推送 ETKvNext 私有仓库。
- 任务归档遵循用户全局 QMD/Obsidian流程，原始JSONL只读，工具链故障核实后再修复。
