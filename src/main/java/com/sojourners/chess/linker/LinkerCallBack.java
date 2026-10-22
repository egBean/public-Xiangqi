package com.sojourners.chess.linker;

public interface LinkerCallBack {

    void linkerInitChessBoard(String fenCode, boolean isReverse);

    char[][] getEngineBoard();

    boolean isThinking();

    boolean isWatchMode();

    void linkerMove(int x1, int y1, int x2, int y2);

    /**
     * 扫描线程异常退出时回调，用于关闭连线状态
     */
    void linkerError(Throwable e);
}
