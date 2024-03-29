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

package net.sf.diningout.data;

public class Restaurant extends Synced {
    public String placeId;
    public String googleId;
    public String googleReference;
    public String name;
    public String address;
    public String phone;
    public String url;
    public String notes;

    /**
     * Reset the fields in this class (but not the parent) to their default values.
     */
    public Restaurant clear() {
        placeId = null;
        googleId = null;
        googleReference = null;
        name = null;
        address = null;
        phone = null;
        url = null;
        notes = null;
        return this;
    }
}
