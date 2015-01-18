/*
 * Copyright 2015 pushbit <pushbit@gmail.com>
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

import net.sf.sprockets.picasso.GradientTransformation;

import static android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP;
import static android.graphics.drawable.GradientDrawable.Orientation.RIGHT_LEFT;
import static android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM;
import static net.sf.sprockets.app.SprocketsApplication.context;

/**
 * Instances of Transformations.
 */
public class Transformations {
    public static final GradientTransformation UP =
            new GradientTransformation(context(), BOTTOM_TOP);
    public static final GradientTransformation DOWN =
            new GradientTransformation(context(), TOP_BOTTOM);
    public static final GradientTransformation LEFT =
            new GradientTransformation(context(), RIGHT_LEFT);
}
