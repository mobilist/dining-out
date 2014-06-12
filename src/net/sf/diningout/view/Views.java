/*
 * Copyright 2014 pushbit <pushbit@gmail.com>
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

package net.sf.diningout.view;

import net.sf.diningout.R;
import android.view.View;
import android.widget.SearchView;

/**
 * Utility methods for working with Views.
 */
public class Views {
	private Views() {
	}

	/**
	 * Override the default background with a theme version.
	 */
	public static SearchView setBackground(SearchView view) {
		int id = view.getResources().getIdentifier("android:id/search_plate", null, null);
		if (id > 0) {
			View search = view.findViewById(id);
			if (search != null) {
				search.setBackgroundResource(R.drawable.textfield_searchview);
			}
		}
		return view;
	}
}
