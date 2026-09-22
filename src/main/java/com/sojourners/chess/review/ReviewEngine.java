package com.sojourners.chess.review;

import com.sojourners.chess.model.EngineConfig;
import com.sojourners.chess.util.PathUtils;
import com.sojourners.chess.util.StringUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 复盘专用的同步引擎客户端。
 * <p>
 * 与 {@link com.sojourners.chess.enginee.Engine} 不同，本类会独立启动一个引擎进程，
 * 每次分析都同步等待 bestmove 返回，便于逐个局面顺序复盘，且不会影响主界面的引擎。
 */
public class ReviewEngine implements Closeable {

    private final Process process;

    private final BufferedReader reader;

    private final BufferedWriter writer;

    private final String protocol;

    public ReviewEngine(EngineConfig ec) throws IOException {
        this.protocol = StringUtils.isEmpty(ec.getProtocol()) ? "uci" : ec.getProtocol();
        this.process = Runtime.getRuntime().exec(ec.getPath(), null, PathUtils.getParentDir(ec.getPath()));
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

        send(protocol);
        waitFor("ucci".equals(protocol) ? "ucciok" : "uciok");

        for (Map.Entry<String, String> entry : ec.getOptions().entrySet()) {
            // 复盘只取第一路着法，MultiPV 稍后单独设置为 1
            if ("MultiPV".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            setOption(entry.getKey(), entry.getValue());
        }
        boolean hasMultiPv = ec.getOptions().keySet().stream()
                .anyMatch(key -> "MultiPV".equalsIgnoreCase(key));
        if (hasMultiPv) {
            setOption("MultiPV", "1");
        }
        send("isready");
        waitFor("readyok");
    }

    private void setOption(String name, String value) {
        if ("ucci".equals(protocol)) {
            send("setoption " + name + " " + value);
        } else {
            send("setoption name " + name + " value " + value);
        }
    }

    private void send(String cmd) {
        try {
            writer.write(cmd + System.lineSeparator());
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException("发送引擎指令失败: " + cmd, e);
        }
    }

    private void waitFor(String token) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains(token)) {
                return;
            }
        }
        throw new IOException("引擎未响应: " + token);
    }

    /**
     * 分析一个局面并返回最佳着法与分值。
     *
     * @param fenCode 起始局面
     * @param moves   起始局面之后已经走过的着法（引擎着法）
     * @param timeMs  每步分析时间（毫秒）
     */
    public ReviewEval analyze(String fenCode, List<String> moves, long timeMs) throws IOException {
        StringBuilder sb = new StringBuilder("position fen ").append(fenCode);
        if (moves != null && !moves.isEmpty()) {
            sb.append(" moves");
            for (String move : moves) {
                sb.append(' ').append(move);
            }
        }
        send(sb.toString());
        send("go movetime " + timeMs);

        ReviewEval eval = new ReviewEval();
        boolean finished = false;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("bestmove")) {
                String[] arr = line.split("\\s+");
                if (arr.length >= 2 && !"(none)".equals(arr[1])) {
                    eval.setBestMove(arr[1]);
                }
                finished = true;
                break;
            }
            if (line.startsWith("info")) {
                parseInfo(line, eval);
            }
        }
        if (!finished) {
            throw new IOException("引擎未返回着法");
        }
        return eval;
    }

    private void parseInfo(String line, ReviewEval eval) {
        String[] arr = line.split("\\s+");
        if (arr.length > 1 && "string".equals(arr[1])) {
            return;
        }
        int multipv = 1;
        for (int i = 0; i < arr.length; i++) {
            switch (arr[i]) {
                case "multipv":
                    if (i + 1 < arr.length) {
                        multipv = parseInt(arr[i + 1], 1);
                    }
                    break;
                case "score":
                    if (i + 2 < arr.length && multipv == 1) {
                        String type = arr[i + 1];
                        int value = parseInt(arr[i + 2], Integer.MIN_VALUE);
                        if (value != Integer.MIN_VALUE) {
                            eval.setScoreType(type);
                            eval.setScoreValue(value);
                            eval.setMate("mate".equalsIgnoreCase(type));
                            eval.setScored(true);
                        }
                    }
                    break;
                case "pv":
                    if (multipv == 1) {
                        List<String> pv = new ArrayList<>();
                        for (int j = i + 1; j < arr.length; j++) {
                            pv.add(arr[j]);
                        }
                        eval.setPv(pv);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private int parseInt(String str, int def) {
        try {
            return Integer.parseInt(str);
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public void close() {
        try {
            send("quit");
        } catch (Exception ignore) {
            // ignore
        }
        try {
            process.destroy();
        } catch (Exception ignore) {
            // ignore
        }
        try {
            reader.close();
        } catch (Exception ignore) {
            // ignore
        }
        try {
            writer.close();
        } catch (Exception ignore) {
            // ignore
        }
    }
}
