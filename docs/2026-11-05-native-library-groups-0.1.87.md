# 原生媒体库分组 0.1.87

用户要求服务器原生支持一层“分组→媒体库”，ETKN-TV同步支持，并通过ADB覆盖192.168.31.188。本轮不推送、不创建GitHub Release。

- 首页和主菜单媒体库入口识别/api/tv的SourceType=group，进入独立分组页再选真实子库。保留library-N及原有排序、文件夹、播放和管理操作。
- 遥控器返回先回到分组及刚才子库卡片，再返回首页/媒体库及原分组卡片；更新通知和卡片尺寸变化刷新分组且恢复焦点。分组不显示媒体刷新/删除菜单。
- 服务端分组独立存储（迁移0184），纯分组不占额度，根库/子库按账号全局计数：普通2、Plus8。Plus电影4+剧集4允许，第9个拒绝。权限先筛选子库，封面取首个有封面的可见子库；没有复制媒体条目。

验证：96项Release单测零失败/错误/跳过，Lint和正式签名构建通过；证书SHA256 e02d42443875c3631be48199e0df419b0570fcc421be7ff2b620f4c643e67bd7，20JNI与0.1.86逐一相同。指定电视API28的独立同签名applicationId包通过1项分组往返/遥控器/焦点/刷新/文件夹回归（21.534秒），2项普通库分页排序及合集返回回归（29.419秒）。最终两张截图已查看，保存在outputs/etkn-tv-0.1.87/verification。合成HTTP服务在设备loopback内运行，不接触正式账号数据。

正式APK已通过ADB -r保留数据覆盖192.168.31.188，回读0.1.87/code88/min24/target35，SetupActivity启动Status=ok。APK SHA256 54c95cfbe643cafee535c882372785aff9161d9688cb51363378ab6deefdf941，旧包保存outputs/etkn-tv-0.1.87/rollback-0.1.86.apk；本轮两个独立验收包已卸载。当前正式账号创建分组后的持续使用仍待用户实测，不把合成导航回归表述为真实片库已配置。

服务端5c5e9486本地提交及etkn热更新完成，20:17:18重启healthy/RestartCount0；18新增+33既有离线回归、21真实HTTP检查及临时行/会话0残留。12发布文件哈希、两树755/644、UID1000导入、健康、静态MIME和HTTP字节检查通过。备份/tmp/etkn-hotfix-backup/20261105-201438-native-groups，详细报告见D:/work/ETKvNext/docs/reports/2026-11-05-native-library-groups.md。
