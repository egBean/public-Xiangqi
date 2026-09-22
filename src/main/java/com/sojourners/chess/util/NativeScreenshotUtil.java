package com.sojourners.chess.util;

import com.sun.jna.Memory;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinGDI;
import com.sun.jna.platform.win32.WinNT;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

public class NativeScreenshotUtil {

    private static final int SRCCOPY = 0x00CC0020;
    private static final int CAPTUREBLT = 0x40000000;

    public static BufferedImage capture(Rectangle bounds) {
        // 1. 获取桌面窗口句柄和设备上下文
        WinDef.HWND hwndDesktop = User32.INSTANCE.GetDesktopWindow();
        WinDef.HDC hdcWindow = User32.INSTANCE.GetDC(hwndDesktop);
        if (hdcWindow == null) {
            System.err.println("GetDC failed.");
            return null;
        }

        WinDef.HDC hdcMemDC = null;
        WinDef.HBITMAP hBitmap = null;
        WinNT.HANDLE hOldBitmap = null;

        try {
            // 2. 创建与屏幕兼容的内存 DC
            hdcMemDC = GDI32.INSTANCE.CreateCompatibleDC(hdcWindow);
            if (hdcMemDC == null) {
                System.err.println("CreateCompatibleDC failed.");
                return null;
            }

            // 3. 创建与屏幕兼容的位图
            hBitmap = GDI32.INSTANCE.CreateCompatibleBitmap(hdcWindow, bounds.width, bounds.height);
            if (hBitmap == null) {
                System.err.println("CreateCompatibleBitmap failed.");
                return null;
            }

            // 4. 将位图选入内存 DC
            hOldBitmap = GDI32.INSTANCE.SelectObject(hdcMemDC, hBitmap);

            // 5. 执行位块传输 (BitBlt)
            boolean bltSuccess = GDI32.INSTANCE.BitBlt(
                    hdcMemDC, 0, 0, bounds.width, bounds.height,
                    hdcWindow, bounds.x, bounds.y,
                    SRCCOPY | CAPTUREBLT
            );

            if (!bltSuccess) {
                System.err.println("BitBlt failed.");
                return null;
            }

            // 6. 将位图数据提取为 BufferedImage
            return bitmapToBufferedImage(hdcMemDC, hBitmap, bounds.width, bounds.height);

        } finally {
            // 7. 严格按顺序释放资源，防止泄漏
            if (hdcMemDC != null) {
                if (hOldBitmap != null) {
                    GDI32.INSTANCE.SelectObject(hdcMemDC, hOldBitmap); // 恢复原始位图
                }
                GDI32.INSTANCE.DeleteDC(hdcMemDC);
            }
            if (hBitmap != null) {
                GDI32.INSTANCE.DeleteObject(hBitmap);
            }
            if (hdcWindow != null) {
                User32.INSTANCE.ReleaseDC(hwndDesktop, hdcWindow); // 释放屏幕 DC
            }
        }
    }

    private static BufferedImage bitmapToBufferedImage(WinDef.HDC hdc, WinDef.HBITMAP hBitmap, int width, int height) {
        WinGDI.BITMAPINFO bmi = new WinGDI.BITMAPINFO();
        bmi.bmiHeader.biSize = 40;
        bmi.bmiHeader.biWidth = width;
        bmi.bmiHeader.biHeight = -height; // 负数表示自上而下
        bmi.bmiHeader.biPlanes = 1;
        bmi.bmiHeader.biBitCount = 32;
        bmi.bmiHeader.biCompression = WinGDI.BI_RGB;

        Memory buffer = new Memory((long) width * height * 4);
        int result = GDI32.INSTANCE.GetDIBits(hdc, hBitmap, 0, height, buffer, bmi, WinGDI.DIB_RGB_COLORS);
        if (result == 0) {
            System.err.println("GetDIBits failed.");
            return null;
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] imagePixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        int[] rawPixels = buffer.getIntArray(0, width * height);
        for (int i = 0; i < rawPixels.length; i++) {
            int pixel = rawPixels[i];
            imagePixels[i] = (pixel & 0xFFFFFF); // 直接保留 RGB 分量
        }
        return image;
    }
}