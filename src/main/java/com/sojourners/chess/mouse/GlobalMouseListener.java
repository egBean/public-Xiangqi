package com.sojourners.chess.mouse;


import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.mouse.NativeMouseEvent;
import org.jnativehook.mouse.NativeMouseInputListener;

import java.util.logging.Level;
import java.util.logging.Logger;

public class GlobalMouseListener implements NativeMouseInputListener {



    private MouseListenCallBack cb;

    private static boolean flag;

    public void nativeMouseClicked(NativeMouseEvent e) {
        System.out.println("Mouse Clicked: " + e.getClickCount());

        this.cb.mouseClick();
    }

    public void nativeMousePressed(NativeMouseEvent e) {

    }

    public void nativeMouseReleased(NativeMouseEvent e) {

    }

    public void nativeMouseMoved(NativeMouseEvent e) {

    }

    public void nativeMouseDragged(NativeMouseEvent e) {

    }

    public GlobalMouseListener(MouseListenCallBack cb) {
        this.cb = cb;
    }

    public void startListenMouse() throws NativeHookException {
        if(!flag){
            Logger logger = Logger.getLogger("org.jnativehook");
            logger.setLevel(Level.WARNING);
            logger.setUseParentHandlers(false);
            flag = true;
        }

        GlobalScreen.registerNativeHook();
        GlobalScreen.addNativeMouseListener(this);
    }
    public void stopListenMouse() throws NativeHookException {
        GlobalScreen.removeNativeMouseListener(this);
        GlobalScreen.unregisterNativeHook();
    }

}