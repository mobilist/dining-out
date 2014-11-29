/*
 * Copyright 2013-2014 pushbit <pushbit@gmail.com>
 * 
 * This file is part of Dining Out.
 * 
 * Dining Out is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * Dining Out is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Dining Out. If not,
 * see <http://www.gnu.org/licenses/>.
 */

package net.sf.diningout.picasso;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;

import com.google.common.base.Objects;
import com.squareup.picasso.Transformation;

import net.sf.diningout.R;
import net.sf.sprockets.graphics.Bitmaps;

import static android.graphics.Color.TRANSPARENT;
import static android.graphics.Shader.TileMode.CLAMP;
import static net.sf.sprockets.app.SprocketsApplication.res;

/**
 * Adds translucent background protection that covers half of the input and fades to transparent
 * across the other half.
 */
public class OverlayTransformation implements Transformation {
    /**
     * Covers the bottom half and fades from the middle to the top.
     */
    public static final OverlayTransformation UP = new OverlayTransformation(0);
    /**
     * Covers the left half and fades from the middle to the right.
     */
    public static final OverlayTransformation RIGHT = new OverlayTransformation(90);
    /**
     * Covers the top half and fades from the middle to the bottom.
     */
    public static final OverlayTransformation DOWN = new OverlayTransformation(180);
    /**
     * Covers the right half and fades from the middle to the left.
     */
    public static final OverlayTransformation LEFT = new OverlayTransformation(270);

    private final int mAngle;

    /**
     * Fade from translucent to transparent in the direction of the angle.
     */
    private OverlayTransformation(int angle) {
        mAngle = angle;
    }

    private static final int[] sColors = {res().getColor(R.color.overlay), TRANSPARENT};
    private static final float[] sPositions = {0.5f, 1.0f};

    @Override
    public Bitmap transform(Bitmap source) {
        Bitmap bm = Bitmaps.mutable(source);
        if (bm == null) { // couldn't make a copy to transform
            return source;
        }
        Canvas canvas = new Canvas(bm);
        Paint paint = new Paint();
        int width = bm.getWidth();
        int height = bm.getHeight();
        float x0 = 0.0f, y0 = 0.0f, x1 = 0.0f, y1 = 0.0f;
        switch (mAngle) {
            case 0:
                y0 = height;
                break;
            case 90:
                x1 = width;
                break;
            case 180:
                y1 = height;
                break;
            case 270:
                x0 = width;
                break;
        }
        paint.setShader(new LinearGradient(x0, y0, x1, y1, sColors, sPositions, CLAMP));
        canvas.drawRect(0.0f, 0.0f, width, height, paint);
        return bm;
    }

    @Override
    public String key() {
        return toString();
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("angle", mAngle).toString();
    }
}
