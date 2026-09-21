package com.sojourners.chess;

import javafx.application.Application;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class Main {

    public static void main(String[] args) {
        initLog();
        System.setProperty("sun.java2d.dpiaware", "true");
        Application.launch(App.class);
    }

    private static void initLog() {
        try {
            File logDir = new File("log");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            // 第二个参数 false = 每次启动覆盖旧日志
            PrintStream fileOut = new PrintStream(
                    new FileOutputStream(new File(logDir, "app.log"), false),
                    true,
                    StandardCharsets.UTF_8
            );

            // 错误日志（System.err）做双写：控制台 + 文件
            PrintStream consoleErr = System.err;
            System.setErr(new TeePrintStream(consoleErr, fileOut));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 双写流：内容同时写向控制台和文件
     */
    static class TeePrintStream extends PrintStream {
        private final PrintStream second;

        TeePrintStream(PrintStream first, PrintStream second) {
            super(first);
            this.second = second;
        }

        @Override
        public void write(int b) {
            super.write(b);
            second.write(b);
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            super.write(buf, off, len);
            second.write(buf, off, len);
        }

        @Override
        public void flush() {
            super.flush();
            second.flush();
        }

        @Override
        public void close() {
            super.close();
            second.close();
        }
    }
}