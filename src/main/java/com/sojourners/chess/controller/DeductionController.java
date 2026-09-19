package com.sojourners.chess.controller;

import com.sojourners.chess.board.BaseBoardRender;
import com.sojourners.chess.board.ChessBoard;
import com.sojourners.chess.board.CustomBoardRender;
import com.sojourners.chess.board.DefaultBoardRender;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.util.ClipboardUtils;
import com.sojourners.chess.util.XiangqiUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * 推演窗口。
 * 从当前局面弹出一个独立的小棋盘，可自由走子推演，支持回退/前进/重置等。
 * 这里不使用 {@link ChessBoard}（其棋局与渲染为静态，会与主棋盘冲突），
 * 而是维护自己的局面并用独立的 {@link BaseBoardRender} 直接绘制。
 */
public class DeductionController {

    @FXML
    private Canvas canvas;
    @FXML
    private Button undoButton;
    @FXML
    private Button redoButton;
    @FXML
    private ComboBox<String> sizeComboBox;

    /** 当前推演局面 */
    private char[][] board;
    /** 进入推演时的初始局面 */
    private char[][] initialBoard;
    private boolean initialRedGo;

    private BaseBoardRender boardRender;

    /** 棋盘大小可选值（与下拉框顺序一致） */
    private static final String[] SIZE_NAMES = {"小", "中", "大", "特大"};
    private static final ChessBoard.BoardSize[] SIZE_VALUES = {
            ChessBoard.BoardSize.SMALL_BOARD,
            ChessBoard.BoardSize.MIDDLE_BOARD,
            ChessBoard.BoardSize.BIG_BOARD,
            ChessBoard.BoardSize.LARGE_BOARD
    };

    private ChessBoard.BoardSize boardSize = ChessBoard.BoardSize.MIDDLE_BOARD;

    /** 已选中的棋子 */
    private ChessBoard.Point remark;
    /** 上一步走棋（用于标记） */
    private ChessBoard.Step prevStep;

    /** 当前行棋方：true 红方 */
    private boolean redGo;
    /** 是否翻转 */
    private boolean isReverse;

    private final Deque<State> undoStack = new ArrayDeque<>();
    private final Deque<State> redoStack = new ArrayDeque<>();

    /** 局面快照，用于回退/前进 */
    private static class State {
        final char[][] board;
        final ChessBoard.Step prevStep;
        final boolean redGo;

        State(char[][] board, ChessBoard.Step prevStep, boolean redGo) {
            this.board = copy(board);
            this.prevStep = prevStep;
            this.redGo = redGo;
        }
    }

    /**
     * 初始化推演棋盘。
     *
     * @param src       来源局面
     * @param redGo     来源局面行棋方
     * @param isReverse 是否与主棋盘一致地翻转
     */
    public void init(char[][] src, boolean redGo, boolean isReverse) {
        if (this.boardRender == null) {
            this.boardRender = !Objects.equals(Properties.getInstance().getBoardStyle(), "default")
                    ? new CustomBoardRender(canvas, Properties.getInstance().getBoardStyle())
                    : new DefaultBoardRender(canvas);
        }
        if (sizeComboBox.getItems().isEmpty()) {
            sizeComboBox.getItems().addAll(SIZE_NAMES);
        }
        resetTo(src, redGo, isReverse);
        int index = 0;
        for (int i = 0; i < SIZE_VALUES.length; i++) {
            if (SIZE_VALUES[i] == boardSize) {
                index = i;
                break;
            }
        }
        sizeComboBox.getSelectionModel().select(index);
    }

    /**
     * 切换棋盘大小后重新绘制，并让窗口自适应新尺寸。
     */
    @FXML
    public void sizeComboBoxChanged(ActionEvent event) {
        int index = sizeComboBox.getSelectionModel().getSelectedIndex();
        if (index < 0 || index >= SIZE_VALUES.length) {
            return;
        }
        boardSize = SIZE_VALUES[index];
        if (board != null) {
            paint();
        }
        resizeStage();
    }

    /**
     * 让窗口重新适配画布大小。
     */
    private void resizeStage() {
        if (canvas.getScene() != null && canvas.getScene().getWindow() instanceof Stage stage) {
            stage.sizeToScene();
        }
    }

    /**
     * 将推演棋盘重置到指定局面，并清空所有走棋数据（回退/前进历史）。
     * 打开推演窗口和主棋盘走子同步时都会调用。
     */
    public void resetTo(char[][] src, boolean redGo, boolean isReverse) {
        this.initialBoard = copy(src);
        this.initialRedGo = redGo;
        this.board = copy(src);
        this.redGo = redGo;
        this.isReverse = isReverse;
        this.remark = null;
        this.prevStep = null;
        this.undoStack.clear();
        this.redoStack.clear();
        updateButtons();
        paint();
    }

    private static char[][] copy(char[][] src) {
        char[][] dst = new char[src.length][];
        for (int i = 0; i < src.length; i++) {
            dst[i] = src[i].clone();
        }
        return dst;
    }

    private void paint() {
        boardRender.paint(boardSize, board, prevStep, remark, false,
                false, null, isReverse, Properties.getInstance().isShowNumber(), false, null);
    }

    @FXML
    public void canvasClick(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        int padding = boardRender.getPadding(boardSize);
        int piece = boardRender.getPieceSize(boardSize);
        int col = (int) Math.floor((event.getX() - padding) / piece);
        int row = (int) Math.floor((event.getY() - padding) / piece);
        if (isReverse) {
            col = 8 - col;
            row = 9 - row;
        }
        if (col < 0 || col > 8 || row < 0 || row > 9) {
            return;
        }

        if (remark != null) {
            // 点击己方棋子则重新选择
            if (board[row][col] != ' ' && XiangqiUtils.isRed(board[row][col]) == XiangqiUtils.isRed(board[remark.getY()][remark.getX()])) {
                remark = new ChessBoard.Point(col, row);
                paint();
                return;
            }
            // 走子
            if (XiangqiUtils.canGo(board, remark.getY(), remark.getX(), row, col)) {
                makeMove(remark.getY(), remark.getX(), row, col);
            }
        } else if (board[row][col] != ' ' && XiangqiUtils.isRed(board[row][col]) == redGo) {
            remark = new ChessBoard.Point(col, row);
            paint();
        }
    }

    private void makeMove(int row1, int col1, int row2, int col2) {
        boolean isRed = XiangqiUtils.isRed(board[row1][col1]);
        char captured = board[row2][col2];
        // 先快照走子前的局面，供回退使用
        State before = new State(board, prevStep, redGo);
        board[row2][col2] = board[row1][col1];
        board[row1][col1] = ' ';
        // 不可送将
        if (XiangqiUtils.isJiang(board, isRed)) {
            board[row1][col1] = board[row2][col2];
            board[row2][col2] = captured;
            return;
        }

        // 合法着法：记录走子前的局面并清空重做栈
        undoStack.push(before);
        redoStack.clear();
        prevStep = new ChessBoard.Step(new ChessBoard.Point(col1, row1), new ChessBoard.Point(col2, row2));
        redGo = !redGo;
        remark = null;
        updateButtons();
        paint();
    }

    private void restore(State state) {
        this.board = copy(state.board);
        this.prevStep = state.prevStep;
        this.redGo = state.redGo;
        this.remark = null;
    }

    @FXML
    public void undoButtonClick(ActionEvent event) {
        if (undoStack.isEmpty()) {
            return;
        }
        redoStack.push(new State(board, prevStep, redGo));
        restore(undoStack.pop());
        updateButtons();
        paint();
    }

    @FXML
    public void redoButtonClick(ActionEvent event) {
        if (redoStack.isEmpty()) {
            return;
        }
        undoStack.push(new State(board, prevStep, redGo));
        restore(redoStack.pop());
        updateButtons();
        paint();
    }

    @FXML
    public void resetButtonClick(ActionEvent event) {
        if (board == null) {
            return;
        }
        // 回到进入推演（或最近一次同步）时的局面，并清空之前的走棋数据
        this.board = copy(initialBoard);
        this.redGo = initialRedGo;
        this.prevStep = null;
        this.remark = null;
        this.undoStack.clear();
        this.redoStack.clear();
        updateButtons();
        paint();
    }

    @FXML
    public void copyFenButtonClick(ActionEvent event) {
        ClipboardUtils.setText(ChessBoard.fenCode(board, redGo));
    }

    @FXML
    public void closeButtonClick(ActionEvent event) {
        ((Stage) canvas.getScene().getWindow()).close();
    }

    private void updateButtons() {
        undoButton.setDisable(undoStack.isEmpty());
        redoButton.setDisable(redoStack.isEmpty());
    }
}
