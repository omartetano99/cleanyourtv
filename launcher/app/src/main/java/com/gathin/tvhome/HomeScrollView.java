package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;

/** Shelves choose their scroll anchor, so Android cannot scroll their headings offscreen. */
public final class HomeScrollView extends ScrollView {
    public HomeScrollView(Context context, AttributeSet attrs) { super(context, attrs); }
    @Override public void requestChildFocus(View child, View focused) {
        // ViewGroup still dispatches focus; ScrollView's automatic reveal is suppressed below.
        super.requestChildFocus(child, focused);
    }
    @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) { return 0; }
    @Override public boolean requestChildRectangleOnScreen(View child, Rect rect, boolean immediate) { return false; }
}
