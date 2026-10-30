# ETKN TV 0.1.30：修复切换已播放后分集矩阵叠加

## 问题

0.1.29 详情页对剧集/季「标记已播放 / 标为未播放」后，页面同时叠出旧、新两种状态的分集矩阵，返回重开详情才正常。

## 根因

`togglePlayed` 对剧集/季重新调用 `loadEpisodes()` 拉取最新分集状态，但该方法向 `episodeArea` 追加新的季行、预览与矩阵；重入时旧的视图树没有移除（初次进入时 `episodeArea` 本来就是空的，所以首发路径不暴露）。

## 修复

`loadEpisodes` 回调开头先 `episodeArea.removeAllViews()` 并重置 `episodeGrid`/`preview` 引用，再按最新数据重建；初次进入行为不变。仅 [DetailActivity.java](../app/src/main/java/xyz/etkn/tv/DetailActivity.java) 一行级改动。

## 验证

- `testReleaseUnitTest` 50 项 0 失败、`lintRelease`、`assembleRelease` 通过；versionCode 31。
- apksigner v2 验签通过，证书 SHA-256 与既有发行一致（e02d4244…67bd7）。
- 产物 `outputs/etkn-tv-0.1.30/`：APK SHA-256 `b56162950a3b60fc5f7d054dd0cba3b5782a3c4af8ea22ec1b54b5bfea0fa8b9`；update.json 与 SHA256SUMS.txt 沿用 0.1.29 的脚本外补齐方式生成（Build-Release.ps1 在本机 PowerShell 5.1 的 `utf8NoBOM` 兼容问题未改）。
- 未覆盖：真机遥控器实测；视图为 UI 组装逻辑，纯 JVM 单测不覆盖，矩阵重建以代码审查与既有单测回归兜底。
