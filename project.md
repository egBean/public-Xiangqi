# 项目结构说明（TCHESS / Xiangqi）

TCHESS 是一款基于 JavaFX 的跨平台中国象棋界面程序，支持 UCI / UCCI 引擎、对弈、分析、棋谱、连线、开局库等功能。

## 1. 技术栈与构建

| 项目 | 说明 |
| --- | --- |
| 语言 / JDK | Java 21（`maven.compiler.release = 21`） |
| UI | JavaFX 23.0.1（controls / fxml / media / swing） |
| 构建 | Maven，`javafx-maven-plugin` 打包为 jlink 镜像 `Xiangqi` |
| 主要依赖 | onnxruntime（棋子识别）、JNA / jnativehook（全局鼠标、窗口）、sqlite-jdbc（本地库） |
| 主类 | `com.sojourners.chess.Main` |
| 打包参数 | `mainClass = Xiangqi/com.sojourners.chess.Main`，launcher = `launcher` |

## 2. 根目录结构

```
Xiangqi/
├── pom.xml                 # Maven 构建配置与依赖
├── README.md               # 项目简介
├── MANUAL.md               # 使用手册（引擎/连线/开局库/局面/界面设置）
├── LICENSE                 # GPLv3 协议
├── .gitignore              # 忽略 .idea / target / out
├── .pi/                    # pi 助手配置
├── assets/                 # 文档/截图用图片（1.png ~ 25.png）
├── skin/                   # 棋盘皮肤资源
│   ├── a/                  # 皮肤 A（config.json + 棋盘/棋子/掩码图）
│   └── b/                  # 皮肤 B
├── out/                    # IDE 构建产物（out/artifacts/app/app.jar）
├── target/                 # Maven 构建产物（classes 等）
└── src/                    # 源码
    ├── main/java/          # Java 源码
    ├── main/resources/     # 资源（fxml/image/style/sound/font/model）
    └── test/               # 测试目录（当前为空）
```

## 3. Java 源码包结构

根包：`com.sojourners.chess`

```
com.sojourners.chess
├── Main.java               # 程序入口（main）
├── App.java                # JavaFX 主窗口 Application，加载 app.fxml
├── board/                  # 棋盘与渲染
├── config/                 # 配置模型（Properties 序列化）
├── controller/             # FXML 控制器（UI 逻辑）
│   └── handle/             # 棋谱操作回调与处理
├── enginee/                # 象棋引擎通信（UCI/UCCI）
├── jna/                    # JNA 扩展（Windows User32 等）
├── linker/                 # 自动连线（跨平台截图/落子）
├── lock/                   # 单实例锁与后台任务
├── manual/                 # 棋谱读写（多种格式）
├── media/                  # 音效播放
├── menu/                   # 棋盘右键菜单
├── model/                  # 数据模型
├── mouse/                  # 全局鼠标监听
├── openbook/               # 开局库
├── util/                   # 工具类
└── yolo/                   # 基于 ONNX 的棋子识别
```

### 3.1 入口与窗口

| 文件 | 职责 |
| --- | --- |
| `Main.java` | 程序入口，启动 JavaFX 应用 |
| `App.java` | 主窗口（Stage），管理各设置/管理对话框的打开；`openDeduction` 打开推演窗口后在 `positionDeductionStage()` 中将窗口左边缘默认放在屏幕横向约 2/3 处、纵向居中（避免遮挡居中的主棋盘），放不下则贴屏幕右边缘并保证不超出屏幕 |

### 3.2 board/ — 棋盘与渲染

| 文件 | 职责 |
| --- | --- |
| `ChessBoard.java` | 棋盘核心：局面、走子规则、坐标 |
| `BoardRender.java` | 棋盘渲染接口 |
| `BaseBoardRender.java` | 渲染基类（绘制棋盘、棋子、提示、左侧胜率条等）。胜率条由 `drawWinRateBar(...)` 绘制在棋盘左侧空白区，未翻转时下红上黑，翻转时上下互换 |
| `DefaultBoardRender.java` | 默认渲染实现 |
| `CustomBoardRender.java` | 自定义/皮肤渲染实现 |

### 3.3 controller/ — UI 控制器

| 文件 | 职责 |
| --- | --- |
| `Controller.java` | 主界面控制器（核心交互，约 1700 行） |
| `EngineManageController.java` | 引擎管理对话框 |
| `EngineAddController.java` | 添加/编辑引擎（`engineAdd.fxml` 已重排为 `GridPane` 标签右对齐 + 全宽输入框 + `ListView` 自适应高度，并引用 `style/gameSetting.css`） |
| `TimeSettingController.java` | 引擎时间设置 |
| `BookSettingController.java` | 库招设置 |
| `LocalBookController.java` | 本地开局库管理 |
| `LinkSettingController.java` | 连线设置 |
| `ColorSettingController.java` | 界面/颜色设置 |
| `EditChessBoardController.java` | 编辑局面 |
| `DeductionController.java` | 推演窗口：从当前局面弹出的独立小棋盘（自带局面与渲染，支持回退/前进/重置/复制FEN，底部“大小”下拉框可切换 小/中/大/特大 并自动调整窗口尺寸） |
| `handle/ChessManualHandle.java` | 棋谱操作处理（约 860 行） |
| `handle/ChessManualCallBack.java` | 棋谱操作回调接口 |

### 3.4 enginee/ — 引擎通信

| 文件 | 职责 |
| --- | --- |
| `Engine.java` | 引擎进程管理与 UCI/UCCI 协议交互；`setOption/setOptions` 可在引擎运行中实时下发 `setoption` 并同步内部 `threadNum/hashSize/multiPV`，避免下一次 `analysis` 用旧值覆盖。`closed` 标志用于关闭流程：`close()` 先发 `quit` 再抦置 `closed`，读线程循环与 `cmd` 的异常打印均以 `!closed` 为条件，避免退出软件时打印 `java.io.IOException: Stream closed`（`Engine.test()` 同样用 `stopped` 标志静默关闭测试进程的读流） |
| `EngineCallBack.java` | 引擎输出回调 |

### 3.5 linker/ — 自动连线

| 文件 | 职责 |
| --- | --- |
| `GraphLinker.java` | 连线入口/接口 |
| `AbstractGraphLinker.java` | 连线公共逻辑（截图识别、点击落子）。`compareBoard()` 返回 `Action`：1=对方走棋需同步引擎，2=引擎走棋需点击目标平台，3=新棋局需重新初始化，4=疑似识别错误（差异数>2但未达 3）。识别棋盘时截图存入 `lastBoardImg`；当 `action.flag == 4` 且连续 `count > 9` 时调用 `saveErrorBoardImage()`，把该截图以时间戳命名保存到项目根目录（`user.dir`）的 `errorImg/` 下，便于调试识别问题。`run()` 外层 `try/catch(Throwable)` 包住扫描主循环 `scanBoards()`，异常时 `onScanError()` 打印带时间的堆栈到 stderr（经 `Main` 双写进 `log/app.log`）、保存 `errorImg` 截图，并回调 `LinkerCallBack#linkerError()` 让界面关闭连线；`scan()` 每次启动重置 `pause`，保证用户重新点击连线即可恢复。`findChessBoardWithModel()` 在 `XiangqiUtils#validateChessBoard` 判定识别结果不合法时（`f == false`），把该 board 对应的 FEN 用 `System.err.println("[linker] 识别局面不合法，FEN：" + ChessBoard.fenCode(board, null))` 打到 `log/app.log` |
| `WindowsGraphLinker.java` | Windows 平台实现 |
| `LinuxGraphLinker.java` | Linux 平台实现 |
| `MacosGraphLinker.java` | macOS 平台实现 |
| `LinkerCallBack.java` | 连线状态回调 |

### 3.6 openbook/ — 开局库

| 文件 | 职责 |
| --- | --- |
| `OpenBook.java` | 开局库接口 |
| `OpenBookManager.java` | 开局库管理器（调度各实现） |
| `XqbOpenBook.java` | XQB 格式开局库 |
| `BhOpenBook.java` | 冰河格式开局库 |
| `PfOpenBook.java` | PF 格式开局库 |
| `CloudOpenBook.java` | 云端开局库 |
| `MoveRule.java` | 库招筛选规则 |

### 3.7 manual/ — 棋谱读写

| 文件 | 职责 |
| --- | --- |
| `ChessManual.java` | 棋谱接口 |
| `ChessManualService.java` | 棋谱服务 |
| `XqfChessManualImpl.java` | XQF 格式解析/生成 |
| `PgnChessManualImpl.java` | PGN 格式解析/生成 |
| `CbrChessManualImpl.java` | CBR 格式解析/生成 |
| `TxqChessManualImpl.java` | TXQ 格式解析/生成 |

### 3.8 yolo/ — 棋子识别（ONNX）

| 文件 | 职责 |
| --- | --- |
| `OnnxModel.java` | ONNX 模型加载与推理基础 |
| `ChessRecognitionModel.java` | 棋盘棋子识别流程 |
| `Yolo5Model.java` | YOLOv5 推理实现（`findChessBoard` 把检测框映射到 10×9 格子；`nms()` 是**按类别分别**抑制的，不同类之间不互相抑制，所以同一格可能出现多个候选框 —— 写入时用 `float[10][9] best` 记录该格已有最高置信度，**按置信度择优**，避免低分误检覆盖高分正确结果（之前是无条件 `board[i][j] = label`，结果取决于遍历顺序，`labels` 下标大的类总胜，例如红马 N(8) 覆盖红车 R(7)）） |
| `Yolo11Model.java` | YOLOv11 推理实现 |

### 3.9 review/ — 棋谱复盘

| 文件 | 职责                                |
| --- |-----------------------------------|
| `GameReview.java` | 复盘流程：逐局面分析棋谱、对比实际着法与引擎正着、给出评价     |
| `ReviewEngine.java` | 复盘专用同步引擎客户端（独立进程，逐个局面返回 bestmove） |
| `ReviewEval.java` | 单个局面的分析结果（最佳着法、分值）                |
| `ReviewItem.java` | 单步复盘结果（损失、评价、正着等）                 |
| `ReviewSummary.java` | 复盘汇总：双方各类着法数量与准确率                 |
| `MoveQuality.java` | 着法评价等级（正着/优秀/良好/不精确/失误/败着）        |

### 3.10 其他包

| 包 | 文件 | 职责 |
| --- | --- | --- |
| `config` | `Properties.java` | 全局配置模型，Serializable 持久化 |
| `model` | `BookData.java` `EngineConfig.java` `LocalBook.java` `ManualRecord.java` `ThinkData.java` | 引擎、开局库、棋谱、思考数据模型 |
| `lock` | `SingleLock.java` `WorkerTask.java` | 单实例锁、后台任务 |
| `media` | `SoundPlayer.java` | 走棋/吃子/将军等音效播放 |
| `mouse` | `GlobalMouseListener.java` `MouseListenCallBack.java` | 全局鼠标监听（用于连线点击） |
| `menu` | `BoardContextMenu.java` | 棋盘右键菜单（“粘贴局面FEN”回调 `Controller#pasteButtonClick`：剪贴板为空/行数不足 10 行时 `DialogUtils.showWarningDialog` 提示，局面不合法时用 `XiangqiUtils#validateChessBoard` 校验并弹确认框（与粘贴局面图片一致），确认后才 `newFromOriginFen`） |
| `jna` | `User32Extra.java` | Windows User32 API 扩展 |
| `util` | `XiangqiUtils.java`（`#validateChessBoard` 校验 FEN/局面合法性：将/士/象位置、兵卒位置、各兵种数量。兵卒规则由 `#pawnPositionValid` 判断——先看兵/卒是否在自己一方（红兵看帅、黑卒看将所在的那半边），在对方一侧不限；在自己一方时只有两个位置可放：初始行、向前一步的那一行（都紧邻河界，红方第 6/5 行、黑方第 3/4 行），且列必须是初始列 0/2/4/6/8） `ZobristUtils.java` `ClipboardUtils.java` `DateUtils.java` `DialogUtils.java` `ExecutorsUtils.java` `HttpUtils.java` `MathUtils.java` `PathUtils.java` `ShellUtils.java` `StringUtils.java` `SystemUtils.java` | 棋子记谱/Zobrist 哈希、剪贴板、对话框、HTTP、路径、系统等工具 |

## 4. 资源目录（src/main/resources）

| 目录 | 内容 |
| --- | --- |
| `fxml/` | 界面布局：`app.fxml`、`engineDialog.fxml`、`engineAdd.fxml`、`timeSetting.fxml`、`bookSetting.fxml`、`localBook.fxml`、`linkSetting.fxml`、`colorSetting.fxml`、`editChessBoard.fxml`、`deduction.fxml` |
| `image/` | 图标与按钮图片（含 `BOARD.JPG`、棋子/机器人图标等） |
| `style/` | CSS 样式：`app.css`、`dark-theme.css`、`light-theme.css`、`table.css`、`combobox.css` 等 |
| `sound/` | 音效：`move.wav`、`capture.wav`、`check.wav`、`win.wav`、`click.wav` |
| `font/` | `chessman.ttf` 棋子字体 |
| `model/` | ONNX 模型：`yolov11.onnx`、`pose.onnx`、`reg.onnx` |
| `META-INF/` | `MANIFEST.MF` |

## 5. 皮肤目录（skin）

每个皮肤目录（`a`、`b`）包含 `config.json`（棋盘偏移、棋子缩放/偏移/阴影）以及棋盘背景、红黑双方各兵种棋子图片、掩码图等资源。

## 6. 功能模块概览

- **引擎**：配置/加载 UCI、UCCI 引擎，支持对弈与分析、时间设置。**引擎参数快捷修改**：引擎栏 `hashComboBox` 后的“MB”后面有一个扳手图标按钮 `engineParamButton`（`app.fxml` 内用 `SVGPath` 画扳手，样式沿用 `style/app.css` 的 `.button`；提示“引擎参数”在 `Controller#setButtonTips` 注册），点击走 `Controller#engineParamButtonClick`：用 `EngineAddController.ec = 当前引擎配置` + 直接调原本的 `App#openEngineAdd()`（弹窗标题仍为“添加引擎”，纯粹作为快捷入口，无新界面）打开引擎选项编辑弹窗；确定后比较修改前后参数——仅参数变化时 `Engine#setOptions` 直接向运行中的引擎下发 `setoption`（先 `stop`），引擎程序/协议变化时 `loadEngine(EngineConfig)` 重新加载进程，名称变化同步 `Properties.engineName` 与引擎下拉框，并把 `Threads`/`Hash` 选项回写 `Properties.threadNum/hashSize` 与两个下拉框，最后按原状态（引擎执红/执黑/分析）重新 `engineGo()`，做到不改配置、不重开程序即时生效（`multiPV` 变化会同步 `board.showMultiPV`）。
- **引擎分析输出（多 PV 分组）**：引擎栏的 `listView`（`ListView<ThinkData>`）在多 PV（`Engine#getMultiPV() > 1`）时按“每一次完整输出”分组：`Controller#addEngineOutput` 把每组 PV1 插到列表顶部，组内按 PV 序号自上而下递增（`Controller#engineOutputGroupEnd` 记录当前组在列表中的结束位置），组与组之间插入一条 `ThinkData#isSeparator()` 标记的虚线行（`Controller#createEngineOutputDivider` 用 `Line` + `strokeDashArray(4,4)` 绘制，单元格上下各留 5px）；列表上限仍为 128 行（超出从尾部移除，并按需夹取 `engineOutputGroupEnd`）。单 PV 时保持原来的 `addFirst` 行为。多 PV 时每条结果标题最前面加 `PVn` 徽标（`Controller#createPvIndexLabel`：PV1 金杏色 `#c47b0c`、其余灰蓝 `#5f6f7f`，白字加粗圆角），同时去掉标题里原有的“PV: n”（`Controller#stripPvFromTitle`）以免重复；`changeTacticButtonClick` 遍历列表时跳过虚线行与空 PV。`newChessBoard` 清空列表时同时重置 `engineOutputGroupEnd`。
- **金手指（选中棋子着法分析）**：工具栏“分析模式”灯泡按钮之后的 `fingerButton`（`app.fxml`，金手指图标用 `SVGPath`（touch_app 手形）`fill=#e0a80d`），点击走 `Controller#fingerButtonClick`。要求引擎已加载、棋盘上已选中当前走棋方的棋子（`ChessBoard#getSelectedPoint` 返回 `remark`，且 `XiangqiUtils#isRed(piece) == redGo`），候选着法由 `ChessBoard#getTacticList(x, y, redGo)`（新增重载：只列举该棋子位置的 `XiangqiUtils#canGo` 着法，并用临时局面排除 `XiangqiUtils#isJiang` 送将，再转成引擎坐标，避免把非法着法交给 `searchmoves`）得到。**是一个独立的选中状态 `fingerMode`（用 `addListener(fingerButton, fingerMode)` 高亮，与执红/执黑/分析按钮同风格）**：点一下开启——连线中先 `stopGraphLink()`，若原来勾选则取消引擎执红/执黑/分析三个状态（不自动恢复，由用户手动切换）。**金手指模式不会自动退出**：`Controller#refreshFingerAnalysis()` 判断“选中的棋子属于当前走棋方且有着法”时调 `Controller#startFingerAnalysis()`（`Engine.AnalysisModel.INFINITE` + `Engine#analysis(fen, moves, tacticList, searchMultiPv)`（新增重载），`go infinite ... searchmoves ...` 只搜这些着法并把 MultiPV 提到候选着法数），否则调 `Controller#stopFingerAnalysis()` 停止引擎、清光点（**选中框保留**）；触发点为 `canvasClick`（点棋盘选中/走子）、`browseChessRecord`、`newChessBoard`、`loadEngine`（切引擎）、修改引擎参数。`board.showMultiPV(...)` 让棋盘画多路提示；金手指模式不用箭头提示，而是在走棋**目标位置**画白色闪烁光点、光点中心显示 PV 序号（1~5）：`Controller#thinkDetail` 里只在 `td.getPv() <= FINGER_MAX_TIPS`（=5）时调 `ChessBoard#setFingerTip(move, pv)`；闪烁由 `Controller#fingerMode` 监听调 `startFingerTipBlink()`/`stopFingerTips()`（`Timeline` 每 450ms 切换 `ChessBoard#setFingerTipVisible` 并重绘）驱动，退出金手指/切换局面时停止并清除光点；`BaseBoardRender#drawFingerTips` 画外圈光晕 + 白色光点 + 深色加粗序号（亮/暗两态都用较低 `globalAlpha`：光晕 0.20/0.05、光点 0.55/0.15、序号 0.85/0.30，半透明能看到底下的棋子），画在棋盘所有元素之上、且不受“棋步提示”开关影响，分析栏仍显示全部结果。再点一次金手指则手动取消选中、`engineStop()` 并清光点。互斥规则：执红与执黑可同时选中，但两者都与分析模式/金手指互斥（点执红/执黑会先走 `exitAnalysisModes()` 结束分析/金手指）；分析模式与执红/执黑/金手指互斥。`Controller#thinkDetail` 的写入条件除执红/执黑/分析外也包含 `fingerAnalysisOn`（金手指正在分析），否则引擎输出不会显示到分析栏。只有切到执红/执黑/分析（`exitAnalysisModes`）、开始连线（`stopGraphLink`）、复盘、卸载引擎才会被动清掉 `fingerMode`；走子、悔棋、浏览棋谱、新局面都保留金手指状态，只是因选中框被清空而暂停分析。普通分析（`analysis(fen, moves, tacticList)`，`searchMultiPv=0`）会通过 `Engine#applyMultiPv` 恢复到 `Engine#configMultiPV`（引擎配置里的 MultiPV，构造时记录，`setOption("MultiPV", ...)` 也会同步），因此金手指的临时 MultiPV 不会污染后续分析。引擎分析栏每次输出的分组内再按行棋方胜率倒序：`ThinkData#generate` 在按棋盘翻转分值前记录原始分值到 `ThinkData#sortScore`（绝杀换算为 ±(30000-步数)），`Controller#sortCurrentEngineOutputGroup` 用它对最新一组做 `score` 降序重排。
- **连线**：自动识别屏幕棋盘并按引擎走子落子，支持后台模式与动画确认。
- **开局库**：支持 XQB、冰河、PF 及云端开局库，可配置库招规则。
- **局面**：编辑局面、FEN 导入导出、局面图片识别。
- **棋谱**：XQF / PGN / CBR / TXQ 多格式读写；支持一键复盘，逐步给出引擎正着与“最佳/优秀/良好/不精确/失误/大漏”评价，并统计双方准确率。棋谱工具栏（`app.fxml` 中 `recordPane` 顶部 `ToolBar`）的按钮由 `ChessManualHandle` 处理：`manualPlayButton`（播放棋谱）为图标按钮；`reviewButton`（复盘）改为**纯图标按钮**（`contentDisplay=GRAPHIC_ONLY`、24×24、样式 `style/app.css` 的 `.button`，图标为 `SVGPath`（Material `fact_check` 清单打勾）`fill=#6d7a86`；原 `flat-tool-button` 文字样式已不再使用）。**播放速度**已从工具栏按钮改为“设置 → 播放速度”子菜单（`app.fxml` 中 `menuOfPlaySpeedFastest/Fast/Medium/Slow`，`ToggleGroup` 为 `playSpeedGroup`），档位 超快1s/快3s/中5s/慢10s 存在 `Properties`（枚举 `Properties.PlaySpeed`，字段 `playSpeed`，读取时默认 `FAST`），由 `Controller#playSpeedSelected` 写入并 `prop.save()`；`ChessManualHandle#startPlayTimeline` 按 `prop.getPlaySpeed().getSeconds()` 取时长，播放中切换档位会经 `ChessManualHandle#applyPlaySpeedChange` 立即按新速度继续。
- **界面设置**：棋步提示、音效、线路显示、状态栏、棋盘样式与大小、播放速度。
- **推演**：工具栏“连线”后的**推演图标按钮**（`app.fxml` 的 `deductionButton`，`contentDisplay=GRAPHIC_ONLY`、24×24，图标为 `SVGPath`（Material `grid_on` 九宫格）`fill=#6d7a86`；原金色文字按钮样式与“连线与推演之间的空隙”`Region` 已移除），通过 `App#openDeduction` 弹出独立小棋盘（`DeductionController` + `deduction.fxml`）。因 `ChessBoard` 的棋局与渲染为静态，推演不复用它，而是维护私有 `char[][]` 并用独立 `BaseBoardRender` 绘制；支持回退/前进/重置/复制FEN，走子校验用 `XiangqiUtils#canGo` 与 `isJiang`（不可送将）；底部“大小”下拉框（`sizeComboBox`）可在 小/中/大/特大（`SMALL/MIDDLE/BIG/LARGE_BOARD`）间切换，切换后 `paint()` 重绘并 `Stage#sizeToScene()` 让窗口自适应。重置会回到进入/最近同步时的局面并清空全部走棋历史；主棋盘发生走子（`goCallBack`/`browseChessRecord`/`newChessBoard`/`linkerInitChessBoard`）时通过 `App#syncDeduction` 调用 `resetTo` 同步到最新局面（等同重新打开推演棋盘）。
- **胜率条**：棋盘左侧竖直胜率条，贯穿整个棋盘（画布）高度并与棋盘上下对齐。初始红黑各半，未翻转时下红上黑（与棋盘翻转同步）。胜率来源优先级：①引擎分析（`Controller#thinkDetail` 中 `pv==1`）/复盘（`GameReview` 回调）实时结果优先；②无引擎分析时，浏览棋谱或走子（`goCallBack`/`browseChessRecord`）取当前记录“分数/胜率”列（`ManualRecord#getWinRateBottom`）回显，无值则五五开。分值经 `GameReview#eloToWinRate` 换算，绝杀按 ±30000 处理。

## 7. 构建与运行（参考）

```bash
mvn clean package          # 编译打包
mvn javafx:run             # 通过插件运行主类
```

打包产物：`target/`（Maven）与 `out/artifacts/app/app.jar`（IDE）。

## 8. 启动菜单与对弈模式（新增）

程序启动后先进入 `menu.fxml` 启动主菜单（`MenuController`），三行按钮：

| 按钮 | 行为 |
| --- | --- |
| 对弈 | 进入 `game.fxml`（`GameController`），精简对弈界面 |
| 打谱 | 进入原 `app.fxml`（`Controller`）分析/棋谱主界面 |
| 训练 | 暂为占位提示（“开发中”） |

主菜单视觉（`MenuController#initialize` + `style/menu.css`，由 `App.showMenu` 在 `applyTheme` 后加载）：根 `StackPane` 用 `skin/menuBack.png` 作为 cover 居中铺满的背景图（`MenuController#loadBackgroundImage` 同样兼容 IDE 工作目录与打包后 jar 目录，文件不存在则保持主题默认背景）；三个入口按钮各自包在 `StackPane.menu-button-wrap`（`playButtonWrap/manualButtonWrap/trainButtonWrap`，高 62）里，`installFrostedGlass` 在按钮下方插入一层与背景图完全对齐的高斯模糊 `ImageView`（`FROST_BLUR=18`、圆角 `FROST_ARC=16`、外扩 `FROST_MARGIN=24`，逻辑与 `GameController` 的磨砂面板一致，背景图 cover 对齐计算相同），形成毛玻璃；按钮本体用 `.menu-button`（半透明深色底罩 `rgba(15,20,26,0.34)` + 白色 25px 粗体 + 投影）保证文字清晰，标题/副标题 `.menu-title/.menu-subtitle` 也加投影防与背景冲突。CSS 选择器写成 `.dark-theme .menu-button` / `.light-theme .menu-button` 以压过主题里的 `.dark-theme .button`。主菜单窗口的位置与大小会被记忆：`MenuController#dispose()`（由 `App#disposeCurrentController` 在切换模式或退出时调用）把 `mainStage` 的 X/Y/宽/高写入 `Properties.menuStageX/Y/Width/Height + menuStagePositionSet` 并 `save()`；`App#showMenu` 加载时若已记录则 `setWidth/Height` 与 `setX/Y`，否则仍 `sizeToScene()` + `centerOnScreen()`。

`App` 现在通过 `showMenu() / showManual() / showGame()` 在同一 `mainStage` 上切换 Scene；切换前调用当前控制器的 `dispose()` 清理资源。`Controller#exit()` 仍负责真正退出（多调用一次 `OpenBookManager.close()`），新增 `Controller#dispose()` 与 `returnToMenuClick`。

**对弈模式顶部工具栏**（`game.fxml`，带 `tool-bar` 样式的 `HBox`）：左侧依次是“棋力 / 执棋 / 局时”：棋力、局时为下拉框；“执棋”改为按钮 `sideButton`，点击在“引擎执红 / 引擎执黑”之间切换（`GameController#sideButtonClick`，同步翻转棋盘与时钟上下顺序），无需下拉选择（原“出招速度”已移除，出招延迟改由局时档位决定）；紧接着是“开始对弈 / 认输 / 封棋”（开始对弈与 认输/封棋 交替显示，靠左，与引擎设置同区），其后 `Region` 占位，最右是“设置”“返回主菜单”。时钟在左栏（`leftPanel`/`clockBox`），对局状态文字位于棋盘右侧空白区域（`rightPanel`）底部：宽度占满该区域，高度为其 1/4（详见本节“状态浮层”）。`GameController#autoFitBoard` 先按窗口宽度减左右最小预留 `SIDE_MIN=240` 定棋盘大小，再把左右栏 `prefWidth` 设为棋盘两侧的实际区域宽度 `(总宽 - 棋盘宽)/2`，使左右栏正好等于“棋盘边 ~ 窗体边”的区域（右栏现在仅为保持棋盘居中的空占位）。`app.fxml` 的“返回主菜单”同样在顶部工具栏最右（外层 `HBox` 包裹 `ToolBar` + 按钮）。注意：该 `HBox` 需带 `styleClass="tool-bar"`（并把内层 `ToolBar` 设为透明背景/边框、HBox 自身 `-fx-padding/-fx-spacing: 0`），否则“返回主菜单”按钮落在工具栏之外，拿不到 `.tool-bar .button` 样式，在默认（浅色）主题下会露出普通按钮的白色高光边框。

### 8.1 对弈模式配置隔离

**配置独立原则**：对弈、打谱、训练三种模式的配置尽量完全独立、单独保存。当前对弈用 `model/GameConfig.java`（jar 目录下 `gameConfig`），打谱用 `config/Properties`（jar 目录下 `properties`），训练尚未实现、实现时必须使用自己的独立配置文件，不得复用以上两者。窗口位置/大小也按模式分别记忆：对弈存在 `GameConfig.stageX/Y/Width/Height + stagePositionSet`，打谱存在 `Properties.stageX/Y/Width/Height + stagePositionSet`，主菜单存在 `Properties.menuStageX/Y/Width/Height + menuStagePositionSet`（分别在 `GameController#dispose`/`Controller#dispose`/`MenuController#dispose` 时写入，`App#showGame/showManual/showMenu` 时恢复）。棋盘样式、棋步提示、走棋音效、显示序号等界面偏好不单独区分，对弈模式直接沿用打谱模式的 `Properties`。

对弈模式使用独立模型 `model/GameConfig.java`，单独序列化保存到 jar 目录下 `gameConfig` 文件，与分析模式 `config/Properties` 完全隔离：

- **对弈引擎（完全独立）**：`GameConfig.engineConfigList` 保存对弈模式专属的引擎列表，`engineName / enginePath / engineProtocol / engineOptions`（基础 options 快照，含 `Skill Level`）记录当前所选引擎，加上 `hashSize / threadNum / depth`（每步层数）。打谱模式仍用 `Properties.engineConfigList`，两者互不影响。引擎管理对话框通过 `App.openEngineDialog(List<EngineConfig>, Runnable onChanged)` 复用：对弈模式传入 `gameConfig.getEngineConfigList()` 与 `gameConfig::save`，打谱模式传 `null`（退回全局列表并 `prop.save()`）；`EngineManageController.targetEngineList / onEngineListChanged` 承接目标列表与保存回调，`EngineAddController` 也写入该目标列表。
- **设置弹窗**：对弈引擎选择与引擎管理、`hashSize / threadNum / depth`（每步层数）以及对弈开局库（启用、库文件选择、库招规则、脱谱步数）统一放在“设置”弹窗（`gameSetting.fxml` + `GameSettingController`），由 `App.openGameSetting(GameConfig)` 打开。界面已重排：各 `GridPane` 标签列统一 `halignment=RIGHT`（宽 64）、行/列间距 `12/10`，四周留 `20px` 边距；各下拉/输入框改为固定宽度（引擎/线程/Hash/每步层数 120，开局库/库招规则/脱谱步数 150，均左对齐）且 `maxWidth=-Infinity` 不拉伸（不占满）；引擎框为非可编辑下拉（原 `editable=true` 已去掉）；“启用开局库”改为 `Label`(列 0 右对齐) + 无文字 `CheckBox`(列 1)，与其余标签对齐；内容区四周留边距（`center` 的 VBox 用 **`<BorderPane.margin>`** 而非 `VBox.margin`，否则作为 BorderPane 子节点时会被忽略导致内容顶到左上；top 20 / left 24 / right 24 / bottom 8，底部按钮区 BorderPane.margin 24），引擎“引擎管理”与开局库“选择”按钮与对应控件同行；弹窗局部样式 `style/gameSetting.css`（`.section-title` / `.hint-label`）。**每步层数限制 10~20**：`GameSettingController` 的 `DEPTH_MIN/DEPTH_MAX` 与 `GameConfig.setDepth/normalize` 双重限制。弹窗内所有修改先存入草稿（`GameSettingController` 的 `draft*` 字段，引擎列表为深拷贝），点“保存”才写回 `GameConfig` 并 `save()`；“取消”不保存任何修改（包括引擎管理的改动）。
- **局时**：`GameConfig.ClockTier` 三档（与棋力同在顶部工具栏选择；“执棋”为点击切换按钮）：快=局时5分钟/步时1分钟/用完后步时20秒；中=10分钟/步时2分钟/用完后30秒；慢=20分钟/步时3分钟/用完后1分钟。默认**快**；棋力/局时都会记住上次选择（`configVersion < 3` 的旧配置会迁移到新默认）；**执子不记忆**：每次进入对弈都重置为玩家执红（引擎执黑），且“执子”改动不再写盘。`GameController` 用 `Timeline`（200ms）扣减：局时>0时同时扣主局时与步时（步时上限），局时归零时若本步剩余超过每步加时则立即降为每步加时，之后每步按 bonus 重置；超时判负。
- **棋力**：通过引擎 `Skill Level` option（0~20）设置，`GameConfig.SkillLevel` 定义 10 档：村冠0 / 镇冠2 / 县冠4 / 市冠6 / 省冠8 / 国家大师10 / 象棋大师12 / 特级大师14 / 棋圣16 / 棋神20。开始对弈后棋力及引擎相关控件全部禁用，开始前手动选择。
- **出招延迟**：不再单独设挡位，由 `GameConfig.ClockTier` 的加时决定（`ClockTier.delayMinSeconds/MaxSeconds` + `randomDelayMillis()`）：加时 20s(快)→5~10s、加时 30s(中)→10~20s、加时 60s(慢)→30~50s。“延迟”是引擎算完后的**额外等待**，不含引擎思考时间。
- **开局库**：`useBook / openBookList / bookPath / moveRule / offManualSteps`（脱谱步数）独立存储，且均在“设置”弹窗里可配；`GameController#queryGameBook` 直接实例化 `XqbOpenBook / BhOpenBook / PfOpenBook` 查询，不走全局 `OpenBookManager`。命中库招时状态栏显示“开局库出招...”，否则走引擎分析。对弈模式不会读取打谱模式的 `Properties` 开局库列表，需在“设置”里单独添加。**开局库下拉即“当前启用的库”**：`GameConfig.bookPath` 记录所选库路径，对弈时只查询该库（为空则回退整个 `openBookList`）；`openBookList` 作为可选库池，下拉只显示文件名，不存在的文件标注“（已失效）”并可用“删除”按钮移除（`GameSettingController#bookDeleteButtonClick`，`gameSetting.fxml` 的 `bookDeleteButton`）；切换下拉立即更新 `draftBookPath`，悬停显示完整路径。

### 8.2 对弈模式实现要点

- `GameController` 实现 `EngineCallBack`，用 `Engine` 以 `FIXED_STEPS`（每步层数）分析；调用 `engine.analysis(initialFen, moves, null)` 直接走引擎，绕过引擎内部库招与全局延迟。
- 延迟由 `GameController#delayedPlay` 自行控制：在引擎读线程/库招虚拟线程 `Thread.sleep` 后 `Platform.runLater` 落子。**延迟独立于引擎思考时间**：`wait = clockTier().randomDelayMillis()`，引擎算完后再等该时长（延迟 10s + 引擎 3s = 约 13s）。无需再按本步剩余封顶：延迟上限已设计为“加时 - 10s”（20→5~10 / 30→10~20 / 60→30~50），即使局时在等待期间耗尽、本步降为加时，最大延迟仍比加时小 10s，不会超时。`Engine#setEngineDelay(0,0)` 用于关闭引擎内部全局出招延迟（`Engine` 新增 `engineDelayStart/End` 字段与 setter）。
- 已移除“悔棋 / 立即出招 / 翻转 / 新局面”按钮：开始对弈即新的一局（`resetBoard`）；玩家执黑时自动翻转棋盘（`isReverse = engineRed()`，切换“执子”时立即 `board.reverse`）。
- 对局未结束前不允许返回主菜单：`GameController#backToMenuClick` 在 `started` 时弹提示并拦截（将死/困毙/超时/认输后 `started=false` 才能返回）。
- 左栏显示计时：两个类似国外象棋时钟的卡片（`redClockPanel / blackClockPanel`）分别贴近棋盘的上下底线（不翻转时上黑下红，翻转时上下互换，`GameController#layoutClockOrder`），通过中间可伸缩 `clockSpacer` 分开；`autoFitBoard` 现用 `canvasPane 高度 / 4` 作为左栏上下 padding（上下各留 1/4 间距），避免两个时钟被撑得上下过远。每张卡片有“局时 / 步时”小标题和下面等大加粗的时间数字（`redClockMain / redClockMove / blackClockMain / blackClockMove`，等宽字体）；当前行棋方卡片加亮（`clock-panel-active`），剩余不足 10 秒数字变红（`clock-low`）。样式在新增的 `style/game.css`（`App.showGame` 加载，`.clock-time` 52px 为兜底）。时钟随窗体动态缩放：`GameController#applyClockScale(side)` 在 `autoFitBoard` 末尾调用，**用二分法实测**出能完整放进侧栏的最大基准字号（不再用固定比例，避免字体/取整导致 `Label` 出现省略号）：`lo=8`、`hi=min(72,side)` 迭代 12 次，每次 `applyClockLayout(side,mid)` 后由 `clockPanelsFit(side,mid)` 用 `panel.applyCss()` + `prefWidth(-1) <= side-2*panelPad-2` 判定；`applyClockLayout` 里 `gap=fontSize*0.46`，`scaleClockNode` 递归整个时钟块按样式类缩放时间数字（1.0×）、红黑徽标（0.25×，含 padding）、局时/步时小标题（0.21×）并均设下限；同时按比例缩放左栏左右 padding（0.27×，上下仍为 `canvasPane 高度/4`）与时钟卡片内边距（纵向 0.115×、横向 0.19×，均内联覆盖 CSS，`HBox.setSpacing` 覆盖 fxml 的 24）。红黑徽标底色仍在 fxml 内联（`#c0392b` / `#2f3b46`）；因 `setStyle` 会覆盖内联，`scaleClockNode` 为徽标生成样式时按文字“红/黑”重新带上背景色。低时提示：当**我方**（`engineRed()?1:0`）的**步时**不足 10 秒（`clock-low`）时，在红黑时钟中间的 `clockSpacer`（已由 `Region` 改为 `StackPane`，内含 `lowSecondsLabel`，样式 `.low-seconds`）显示剩余秒数：`updateLowSeconds`（在 `updateClockLabels` 末尾调用）只取该方步时的剩余毫秒向上取整为秒（局时耗尽不再触发闪烁），闪烁与跳秒同步：`updateLowSeconds` 仅当 `(low+999)/1000` 的秒数发生变化（`lastLowSecond`）时才调 `flashLowSeconds`，立即把 opacity 置 1，并由一次性 `blinkTimeline` 在 500ms 后置 0，因此每秒闪一次且与下面时间数字同一帧跳变；`stopClock` 里调 `stopBlink` 停止并清空。字号随窗体与时钟同缩放。
- 引擎思考时状态栏只显示“引擎思考中...”，不再显示深度/分数（`thinkDetail` 已简化）。
- 开局后右侧配置面板 `configBox` 隐藏，仅显示“认输”按钮（`gameActionBox`）；将死/困毙（`XiangqiUtils#isSha`）、超时（`endGameByTimeout`）或点击“认输”（`resignButtonClick`）后恢复显示配置并允许再来一局。
- 对弈模式隐藏棋盘左侧胜率条：`ChessBoard` 新增 `showWinRate` 字段与 `setShowWinRate(boolean)`，`GameController` 创建棋盘后调用 `board.setShowWinRate(false)`。
- 棋盘随窗口动态调整：对弈棋盘使用 `ChessBoard.BoardSize.AUTOFIT_BOARD`，`ChessBoard` 新增 2 参重载 `autoFitSize(width, height)`（按 9⅓×10⅓ 格空间算棋子尺寸，最小 24）；`game.fxml` 中央改用 `StackPane`（自动居中画布），`GameController#initAutoFitBoard` 监听 `canvasPane` 宽高变化调用自适应；`BaseBoardRender.autoPieceSize` 默认 60。
- `ChessBoard` 的静态 `boardRender` 会跨画布复用，故 `Controller#newChessBoard` 与 `GameController` 创建棋盘后都调用 `board.setBoardStyle(...)` 重新绑定到当前 canvas。
- **超时弹窗**：`endGameByTimeout` 由计时 `Timeline`（动画处理中）调用，直接 `showAndWait` 会抛 “showAndWait is not allowed during animation or layout processing”，故其 `DialogUtils.showInfoDialog` 用 `Platform.runLater` 延到下一帧。
- **对局结束 → 复盘**：对局结束（将死/困毙、超时、认输）时由 `GameController#showGameOverView` 在顶部工具栏显示“复盘”按钮 `reviewButton`（`game.fxml`，仅有棋谱时显示；`showGameView`/初始化时隐藏）。点击后 `GameController#reviewButtonClick` 调用 `App.showManualWithGame(initialFen, moves)`：`App` 用 `pendingGameFen / pendingGameMoves` 暂存该局棋谱并切到打谱界面；`Controller#initStage` 检测 `App.hasPendingGame()` 后调用新增的 `Controller#loadGameRecord(fen, moveList)`，先 `chessManualHandle.newChessManual(fen)` 与 `newChessBoard(fen, true)`，再逐着 `board.move` + `chessManualHandle.boardMove(move, board.translate(move, true))` 写入棋谱（载入期间关闭走棋音效），最终棋盘停在终局局面，可直接在分析模式中浏览/复盘。
- **对弈背景图片**：若 `skin/back.png`（工作目录下，兼容打包后的 jar 目录 `PathUtils.getJarPath()/skin/back.png`）存在，`GameController#applyBackgroundImage` 会在 `initialize()` 中把它作为整个对弈窗口（`rootPane`）的背景图，`BackgroundSize` 用 100% + `cover` 随窗口自适应铺满；文件不存在则保持主题默认背景。查找逻辑见 `loadBackgroundImage()`。
- **状态浮层**：对局状态文字从右栏顶部移到**棋盘右侧空白区域（`rightPanel`）底部：宽度占满右栏，高度为右栏的 1/4**（不在棋盘上）。`game.fxml` 的 `<right>` 由空 `VBox` 改为 `StackPane`（`alignment=BOTTOM_RIGHT`），内含 `statusOverlay`(StackPane，`StackPane.alignment=BOTTOM_RIGHT`) 与 `statusLabel`；`canvas` 仍只在 `canvasPane` 中，两者不重叠。`GameController#initStatusOverlay` 将 `statusOverlay.prefWidth` 绑定为 `rightPanel` 宽度、`prefHeight` 绑定为 `rightPanel` 高度的 1/4 并 `maxSize=USE_PREF_SIZE`，使其固定贴底部。样式在 `game.css` 的 `.status-overlay`（半透明黑底 + 白边框/圆角，hover 加深）与 `.status-text`（白字 + 投影），保证与背景图或浅/深主题下都清晰可读；字号随浮层大小缩放（`applyStatusScale`，`min(宽,高)*0.13` 限制 13~40px）。文字超出浮层高度时 `updateStatusTooltip` 安装 `Tooltip`（`textProperty` 绑定 `statusLabel`），悬停显示完整文本，放得下则移除。
- **磨砂玻璃（高斯模糊）时钟/状态面板**：存在 `skin/back.png` 时，`GameController#initFrostedGlass` 会给左右两个时钟面板与右下状态浮层各插入一层与主背景对齐、经 `GaussianBlur(18)` 模糊的 `ImageView`（`installFrostedGlass`/`updateFrostedGlass`）。**参数与配色对齐主菜单**：`FROST_BLUR=18 / FROST_ARC=12(圆角半径，arcWidth=半径×2) / FROST_MARGIN=24 / FROST_INSET=2`、磨砂层 `opacity=0.85`，`.clock-panel`/`.status-overlay` 底色用菜单同色系但更暗的 `rgba(15,20,26,0.55)`（保证文字在背景图上清晰）+ 白描边 `rgba(255,255,255,0.45)` + `dropshadow 10/0.3`（深/浅主题一致，行棋方高亮边框保留，active/hover 用 0.68）。实现：背景在 `rootPane` 以 `cover+center` 铺满，根据面板在 `rootPane` 中的位置反推图片视口（`ImageView#setViewport`）并外扩 `24px` 避免模糊边缘透明，图层随面板尺寸/位置变化（监听 `width/height/localToSceneTransform/scene`）实时同步；磨砂层向内缩 `2px`并用圆角 `Rectangle` 裁剪，保留面板自身的半透明底色与边框（含行棋方黄色高亮边框）。无背景图时自动跳过，仅用半透明底色。时钟/状态文字改为白色 + 投影（`game.css` 中 `.clock-panel`/`.clock-time`/`.clock-caption` 与 `.status-overlay`/`.status-text` 统一为半透明暗底 + 白字），保证任意背景下可读。视口坐标会夹取到背景图范围内，布局/缩放瞬时越界时直接跳过本次更新，避免 Viewport 宽高为负导致 `Rectangle2D` 报“Both width and height must be >= 0”。
- **封棋 / 恢复对局**：对局进行中工具栏显示“封棋”按钮（`game.fxml` 的 `suspendButton`，由 `GameController#showGameView/showConfigView` 控制显隐）。点击 `suspendButtonClick`：先 `started=false` + `gameGeneration++` + `stopClock()` + `closeEngine()`（使引擎延迟出招回调失效，避免封棋后继续走子/超时），再把棋谱、计时、引擎执红黑等存入 `model/GameSnapshot` 并序列化到工作目录根下临时文件 `gameSuspend.tmp`，最后 `App.showMenu()` 回主菜单。快照字段：`initialFen`、`moves`、`redGo`、`engineSide`、`clockTier`、双方剩余局时。下次进入对弈模式时 `GameController#initialize` 用 `Platform.runLater(checkSuspendRestore)` 检查：若临时文件存在，**先 `load()` 读出内容并 `delete()` 删除文件**，再弹窗询问是否恢复（`DialogUtils.showConfirmDialog`）；选“确定”调 `restoreGame`（还原执红黑/局时档位/棋谱并 `board.browseChessRecord` 摆回局面、`loadEngine`、开钟，若是引擎行棋则 `engineGo`，刷新状态），选“取消”则开始新对局。**时间与防超时**：局时按快照还原，**步时直接“白送”满值**（`freshMoveMillis`：局时用完则送加秒 `bonusMillis`，否则送步时 `perMoveMillis`），双方都刷新保证显示一致。这样逻辑最简单，同时避免恢复后引擎需从头重新分析却带着“已消耗过”的步时直接超时。**对局代号 `gameGeneration`**：`loadEngine` 每次新建引擎都 `++gameGeneration` 并把该代号绑定到引擎回调（`EngineCallBack` 包装类，代号不匹配则丢弃）；`delayedPlay(move, gen)` 与开局库虚拟线程也带代号校验，因此封棋/重新开始后旧引擎的回调与正在 `sleep` 的延迟出招唤醒后都会被丢弃，不会把旧着法落到新对局上。
