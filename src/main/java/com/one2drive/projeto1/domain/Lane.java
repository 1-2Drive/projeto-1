package com.one2drive.projeto1.domain;

public enum Lane {
    LEFT,
    CENTER,
    RIGHT;

    public Lane left() {
        return switch (this) {
            case LEFT -> LEFT;
            case CENTER -> LEFT;
            case RIGHT -> CENTER;
        };
    }

    public Lane right() {
        return switch (this) {
            case LEFT -> CENTER;
            case CENTER -> RIGHT;
            case RIGHT -> RIGHT;
        };
    }
}
