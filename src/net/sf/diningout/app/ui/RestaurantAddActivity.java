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

package net.sf.diningout.app.ui;

import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.GridView;

import net.sf.diningout.R;
import net.sf.diningout.app.RestaurantService;
import net.sf.diningout.provider.Contract.Restaurants;
import net.sf.sprockets.app.ui.SprocketsActivity;
import net.sf.sprockets.database.Cursors;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.google.Place;
import net.sf.sprockets.google.Place.IdPredicate;

import icepick.Icicle;

import static android.provider.BaseColumns._ID;
import static net.sf.diningout.app.RestaurantService.EXTRA_ID;
import static net.sf.diningout.data.Status.ACTIVE;
import static net.sf.sprockets.app.SprocketsApplication.cr;
import static net.sf.sprockets.database.sqlite.SQLite.normalise;
import static net.sf.sprockets.gms.analytics.Trackers.event;

/**
 * Displays restaurant autocomplete and nearby restaurants or restaurant search results.
 */
public class RestaurantAddActivity extends SprocketsActivity implements LoaderCallbacks<Cursor>,
        RestaurantAutocompleteFragment.Listener, RestaurantsNearbyFragment.Listener {
    @Icicle
    String mId;
    @Icicle
    String mReference;
    @Icicle
    String mName;
    @Icicle
    String mSource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.restaurant_add);
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String[] proj = {Restaurants.GOOGLE_ID};
        String sel = Restaurants.GOOGLE_ID + " IS NOT NULL AND " + Restaurants.STATUS_ID + " = ?";
        String[] selArgs = {String.valueOf(ACTIVE.id)};
        return new CursorLoader(this, Restaurants.CONTENT_URI, proj, sel, selArgs, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        IdPredicate filter = null;
        if (data.getCount() > 0) {
            filter = new IdPredicate().exclude(Cursors.allStrings(data, false));
            autocomplete().mName.setPlaceFilter(filter);
        }
        nearby().filter(filter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.restaurant_add, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.done:
                ContentValues vals = new ContentValues(5);
                long id = 0L;
                if (mId != null) { // check for existing Google restaurant, update or insert new
                    String[] proj = {_ID, Restaurants.STATUS_ID};
                    String sel = Restaurants.GOOGLE_ID + " = ?";
                    String[] args = {mId};
                    EasyCursor c = new EasyCursor(cr().query(Restaurants.CONTENT_URI, proj,
                            sel, args, null));
                    if (c.moveToFirst()) {
                        id = c.getLong(_ID);
                    }
                    vals.put(Restaurants.GOOGLE_REFERENCE, mReference);
                    vals.put(Restaurants.NAME, mName);
                    vals.put(Restaurants.NORMALISED_NAME, normalise(mName));
                    if (id <= 0) { // insert new
                        vals.put(Restaurants.GOOGLE_ID, mId);
                        id = ContentUris.parseId(cr().insert(Restaurants.CONTENT_URI, vals));
                    } else if (c.getInt(Restaurants.STATUS_ID) != ACTIVE.id) { // resurrect
                        vals.put(Restaurants.STATUS_ID, ACTIVE.id);
                        vals.put(Restaurants.DIRTY, 1);
                        cr().update(ContentUris.withAppendedId(Restaurants.CONTENT_URI, id), vals,
                                null, null);
                    }
                    c.close();
                    if (id > 0) {
                        startService(new Intent(this, RestaurantService.class).putExtra(EXTRA_ID,
                                id));
                    }
                } else { // insert own restaurant
                    String name = autocomplete().mName.getText().toString().trim();
                    if (!TextUtils.isEmpty(name)) {
                        vals.put(Restaurants.NAME, name);
                        vals.put(Restaurants.NORMALISED_NAME, normalise(name));
                        id = ContentUris.parseId(cr().insert(Restaurants.CONTENT_URI, vals));
                    }
                }
                if (id > 0) {
                    startActivity(new Intent(this, RestaurantActivity.class).putExtra(EXTRA_ID,
                            id));
                    finish();
                }
                if (mSource != null) {
                    event("restaurant add", "chosen from", mSource);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRestaurantNameChange(CharSequence name) {
        GridView grid = nearby().mGrid;
        if (name.length() > 0) {
            name = name.toString().trim();
            if (!TextUtils.isEmpty(name)) {
                setTitle(getString(R.string.add_s, name));
            } else {
                setTitle(R.string.add_restaurant_title);
            }
            if (grid.getCheckedItemCount() > 0) { // switch from list to autocomplete
                grid.setItemChecked(grid.getCheckedItemPosition(), false);
                clear();
            }
        } else {
            setTitle(R.string.add_restaurant_title);
            if (grid.getCheckedItemCount() == 0) { // forget any autocompletion
                clear();
            }
        }
    }

    /**
     * Set fields to default values.
     */
    private void clear() {
        mId = null;
        mReference = null;
        mName = null;
        mSource = null;
    }

    @Override
    public void onRestaurantAutocomplete(Place place) {
        fields(place, "autocomplete");
    }

    @Override
    public void onRestaurantSearch(CharSequence name) {
        nearby().search(name.toString());
        event("restaurant add", "search");
    }

    @Override
    public void onRestaurantClick(Place place) {
        autocomplete().mName.setText(null); // first because it resets title
        setTitle(getString(R.string.add_s, place.getName()));
        fields(place, "nearby");
    }

    /**
     * Populate fields from the place.
     */
    private void fields(Place place, String source) {
        mId = place.getId();
        mReference = place.getReference();
        mName = place.getName();
        mSource = source;
    }

    private RestaurantAutocompleteFragment autocomplete() {
        return (RestaurantAutocompleteFragment) getFragmentManager()
                .findFragmentById(R.id.autocomplete);
    }

    private RestaurantsNearbyFragment nearby() {
        return (RestaurantsNearbyFragment) getFragmentManager().findFragmentById(R.id.nearby);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }
}
