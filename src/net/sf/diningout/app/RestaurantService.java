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

package net.sf.diningout.app;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import com.google.common.io.Files;

import net.sf.diningout.R;
import net.sf.diningout.data.Restaurant;
import net.sf.diningout.data.Review;
import net.sf.diningout.net.Server;
import net.sf.diningout.provider.Contract.RestaurantPhotos;
import net.sf.diningout.provider.Contract.Restaurants;
import net.sf.diningout.provider.Contract.Reviews;
import net.sf.sprockets.database.Cursors;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.google.Place;
import net.sf.sprockets.google.Places;
import net.sf.sprockets.google.Places.Field;
import net.sf.sprockets.google.Places.Params;
import net.sf.sprockets.google.Places.Response;
import net.sf.sprockets.net.HttpClient;
import net.sf.sprockets.net.Uris;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.List;

import static android.provider.BaseColumns._ID;
import static net.sf.diningout.data.Review.Type.GOOGLE;
import static net.sf.diningout.data.Status.ACTIVE;
import static net.sf.diningout.provider.Contract.AUTHORITY_URI;
import static net.sf.diningout.provider.Contract.CALL_UPDATE_RESTAURANT_LAST_VISIT;
import static net.sf.diningout.provider.Contract.CALL_UPDATE_RESTAURANT_RATING;
import static net.sf.sprockets.app.SprocketsApplication.cr;
import static net.sf.sprockets.app.SprocketsApplication.res;
import static net.sf.sprockets.google.Places.Response.Status.OK;

/**
 * Updates details, reviews, and photos for a restaurant. Callers must include {@link #EXTRA_ID} in
 * their Intent extras.
 */
public class RestaurantService extends IntentService {
    /**
     * ID of the restaurant.
     */
    public static final String EXTRA_ID = "intent.extra.ID";
    private static final String TAG = RestaurantService.class.getSimpleName();

    public RestaurantService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        download(intent.getLongExtra(EXTRA_ID, 0L));
    }

    /**
     * Add a placeholder for a restaurant with the global ID.
     *
     * @return id of the new restaurant or -1 if an error occurred
     */
    public static long add(long globalId) {
        ContentValues vals = new ContentValues(4);
        vals.put(Restaurants.GLOBAL_ID, globalId);
        vals.put(Restaurants.NAME, "");
        vals.put(Restaurants.NORMALISED_NAME, "");
        vals.put(Restaurants.DIRTY, 0);
        return ContentUris.parseId(cr().insert(Restaurants.CONTENT_URI, vals));
    }

    /**
     * Download details, reviews, and photos for the restaurant.
     */
    public static void download(long id) {
        ContentResolver cr = cr();
        Uri uri = ContentUris.withAppendedId(Restaurants.CONTENT_URI, id);
        String[] proj = {Restaurants.GLOBAL_ID, Restaurants.GOOGLE_ID,
                Restaurants.GOOGLE_REFERENCE};
        EasyCursor c = new EasyCursor(cr.query(uri, proj, null, null, null));
        Restaurant restaurant = new Restaurant();
        restaurant.localId = id;
        if (c.moveToFirst()) {
            restaurant.globalId = c.getLong(Restaurants.GLOBAL_ID);
            restaurant.googleId = c.getString(Restaurants.GOOGLE_ID);
            restaurant.googleReference = c.getString(Restaurants.GOOGLE_REFERENCE);
        }
        c.close();
        /* try to get Google reference from server if don't already have */
        if (restaurant.globalId > 0 && TextUtils.isEmpty(restaurant.googleReference)) {
            restaurant = Server.restaurant(restaurant);
        }
        /* get Google details, reviews, and photos if available */
        if (restaurant != null && !TextUtils.isEmpty(restaurant.googleReference)) {
            ContentValues vals = new ContentValues(15);
            vals.put(Restaurants.GOOGLE_REFERENCE, restaurant.googleReference);
            Pair<Place, Long> details = details(id, vals);
            if (details.first != null) {
                try {
                    photo(details.second, id, details.first);
                } catch (IOException e) {
                    Log.e(TAG, "downloading restaurant photo", e);
                }
            }
        }
        /* get server details if haven't already */
        if (restaurant != null && restaurant.status == null) {
            restaurant = Server.restaurant(restaurant);
        }
        if (restaurant != null) { // save details
            restaurant.status = ACTIVE; // in case re-adding after deleting
            ContentValues vals = Restaurants.values(restaurant);
            cr.update(uri, vals, null, null);
            /* get Street View image if needed */
            Double lat = vals.getAsDouble(Restaurants.LATITUDE);
            Double lng = vals.getAsDouble(Restaurants.LONGITUDE);
            if (lat != null && lng != null) {
                try {
                    photo(id, lat, lng);
                } catch (IOException e) {
                    Log.e(TAG, "downloading restaurant Street View image", e);
                }
            }
            /* get server reviews */
            List<Review> reviews = Server.reviews(restaurant);
            if (reviews != null) {
                boolean hasOwn = false;
                for (Review review : reviews) {
                    review.localId = ContentUris.parseId(
                            cr.insert(Reviews.CONTENT_URI, Reviews.values(review)));
                    if (review.localId > 0 && review.userId == 0) {
                        hasOwn = true;
                    }
                }
                String restaurantId = String.valueOf(id);
                cr.call(AUTHORITY_URI, CALL_UPDATE_RESTAURANT_RATING, restaurantId, null);
                if (hasOwn) {
                    cr.call(AUTHORITY_URI, CALL_UPDATE_RESTAURANT_LAST_VISIT, restaurantId, null);
                }
            }
        }
    }

    /**
     * Update the Google restaurant's details, insert reviews and photos.
     *
     * @param vals should have a size of 15 or greater and must include
     *             {@link Restaurants#GOOGLE_REFERENCE}
     * @return {@link Places#details(Params, Field...) details} response and ID of the first
     * photo or 0 if there aren't any photos
     */
    static Pair<Place, Long> details(long id, ContentValues vals) {
        Place place = null;
        long photoId = 0L;
        try {
            Params params = new Params().reference(vals.getAsString(Restaurants.GOOGLE_REFERENCE));
            Response<Place> resp = Places.details(params, Restaurants.detailsFields());
            place = resp.getResult();
            if (resp.getStatus() == OK && place != null) {
                ContentResolver cr = cr();
                cr.update(ContentUris.withAppendedId(Restaurants.CONTENT_URI, id),
                        Restaurants.values(vals, place), null, null);
                /* insert reviews if none yet */
                String restaurantId = String.valueOf(id);
                Uri uri = Uris.limit(Reviews.CONTENT_URI, "1");
                String[] proj = {_ID};
                String sel = Reviews.RESTAURANT_ID + " = ? AND " + Reviews.TYPE_ID + " = ?";
                String[] args = {restaurantId, String.valueOf(GOOGLE.id)};
                if (Cursors.count(cr.query(uri, proj, sel, args, null), true) == 0) {
                    ContentValues[] reviewVals = Reviews.values(id, place);
                    if (reviewVals != null) {
                        for (ContentValues reviewVal : reviewVals) {
                            if (reviewVal != null) { // could be if review doesn't have comments
                                cr.insert(Reviews.CONTENT_URI, reviewVal);
                            }
                        }
                        cr.call(AUTHORITY_URI, CALL_UPDATE_RESTAURANT_RATING, restaurantId, null);
                    }
                }
                /* insert photos if none yet */
                uri = Uris.limit(RestaurantPhotos.CONTENT_URI, "1");
                sel = RestaurantPhotos.RESTAURANT_ID + " = ?";
                args = new String[]{restaurantId};
                photoId = Cursors.firstLong(cr.query(uri, proj, sel, args, _ID), true);
                if (photoId <= 0) {
                    ContentValues[] photoVals = RestaurantPhotos.values(id, place);
                    if (photoVals != null) {
                        for (ContentValues photoVal : photoVals) {
                            uri = cr.insert(RestaurantPhotos.CONTENT_URI, photoVal);
                            if (photoId <= 0) {
                                photoId = ContentUris.parseId(uri);
                            }
                        }
                    }
                }
            } else {
                Log.e(TAG, "Places.details failed, status: " + resp.getStatus());
            }
        } catch (IOException e) {
            Log.e(TAG, "getting place details", e);
        }
        return Pair.create(place, photoId >= 0 ? photoId : 0L);
    }

    /**
     * Download a photo for the place and save it to disk.
     *
     * @param id can be 0 if the place doesn't have any photos and a Street View image should be
     *           downloaded
     */
    static void photo(long id, long restaurantId, Place place) throws IOException {
        Resources res = res();
        String url = RestaurantPhotos.url(place,
                res.getDimensionPixelSize(R.dimen.restaurant_photo_width),
                res.getDimensionPixelSize(R.dimen.restaurant_photo_height));
        String etag = photo(id, restaurantId, url);
        if (id > 0 && !TextUtils.isEmpty(etag)) {
            ContentValues vals = new ContentValues(1);
            vals.put(RestaurantPhotos.ETAG, etag);
            cr().update(ContentUris.withAppendedId(RestaurantPhotos.CONTENT_URI, id), vals, null,
                    null);
        }
    }

    /**
     * Download a Street View image for the location and save it to disk.
     */
    private static void photo(long restaurantId, double lat, double lng) throws IOException {
        Resources res = res();
        String url = RestaurantPhotos.url(lat, lng,
                res.getDimensionPixelSize(R.dimen.restaurant_photo_width),
                res.getDimensionPixelSize(R.dimen.restaurant_photo_height));
        photo(0, restaurantId, url);
    }

    /**
     * Download the photo at the URL and save it to disk.
     *
     * @return ETag header value, if available
     */
    private static String photo(long id, final long restaurantId, String url) throws IOException {
        File file = RestaurantPhotos.file(id, restaurantId);
        if (file != null) {
            Files.createParentDirs(file);
            URLConnection con = HttpClient.openConnection(url);
            Closer closer = Closer.create();
            try {
                ByteStreams.copy(closer.register(con.getInputStream()),
                        closer.register(new FileOutputStream(file)));
            } catch (Throwable e) {
                throw closer.rethrow(e);
            } finally {
                closer.close();
            }
            /* notify observers about new photo */
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    Uri uri = ContentUris.withAppendedId(Restaurants.CONTENT_URI, restaurantId);
                    cr().notifyChange(uri, null, false);
                }
            }, 500L); // when the file will hopefully already be flushed to disk
            return con.getHeaderField("ETag");
        }
        return null;
    }
}
