package com.sojourners.chess.board;

import com.sojourners.chess.util.PathUtils;
import javafx.scene.canvas.Canvas;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CustomBoardRender extends BaseBoardRender {

    private Image bgImage;
    private Image maskImage;
    private Image mask2Image;
    private Map<Character, Image> map;

    public CustomBoardRender(Canvas canvas,String skin) {
        super(canvas);


        File skinDir = findSkinDir(skin);
        String baseDir = skinDir != null ? skinDir.getAbsolutePath()
                : PathUtils.getJarPath() + "/ui";   // 找不到就退回默认

        this.bgImage   = loadImage(baseDir, "board.png");
        this.maskImage = loadImage(baseDir, "mask.png");
        this.mask2Image = loadImage(baseDir, "mask2.png");

        map = new HashMap<>();
        map.put('r', loadImage(baseDir, "br.png"));
        map.put('n', loadImage(baseDir, "bn.png"));
        map.put('b', loadImage(baseDir, "bb.png"));
        map.put('a', loadImage(baseDir, "ba.png"));
        map.put('k', loadImage(baseDir, "bk.png"));
        map.put('c', loadImage(baseDir, "bc.png"));
        map.put('p', loadImage(baseDir, "bp.png"));

        map.put('R', loadImage(baseDir, "rr.png"));
        map.put('N', loadImage(baseDir, "rn.png"));
        map.put('B', loadImage(baseDir, "rb.png"));
        map.put('A', loadImage(baseDir, "ra.png"));
        map.put('K', loadImage(baseDir, "rk.png"));
        map.put('C', loadImage(baseDir, "rc.png"));
        map.put('P', loadImage(baseDir, "rp.png"));
    }

    /** 从 jarPath 往上逐级找 skin/<皮肤名>，覆盖 IDE 和 jar 两种情况 */
    private File findSkinDir(String skin) {
        // 1. 工作目录下的 skin（IDE 默认工作目录 = 项目根目录）
        File dir = new File("skin", skin);
        if (dir.isDirectory()) {
            return dir;
        }
        return null;
    }

    /** 从指定目录加载图片 */
    private Image loadImage(String baseDir, String fileName) {
        File f = new File(baseDir, fileName);
        if (!f.exists()) {
            // 该皮肤没有这张图，退回默认 ui 目录
            f = new File(PathUtils.getJarPath() + "/ui", fileName);
        }
        return new Image(f.toURI().toString());
    }

    @Override
    public void drawBackgroundImage(double width, double height) {
        double xPadding = this.getBoardOffsetX();
        if(xPadding == 0){
            gc.drawImage(bgImage, 0, 0, width, height);
            return;
        }
        double imgW = bgImage.getWidth();
        double imgH = bgImage.getHeight();

        // 1. 反推屏幕每格大小（宽高只取一个即可）
        double cellSize = width / (9.0 + 1.0 / 3);

        // 2. 原图每格大小
        double imgCellSize = (imgW - 2 * xPadding) / 8.0;

        // 3. 统一缩放比例
        double scale = cellSize / imgCellSize;

        // 4. 让原图棋盘中心对齐到画布中心
        double drawX = width  / 2.0 - (imgW / 2.0) * scale;
        double drawY = height / 2.0 - (imgH / 2.0) * scale;

        // 5. 绘制（多余边距自动移出画布外）
        gc.drawImage(bgImage, drawX, drawY, imgW * scale, imgH * scale);

        //此处本质就是先计算出原图缩放值，让原图的棋盘四个角与目标棋盘线的四个角能完全重叠。然后再算出缩放后的原图，起始位置偏移量。也就是drawX，drawY
    }

    @Override
    public void drawCenterText(int pos, int piece, ChessBoard.BoardSize style) {

    }

    @Override
    public void drawBoardLine(int pos, int padding, int piece, boolean isReverse, ChessBoard.BoardSize style) {

    }


    @Override
    public void drawPieces(int pos, int piece, char[][] board, boolean isReverse, ChessBoard.BoardSize style, boolean pieceShadow, double pieceScale) {
        // 棋子半径跟着 scale 缩放
        int r = (int) Math.round((piece - piece / 16) / 2 * pieceScale);

        // 图片自带阴影时，视觉中心不在图片几何中心，需要偏移
        double offX = r * this.getPieceOffsetX();
        double offY = r * this.getPieceOffsetY();

        if (!pieceShadow) {
            for (int i = 0; i < board.length; i++) {
                for (int j = 0; j < board[0].length; j++) {
                    Image img = map.get(board[i][j]);
                    if (img != null) {
                        int x = pos + piece * getReverseX(j, isReverse);
                        int y = pos + piece * getReverseY(i, isReverse);
                        gc.drawImage(img, x - r + offX, y - r + offY, 2 * r, 2 * r);
                    }
                }
            }
            return;
        }

        DropShadow ambient = new DropShadow();
        //radius表示的是阴影总宽度。比如棋子r=1，那r*0.18则表示阴影半径为1.18.阴影距离棋子边缘宽度为0.18。
        ambient.setRadius(r * 0.18);
        ambient.setOffsetX(r * 0.21);
        ambient.setOffsetY(r * 0.24);
        //spread表示实心纯黑阴影占阴影宽度的比例。
        ambient.setSpread(0.0);
        ambient.setColor(Color.rgb(45, 30, 15, 0.45));   // 不动

        DropShadow contact = new DropShadow();
        contact.setRadius(r * 0.10);
        contact.setOffsetX(r * 0.03);
        contact.setOffsetY(r * 0.05);
        contact.setSpread(0.25);
        contact.setColor(Color.rgb(35, 20, 10, 0.60));
        contact.setInput(ambient);

        gc.save();
        gc.setEffect(contact);
        for (int i = 0; i < board.length; i++) {
            for (int j = 0; j < board[0].length; j++) {
                Image img = map.get(board[i][j]);
                if (img == null) continue;
                int x = pos + piece * getReverseX(j, isReverse);
                int y = pos + piece * getReverseY(i, isReverse);
                gc.drawImage(img, x - r + offX, y - r + offY, 2 * r, 2 * r);
            }
        }

        gc.restore();
    }

    @Override
    public Color getBackgroundColor() {
        int centerX = (int) (bgImage.getWidth() / 2);
        int centerY = (int) (bgImage.getHeight() / 2);
        return this.bgImage.getPixelReader().getColor(centerX, centerY);
    }

    @Override
    public void drawStepRemark(int pos, int piece, int x, int y, boolean isPrevStep, boolean isReverse, ChessBoard.BoardSize style) {

        int r = (piece - piece / 32) / 2;

        x = pos + piece * getReverseX(x, isReverse);
        y = pos + piece * getReverseY(y, isReverse);

        Image img = isPrevStep ? mask2Image : maskImage;
        gc.drawImage(img, x - r, y - r, 2 * r, 2 * r);
    }
}
