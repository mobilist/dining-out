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

package net.sf.diningout.preference;

/**
 * Keys used in SharedPreferences.
 */
public class Keys {
    /**
     * Name of selected account.
     */
    public static final String ACCOUNT_NAME = "account_name";
    /**
     * True if the account has been initialised.
     */
    public static final String ACCOUNT_INITIALISED = "account_initialised";
    /**
     * True if the user allows anonymous usage statistics to be sent.
     */
    public static final String ALLOW_ANALYTICS = "allow_analytics";
    /**
     * Identifier for this installation.
     */
    public static final String INSTALL_ID = "install_id";
    /**
     * True if the user has completed the onboarding process.
     */
    public static final String ONBOARDED = "onboarded";
    /**
     * Epoch milliseconds of the last full sync.
     */
    public static final String LAST_SYNC = "last_sync";
    /**
     * Used by the server to contact this device through cloud messaging.
     */
    public static final String CLOUD_ID = "cloud_id";
    /**
     * True if notifications for sync events should be shown.
     */
    public static final String SHOW_SYNC_NOTIFICATIONS = "show_sync_notifications";
    /**
     * URI path for the selected notification ringtone or empty if no ringtone should be played.
     */
    public static final String RINGTONE = "ringtone";
    /**
     * True if notifications should cause vibration.
     */
    public static final String VIBRATE = "vibrate";

    private Keys() {
    }
}
