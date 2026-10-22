package com.gathin.tvhome;

import android.view.View;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** Focus an offscreen card after layout, without waiting for a scroll event that may never fire. */
final class FocusRequests {
    static void focus(RecyclerView row, int position) {
        Object token = new Object();
        row.setTag(token);
        RecyclerView.ViewHolder h = row.findViewHolderForAdapterPosition(position);
        if (h != null) { h.itemView.findViewById(R.id.card).requestFocus(); return; }
        if (row.getLayoutManager() instanceof LinearLayoutManager) {
            ((LinearLayoutManager) row.getLayoutManager()).scrollToPositionWithOffset(position, row.getPaddingLeft());
        } else row.scrollToPosition(position);
        row.post(new Runnable() {
            int attempts;
            @Override public void run() {
                if (row.getTag() != token || !row.isAttachedToWindow()) return;
                RecyclerView.ViewHolder holder = row.findViewHolderForAdapterPosition(position);
                if (holder != null) {
                    View card = holder.itemView.findViewById(R.id.card);
                    if (card != null) card.requestFocus();
                } else if (++attempts < 8) row.postOnAnimation(this);
            }
        });
    }
}
