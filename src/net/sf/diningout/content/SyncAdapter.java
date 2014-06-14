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

import static android.content.ContentResolver.SYNC_EXTRAS_INITIALIZE;
import static android.content.ContentResolver.SYNC_EXTRAS_UPLOAD;
import static android.provider.BaseColumns._ID;
import static android.text.format.DateUtils.DAY_IN_MILLIS;
import static com.google.common.base.Charsets.UTF_8;
import static java.util.Locale.ENGLISH;
import static net.sf.diningout.app.Notifications.ID_SYNC;
import static net.sf.diningout.app.ui.RestaurantActivity.EXTRA_ID;
import static net.sf.diningout.data.Review.Type.PRIVATE;
import static net.sf.diningout.data.Status.ACTIVE;
import static net.sf.diningout.data.Status.DELETED;
import static net.sf.diningout.data.Sync.Action.INSERT;
import static net.sf.diningout.data.Sync.Action.UPDATE;
import static net.sf.diningout.preference.Keys.ACCOUNT_INITIALISED;
import static net.sf.diningout.preference.Keys.INSTALL_ID;
import static net.sf.diningout.preference.Keys.LAST_SYNC;
import static net.sf.diningout.preference.Keys.ONBOARDED;
import static net.sf.diningout.preference.Keys.SHOW_SYNC_NOTIFICATIONS;
import static net.sf.diningout.provider.Contract.ACTION_CONTACTS_SYNCED;
import static net.sf.diningout.provider.Contract.ACTION_CONTACTS_SYNCING;
import static net.sf.diningout.provider.Contract.ACTION_USER_LOGGED_IN;
import static net.sf.diningout.provider.Contract.AUTHORITY;
import static net.sf.diningout.provider.Contract.AUTHORITY_URI;
import static net.sf.diningout.provider.Contract.CALL_UPDATE_RESTAURANT_LAST_VISIT;
import static net.sf.diningout.provider.Contract.CALL_UPDATE_RESTAURANT_RATING;
import static net.sf.diningout.provider.Contract.EXTRA_HAS_RESTAURANTS;
import static net.sf.diningout.provider.Contract.SYNC_EXTRAS_CONTACTS_ONLY;
import static net.sf.sprockets.content.Content.SYNC_EXTRAS_DOWNLOAD;
import static net.sf.sprockets.database.sqlite.SQLite.alias;
import static net.sf.sprockets.database.sqlite.SQLite.aliased;
import static net.sf.sprockets.database.sqlite.SQLite.millis;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import net.sf.diningout.R;
import net.sf.diningout.accounts.Accounts;
import net.sf.diningout.app.Notifications;
import net.sf.diningout.app.RestaurantService;
import net.sf.diningout.app.ReviewsService;
import net.sf.diningout.app.SyncsReadService;
import net.sf.diningout.app.ui.FriendsActivity;
import net.sf.diningout.app.ui.RestaurantActivity;
import net.sf.diningout.app.ui.RestaurantsActivity;
import net.sf.diningout.data.Init;
import net.sf.diningout.data.Restaurant;
import net.sf.diningout.data.Review;
import net.sf.diningout.data.Sync;
import net.sf.diningout.data.Sync.Type;
import net.sf.diningout.data.Synced;
import net.sf.diningout.data.Syncing;
import net.sf.diningout.data.User;
import net.sf.diningout.net.Server;
import net.sf.diningout.provider.Contract.Columns;
import net.sf.diningout.provider.Contract.Contacts;
import net.sf.diningout.provider.Contract.RestaurantPhotos;
import net.sf.diningout.provider.Contract.Restaurants;
import net.sf.diningout.provider.Contract.Reviews;
import net.sf.diningout.provider.Contract.ReviewsJoinContacts;
import net.sf.diningout.provider.Contract.ReviewsJoinRestaurants;
import net.sf.diningout.provider.Contract.ReviewsJoinRestaurantsJoinContacts;
import net.sf.diningout.provider.Contract.Syncs;
import net.sf.sprockets.content.Managers;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.net.Uris;
import net.sf.sprockets.preference.Prefs;
import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.content.res.Resources;
import android.database.CursorJoiner;
import android.database.CursorJoiner.Result;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.RawContacts;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.squareup.picasso.Picasso;

/**
 * Synchronises the content provider with the server.
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {
	private static final String TAG = SyncAdapter.class.getSimpleName();
	/** Contacts content URI specifying that the caller is a sync adapter. */
	private static final Uri CONTACTS_URI = Uris.callerIsSyncAdapter(Contacts.CONTENT_URI);
	/** Restaurants content URI specifying that the caller is a sync adapter. */
	private static final Uri RESTAURANTS_URI = Uris.callerIsSyncAdapter(Restaurants.CONTENT_URI);
	/** Reviews content URI specifying that the caller is a sync adapter. */
	private static final Uri REVIEWS_URI = Uris.callerIsSyncAdapter(Reviews.CONTENT_URI);
	/** Syncs content URI specifying that the caller is a sync adapter. */
	private static final Uri SYNCS_URI = Uris.callerIsSyncAdapter(Syncs.CONTENT_URI);

	public SyncAdapter(Context context) {
		super(context, false, false);
	}

	@Override
	public void onPerformSync(Account account, Bundle extras, String authority,
			ContentProviderClient provider, SyncResult result) {
		Account selected = Accounts.selected();
		if (!account.equals(selected)) {
			if (selected != null) { // never going to sync this account that wasn't selected
				ContentResolver.setIsSyncable(account, AUTHORITY, 0);
			}
			return;
		}
		if (extras.containsKey(SYNC_EXTRAS_INITIALIZE)) {
			return; // will initialise on first normal sync
		}
		Context context = getContext();
		try {
			if (!Prefs.getBoolean(context, ACCOUNT_INITIALISED)) { // first run, log the user in
				initUser(context, provider);
			}
			if (extras.getBoolean(SYNC_EXTRAS_CONTACTS_ONLY)) {
				syncContacts(context, provider);
				uploadContacts(context, provider);
				return;
			}
			if (!Prefs.getBoolean(context, ONBOARDED)) {
				return; // don't sync yet
			}
			long now = System.currentTimeMillis(); // full upload and download daily
			if (now - Prefs.getLong(context, LAST_SYNC) >= DAY_IN_MILLIS) {
				Prefs.putLong(context, LAST_SYNC, now);
				extras.putBoolean(SYNC_EXTRAS_UPLOAD, true);
				extras.putBoolean(SYNC_EXTRAS_DOWNLOAD, true);
				syncContacts(context, provider);
			}
			if (extras.containsKey(SYNC_EXTRAS_UPLOAD) || extras.containsKey(SYNC_EXTRAS_DOWNLOAD)) {
				uploadContacts(context, provider);
				uploadRestaurants(provider);
				uploadReviews(provider);
			}
			if (extras.containsKey(SYNC_EXTRAS_DOWNLOAD)) {
				download(context, provider);
				if (Prefs.getBoolean(context, SHOW_SYNC_NOTIFICATIONS)) {
					notify(context, provider);
				}
			}
		} catch (RemoteException e) {
			Log.e(TAG, "syncing the ContentProvider", e);
		} catch (IOException e) {
			Log.e(TAG, "loading contact or restaurant photo", e);
		}
	}

	/**
	 * Log the user into the server and restore any contacts or restaurants.
	 */
	private void initUser(Context context, ContentProviderClient cp) throws RemoteException {
		Init init = Server.init();
		Intent intent = new Intent(ACTION_USER_LOGGED_IN);
		intent.putExtra(EXTRA_HAS_RESTAURANTS, init != null && init.restaurants != null);
		LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
		if (init != null) {
			Prefs.edit(context).putBoolean(ACCOUNT_INITIALISED, true)
					.putLong(INSTALL_ID, init.installId).apply();
			if (init.users != null) {
				ContentValues vals = new ContentValues(5);
				for (User user : init.users) {
					cp.insert(CONTACTS_URI, Contacts.values(vals, user));
				}
			}
			if (init.restaurants != null) {
				Prefs.putBoolean(context, ONBOARDED, true);
				for (Restaurant restaurant : init.restaurants) {
					restaurant.localId = RestaurantService.add(restaurant.globalId);
					if (restaurant.localId > 0) {
						RestaurantService.download(restaurant.localId);
					}
				}
			}
		}
	}

	/**
	 * Insert new system contacts, delete orphaned app contacts, and synchronise any changes to
	 * existing.
	 */
	private void syncContacts(Context context, ContentProviderClient cp) throws RemoteException {
		/* get system contacts */
		String[] proj = { Email.ADDRESS, ContactsContract.Contacts.LOOKUP_KEY,
				RawContacts.CONTACT_ID, ContactsContract.Contacts.DISPLAY_NAME };
		String sel = Email.IN_VISIBLE_GROUP + " = 1 AND " + Email.ADDRESS + " <> ?";
		String[] args = { Accounts.selected().name };
		EasyCursor sys = new EasyCursor(context.getContentResolver().query(Email.CONTENT_URI, proj,
				sel, args, Email.ADDRESS));
		/* get app contacts */
		proj = new String[] { Contacts.EMAIL, Contacts.ANDROID_LOOKUP_KEY, Contacts.ANDROID_ID,
				Contacts.NAME, _ID, Contacts.FOLLOWING, Contacts.STATUS_ID };
		sel = Contacts.EMAIL + " IS NOT NULL";
		EasyCursor app = new EasyCursor(cp.query(CONTACTS_URI, proj, sel, null, Contacts.EMAIL));
		/* compare and sync */
		ContentValues vals = new ContentValues(5);
		for (Result result : new CursorJoiner(sys, new String[] { Email.ADDRESS }, app,
				new String[] { Contacts.EMAIL })) {
			switch (result) {
			case LEFT: // new system contact, insert into app contacts
				String email = sys.getString(Email.ADDRESS);
				String hash = BaseEncoding.base64().encode(
						Hashing.sha512().hashString(email.toLowerCase(ENGLISH), UTF_8).asBytes());
				long id = Contacts.idForHash(hash); // do we have this contact and not know it?
				/* insert or update values */
				vals.put(Contacts.ANDROID_LOOKUP_KEY,
						sys.getString(ContactsContract.Contacts.LOOKUP_KEY));
				vals.put(Contacts.ANDROID_ID, sys.getLong(RawContacts.CONTACT_ID));
				vals.put(Contacts.NAME, sys.getString(ContactsContract.Contacts.DISPLAY_NAME));
				vals.put(Contacts.EMAIL, email);
				if (id <= 0) {
					vals.put(Contacts.EMAIL_HASH, hash);
					cp.insert(CONTACTS_URI, vals);
				} else {
					cp.update(ContentUris.withAppendedId(CONTACTS_URI, id), vals, null, null);
				}
				break;
			case RIGHT: // orphaned app contact, delete unless user is following
				if (app.getInt(Contacts.FOLLOWING) == 0
						&& app.getInt(Contacts.STATUS_ID) == ACTIVE.id) {
					vals.put(Contacts.STATUS_ID, DELETED.id);
					vals.put(Contacts.DIRTY, 1);
					cp.update(Uris.appendId(CONTACTS_URI, app), vals, null, null);
				}
				break;
			case BOTH: // matching contacts, update details in app if needed
				String s = sys.getString(ContactsContract.Contacts.LOOKUP_KEY);
				if (!s.equals(app.getString(Contacts.ANDROID_LOOKUP_KEY))) {
					vals.put(Contacts.ANDROID_LOOKUP_KEY, s);
				}
				long l = sys.getLong(RawContacts.CONTACT_ID);
				if (l != app.getLong(Contacts.ANDROID_ID)) {
					vals.put(Contacts.ANDROID_ID, l);
				}
				s = sys.getString(ContactsContract.Contacts.DISPLAY_NAME);
				if (!s.equals(app.getString(Contacts.NAME))) {
					vals.put(Contacts.NAME, s);
				}
				if (app.getInt(Contacts.STATUS_ID) == DELETED.id) {
					vals.put(Contacts.STATUS_ID, ACTIVE.id);
					vals.put(Contacts.DIRTY, 1);
				}
				if (vals.size() > 0) {
					cp.update(Uris.appendId(CONTACTS_URI, app), vals, null, null);
				}
				break;
			}
			vals.clear();
		}
		sys.close();
		app.close();
	}

	/**
	 * Upload contact changes to the server.
	 */
	private void uploadContacts(Context context, ContentProviderClient cp) throws RemoteException {
		String[] proj = { _ID, Contacts.GLOBAL_ID, Contacts.EMAIL_HASH, Contacts.FOLLOWING,
				Contacts.STATUS_ID, Contacts.DIRTY, Contacts.VERSION };
		String sel = Contacts.DIRTY + " = 1";
		List<User> users = Contacts.from(cp.query(CONTACTS_URI, proj, sel, null, null));
		if (users != null) {
			LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
			bm.sendBroadcast(new Intent(ACTION_CONTACTS_SYNCING));
			response(Server.syncContacts(users), cp, CONTACTS_URI);
			bm.sendBroadcast(new Intent(ACTION_CONTACTS_SYNCED));
		}
	}

	/**
	 * Upload restaurant changes to the server.
	 */
	private void uploadRestaurants(ContentProviderClient cp) throws RemoteException {
		String[] proj = { _ID, Restaurants.GLOBAL_ID, Restaurants.GOOGLE_ID,
				Restaurants.GOOGLE_REFERENCE, Restaurants.NAME, Restaurants.NOTES,
				Restaurants.STATUS_ID, Restaurants.DIRTY, Restaurants.VERSION };
		String sel = Restaurants.DIRTY + " = 1";
		List<Restaurant> restaurants = Restaurants.from(cp.query(RESTAURANTS_URI, proj, sel, null,
				null));
		if (restaurants != null) {
			response(Server.syncRestaurants(restaurants), cp, RESTAURANTS_URI);
		}
	}

	/**
	 * Upload review changes to the server.
	 */
	private void uploadReviews(ContentProviderClient cp) throws RemoteException {
		String[] proj = { ReviewsJoinRestaurants.REVIEW_ID + " AS " + _ID,
				ReviewsJoinRestaurants.REVIEW_GLOBAL_ID + " AS " + Reviews.GLOBAL_ID,
				ReviewsJoinRestaurants.RESTAURANT_GLOBAL_ID + " AS " + Reviews.RESTAURANT_ID,
				Reviews.COMMENTS, ReviewsJoinRestaurants.REVIEW_RATING + " AS " + Reviews.RATING,
				Reviews.WRITTEN_ON,
				ReviewsJoinRestaurants.REVIEW_STATUS_ID + " AS " + Reviews.STATUS_ID,
				ReviewsJoinRestaurants.REVIEW_DIRTY + " AS " + Reviews.DIRTY,
				ReviewsJoinRestaurants.REVIEW_VERSION + " AS " + Reviews.VERSION };
		String sel = Reviews.TYPE_ID + " = ? AND " + ReviewsJoinRestaurants.REVIEW_DIRTY + " = 1";
		String[] args = { String.valueOf(PRIVATE.id) };
		List<Review> reviews = Reviews.from(cp.query(ReviewsJoinRestaurants.CONTENT_URI, proj, sel,
				args, null));
		if (reviews != null) {
			response(Server.syncReviews(reviews), cp, REVIEWS_URI);
		}
	}

	/**
	 * Update the synchronised object at the URI with the values received from the server.
	 * 
	 * @param synceds
	 *            can be null
	 */
	private void response(List<? extends Synced> synceds, ContentProviderClient cp, Uri uri)
			throws RemoteException {
		if (synceds == null) {
			return;
		}
		ContentValues vals = new ContentValues(2);
		String sel = Columns.VERSION + " = ?";
		String[] args = new String[1];
		for (Synced synced : synceds) {
			vals.put(Columns.GLOBAL_ID, synced.globalId > 0 ? synced.globalId : null);
			vals.put(Columns.DIRTY, synced.dirty);
			args[0] = String.valueOf(synced.version);
			try {
				cp.update(ContentUris.withAppendedId(uri, synced.localId), vals, sel, args);
			} catch (RemoteException e) { // probably global_id conflict, just mark not dirty
				Log.e(TAG, "updating synchronised object, trying again without global_id", e);
				vals.remove(Columns.GLOBAL_ID);
				cp.update(ContentUris.withAppendedId(uri, synced.localId), vals, sel, args);
			}
		}
	}

	/**
	 * Download changes from the server and synchronise them with the content provider.
	 */
	private void download(Context context, ContentProviderClient cp) throws RemoteException {
		Syncing syncing = Server.sync();
		if (syncing != null) {
			if (syncing.users != null) {
				for (Sync<User> sync : syncing.users) {
					if (sync.userId == 0) { // in case server starts sending changes by other users
						syncUser(cp, sync);
					}
				}
			}
			if (syncing.restaurants != null) {
				for (Sync<Restaurant> sync : syncing.restaurants) {
					if (sync.userId == 0) { // in case server starts sending changes by other users
						syncRestaurant(cp, sync);
					}
				}
			}
			if (syncing.reviews != null) {
				for (Sync<Review> sync : syncing.reviews) {
					syncReview(context, cp, sync);
				}
			}
		}
	}

	/**
	 * Convert a contact when they become a user and sync remote changes to a followed user.
	 */
	private void syncUser(ContentProviderClient cp, Sync<User> sync) throws RemoteException {
		User user = sync.object;
		ContentValues vals = new ContentValues(1);
		switch (sync.action) {
		case INSERT:
			user.localId = Contacts.idForHash(user.emailHash);
			if (user.localId > 0) {
				vals.put(Contacts.GLOBAL_ID, user.globalId);
				cp.update(ContentUris.withAppendedId(CONTACTS_URI, user.localId), vals, null, null);
				cp.insert(SYNCS_URI, Syncs.values(sync));
			}
			break;
		case UPDATE:
			int following = user.isFollowing ? 1 : 0;
			vals.put(Contacts.FOLLOWING, following);
			String sel = Contacts.GLOBAL_ID + " = ? AND " + Contacts.FOLLOWING + " <> ?";
			String[] args = { String.valueOf(user.globalId), String.valueOf(following) };
			if (cp.update(CONTACTS_URI, vals, sel, args) > 0 && user.isFollowing) {
				ReviewsService.download(Contacts.idForGlobalId(user.globalId));
			}
			break;
		}
	}

	/**
	 * Sync remote changes to a restaurant.
	 */
	private void syncRestaurant(ContentProviderClient cp, Sync<Restaurant> sync)
			throws RemoteException {
		Restaurant restaurant = sync.object;
		switch (sync.action) {
		case INSERT:
			restaurant.localId = ContentUris.parseId(cp.insert(RESTAURANTS_URI,
					Restaurants.values(restaurant)));
			if (restaurant.localId > 0 && restaurant.status == ACTIVE) {
				RestaurantService.download(restaurant.localId);
			}
			break;
		case UPDATE:
			ContentValues vals = new ContentValues(2);
			vals.put(Restaurants.NOTES, restaurant.notes);
			vals.put(Restaurants.STATUS_ID, restaurant.status.id);
			String sel = Restaurants.GLOBAL_ID + " = ?";
			String[] args = { String.valueOf(restaurant.globalId) };
			if (cp.update(RESTAURANTS_URI, vals, sel, args) == 0) { // re-added on other device
				sync.action = INSERT;
				syncRestaurant(cp, sync);
			}
			break;
		}
	}

	/**
	 * Sync remote changes to a review.
	 */
	private void syncReview(Context context, ContentProviderClient cp, Sync<Review> sync)
			throws RemoteException {
		Review review = sync.object;
		switch (review.status) {
		case ACTIVE:
			switch (sync.action) {
			case INSERT:
				ReviewsService.add(review);
				if (review.localId > 0 && review.userId > 0) { // friend's review
					cp.insert(SYNCS_URI, Syncs.values(sync));
				}
				break;
			case UPDATE:
				ContentValues vals = new ContentValues(3);
				vals.put(Reviews.COMMENTS, review.comments);
				vals.put(Reviews.RATING, review.rating);
				vals.put(Reviews.STATUS_ID, review.status.id);
				String sel = Reviews.GLOBAL_ID + " = ?";
				String[] args = { String.valueOf(review.globalId) };
				cp.update(REVIEWS_URI, vals, sel, args);
				break;
			}
			break;
		case DELETED:
			String sel = Reviews.GLOBAL_ID + " = ?";
			String[] args = { String.valueOf(review.globalId) };
			cp.delete(REVIEWS_URI, sel, args);
			break;
		}
		/* update restaurant rating and last visit if own review */
		ContentResolver cr = context.getContentResolver(); // cp.call added in API 17
		String id = String.valueOf(Restaurants.idForGlobalId(review.restaurantId));
		if (sync.action != INSERT) { // already called in add
			cr.call(AUTHORITY_URI, CALL_UPDATE_RESTAURANT_RATING, id, null);
		}
		if (review.userId == 0 && sync.action != UPDATE) {
			cr.call(AUTHORITY_URI, CALL_UPDATE_RESTAURANT_LAST_VISIT, id, null);
		}
	}

	/**
	 * Post a system notification for any unread server changes.
	 */
	private void notify(Context context, ContentProviderClient cp) throws RemoteException,
			IOException {
		String[] proj = { Syncs.TYPE_ID, Syncs.OBJECT_ID, millis(Syncs.ACTION_ON) };
		String sel = Syncs.STATUS_ID + " = ?";
		String[] args = { String.valueOf(ACTIVE.id) };
		String order = Syncs.ACTION_ON + " DESC";
		EasyCursor c = new EasyCursor(cp.query(SYNCS_URI, proj, sel, args, order));
		if (c.getCount() > 0) {
			int users = 0;
			int reviews = 0;
			long restaurantId = 0L; // of review
			Collection<CharSequence> lines = new LinkedHashSet<>(); // no dupes
			long when = 0L;
			Bitmap icon = null;
			Resources res = context.getResources();
			int w = res.getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
			int h = res.getDimensionPixelSize(android.R.dimen.notification_large_icon_height);
			/* get the change details */
			while (c.moveToNext()) {
				Uri photo = null;
				switch (Type.get(c.getInt(Syncs.TYPE_ID))) {
				case USER:
					photo = notifyUser(context, cp, c.getLong(Syncs.OBJECT_ID), lines, icon);
					if (photo != null) {
						users++;
					}
					break;
				case REVIEW:
					Pair<Uri, Long> pair = notifyReview(context, cp, c.getLong(Syncs.OBJECT_ID),
							lines, icon);
					photo = pair.first;
					if (photo != null) {
						reviews++;
						restaurantId = pair.second;
					}
					break;
				}
				if (when == 0) {
					when = c.getLong(Syncs.ACTION_ON);
				}
				if (photo != null && photo != Uri.EMPTY) {
					try {
						icon = Picasso.with(context).load(photo).resize(w, h).centerCrop().get();
					} catch (FileNotFoundException e) { // own restaurant may not have photo
					}
				}
			}
			/* build the title */
			StringBuilder title = new StringBuilder(32);
			if (users > 0) {
				title.append(res.getQuantityString(R.plurals.n_new_friends, users, users));
			}
			if (reviews > 0) {
				if (title.length() > 0) {
					title.append(context.getString(R.string.delimiter));
				}
				title.append(res.getQuantityString(R.plurals.n_new_reviews, reviews, reviews));
			}
			if (title.length() > 0) { // figure out where to go
				Intent activity;
				if (users > 0) {
					activity = new Intent(context, FriendsActivity.class);
				} else if (reviews == 1) {
					activity = new Intent(context, RestaurantActivity.class).putExtra(EXTRA_ID,
							restaurantId);
				} else {
					activity = new Intent(context, RestaurantsActivity.class);
				}
				Notifications.inboxStyle(ID_SYNC, title, lines, when, icon, users + reviews,
						activity);
			} else { // sync object was deleted
				Managers.notification(context).cancel(ID_SYNC);
				context.startService(new Intent(context, SyncsReadService.class));
			}
		}
		c.close();
	}

	/**
	 * Add a message to the list about the user.
	 * 
	 * @return photo if available, {@link Uri#EMPTY} if not needed, or null if the user wasn't found
	 */
	private Uri notifyUser(Context context, ContentProviderClient cp, long id,
			Collection<CharSequence> lines, Bitmap icon) throws RemoteException {
		Uri photo = null;
		String[] proj = { Contacts.ANDROID_LOOKUP_KEY, Contacts.ANDROID_ID, Contacts.NAME };
		String sel = Contacts.STATUS_ID + " = ?";
		String[] args = { String.valueOf(ACTIVE.id) };
		EasyCursor c = new EasyCursor(cp.query(ContentUris.withAppendedId(CONTACTS_URI, id), proj,
				sel, args, null));
		if (c.moveToFirst()) {
			String name = c.getString(Contacts.NAME);
			if (name == null) {
				name = context.getString(R.string.non_contact);
			}
			lines.add(Html.fromHtml(context.getString(R.string.new_friend,
					TextUtils.htmlEncode(name))));
			photo = Uri.EMPTY;
			if (icon == null) {
				String androidKey = c.getString(Contacts.ANDROID_LOOKUP_KEY);
				long androidId = c.getLong(Contacts.ANDROID_ID);
				if (androidKey != null && androidId > 0) {
					photo = ContactsContract.Contacts.getLookupUri(androidId, androidKey);
				}
			}
		}
		c.close();
		return photo;
	}

	/**
	 * Add a message to the list about the review.
	 * 
	 * @return photo if available, {@link Uri#EMPTY} if not needed, or null if the review wasn't
	 *         found, and the ID of the restaurant the review is for or 0 if the review wasn't found
	 */
	private Pair<Uri, Long> notifyReview(Context context, ContentProviderClient cp, long id,
			Collection<CharSequence> lines, Bitmap icon) throws RemoteException {
		Uri photo = null;
		long restaurantId = 0L;
		String[] proj = { Reviews.RESTAURANT_ID, alias(ReviewsJoinRestaurants.RESTAURANT_NAME),
				alias(ReviewsJoinContacts.CONTACT_NAME) };
		String sel = Reviews.TYPE_ID + " = ? AND " + ReviewsJoinRestaurants.REVIEW_STATUS_ID
				+ " = ? AND " + ReviewsJoinRestaurants.RESTAURANT_STATUS_ID + " = ?";
		String[] args = { String.valueOf(PRIVATE.id), String.valueOf(ACTIVE.id),
				String.valueOf(ACTIVE.id) };
		Uri uri = ContentUris.withAppendedId(ReviewsJoinRestaurantsJoinContacts.CONTENT_URI, id);
		EasyCursor c = new EasyCursor(cp.query(uri, proj, sel, args, null));
		if (c.moveToFirst()) {
			String contact = c.getString(aliased(ReviewsJoinContacts.CONTACT_NAME));
			if (contact == null) {
				contact = context.getString(R.string.non_contact);
			}
			lines.add(context.getString(R.string.new_review, contact,
					c.getString(aliased(ReviewsJoinRestaurants.RESTAURANT_NAME))));
			restaurantId = c.getLong(Reviews.RESTAURANT_ID);
			photo = icon == null ? RestaurantPhotos.uriForRestaurant(restaurantId) : Uri.EMPTY;
		}
		c.close();
		return Pair.create(photo, restaurantId);
	}
}
