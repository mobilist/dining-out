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

package net.sf.diningout.content;

import static android.content.UriMatcher.NO_MATCH;
import static android.os.ParcelFileDescriptor.MODE_READ_ONLY;
import static net.sf.diningout.provider.Contract.AUTHORITY;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import net.sf.diningout.provider.Contract;
import net.sf.diningout.provider.Contract.Contacts;
import net.sf.diningout.provider.Contract.RestaurantPhotos;
import net.sf.diningout.provider.Contract.Restaurants;
import net.sf.diningout.provider.Contract.ReviewDrafts;
import net.sf.diningout.provider.Contract.Reviews;
import net.sf.diningout.provider.Contract.ReviewsJoinContacts;
import net.sf.diningout.provider.Contract.ReviewsJoinRestaurants;
import net.sf.diningout.provider.Contract.ReviewsJoinRestaurantsJoinContacts;
import net.sf.diningout.provider.Contract.Syncs;
import net.sf.diningout.sql.Queries;
import net.sf.sprockets.content.DbContentProvider;
import net.sf.sprockets.database.sqlite.DbOpenHelper;
import android.content.ContentUris;
import android.content.UriMatcher;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

/**
 * See {@link Contract} for the interface to this ContentProvider.
 */
public class AppContentProvider extends DbContentProvider {
	private static final String TAG = AppContentProvider.class.getSimpleName();
	private static final int CONTACTS = 100;
	private static final int CONTACT_ID = 110;
	private static final int RESTAURANTS = 200;
	private static final int RESTAURANT_ID = 210;
	private static final int RESTAURANT_PHOTOS = 300;
	private static final int RESTAURANT_PHOTO_ID = 310;
	private static final int REVIEWS = 400;
	private static final int REVIEW_ID = 410;
	private static final int REVIEWS_JOIN_RESTAURANTS = 500;
	private static final int REVIEW_JOIN_RESTAURANT_ID = 510;
	private static final int REVIEWS_JOIN_CONTACTS = 600;
	private static final int REVIEW_JOIN_CONTACT_ID = 610;
	private static final int REVIEWS_JOIN_RESTAURANTS_JOIN_CONTACTS = 700;
	private static final int REVIEW_JOIN_RESTAURANT_JOIN_CONTACT_ID = 710;
	private static final int REVIEW_DRAFTS = 800;
	private static final int REVIEW_DRAFT_ID = 810;
	private static final int SYNCS = 900;
	private static final int SYNC_ID = 910;
	private static final UriMatcher sMatcher = new UriMatcher(NO_MATCH);
	static {
		sMatcher.addURI(AUTHORITY, "contact", CONTACTS);
		sMatcher.addURI(AUTHORITY, "contact/#", CONTACT_ID);
		sMatcher.addURI(AUTHORITY, "restaurant", RESTAURANTS);
		sMatcher.addURI(AUTHORITY, "restaurant/#", RESTAURANT_ID);
		sMatcher.addURI(AUTHORITY, "restaurant_photo", RESTAURANT_PHOTOS);
		sMatcher.addURI(AUTHORITY, "restaurant_photo/#", RESTAURANT_PHOTO_ID);
		sMatcher.addURI(AUTHORITY, "review", REVIEWS);
		sMatcher.addURI(AUTHORITY, "review/#", REVIEW_ID);
		sMatcher.addURI(AUTHORITY, "review_join_restaurant", REVIEWS_JOIN_RESTAURANTS);
		sMatcher.addURI(AUTHORITY, "review_join_restaurant/#", REVIEW_JOIN_RESTAURANT_ID);
		sMatcher.addURI(AUTHORITY, "review_join_contact", REVIEWS_JOIN_CONTACTS);
		sMatcher.addURI(AUTHORITY, "review_join_contact/#", REVIEW_JOIN_CONTACT_ID);
		sMatcher.addURI(AUTHORITY, "review_join_restaurant_join_contact",
				REVIEWS_JOIN_RESTAURANTS_JOIN_CONTACTS);
		sMatcher.addURI(AUTHORITY, "review_join_restaurant_join_contact/#",
				REVIEW_JOIN_RESTAURANT_JOIN_CONTACT_ID);
		sMatcher.addURI(AUTHORITY, "review_draft", REVIEW_DRAFTS);
		sMatcher.addURI(AUTHORITY, "review_draft/#", REVIEW_DRAFT_ID);
		sMatcher.addURI(AUTHORITY, "sync", SYNCS);
		sMatcher.addURI(AUTHORITY, "sync/#", SYNC_ID);
	}

	private SQLiteOpenHelper mHelper;

	@Override
	protected SQLiteOpenHelper getOpenHelper() {
		mHelper = new DbOpenHelper(getContext(), "dining-out-v100.db", 1);
		return mHelper;
	}

	@Override
	protected Sql translate(Uri uri) {
		int code = sMatcher.match(uri);
		if (code >= 0) {
			Sql sql = new Sql();
			switch (code) {
			case REVIEW_JOIN_RESTAURANT_ID:
				sql.sel(ReviewsJoinRestaurants.REVIEW_ID + " = ?");
			case REVIEWS_JOIN_RESTAURANTS:
				sql.table("review AS w").join("JOIN restaurant AS r ON w.restaurant_id = r._id")
						.notify(Reviews.CONTENT_URI);
				break;
			case REVIEW_JOIN_CONTACT_ID:
				sql.sel(ReviewsJoinContacts.REVIEW_ID + " = ?");
			case REVIEWS_JOIN_CONTACTS:
				sql.table("review AS w").join("LEFT JOIN contact AS c ON w.contact_id = c._id")
						.notify(Reviews.CONTENT_URI);
				break;
			case REVIEW_JOIN_RESTAURANT_JOIN_CONTACT_ID:
				sql.sel(ReviewsJoinRestaurantsJoinContacts.REVIEW_ID + " = ?");
			case REVIEWS_JOIN_RESTAURANTS_JOIN_CONTACTS:
				sql.table("review AS w")
						.join("JOIN restaurant AS r ON w.restaurant_id = r._id "
								+ "LEFT JOIN contact AS c ON w.contact_id = c._id")
						.notify(Reviews.CONTENT_URI);
				break;
			}
			return sql;
		}
		return null;
	}

	@Override
	public Bundle call(String method, String arg, Bundle extras) {
		SQLiteDatabase db = mHelper.getWritableDatabase();
		try {
			db.execSQL(Queries.get(method), new Object[] { arg, arg });
		} catch (IOException e) {
			Log.e(TAG, method, e);
		}
		Uri uri = ContentUris.withAppendedId(Restaurants.CONTENT_URI, Long.parseLong(arg));
		getContext().getContentResolver().notifyChange(uri, null, false);
		return null;
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
		switch (sMatcher.match(uri)) {
		case RESTAURANT_PHOTOS:
			File file = RestaurantPhotos.file(Long.parseLong(uri
					.getQueryParameter(RestaurantPhotos.RESTAURANT_ID)));
			if (file != null) {
				return ParcelFileDescriptor.open(file, MODE_READ_ONLY);
			}
			break;
		}
		return super.openFile(uri, mode);
	}

	@Override
	public String getType(Uri uri) {
		switch (sMatcher.match(uri)) {
		case CONTACTS:
			return Contacts.CONTENT_TYPE;
		case CONTACT_ID:
			return Contacts.CONTENT_ITEM_TYPE;
		case RESTAURANTS:
			return Restaurants.CONTENT_TYPE;
		case RESTAURANT_ID:
			return Restaurants.CONTENT_ITEM_TYPE;
		case RESTAURANT_PHOTOS:
			return RestaurantPhotos.CONTENT_TYPE;
		case RESTAURANT_PHOTO_ID:
			return RestaurantPhotos.CONTENT_ITEM_TYPE;
		case REVIEWS:
			return Reviews.CONTENT_TYPE;
		case REVIEW_ID:
			return Reviews.CONTENT_ITEM_TYPE;
		case REVIEWS_JOIN_RESTAURANTS:
			return ReviewsJoinRestaurants.CONTENT_TYPE;
		case REVIEW_JOIN_RESTAURANT_ID:
			return ReviewsJoinRestaurants.CONTENT_ITEM_TYPE;
		case REVIEWS_JOIN_CONTACTS:
			return ReviewsJoinContacts.CONTENT_TYPE;
		case REVIEW_JOIN_CONTACT_ID:
			return ReviewsJoinContacts.CONTENT_ITEM_TYPE;
		case REVIEWS_JOIN_RESTAURANTS_JOIN_CONTACTS:
			return ReviewsJoinRestaurantsJoinContacts.CONTENT_TYPE;
		case REVIEW_JOIN_RESTAURANT_JOIN_CONTACT_ID:
			return ReviewsJoinRestaurantsJoinContacts.CONTENT_ITEM_TYPE;
		case REVIEW_DRAFTS:
			return ReviewDrafts.CONTENT_TYPE;
		case REVIEW_DRAFT_ID:
			return ReviewDrafts.CONTENT_ITEM_TYPE;
		case SYNCS:
			return Syncs.CONTENT_TYPE;
		case SYNC_ID:
			return Syncs.CONTENT_ITEM_TYPE;
		}
		return null;
	}
}