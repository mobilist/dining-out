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

package net.sf.diningout.app;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.util.Log;

import net.sf.diningout.provider.Contract.Restaurants;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.google.Place;
import net.sf.sprockets.google.Places;
import net.sf.sprockets.google.Places.Params;
import net.sf.sprockets.google.Places.Response;
import net.sf.sprockets.net.Uris;
import net.sf.sprockets.preference.Prefs;

import java.io.IOException;

import static android.provider.BaseColumns._ID;
import static net.sf.diningout.preference.Keys.MIGRATE_TO_PLACE_ID;
import static net.sf.sprockets.app.SprocketsApplication.cr;
import static net.sf.sprockets.gms.analytics.Trackers.event;
import static net.sf.sprockets.gms.analytics.Trackers.exception;
import static net.sf.sprockets.google.Places.Field.NONE;
import static net.sf.sprockets.google.Places.Response.Status.OK;

/**
 * Gets the place ID for any Google restaurants that don't have one yet.
 */
public class RestaurantsPlaceIdService extends IntentService {
    private static final String TAG = RestaurantsPlaceIdService.class.getSimpleName();

    public RestaurantsPlaceIdService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        ContentResolver cr = cr();
        String[] proj = {_ID, Restaurants.GOOGLE_REFERENCE};
        String sel = Restaurants.PLACE_ID + " IS NULL AND "
                + Restaurants.GOOGLE_REFERENCE + " IS NOT NULL";
        EasyCursor c = new EasyCursor(cr.query(Restaurants.CONTENT_URI, proj, sel, null, null));
        Params params = new Params();
        ContentValues vals = new ContentValues(1);
        while (c.moveToNext()) {
            try {
                Response<Place> resp = Places.details(
                        params.reference(c.getString(Restaurants.GOOGLE_REFERENCE)), NONE);
                Place place = resp.getResult();
                if (resp.getStatus() == OK && place != null) {
                    vals.put(Restaurants.PLACE_ID, place.getPlaceId().getId());
                    cr.update(Uris.appendId(Restaurants.CONTENT_URI, c), vals, null, null);
                } else {
                    Log.e(TAG, "Places.details failed, status: " + resp.getStatus());
                    event("restaurant", "Places.details failed", resp.getStatus().toString());
                }
            } catch (IOException e) {
                Log.e(TAG, "getting place details", e);
                exception(e);
            } catch (Exception e) {
                Log.e(TAG, "probably updating with non-unique place ID", e);
                exception(e);
            }
        }
        if (c.getCount() == 0) { // all done
            Prefs.putBoolean(this, MIGRATE_TO_PLACE_ID, false);
        }
        c.close();
    }
}
