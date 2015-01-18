/*
 * Copyright 2014-2015 pushbit <pushbit@gmail.com>
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

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

import net.sf.diningout.provider.Contract.Columns;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.graphics.drawable.Drawables;

/**
 * Provides Drawables for placeholders.
 */
public class Placeholders {
    private Placeholders() {
    }

    /**
     * Get the default placeholder.
     */
    public static Drawable get() {
        return Drawables.darkColor();
    }

    /**
     * Get a placeholder with the cursor's {@link Columns#COLOR color}.
     *
     * @return default placeholder if the cursor is null or does not have a color value
     */
    public static Drawable get(EasyCursor c) {
        return c != null && !c.isNull(Columns.COLOR) ? new ColorDrawable(c.getInt(Columns.COLOR))
                : get();
    }
}
