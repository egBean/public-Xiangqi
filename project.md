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
| `EngineAddController.java` | 添加/编辑引擎 |
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
| `Engine.java` | 引擎进程管理与 UCI/UCCI 协议交互 |
| `EngineCallBack.java` | 引擎输出回调 |

### 3.5 linker/ — 自动连线

| 文件 | 职责 |
| --- | --- |
| `GraphLinker.java` | 连线入口/接口 |
| `AbstractGraphLinker.java` | 连线公共逻辑（截图识别、点击落子） |
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
| `Yolo5Model.java` | YOLOv5 推理实现 |
| `Yolo11Model.java` | YOLOv11 推理实现 |

### 3.9 review/ — 棋谱复盘

| 文件 | 职责 |
| --- | --- |
| `GameReview.java` | 复盘流程：逐局面分析棋谱、对比实际着法与引擎正着、给出评价 |
| `ReviewEngine.java` | 复盘专用同步引擎客户端（独立进程，逐个局面返回 bestmove） |
| `ReviewEval.java` | 单个局面的分析结果（最佳着法、分值） |
| `ReviewItem.java` | 单步复盘结果（损失、评价、正着等） |
| `ReviewSummary.java` | 复盘汇总：双方各类着法数量与准确率 |
| `MoveQuality.java` | 着法评价等级（最佳/优秀/良好/不精确/失误/大漏） |

### 3.10 其他包

| 包 | 文件 | 职责 |
| --- | --- | --- |
| `config` | `Properties.java` | 全局配置模型，Serializable 持久化 |
| `model` | `BookData.java` `EngineConfig.java` `LocalBook.java` `ManualRecord.java` `ThinkData.java` | 引擎、开局库、棋谱、思考数据模型 |
| `lock` | `SingleLock.java` `WorkerTask.java` | 单实例锁、后台任务 |
| `media` | `SoundPlayer.java` | 走棋/吃子/将军等音效播放 |
| `mouse` | `GlobalMouseListener.java` `MouseListenCallBack.java` | 全局鼠标监听（用于连线点击） |
| `menu` | `BoardContextMenu.java` | 棋盘右键菜单 |
| `jna` | `User32Extra.java` | Windows User32 API 扩展 |
| `util` | `XiangqiUtils.java` `ZobristUtils.java` `ClipboardUtils.java` `DateUtils.java` `DialogUtils.java` `ExecutorsUtils.java` `HttpUtils.java` `MathUtils.java` `PathUtils.java` `ShellUtils.java` `StringUtils.java` `SystemUtils.java` | 棋子记谱/Zobrist 哈希、剪贴板、对话框、HTTP、路径、系统等工具 |

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

- **引擎**：配置/加载 UCI、UCCI 引擎，支持对弈与分析、时间设置。
- **连线**：自动识别屏幕棋盘并按引擎走子落子，支持后台模式与动画确认。
- **开局库**：支持 XQB、冰河、PF 及云端开局库，可配置库招规则。
- **局面**：编辑局面、FEN 导入导出、局面图片识别。
- **棋谱**：XQF / PGN / CBR / TXQ 多格式读写；支持一键复盘，逐步给出引擎正着与“最佳/优秀/良好/不精确/失误/大漏”评价，并统计双方准确率。
- **界面设置**：棋步提示、音效、线路显示、状态栏、棋盘样式与大小。
- **推演**：工具栏“连线”后的“推演”按钮，通过 `App#openDeduction` 弹出独立小棋盘（`DeductionController` + `deduction.fxml`）。因 `ChessBoard` 的棋局与渲染为静态，推演不复用它，而是维护私有 `char[][]` 并用独立 `BaseBoardRender` 绘制；支持回退/前进/重置/复制FEN，走子校验用 `XiangqiUtils#canGo` 与 `isJiang`（不可送将）；底部“大小”下拉框（`sizeComboBox`）可在 小/中/大/特大（`SMALL/MIDDLE/BIG/LARGE_BOARD`）间切换，切换后 `paint()` 重绘并 `Stage#sizeToScene()` 让窗口自适应。重置会回到进入/最近同步时的局面并清空全部走棋历史；主棋盘发生走子（`goCallBack`/`browseChessRecord`/`newChessBoard`/`linkerInitChessBoard`）时通过 `App#syncDeduction` 调用 `resetTo` 同步到最新局面（等同重新打开推演棋盘）。
- **胜率条**：棋盘左侧竖直胜率条，贯穿整个棋盘（画布）高度并与棋盘上下对齐。初始红黑各半，未翻转时下红上黑（与棋盘翻转同步）。胜率来源优先级：①引擎分析（`Controller#thinkDetail` 中 `pv==1`）/复盘（`GameReview` 回调）实时结果优先；②无引擎分析时，浏览棋谱或走子（`goCallBack`/`browseChessRecord`）取当前记录“分数/胜率”列（`ManualRecord#getWinRateBottom`）回显，无值则五五开。分值经 `GameReview#eloToWinRate` 换算，绝杀按 ±30000 处理。

## 7. 构建与运行（参考）

```bash
mvn clean package          # 编译打包
mvn javafx:run             # 通过插件运行主类
```

打包产物：`target/`（Maven）与 `out/artifacts/app/app.jar`（IDE）。
