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

import android.app.Notification.Builder;
import android.app.Notification.InboxStyle;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.TextUtils;

import net.sf.diningout.R;
import net.sf.sprockets.content.Managers;
import net.sf.sprockets.preference.Prefs;

import static android.app.Notification.DEFAULT_LIGHTS;
import static android.app.Notification.DEFAULT_VIBRATE;
import static android.app.PendingIntent.FLAG_CANCEL_CURRENT;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static net.sf.diningout.app.SyncsReadService.EXTRA_ACTIVITIES;
import static net.sf.diningout.preference.Keys.RINGTONE;
import static net.sf.diningout.preference.Keys.VIBRATE;
import static net.sf.sprockets.app.SprocketsApplication.context;

/**
 * Constants and methods for working with notifications.
 */
public class Notifications {
    /**
     * Notification ID for sync events.
     */
    public static final int ID_SYNC = 0;

    private Notifications() {
    }

    /**
     * Show an {@link InboxStyle} notification that starts the Activity.
     *
     * @param id     one of the ID_* constants in this class
     * @param number total number of items, may be more than number of lines
     */
    public static void inboxStyle(int id, CharSequence title, Iterable<CharSequence> lines,
                                  long when, Bitmap icon, int number, Intent activity) {
        Context context = context();
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
