/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import helium314.keyboard.keyboard.Key;
import helium314.keyboard.keyboard.KeyboardTypeface;
import helium314.keyboard.latin.R;
import helium314.keyboard.latin.common.StringUtilsKt;
import helium314.keyboard.latin.settings.Settings;

import java.util.HashSet;

/** The pop up key preview view. */
// Android Studio complains about TextView, but we're not using tint or auto-size that should be the relevant differences
public class KeyPreviewView extends TextView {
    public static final int POSITION_MIDDLE = 0;
    public static final int POSITION_LEFT = 1;
    public static final int POSITION_RIGHT = 2;

    private final Rect mBackgroundPadding = new Rect();
    private static final HashSet<String> sNoScaleXTextSet = new HashSet<>();
    /**
     * How much taller than the key its preview may get. The preview is meant to be a slightly
     * enlarged copy of the key, which is what upstream produces at the default font scale.
     */
    private static final float MAX_HEIGHT_RATIO = 1.1f;
    /** Size of the key this preview stands for; the preview is meant to look like an enlarged copy of it. */
    private int mKeyWidth;
    private int mKeyHeight;

    public KeyPreviewView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyPreviewView(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setGravity(Gravity.CENTER);
    }

    public void setPreviewVisual(final Key key, final KeyboardIconsSet iconsSet, final KeyDrawParams drawParams) {
        mKeyWidth = key.getDrawWidth();
        mKeyHeight = key.getHeight();
        // What we show as preview should match what we show on a key top in onDraw().
        if (key.getIconName() != null) {
            setCompoundDrawables(key.getPreviewIcon(iconsSet), null, null, null);
            setText(null);
            return;
        }

        setCompoundDrawables(null, null, null, null);
        setTextColor(drawParams.mPreviewTextColor);
        setPreviewTextSize(key.selectPreviewTextSize(drawParams)
                * Settings.getValues().mFontSizeMultiplier);
        KeyboardTypeface.applyToTextView(this, key.getPreviewLabel(), key.selectPreviewTypeface(drawParams));
        // TODO Should take care of temporaryShiftLabel here.
        setTextAndScaleX(key.getPreviewLabel());
        applyKeyProportions();
    }

    /**
     * The font scale multiplies the preview's text size, but the key it stands for does not grow
     * with it - so at 150 % the preview ends up 1.6 times the size of its own key instead of the
     * slightly enlarged copy it is meant to be. The key itself limits its label the same way (see
     * MAX_LABEL_RATIO in KeyboardView); the preview simply never did.
     */
    private void setPreviewTextSize(final float wantedSize) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, wantedSize);
        if (mKeyHeight <= 0) {
            return;
        }
        final Paint.FontMetrics metrics = getPaint().getFontMetrics();
        final float labelHeight = metrics.bottom - metrics.top;
        final float maxHeight = mKeyHeight * MAX_HEIGHT_RATIO;
        if (labelHeight > maxHeight) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, wantedSize * maxHeight / labelHeight);
        }
    }

    /**
     * The preview stands for an enlarged key, but its width comes from a fixed size declared in
     * the background drawable while its height follows the label - so the larger the font scale,
     * the narrower the preview gets: measured 70x226 visible at 150 % for a key of 96x138. Ask
     * for a width that gives the visible part the key's proportions. The height is never touched:
     * limiting it would crop the very glyph the preview exists to show.
     */
    private void applyKeyProportions() {
        if (mKeyWidth <= 0 || mKeyHeight <= 0) {
            setMinWidth(0);
            return;
        }
        final Paint.FontMetrics metrics = getPaint().getFontMetrics();
        final int labelHeight = (int) (metrics.bottom - metrics.top);
        setMinWidth(labelHeight * mKeyWidth / mKeyHeight + getPaddingLeft() + getPaddingRight());
    }

    private void setTextAndScaleX(final String text) {
        setTextScaleX(1.0f);
        setText(text);
        if (StringUtilsKt.isEmoji(text)) {
            return;
        }
        // TODO: Override {@link #setBackground(Drawable)} that is supported from API 16 and
        // calculate maximum text width.
        final Drawable background = getBackground();
        if (background == null) {
            return;
        }
        background.getPadding(mBackgroundPadding);
        // The drawable declares a fixed width, which at large font scales is narrower than the
        // label: the glyph then gets squeezed horizontally until only a stroke of it is left.
        // The preview may grow as wide as the key's proportions allow, so measure against that.
        int maxWidth = background.getIntrinsicWidth() - mBackgroundPadding.left
                - mBackgroundPadding.right;
        if (mKeyWidth > 0 && mKeyHeight > 0) {
            final Paint.FontMetrics metrics = getPaint().getFontMetrics();
            final int labelHeight = (int) (metrics.bottom - metrics.top);
            maxWidth = Math.max(maxWidth, labelHeight * mKeyWidth / mKeyHeight);
        }
        final float width = getTextWidth(text, getPaint());
        if (width <= maxWidth) {
            return;
        }
        setTextScaleX(maxWidth / width);
    }

    public static void clearTextCache() {
        sNoScaleXTextSet.clear();
    }

    private static float getTextWidth(final String text, final TextPaint paint) {
        if (TextUtils.isEmpty(text)) {
            return 0.0f;
        }
        final int len = text.length();
        final float[] widths = new float[len];
        final int count = paint.getTextWidths(text, 0, len, widths);
        float width = 0;
        for (int i = 0; i < count; i++) {
            width += widths[i];
        }
        return width;
    }

    // Background state set
    private static final int[][][] KEY_PREVIEW_BACKGROUND_STATE_TABLE = {
        { // POSITION_MIDDLE
            {},
            { R.attr.state_has_popup_keys}
        },
        { // POSITION_LEFT
            { R.attr.state_left_edge },
            { R.attr.state_left_edge, R.attr.state_has_popup_keys}
        },
        { // POSITION_RIGHT
            { R.attr.state_right_edge },
            { R.attr.state_right_edge, R.attr.state_has_popup_keys}
        }
    };
    private static final int STATE_NORMAL = 0;
    private static final int STATE_HAS_POPUPKEYS = 1;

    public void setPreviewBackground(final boolean hasPopupKeys, final int position) {
        final Drawable background = getBackground();
        if (background == null) {
            return;
        }
        final int hasPopupKeysState = hasPopupKeys ? STATE_HAS_POPUPKEYS : STATE_NORMAL;
        background.setState(KEY_PREVIEW_BACKGROUND_STATE_TABLE[position][hasPopupKeysState]);
    }
}
