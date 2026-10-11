package com.sojourners.chess.model;

import com.sojourners.chess.openbook.MoveRule;
import com.sojourners.chess.util.PathUtils;

import java.io.*;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

/**
 * 对弈模式配置。
 *
 * 与分析（打谱）模式的 {@link com.sojourners.chess.config.Properties} 完全独立，
 * 单独序列化保存到 jar 目录下的 gameConfig 文件。
 */
public class GameConfig implements Serializable {

    private static final long serialVersionUID = 2026101101L;

    /** 配置版本，用于新增字段/default 的迁移 */
    private int configVersion = 3;

    /** 对弈模式专属的引擎列表，与打谱（分析）模式的引擎管理完全独立、单独保存 */
    private List<EngineConfig> engineConfigList = new ArrayList<>();

    /** 对弈引擎：身份与基础 options 均为独立快照，与分析引擎互不影响 */
    private String engineName = "";
    private String enginePath = "";
    private String engineProtocol = "";
    private LinkedHashMap<String, String> engineOptions = new LinkedHashMap<>();

    /** hash、线程、每步层数，与基础 options 放一起构成对弈引擎配置 */
    private int hashSize = 128;
    private int threadNum = 1;
    private int depth = 15;

    /** 棋力：通过引擎的 Skill Level option（0~20）设置 */
    private int skillLevel = 10;

    /** 局时档位（快/中/慢），默认快；引擎出招延迟（总出招时间）由该档位的加时决定 */
    private String clockTier = ClockTier.FAST.name();

    /** 开局库（对弈专用，与打谱模式隔离） */
    private boolean useBook = true;
    private List<String> openBookList = new ArrayList<>();
    /** 当前启用的开局库路径（对弈时只查询该库；为空表示未选择，则退回使用整个列表） */
    private String bookPath = "";
    private MoveRule moveRule = MoveRule.BEST_SCORE;
    private int offManualSteps = 9999;

    /** 引擎执子：red / black */
    private String engineSide = "black";

    /** 对弈窗口位置与大小（独立记忆；棋盘显示等界面偏好沿用打谱模式） */
    private boolean stagePositionSet = false;
    private double stageX = 0;
    private double stageY = 0;
    private double stageWidth = 880;
    private double stageHeight = 680;

    /**
     * 局时档位。
     * 快：局时 5 分钟，步时 1 分钟，局时用完后每步 20 秒；
     * 中：局时 10 分钟，步时 2 分钟，局时用完后每步 30 秒；
     * 慢：局时 20 分钟，步时 3 分钟，局时用完后每步 1 分钟。
     *
     * 引擎的“出招延迟”（总出招时间）由加时决定：
     * 加时 20s -> 5~10s，加时 30s -> 10~20s，加时 60s -> 30~50s。
     */
    public enum ClockTier {
        FAST("快", 5 * 60_000L, 1 * 60_000L, 20_000L, 5, 10),
        MEDIUM("中", 10 * 60_000L, 2 * 60_000L, 30_000L, 10, 20),
        SLOW("慢", 20 * 60_000L, 3 * 60_000L, 60_000L, 30, 50);

        private final String title;
        private final long mainMillis;
        private final long perMoveMillis;
        private final long bonusMillis;
        private final int delayMinSeconds;
        private final int delayMaxSeconds;

        ClockTier(String title, long mainMillis, long perMoveMillis, long bonusMillis,
                  int delayMinSeconds, int delayMaxSeconds) {
            this.title = title;
            this.mainMillis = mainMillis;
            this.perMoveMillis = perMoveMillis;
            this.bonusMillis = bonusMillis;
            this.delayMinSeconds = delayMinSeconds;
            this.delayMaxSeconds = delayMaxSeconds;
        }

        public String getTitle() {
            return title;
        }

        public long getMainMillis() {
            return mainMillis;
        }

        public long getPerMoveMillis() {
            return perMoveMillis;
        }

        public long getBonusMillis() {
            return bonusMillis;
        }

        public int getDelayMinSeconds() {
            return delayMinSeconds;
        }

        public int getDelayMaxSeconds() {
            return delayMaxSeconds;
        }

        /** 随机一个“总出招时间”（毫秒），范围由本档位加时决定。 */
        public long randomDelayMillis() {
            Random r = new SecureRandom();
            int sec = delayMinSeconds + r.nextInt(delayMaxSeconds - delayMinSeconds + 1);
            return sec * 1000L;
        }

        public static ClockTier fromTitle(String title) {
            for (ClockTier t : values()) {
                if (t.title.equals(title)) {
                    return t;
                }
            }
            return MEDIUM;
        }

        public static ClockTier fromName(String name) {
            if (name == null) {
                return MEDIUM;
            }
            try {
                return valueOf(name);
            } catch (Exception e) {
                return MEDIUM;
            }
        }
    }

    /**
     * 棋力档位，共 10 档，对应引擎 Skill Level 0~20。
     */
    public enum SkillLevel {
        VILLAGE("村冠", 0),
        TOWN("镇冠", 2),
        COUNTY("县冠", 4),
        CITY("市冠", 6),
        PROVINCE("省冠", 8),
        NATIONAL_MASTER("国家大师", 10),
        CHESS_MASTER("象棋大师", 12),
        GRANDMASTER("特级大师", 14),
        SAGE("棋圣", 16),
        GOD("棋神", 20);

        private final String title;
        private final int value;

        SkillLevel(String title, int value) {
            this.title = title;
            this.value = value;
        }

        public String getTitle() {
            return title;
        }

        public int getValue() {
            return value;
        }

        public static SkillLevel fromTitle(String title) {
            for (SkillLevel s : values()) {
                if (s.title.equals(title)) {
                    return s;
                }
            }
            return NATIONAL_MASTER;
        }

        public static SkillLevel fromValue(int value) {
            SkillLevel best = VILLAGE;
            for (SkillLevel s : values()) {
                if (s.value <= value) {
                    best = s;
                } else {
                    break;
                }
            }
            return best;
        }
    }

    public EngineConfig toEngineConfig() {
        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        if (engineOptions != null) {
            options.putAll(engineOptions);
        }
        options.put("Skill Level", String.valueOf(skillLevel));
        return new EngineConfig(engineName, enginePath, engineProtocol, options);
    }

    public void copyEngineIdentity(EngineConfig ec) {
        if (ec == null) {
            return;
        }
        this.engineName = ec.getName();
        this.enginePath = ec.getPath();
        this.engineProtocol = ec.getProtocol();
        this.engineOptions = new LinkedHashMap<>();
        if (ec.getOptions() != null) {
            this.engineOptions.putAll(ec.getOptions());
        }
    }

    public static GameConfig load() {
        File file = new File(PathUtils.getJarPath() + "gameConfig");
        if (file.exists()) {
            try (ObjectInputStream os = new ObjectInputStream(new FileInputStream(file))) {
                Object obj = os.readObject();
                if (obj instanceof GameConfig gc) {
                    normalize(gc);
                    return gc;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new GameConfig();
    }

    public void save() {
        try (ObjectOutputStream os = new ObjectOutputStream(
                new FileOutputStream(PathUtils.getJarPath() + "gameConfig"))) {
            os.writeObject(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void normalize(GameConfig gc) {
        if (gc.configVersion < 3) {
            // 迁移到新的默认：局时快（出招延迟由局时档位决定）
            gc.clockTier = ClockTier.FAST.name();
            gc.configVersion = 3;
        }
        if (gc.engineConfigList == null) gc.engineConfigList = new ArrayList<>();
        if (gc.engineName == null) gc.engineName = "";
        if (gc.enginePath == null) gc.enginePath = "";
        if (gc.engineProtocol == null) gc.engineProtocol = "";
        if (gc.engineOptions == null) gc.engineOptions = new LinkedHashMap<>();
        if (gc.openBookList == null) gc.openBookList = new ArrayList<>();
        if (gc.bookPath == null) gc.bookPath = "";
        if (gc.bookPath.isBlank() && !gc.openBookList.isEmpty()) {
            gc.bookPath = gc.openBookList.get(0);
        }
        if (gc.moveRule == null) gc.moveRule = MoveRule.BEST_SCORE;
        if (gc.clockTier == null || gc.clockTier.isBlank()) {
            gc.clockTier = ClockTier.FAST.name();
        }
        if (gc.engineSide == null || gc.engineSide.isBlank()) {
            gc.engineSide = "black";
        }
        if (gc.stageWidth <= 0) {
            gc.stageWidth = 880;
        }
        if (gc.stageHeight <= 0) {
            gc.stageHeight = 680;
        }
        if (gc.hashSize < 1) gc.hashSize = 128;
        if (gc.threadNum < 1) gc.threadNum = 1;
        // 每步层数限制在 10~20（限制引擎思考时间）
        if (gc.depth <= 0) gc.depth = 15;
        if (gc.depth < 10) gc.depth = 10;
        if (gc.depth > 20) gc.depth = 20;
        if (gc.skillLevel < 0) gc.skillLevel = 0;
        if (gc.skillLevel > 20) gc.skillLevel = 20;
        if (gc.offManualSteps < 1) gc.offManualSteps = 9999;
    }

    public List<EngineConfig> getEngineConfigList() {
        return engineConfigList;
    }

    public void setEngineConfigList(List<EngineConfig> engineConfigList) {
        this.engineConfigList = engineConfigList;
    }

    public String getEngineName() {
        return engineName;
    }

    public void setEngineName(String engineName) {
        this.engineName = engineName;
    }

    public String getEnginePath() {
        return enginePath;
    }

    public void setEnginePath(String enginePath) {
        this.enginePath = enginePath;
    }

    public String getEngineProtocol() {
        return engineProtocol;
    }

    public void setEngineProtocol(String engineProtocol) {
        this.engineProtocol = engineProtocol;
    }

    public LinkedHashMap<String, String> getEngineOptions() {
        return engineOptions;
    }

    public void setEngineOptions(LinkedHashMap<String, String> engineOptions) {
        this.engineOptions = engineOptions;
    }

    public int getHashSize() {
        return hashSize;
    }

    public void setHashSize(int hashSize) {
        this.hashSize = hashSize;
    }

    public int getThreadNum() {
        return threadNum;
    }

    public void setThreadNum(int threadNum) {
        this.threadNum = threadNum;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        // 每步层数限制在 10~20
        this.depth = Math.max(10, Math.min(20, depth));
    }

    public int getSkillLevel() {
        return skillLevel;
    }

    public void setSkillLevel(int skillLevel) {
        this.skillLevel = skillLevel;
    }

    public String getClockTier() {
        return clockTier;
    }

    public void setClockTier(String clockTier) {
        this.clockTier = clockTier;
    }

    public boolean isUseBook() {
        return useBook;
    }

    public void setUseBook(boolean useBook) {
        this.useBook = useBook;
    }

    public List<String> getOpenBookList() {
        return openBookList;
    }

    public void setOpenBookList(List<String> openBookList) {
        this.openBookList = openBookList;
    }

    public String getBookPath() {
        return bookPath;
    }

    public void setBookPath(String bookPath) {
        this.bookPath = bookPath;
    }

    public MoveRule getMoveRule() {
        return moveRule;
    }

    public void setMoveRule(MoveRule moveRule) {
        this.moveRule = moveRule;
    }

    public int getOffManualSteps() {
        return offManualSteps;
    }

    public void setOffManualSteps(int offManualSteps) {
        this.offManualSteps = offManualSteps;
    }

    public String getEngineSide() {
        return engineSide;
    }

    public void setEngineSide(String engineSide) {
        this.engineSide = engineSide;
    }

    public boolean isStagePositionSet() {
        return stagePositionSet;
    }

    public void setStagePositionSet(boolean stagePositionSet) {
        this.stagePositionSet = stagePositionSet;
    }

    public double getStageX() {
        return stageX;
    }

    public void setStageX(double stageX) {
        this.stageX = stageX;
    }

    public double getStageY() {
        return stageY;
    }

    public void setStageY(double stageY) {
        this.stageY = stageY;
    }

    public double getStageWidth() {
        return stageWidth;
    }

    public void setStageWidth(double stageWidth) {
        this.stageWidth = stageWidth;
    }

    public double getStageHeight() {
        return stageHeight;
    }

    public void setStageHeight(double stageHeight) {
        this.stageHeight = stageHeight;
    }
}
