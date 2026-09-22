package com.sojourners.chess.media;

import javafx.scene.media.AudioClip;

import java.io.File;

public class SoundPlayer {
    private AudioClip pick;

    /**
     * 新开局
     */
    private AudioClip begin;

    /**
     * 悔棋
     */
    private AudioClip undo;

    private AudioClip move;

    private AudioClip eat;

    private AudioClip check;

    private AudioClip over;

    public SoundPlayer(String pickSound, String moveSound, String eatSound, String checkSound,
                       String overSound,String beginSound,String undoSound) {
        pick = new AudioClip(new File(pickSound).toURI().toString());
        move = new AudioClip(new File(moveSound).toURI().toString());
        eat = new AudioClip(new File(eatSound).toURI().toString());
        check = new AudioClip(new File(checkSound).toURI().toString());
        over = new AudioClip(new File(overSound).toURI().toString());
        begin = new AudioClip(new File(beginSound).toURI().toString());
        undo = new AudioClip(new File(undoSound).toURI().toString());
    }

    public void eat() {
        eat.play();
    }

    public void pick() {
        pick.play();
    }

    public void move() {
        move.play();
    }

    public void check() {
        check.play();
    }

    public void over() {
        over.play();
    }

    public void begin() {
        begin.play();
    }

    public void undo() {
        undo.play();
    }
}
