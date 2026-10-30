# 构建与发布操作规范

- Windows 的 rg 目录搜索使用 `rg 关键词 目录 -g '*.java'`，不把通配符拼进路径。路径错误先核对已返回命中及真实目录，不反复盲试。
- `local.properties` 的 Windows 盘符冒号需要转义，如 `sdk.dir=C\:/Android/sdk`。本地属性不入仓库。
- 当前 Google SDK 工具使用 `android --sdk <目录> sdk list --all` 确认实际包名，安装使用 `platforms/android-36` 等斜杠名称。安装返回码须结合目录与安装清单核验，不能仅以 exit 0 判断包已安装。
- Media3 非稳定接口在调用类显式 OptIn，固定依赖版本并跑 Lint，不通过关闭错误来放行。
- 补丁首次失败先检查 diff 确认无部分写入，再用锚点唯一的确定性文本替换。不得连换 heredoc/管道/编码尝试。
- `apply_patch` 头必须为 `*** Update File: 路径`（冒号后有空格）。本轮格式校验失败前没有写入，核对 diff 只有此前诊断测试后，修正头格式即成功；不把补丁解析失败当作源码编译失败。
- 多文件补丁任一锚点不匹配时先检查所有目标 diff。本轮文档锚点遗漏句首导致整份补丁未写入，源码中也没有待加焦点字段；核对实际行后重发完整锚点成功。不要假设前面文件已写入或盲目重复叠加。
- 发行前运行单元测试、Lint、assembleRelease；核验 API 24、包名、递增版本和固定签名。
- 发行私钥与密码只存 `.private/` 或指定私有目录，限制目录 ACL。不得输出密码、提交私钥或将凭据放入 Release。
- Windows 新启动的 Gradle 终端如果未设置 `ETKN_TV_SIGNING_PROPERTIES`，单独运行 `assembleRelease` 仍可能成功，但只生成 `app-release-unsigned.apk`。先检查实际产物；设置签名配置后使用 `scripts/Build-Release.ps1`，并确认 `apksigner` 通过。v0.1.3 已验证该流程生成签名包且证书与此前发行版一致；Gradle 成功本身不代表 APK 已签名。
- GitHub CLI 未认证时使用官方设备授权；账号主人在浏览器确认。授权完成必须通过 API 读取账号确认，再创建仓库/推送/发布；不得把设备码已输入当作登录成功。
- 只发布本轮校验过的 APK、update.json、校验清单和发行说明；同一仓库保存源码和 Releases。发布后匿名下载比对 SHA-256。
- 草稿 Release 的 `releases/tags/<tag>` 查询本轮返回 404，但鉴权后的 `releases` 列表与 `gh release view` 仍能找到草稿。先核对实际草稿 ID、标签、正文和资产，不重建或误发布；按唯一 tag_name 从列表取得 ID，再读取 `releases/<id>` 已通过四资产摘要、正文、main/标签和工作流一致性校验。正式发布后再验匿名 latest 和下载地址。
- `gh release view --json` 本轮不支持 `isLatest`，字段错误发生在正式发布命令已成功之后。先用支持的 `isDraft,publishedAt,tagName,url` 确认发布状态，再以 `gh api repos/<owner>/<repo>/releases/latest` 验证最新版本；已确认 v0.1.16 为正式 latest、通知成功，未重复发布或重跑通知。发布后的规范补充另行提交，不移动已正式发布的标签或替换已校验资产。
- 没有连接 adb 设备时明确记录未实机验收，不把构建通过说成电视操作和真实播放已通过。

- 编辑含中文的 UTF-8 源码时，Python `read_text`/`write_text` 显式指定 `encoding="utf-8"`；默认 GBK 解码失败前可能已有早先替换落盘，先检查 diff，再只续作未完成部分。
- Android 本地测试编译路径不提供 `com.sun.net.httpserver`，网络契约测试使用 `java.net.ServerSocket` 的本地模拟服务；6 项测试、Lint 与发行构建已验证通过。
- 自动发现必须读取实际媒体地址，不能把管理端口当成媒体入口。发送登录信息前，先匿名确认管理/媒体入口返回同一个实例 ID；媒体地址不可达或身份不同必须拒绝。升级旧会话后要求重新登录。
- 首页导航仅在上边界按 ↑ 展开，不拦截列表之间正常的上下移动；菜单按 ↓ 或返回隐藏并恢复原焦点。无电视连接时构建和接口验证不能作为遥控器实测证据。
- 本机若无 emulator 可执行文件，先核对 SDK 已安装包目录并运行 `adb devices -l`；确认无模拟器/电视后停止画面级验证，在交付中明确记录焦点行为待设备实测，不猜测或重复尝试不存在的 SDK 路径。
- Windows 执行策略若在进程启动前拦截本轮专用临时目录的递归清理，先只读核对目标绝对路径与文件状态；保留现场并报告拦截，不改用其他 shell 或命令形式绕过。该限制之外的清理方式未经验证。
- 2026-10-29：0.1.12 发布后的 `build/verify-release-0.1.12` 匿名下载核验目录被同类递归删除命令拦截；已只读确认路径和仓库状态，未换 shell 绕过，目录保留作为可复核现场。
- `mergeDexRelease` 若因 `classes.dex` 被占用失败，先检查相关进程与 `gradlew --status`；只有确认没有活动构建且唯一相关 Gradle daemon 为 IDLE 时，才用 `gradlew --stop` 优雅停止后重跑完整发行检查。本轮重跑通过，但未能唯一确定文件锁持有者；不得强杀其他进程。

## APK 更新签名读取兼容（2026-10-28）

- 触发：旧版应用内更新显示“更新包签名不符”，反馈设备为 Fengmi-L055FCN-TV；未获知系统版本，不根据型号推断 Android 版本。
- 先用官方 apksigner 分别核验已发行及候选 APK。v0.1.0、v0.1.1、v0.1.2 的证书 SHA-256 完全一致，禁止重新生成密钥或移除校验。
- Android 9/10 官方 PackageManager.getPackageArchiveInfo 实现仅在 GET_SIGNATURES 标志存在时收集证书。API 28+ 同时传 GET_SIGNATURES 与 GET_SIGNING_CERTIFICATES，优先 signingInfo，缺失时回退 signatures；空证书必须拒绝，区别无法读取与证书不同。
- 回归在模拟 PackageManager 的实际调用中重现此采集条件；修复前 7 项有 5 项失败，修复后全部通过。另保留不同签名、空签名、多签名与包名/版本拒绝用例。模拟结果不等于电视实测。
- 旧版校验器在安装新版前无法修复自身。发布修复版本后明确提供 APK 手动覆盖安装一次的路径，不要求卸载或清除数据；设备安装及下一次应用内更新仍须实机验证。
- 官方网页读取返回空内容时，停止相同盲试，使用 Python urllib 读取 android.googlesource.com 的同版原始源码并本地保存，已核验 Android 9/10；不把空搜索结果当作引用依据。
- 本轮 aapt2 dump badging 首次检查因错误匹配 sdkVersion 标签而断言失败。先检查实际输出及部分写入：只保存了源码、证书证据，未更新发行说明或文档；确认实际标签是 minSdkVersion 后再续作，实际 minSdk=24 / targetSdk=35 校验通过。检查输出字段名称，不把验证脚本断言失败等同于 APK 构建失败。

## 首页头像账号切换与媒体库最新分区（2026-10-28）

- 首页头像应为遥控器可聚焦、可确认的入口。已登录账号凭据沿用 Android Keystore AES-GCM 加密格式保存在本机，账号列表只暴露用户 ID 与名称；切换时只改活动账号。新账号登录前先比对已保存的唯一媒体服务器地址，不匹配则在发送账号密码之前拒绝。此功能不引入多服务器账号列表。
- 首次访问账号列表时迁移原有单账号 token；退出/令牌失效只移除当前账号，保留同服务器的其它保存账号。登录新账号时清除任务栈并重新创建首页，使各 API/通知使用新活动账号。
- 只在所有创建的 ImageView 上设置圆角背景轮廓并开启 clipToOutline，覆盖媒体卡片、轮播背景和 Logo、设置页头像、菜单头像；头像使用圆形裁切。新增 ImageView 时也要补该处理。
- ETKN 的 `/emby/Users/{id}/Items/Latest` 已支持按 `ParentId=library-*` 查询，且后端已根据媒体库 `definition_json.show_in_latest` 对关闭的库返回空列表，并继续做用户媒体库权限过滤。客户端逐库请求该路由，非空分区标题为“最新+媒体库名称”，不新增后台开关映射或绕过服务器过滤。
- 首页轮播、焦点背景和详情背景按用户 2026-10-29 后续要求统一降低亮度，使用共用灰色遮罩；0.1.13 的遮罩 alpha 从 75/255 加深至 125/255，保持无渐变。此要求覆盖此前移除背景遮罩的约定；海报内评分区的局部底色不属于全屏背景。
- 每个新的 `exec_command` 都会启动独立 PowerShell，不保证继承其它会话设置的 `ANDROID_HOME`、`JAVA_HOME` 或 `PATH`。aapt2 与 apksigner 检查曾因这些变量缺失而未能完成；确认 APK/manifest/checksum 已生成后，必须在同一命令中从 `work/android-toolchain/paths.json` 读取 SDK/JDK 路径、设置环境并运行工具。捕获 apksigner 输出时先检查 `$LASTEXITCODE`，再筛选证书摘要；不要把 Select-String 无匹配误判为签名失败，也不要因环境缺失重建或替换发行资产。
- PowerShell 不要把长输出的原生工具直接管道到 `Select-Object -First`；提前关闭 stdout 可能使工具返回非零，即使已打印有效头部。先完整捕获输出，检查 `$LASTEXITCODE`，再从变量截取显示内容。
- PowerShell 调用 Gradle 时，将带点号的完整 `-P` 属性作为单个引号参数，例如 `'-Pandroid.testInstrumentationRunnerArguments.class=xyz.etkn.tv.HomeFocusTest'`。未引用时曾被拆为任务名，构建在测试前失败、未改发行资产；确认状态后用完整引用参数重跑，模拟器仪器测试通过。

- 仪器测试截图需在 APK 仍安装时提取；Gradle connected 测试可能在完成后卸载被测应用，外部文件目录随之消失。已验证 installDebug/installDebugAndroidTest 后直接 adb shell am instrument -w 可保留截图并 adb pull；先核对 pm list packages 与路径状态，不反复尝试同一失效目录。
- 测试仍在运行时，新截图可能尚未生成。本轮提前 pull 返回 No such file，未生成本地图片；核对远端目录后确认后续用例已生成，再 pull 成功。提取成功后才能调用图片查看工具。

## 详情页与轨道验收

- 分集列表必须显式请求 `Fields=MediaSources,MediaStreams,Overview`。生产抽样默认列表没有 MediaSources，带 Fields 后 20 集均返回；客户端应核对实际响应，不能依据列表 DTO 的理论字段假设其始终存在。
- Media3 合并主媒体与外挂字幕时，Format.id 会带来源前缀，例如 `0:2`、`1:etkn-subtitle-12`。匹配外挂字幕前只去掉冒号前缀，保留服务端轨道 Index 和独立字幕 ID；同语言内封轨道按媒体源内类型顺序定位，不按语言碰运气。使用合成 Matroska 在模拟器检查实际选中轨道。
- 当前补丁接口拒绝在同一补丁内 Delete/Add 同一路径（`multiple operations target`），失败前未写入。先用 git diff 核对无变化，再用 PowerShell UTF-8 here-string 原位写入完整目标文件，现已通过编译、单测与模拟器验证；不继续换输入方式试错。
- PowerShell 字符串中的 `\n` 是字面字符，不是换行。本轮独立验证脚本曾因此 SyntaxError，确认只更新了探针、服务代码与容器健康未受影响后，用 `[Environment]::NewLine` 修正并先 py_compile，重新上传后验证通过。生成多行脚本使用 here-string 或真实换行，上传前本地语法检查。
- PowerShell 不使用反斜杠转义 `python -c` 外层双引号。本轮只读网页检查因此出现未闭合字符串，未修改文件；改成单引号 here-string 经 stdin 传给 Python 后读取成功。复杂脚本优先用独立文件或 here-string，避免多层引号。

## 原生播放与验证补充

- 首行海报缩放越界时，同时检查海报网格和父滚动容器的实际可见矩形；仅关闭子网格裁剪不能保证父容器容纳溢出。网格内部上下保留 FocusScroll.MARGIN_DP，验证聚焦图片四边的实际可见边界。
- NextLib 1.8.0-0.9.0 的 FFmpeg 6 JNI 对立体声 TrueHD 缺少初始声道布局，重置还会丢失配置并读取已释放上下文。使用 third_party/nextlib_compat 的固定源与四 ABI 库；禁止仅凭 supportsFormat 或曲目出现就宣布可解码，必须检查切轨、seek 后的非零输出缓冲。软件视频检查实际输出帧，ASS 检查 seek 完成后的可见像素。
- JNI 公共头搜索使用 -idirafter，避免 FFmpeg 源码根目录 VERSION 在 Windows 上遮蔽 C++ 标准库 <version>。首次失败只生成了独立 scratch、未生成 .so；调整头搜索顺序后四 ABI 编译及模拟器解码通过。
- Android CLI 使用安装清单中真实存在的 android.exe 和 `--sdk $taskPaths.sdk` 分开参数，不用未加子表达式的 `--sdk_root=$taskPaths.sdk`；后者会把整个对象传为路径，失败前未安装。重新核对真实文件后再调用。NDK 安装后以 clang++ 文件存在及编译成功验收。
- pytest 的 basetemp 使用本轮专属目录，先创建其父目录；测试隔离必须在连接池创建前阻断真实数据库。不要清理其他任务的临时测试目录。
- Java 仪器测试调用 Kotlin API 时，若返回类型只在 runtime 依赖中，不直接调用该签名；诊断可反射读取对象字段，不为测试扩大应用依赖。首次编译失败无 APK 更新，修正后重新构建测试 APK。
- 发布新增原生依赖时保留原始版权/许可与固定源码、子模块及构建脚本，检查 GPL 组合许可、APK 内许可入口和 Release 对应源码包。普通 Gradle 构建使用已核验的 JNI 库；修改 JNI 后必须用脚本重建四 ABI 并重复原生解码回归。
- 新增原始 FriBidi/libvpx 许可时，暂存后的 diff --check 会报告上游空白行和行尾空格；首次失败后确认只完成暂存、未提交，再用 .gitattributes 对这两个原始许可限定 -whitespace，保持正文原字节。已验证暂存检查通过且 APK 许可与源码相同，业务源码继续使用正常空白检查。

## APK 静态分析工具核查（2026-10-29）

- `D:\work\jadx-cli\lib\jadx-1.5.6-all.jar` 的 Manifest 主类为 `jadx.gui.JadxGUI`。直接 `java -jar` 虽能显示帮助，带 APK 后会进入 GUI、无输出源码。首次发现未输出后，用线程状态、Manifest 和官方 `bin/jadx.bat` 核对入口；只终止已核验属于本轮的进程，改用 `java -cp <jar> jadx.cli.JadxCLI ...`。已得到本轮 APK 的源码输出，不对其他 jadx 实例操作。
- R8/Kotlin/Compose APK 的 JADX 完整恢复可因复杂控制流返回非零，同时保留大量有效源码。本轮返回 432 个方法错误；不得把整份输出称为可编译原始源码。先核对输出与具体失败方法，对关键路径交叉检查 simple 模式、常量和调用点；simple 模式输出了播放器创建路径，但关联的 Compose 方法仍有类型推断错误。只能据已核验指令形成静态结论，运行性能须另做设备对照测试。
- 本轮 `multi_get(pattern="#28fdd0")` 返回 No files matched，未写入文件或改变索引；核对 MCP 实际接口后使用 `get(file="#28fdd0")` 成功读取全文。docid 单页用 get，批量 multi_get 使用已验证的实际路径；不要在相同错误参数上重复试探。

## 播放器策略迁移与字幕回归（2026-10-29）

- 柴柴 APK 反编译仅提供策略和调用顺序证据，不等于可直接编译的原始工程。ETKN 保留固定 Media3 1.8.0、NextLib 和 libass JNI；迁移 OkHttp 超时/连接池、缓冲、ASS Canvas 与失败降级、多对象 PGS、提取阶段 P7 基底层声明，改动必须有本项目管线回归。
- 服务端 Title 是副标题，DisplayTitle 可能附带“(默认 ass)”等格式信息。优先 Title，去除已有默认标记，不再追加“默认”；详情和播放菜单使用同一规则。自动选择策略保持不变。
- PGS 内封数据依赖容器时间戳；外挂 SUP 有 PG 标识、90kHz PTS/DTS 头。最初把不带时间戳的内封样本直接当外挂文件，真实播放器 Cue 超时；确认解析器单测通过、管线未通过后，补 SUP 时间戳解析和带清屏事件的 SUP 测试。不能以单独解析成功替代真实显示和 seek 验证。
- detail-tracks.mkv 是无 Cues/SeekHead 的简化音轨素材，重建时请求 5.3 秒但会从 0 开始。已检查二进制确认无索引；恢复位置验收使用带索引的 player-codecs.mkv，并先断言 isCurrentMediaItemSeekable。不要通过放宽位置断言掩盖测试素材限制。
- adb shell am instrument 的进程返回码不能单独说明 JUnit 通过；曾返回 0 但正文有 FAILURES。检查最终 OK、失败/跳过状态；VC-1 私有素材缺失按 assumption 跳过，单独记录，不宣称已实播。
- Media3 1.8.0 的 ExoPlaybackException 没有 rendererType 字段，先核对固定版本 API，使用 rendererFormat MIME 与 rendererName 判断轨道；首次编译失败未生成新版 APK。PGS 像素 Y=235/Cr=Cb=128 按现用转换输出 RGB235，单测不得误期望 RGB255。
- 文件读取前先用 rg --files/命中位置核对真实类名。许可入口实际在 SettingsActivity，读取不存在的 LicensesActivity 失败未改源码；已确认 getAssets().list("licenses") 自动列出新增 NOTICE。目录搜索禁止 docs\\*.md 路径通配符；本轮误用后核实未写入，改用 rg 关键词 docs -g '*.md' 完成检索。v0.1.16 的交付记录尚未生成时读取失败，前面的仓库状态和通知运行列表读取已成功，未发生写入；用 rg --files 核实交付目录后，按最终发布证据新建记录，不将计划中的文件当作已存在。
- 文件读写使用绝对路径或先核对 `workdir`。本轮仓库没有 `release-notes/`，发行说明沿用聊天交付目录；误从仓库读聊天 `work/tv-controls/` 也只产生路径不存在错误，未写入。已检查真实目录后使用聊天绝对路径成功读取，不能混用两个工作区的相对路径。
- 调用 Gradle 也必须显式仓库 `workdir`。本轮在聊天目录调用 `gradlew.bat` 未找到命令，只复制了本轮测试日志、未启动构建或安装；确认日志和真实 Wrapper 路径后，在仓库目录重跑成功。不得借错误目录里的 `$LASTEXITCODE` 判断 Gradle 已执行。
- 4K 色条的画面检查曾在中央三分之一裁区误报“单色”：实际采到 13 色、RGB 范围 0/23/231，失败截图有完整色条和 ASS，红色色条在裁区外。保留亮度、颜色数、各通道范围与非纯绿门槛，改为横跨色条的居中宽区域采样；断言输出具体范围，先存截图再断言。不得仅凭单次像素失败改动 JNI 或直接删除检查。

## 首页缓存、分集状态与焦点验证

- 首页缓存按服务器和账号隔离，先渲染缓存再在线校验；TV 专用 SSE 只发送 revision，收到更新后仍使用原有鉴权接口读取数据。后台关闭连接，返回前台及每分钟补查；过期请求不得覆盖新结果。服务端定向测试在建池前阻断真实数据库；替换业务时钟仅替换模块绑定，不修改全局 time.monotonic。
- 首页各分区独立返回时，续播行可能在媒体库取得焦点后增高并推动布局；调整 heroSpacer 后必须重新保证当前焦点完整可见。测试同时覆盖先返回片库、续播随后返回及原有首尾卡片可见矩形，不能只测初始焦点。
- 分集矩阵进入焦点与矩阵内部左右导航分别验证。截图应在滚动到目标矩阵后提取，布局坐标断言不能证明截图已包含该区域；当前绿色填充采用服务端 Played 与 PlaybackPositionTicks/RunTimeTicks。
- 原生依赖未改时，读取 APK 内实际 lib/*.so 清单，逐个比较上一版和受测 Debug 的 SHA-256，再复用对应源码包。不可把历史记录中的库数量当作当前包清单；本版实际为四 ABI 各四库，共 16 个，字节完全一致。Java 类核验须搜索所有 classes*.dex；FocusPulse 位于 classes2.dex，不能仅查主 dex 就判定漏包。校验脚本断言失败时先检查资产是否已写入，不据此重建原生依赖或替换签名。

## 当前原生库重建入口（2026-10-30）

- 当前 APK 的 FFmpeg 静态链接进 `libetkn_media3ext.so`；完整重建使用 `scripts/Build-PlayerNative.py`，NDK r27c。`Build-NextlibCompat.py` 是旧动态链接入口，生成物会依赖 APK 未包含的 `libavcodec.so` 等；模拟器日志实际出现 `dlopen failed: library "libavcodec.so" not found`，不能只按编译成功验收。
- 仅 JNI 源码变更且固定 FFmpeg 静态缓存完整时，可以按 `Build-PlayerNative.py` 的相同编译、链接和 strip 参数重新链接四 ABI；不修改 FFmpeg 配置或库。用 `llvm-readelf -d` 核对不得新增独立 FFmpeg 动态库依赖，再验证实际解码、切换、seek 与字幕输出。本轮四 ABI 重链已通过依赖检查。
- 旧 MSYS 构建缓存的 Makefile 可能保存 Git Bash `/c/...` 路径，直接由另一 MSYS make 启动会报源 Makefile 不存在；先读 build.log 并确认部分产物，本轮仅 x86_64 重建成功，其他 ABI 尚未写出。源文件与静态缓存仍存在时，复用已确认的缓存重链 JNI 可避免改变解码器构建；全量重建的 MSYS 路径修正本轮未验证。
- Git 二进制读取使用可保留原始字节的文件流（如 Python `subprocess.check_output` 后 `write_bytes`）；`git show ... --output=...` 不能用于导出 blob。本轮误用只生成了独立输出目录中的无效诊断文件，未修改源文件；应先验证 ELF 魔数再交给 readelf。

## 视频旋转与输出比例（2026-10-30）

- 用户反馈 L055FCN 的 OMX 对带 270° 标记的视频仍异常，而无旋转标记的视频及软件兼容模式正常。此反馈不能由无旋转元数据的手机样本或模拟器正常硬解替代。
- 系统解码配置与输出尺寸计算都使用零旋转副本，保留原始 Format 的旋转信息。带旋转标记的自动模式与软件模式使用 RotatingPlayerView，通过纹理矩阵旋转一次；无旋转标记的自动模式保持 SurfaceView。
- PlayerView 会在尺寸事件及绑定播放器时重设比例，仅由 Activity 设置内容比例会被后来的视图回调覆盖。RotatingPlayerView 在 onContentAspectRatioChanged 中按原始帧尺寸、像素比例和旋转计算比例，布局完成后更新纹理矩阵。90°/270° 的系统解码、软解与切回系统解码均需检查实际像素、比例及黑边。
- 切换输出视图使用 PlayerView.switchTargetView，迁移原有 ASS 视图，不重建播放器或媒体源；保留字幕、音轨、进度和遥控器状态。模拟器通过只证明当前模拟器路径；L055FCN 的 OMX 真机效果仍须用户安装验证。
