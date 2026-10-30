# ETKN-TV 详情页章节图 0.1.37

## 变更

- 详情接口返回 `Chapters` 且章节带 `ImageTag` 或 `ImagePath` 时，详情页在简介下显示“章节”横向卡片。
- 卡片显示章节图、章节名称和起始时间；按确认键从该章节起点进入播放器。
- 没有章节图的媒体不显示章节区域。
- 章节图片复用 TV 图片接口 `/api/tv/Items/{id}/Images/Chapter/{index}`，沿用现有鉴权、缓存和圆角图片加载。
- 背景切换继续使用 0.1.36 的顺序淡出/淡入及文字同步逻辑。

## 验证

- `:app:compileDebugJavaWithJavac`
- `:app:compileDebugAndroidTestJavaWithJavac`
- `DetailFlowTest`：41 项执行，38 项通过，3 项因私有素材/设备条件跳过；新增章节图显示回归通过。
- `HomeFocusTest`：9 项通过，覆盖背景切换、焦点显隐和模糊回调。
- `:app:testReleaseUnitTest :app:lintRelease :app:assembleRelease` 通过。
- Release 签名校验通过，证书 SHA-256 指纹保持为 `e02d42443875c3631be48199e0df419b0570fcc421be7ff2b620f4c643e67bd7`。

## 交付

- 版本：`0.1.37` / `versionCode 38`
- APK：`outputs/etkn-tv-0.1.37/etkn-tv-0.1.37.apk`
- APK SHA-256：`c7074bfbcf510c6c1e3920cfe6986c0c4a37d9b05f9f159ccefe2857b9bf1ba0`
- `update.json` 与 `SHA256SUMS.txt` 已生成，未推送 GitHub、未发布 Release。
- 模拟器已验证；真实电视安装和章节图真实片源观感尚未验收。
