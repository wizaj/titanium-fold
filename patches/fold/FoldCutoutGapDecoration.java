// Copyright 2026 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management.vertical_tabs;

import android.graphics.Rect;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;

import androidx.recyclerview.widget.RecyclerView;

import org.chromium.build.annotations.NullMarked;
import org.chromium.chrome.tab_ui.R;

import java.util.List;

/**
 * Leaves a gap in the vertical tab list so favicons and close buttons are not drawn under a
 * display cutout (e.g. the Fold inner punch-hole on the mid-right edge).
 */
@NullMarked
public class FoldCutoutGapDecoration extends RecyclerView.ItemDecoration {
    private static final int EXTRA_GAP_DP = 4;

    private final int mEstimatedItemHeightPx;
    private final int mExtraGapPx;
    private boolean mHasGap;
    private int mCutoutTop;
    private int mCutoutBottom;

    public FoldCutoutGapDecoration(RecyclerView recyclerView) {
        mEstimatedItemHeightPx =
                recyclerView
                                .getResources()
                                .getDimensionPixelSize(R.dimen.vertical_tab_item_collapsed_size)
                        + recyclerView
                                .getResources()
                                .getDimensionPixelSize(R.dimen.vertical_tab_item_margin_bottom);
        mExtraGapPx =
                Math.round(EXTRA_GAP_DP * recyclerView.getResources().getDisplayMetrics().density);

        recyclerView.setOnApplyWindowInsetsListener(
                (v, insets) -> {
                    updateCutout(v, insets);
                    v.invalidate();
                    recyclerView.invalidateItemDecorations();
                    return insets;
                });
        recyclerView.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    WindowInsets insets = v.getRootWindowInsets();
                    if (insets != null) {
                        updateCutout(v, insets);
                        recyclerView.invalidateItemDecorations();
                    }
                });
    }

    private void updateCutout(View view, WindowInsets insets) {
        mHasGap = false;
        mCutoutTop = 0;
        mCutoutBottom = 0;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        if (view.getWidth() == 0 || view.getHeight() == 0) return;

        DisplayCutout cutout = insets.getDisplayCutout();
        if (cutout == null) return;
        List<Rect> rects = cutout.getBoundingRects();
        if (rects == null || rects.isEmpty()) return;

        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        Rect viewBounds = new Rect(loc[0], loc[1], loc[0] + view.getWidth(), loc[1] + view.getHeight());

        for (Rect screenRect : rects) {
            if (!Rect.intersects(screenRect, viewBounds)) continue;
            mCutoutTop = screenRect.top - loc[1] - mExtraGapPx;
            mCutoutBottom = screenRect.bottom - loc[1] + mExtraGapPx;
            mHasGap = mCutoutBottom > mCutoutTop;
            break;
        }
    }

    @Override
    public void getItemOffsets(
            Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        outRect.set(0, 0, 0, 0);
        if (!mHasGap) return;

        int pos = parent.getChildAdapterPosition(view);
        if (pos == RecyclerView.NO_POSITION) return;

        RecyclerView.LayoutManager layoutManager = parent.getLayoutManager();
        int y = parent.getPaddingTop();
        for (int i = 0; i < pos; i++) {
            View child = layoutManager != null ? layoutManager.findViewByPosition(i) : null;
            if (child != null && child.getHeight() > 0) {
                y += child.getHeight();
            } else {
                y += mEstimatedItemHeightPx;
            }
        }

        int height = view.getHeight() > 0 ? view.getHeight() : mEstimatedItemHeightPx;
        if (y <= mCutoutTop && y + height > mCutoutTop) {
            outRect.top = Math.max(0, mCutoutBottom - y);
        }
    }
}
