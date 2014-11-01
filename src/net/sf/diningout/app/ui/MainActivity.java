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

package net.sf.diningout.app.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;

import com.google.android.gms.common.GooglePlayServicesUtil;

import net.sf.sprockets.app.ui.SprocketsDialogFragment;
import net.sf.sprockets.preference.Prefs;

import icepick.Icicle;

import static com.google.android.gms.common.ConnectionResult.SUCCESS;
import static net.sf.diningout.preference.Keys.ONBOARDED;

/**
 * Verifies that the required version of Google Play Services is available and then forwards to
 * {@link InitActivity} for onboarding or {@link RestaurantsActivity} for normal use.
 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            tryToContinue();
        }
    }

    /**
     * If Google Play Services is available, continue to next Activity.
     */
    private void tryToContinue() {
        int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (status == SUCCESS) {
            startActivity(new Intent(this, !Prefs.getBoolean(this, ONBOARDED)
                    ? InitActivity.class : RestaurantsActivity.class));
            finish();
        } else {
            ErrorDialog.newInstance(status).show(getFragmentManager(), null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        tryToContinue();
    }

    /**
     * Provides an error dialog from Google Play Services.
     */
    public static class ErrorDialog extends SprocketsDialogFragment {
        @Icicle
        int mStatus;

        /**
         * Create a dialog for the status code from Google Play Services.
         */
        public static ErrorDialog newInstance(int status) {
            ErrorDialog dialog = new ErrorDialog();
            dialog.mStatus = status;
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return GooglePlayServicesUtil.getErrorDialog(mStatus, getActivity(), 0);
        }
    }
}
