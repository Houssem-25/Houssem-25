package com.scanmart.pos;

import android.content.Context;
import android.widget.FrameLayout;

abstract class Panel extends FrameLayout {
    Panel(Context c) {
        super(c);
    }

    /** Called when the panel becomes the visible tab (or the activity resumes on it). */
    void onShow() {
    }

    /** Called when the panel stops being visible (or the activity pauses). */
    void onHide() {
    }
}
