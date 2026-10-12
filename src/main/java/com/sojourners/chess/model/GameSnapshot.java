package com.sojourners.chess.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 封棋快照。
 *
 * 对局进行中点击“封棋”时，把棋谱、计时与引擎执红/执黑等信息序列化保存到
 * 工作目录根下的临时文件（{@value #FILE_NAME}）；下次进入对弈模式时读取并提示恢复。
 * 该文件与 {@link GameConfig} 无关，仅用于“未完成的棋局”。
 */
public class GameSnapshot implements Serializable {

    private static final long serialVersionUID = 2026110701L;

    /** 临时文件名（工作目录根下） */
    private static final String FILE_NAME = "gameSuspend.tmp";

    /** 开局 FEN */
    private String initialFen = "";

    /** 已走着法列表（引擎坐标，如 e0e1） */
    private List<String> moves = new ArrayList<>();

    /** 当前行棋方是否为红方 */
    private boolean redGo = true;

    /** 引擎执子：red / black */
    private String engineSide = "black";

    /** 局时档位名（GameConfig.ClockTier.name()） */
    private String clockTier = "";

    /** 封棋时双方剩余局时（毫秒） */
    private long redMain;
    private long blackMain;

    // ===================== 持久化 =====================

    private static File file() {
        return new File(FILE_NAME);
    }

    public static boolean exists() {
        return file().isFile();
    }

    public static void save(GameSnapshot snapshot) {
        try (ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(file()))) {
            os.writeObject(snapshot);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static GameSnapshot load() {
        File f = file();
        if (!f.isFile()) {
            return null;
        }
        try (ObjectInputStream is = new ObjectInputStream(new FileInputStream(f))) {
            Object obj = is.readObject();
            if (obj instanceof GameSnapshot snapshot) {
                return snapshot;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void delete() {
        File f = file();
        if (f.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    // ===================== getter / setter =====================

    public String getInitialFen() {
        return initialFen;
    }

    public void setInitialFen(String initialFen) {
        this.initialFen = initialFen;
    }

    public List<String> getMoves() {
        return moves;
    }

    public void setMoves(List<String> moves) {
        this.moves = moves;
    }

    public boolean isRedGo() {
        return redGo;
    }

    public void setRedGo(boolean redGo) {
        this.redGo = redGo;
    }

    public String getEngineSide() {
        return engineSide;
    }

    public void setEngineSide(String engineSide) {
        this.engineSide = engineSide;
    }

    public String getClockTier() {
        return clockTier;
    }

    public void setClockTier(String clockTier) {
        this.clockTier = clockTier;
    }

    public long getRedMain() {
        return redMain;
    }

    public void setRedMain(long redMain) {
        this.redMain = redMain;
    }

    public long getBlackMain() {
        return blackMain;
    }

    public void setBlackMain(long blackMain) {
        this.blackMain = blackMain;
    }
}
