# 登录账号卡片被裁切修复 0.1.35

日期：2026-10-31。0.1.35 / versionCode 36；仅修复账号选择框宽度测量，沿用固定发行签名。

## 原因与修改

0.1.34 将“登录新用户”移到独立下方后，纵向 LinearLayout 面板仍使用 wrap_content，但账号 HorizontalScrollView 未显式指定 LayoutParams，使用纵向容器默认的 match_parent 宽度。面板测量宽度退到下方按钮的宽度，账号横排被压窄。单账号模拟器截图复现了右侧裁切：312px 卡片聚焦放大后应约337px宽，实际只显示221px。

SetupActivity 仅将账号滚动行添加方式改为显式 wrap_content 宽高，使面板按账号行内容和可用屏幕宽度测量。账号较少时完整居中显示，较多时使用已有横向滚动；下方新用户按钮和一次 ↓ 的导航规则保留。账号策略核查、私密隐藏及热返回/切号验密逻辑未改。

## 验证与交付

- 新增一项实际布局回归，覆盖1、3、8个普通合成账号；逐个发送右方向键，检查聚焦卡片的实际可见宽高、下方按钮位置和一次 ↓ 可达性。旧0.1.34在单账号宽度断言失败，保留截图和日志。
- 最终固定签名release APK覆盖安装API36模拟器后，PrivateAccountFlowTest全部8项通过（22.255秒），含原有管理员/私密账号登录保护。1、3、8账号截图已拉取并实际查看；修复后聚焦宽度337–338px、高度324px完整可见。
- 60项release单测、lintRelease、assembleRelease、apksigner及git diff --check通过；manifest核验包名xyz.etkn.tv、versionCode36、0.1.35、最低API24、targetAPI35。
- 证据：`build/account-picker-width-20261031/`，`before-test.log`、`before/users-1.png`为复现，`after-test.log`、`geometry.log`及`after/users-{1,3,8}.png`为修复后验证。测试只连接模拟器内本地合成服务，不访问生产。
- APK：`D:\work\ETKN-TV\outputs\etkn-tv-0.1.35\etkn-tv-0.1.35.apk`，46,027,016字节；SHA-256 `126bca6d717c8d02dd6b29d2404b26d89a2688c6b582f86190e35578ac75c5f2`。证书SHA-256仍为`e02d42443875c3631be48199e0df419b0570fcc421be7ff2b620f4c643e67bd7`。

仅本地提交，未推送或发布GitHub Release；本次未改服务端或生产配置。真实电视安装和画面尚未验收；本轮模拟器在截图提取后关闭。后续账号选择框验证同时检查遥控器导航和实际可见边界，不能用焦点可达性代替无裁切证明。
