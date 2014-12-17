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

import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.view.View;

import net.sf.diningout.R;
import net.sf.diningout.app.ui.NotificationsFragment.Listener;
import net.sf.diningout.app.ui.NotificationsFragment.NotificationHolder;
import net.sf.diningout.data.Sync.Type;
import net.sf.sprockets.view.ViewHolder;

import butterknife.InjectView;

import static net.sf.diningout.app.ui.RestaurantActivity.EXTRA_ID;

/**
 * Displays a list of notifications.
 */
public class NotificationsActivity extends BaseNavigationDrawerActivity implements Listener {
    @InjectView(R.id.root)
    DrawerLayout mDrawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.notifications_activity);
        setDrawerLayout(mDrawerLayout);
    }

    @Override
    public void onNotificationClick(View view, Type type, long id) {
        switch (type) {
            case USER:
                startActivity(new Intent(this, FriendsActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                break;
            case REVIEW:
                RestaurantActivity.sPlaceholder =
                        ((NotificationHolder) ViewHolder.get(view)).mPhoto.getDrawable();
                startActivity(new Intent(this, RestaurantActivity.class).putExtra(EXTRA_ID, id),
                        ActivityOptions.makeScaleUpAnimation(view, 0, 0,
                                view.getWidth(), view.getHeight()).toBundle());
                break;
        }
    }
}
