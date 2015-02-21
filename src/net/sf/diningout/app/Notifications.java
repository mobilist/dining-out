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

package net.sf.diningout.app;

import android.app.Notification.Builder;
import android.app.Notification.InboxStyle;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.squareup.picasso.Picasso;

import net.sf.diningout.R;
import net.sf.diningout.app.ui.FriendsActivity;
import net.sf.diningout.app.ui.NotificationsActivity;
import net.sf.diningout.app.ui.RestaurantActivity;
import net.sf.diningout.data.Sync.Type;
import net.sf.diningout.provider.Contract.Contacts;
import net.sf.diningout.provider.Contract.RestaurantPhotos;
import net.sf.diningout.provider.Contract.Reviews;
import net.sf.diningout.provider.Contract.ReviewsJoinAll;
import net.sf.diningout.provider.Contract.ReviewsJoinContacts;
import net.sf.diningout.provider.Contract.ReviewsJoinRestaurants;
import net.sf.diningout.provider.Contract.Syncs;
import net.sf.sprockets.content.Managers;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.preference.Prefs;
import net.sf.sprockets.util.StringArrays;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;

import static android.app.Notification.DEFAULT_LIGHTS;
import static android.app.Notification.DEFAULT_VIBRATE;
import static android.app.PendingIntent.FLAG_CANCEL_CURRENT;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static net.sf.diningout.app.SyncsReadService.EXTRA_ACTIVITIES;
import static net.sf.diningout.app.ui.RestaurantActivity.EXTRA_ID;
import static net.sf.diningout.data.Review.Type.PRIVATE;
import static net.sf.diningout.data.Status.ACTIVE;
import static net.sf.diningout.preference.Keys.RINGTONE;
import static net.sf.diningout.preference.Keys.VIBRATE;
import static net.sf.sprockets.app.SprocketsApplication.cr;
import static net.sf.sprockets.app.SprocketsApplication.res;
import static net.sf.sprockets.gms.analytics.Trackers.event;
import static net.sf.sprockets.sql.SQLite.alias_;
import static net.sf.sprockets.sql.SQLite.aliased_;
import static net.sf.sprockets.sql.SQLite.millis;

/**
 * Methods for posting system notifications.
 */
public class Notifications {
    private static final String TAG = Notifications.class.getSimpleName();
    private static final int ID_SYNC = 0;

    private Notifications() {
    }

    /**
     * Post a notification for any unread server changes.
     */
    public static void sync(Context context) {
        ContentResolver cr = cr();
        String[] proj = {Syncs.TYPE_ID, Syncs.OBJECT_ID, millis(Syncs.ACTION_ON)};
        String sel = Syncs.STATUS_ID + " = ?";
        String[] args = {String.valueOf(ACTIVE.id)};
        String order = Syncs.ACTION_ON + " DESC";
        EasyCursor c = new EasyCursor(cr.query(Syncs.CONTENT_URI, proj, sel, args, order));
        if (c.getCount() > 0) {
            int users = 0;
            int reviews = 0;
            long restaurantId = 0L; // of review
            Collection<CharSequence> lines = new LinkedHashSet<>(); // no dupes
            long when = 0L;
            Bitmap icon = null;
            /* get the change details */
            while (c.moveToNext()) {
                Uri photo = null;
                switch (Type.get(c.getInt(Syncs.TYPE_ID))) {
                    case USER:
                        photo = user(context, cr, c.getLong(Syncs.OBJECT_ID), lines, icon);
                        if (photo != null) {
                            users++;
                        }
                        break;
                    case REVIEW:
                        Pair<Uri, Long> pair = review(context, cr, c.getLong(Syncs.OBJECT_ID),
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
                        icon = Picasso.with(context).load(photo).resizeDimen(
                                android.R.dimen.notification_large_icon_width,
                                android.R.dimen.notification_large_icon_height).centerCrop().get();
                    } catch (IOException e) { // contact or own restaurant may not have photo
                        Log.w(TAG, "loading contact or restaurant photo", e);
                    }
                }
            }
            /* build the title */
            StringBuilder title = new StringBuilder(32);
            if (users > 0) {
                title.append(res().getQuantityString(R.plurals.n_new_friends, users, users));
            }
            if (reviews > 0) {
                if (title.length() > 0) {
                    title.append(context.getString(R.string.delimiter));
                }
                title.append(res().getQuantityString(R.plurals.n_new_reviews, reviews, reviews));
            }
            if (title.length() > 0) { // figure out where to go
                Intent activity;
                if (users > 0 && reviews == 0) {
                    activity = new Intent(context, FriendsActivity.class);
                } else if (users == 0 && reviews == 1) {
                    activity = new Intent(context, RestaurantActivity.class)
                            .putExtra(EXTRA_ID, restaurantId);
                } else {
                    activity = new Intent(context, NotificationsActivity.class);
                }
                int items = users + reviews;
                inboxStyle(context, ID_SYNC, title, lines, when, icon, items, activity);
                event("notification", "notify", "items", items);
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
    private static Uri user(Context context, ContentResolver cr, long id,
                            Collection<CharSequence> lines, Bitmap icon) {
        Uri photo = null;
        String[] proj = {Contacts.ANDROID_LOOKUP_KEY, Contacts.ANDROID_ID, Contacts.NAME};
        String sel = Contacts.STATUS_ID + " = ?";
        String[] args = {String.valueOf(ACTIVE.id)};
        EasyCursor c = new EasyCursor(cr.query(ContentUris.withAppendedId(Contacts.CONTENT_URI, id),
                proj, sel, args, null));
        if (c.moveToFirst()) {
            String name = c.getString(Contacts.NAME);
            if (name == null) {
                name = context.getString(R.string.non_contact);
            }
            lines.add(Html.fromHtml(
                    context.getString(R.string.new_friend, TextUtils.htmlEncode(name))));
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
     * found, and the ID of the restaurant the review is for or 0 if the review wasn't found
     */
    private static Pair<Uri, Long> review(Context context, ContentResolver cr, long id,
                                          Collection<CharSequence> lines, Bitmap icon) {
        Uri photo = null;
        long restaurantId = 0L;
        String[] proj = {Reviews.RESTAURANT_ID, alias_(ReviewsJoinRestaurants.RESTAURANT_NAME),
                alias_(ReviewsJoinContacts.CONTACT_NAME)};
        String sel = Reviews.TYPE_ID + " = ? AND " + ReviewsJoinRestaurants.REVIEW_STATUS_ID
                + " = ? AND " + ReviewsJoinRestaurants.RESTAURANT_STATUS_ID + " = ?";
        String[] args = StringArrays.from(PRIVATE.id, ACTIVE.id, ACTIVE.id);
        EasyCursor c = new EasyCursor(cr.query(
                ContentUris.withAppendedId(ReviewsJoinAll.CONTENT_URI, id), proj, sel, args, null));
        if (c.moveToFirst()) {
            String contact = c.getString(aliased_(ReviewsJoinContacts.CONTACT_NAME));
            if (contact == null) {
                contact = context.getString(R.string.non_contact);
            }
            lines.add(context.getString(R.string.new_review, contact,
                    c.getString(aliased_(ReviewsJoinRestaurants.RESTAURANT_NAME))));
            restaurantId = c.getLong(Reviews.RESTAURANT_ID);
            photo = icon == null ? RestaurantPhotos.uriForRestaurant(restaurantId) : Uri.EMPTY;
        }
        c.close();
        return Pair.create(photo, restaurantId);
    }

    /**
     * Show an {@link InboxStyle} notification that starts the Activity.
     *
     * @param id     one of the ID_* constants in this class
     * @param number total number of items, may be more than number of lines
     */
    private static void inboxStyle(Context context, int id, CharSequence title,
                                   Iterable<CharSequence> lines, long when, Bitmap icon, int number,
                                   Intent activity) {
        int defaults = DEFAULT_LIGHTS;
        if (Prefs.getBoolean(context, VIBRATE)) {
            defaults |= DEFAULT_VIBRATE;
        }
        TaskStackBuilder task = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(activity.addFlags(FLAG_ACTIVITY_NEW_TASK));
        PendingIntent content;
        if (id == ID_SYNC) { // remove after Android issue 41253 is fixed
            Intent read = new Intent(context, SyncsReadService.class)
                    .putExtra(EXTRA_ACTIVITIES, task.getIntents());
            content = PendingIntent.getService(context, 1, read, FLAG_CANCEL_CURRENT);
        } else {
            content = task.getPendingIntent(0, FLAG_CANCEL_CURRENT); // flag for Android issue 61850
        }
        Builder notif = new Builder(context).setDefaults(defaults).setOnlyAlertOnce(true)
                .setTicker(title).setContentTitle(title).setWhen(when)
                .setLargeIcon(icon).setSmallIcon(R.drawable.stat_logo).setNumber(number)
                .setContentIntent(content).setAutoCancel(true);
        String ringtone = Prefs.getString(context, RINGTONE);
        if (!TextUtils.isEmpty(ringtone)) {
            notif.setSound(Uri.parse(ringtone));
        }
        if (id == ID_SYNC) {
            Intent read = new Intent(context, SyncsReadService.class);
            notif.setDeleteIntent(PendingIntent.getService(context, 0, read, FLAG_CANCEL_CURRENT));
        }
        InboxStyle inbox = new InboxStyle(notif);
        for (CharSequence line : lines) {
            inbox.addLine(line);
        }
        Managers.notification(context).notify(id, inbox.build());
    }
}
