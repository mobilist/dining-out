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

import android.app.ActionBar;
import android.app.ActionBar.OnNavigationListener;
import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.view.View;
import android.widget.ArrayAdapter;

import com.google.common.collect.ImmutableSet;

import net.sf.diningout.R;
import net.sf.diningout.app.ui.RestaurantsFragment.Listener;
import net.sf.diningout.widget.RestaurantHolder;
import net.sf.sprockets.view.ActionModePresenter;
import net.sf.sprockets.view.ViewHolder;

import java.util.Set;

import butterknife.InjectView;
import icepick.Icicle;

import static android.app.ActionBar.NAVIGATION_MODE_LIST;
import static android.support.v4.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED;
import static android.support.v4.widget.DrawerLayout.LOCK_MODE_UNLOCKED;
import static android.view.Gravity.START;
import static net.sf.diningout.app.ui.RestaurantActivity.EXTRA_ID;
import static net.sf.sprockets.app.SprocketsApplication.res;
import static net.sf.sprockets.gms.analytics.Trackers.event;

/**
 * Displays a list of the user's restaurants.
 */
public class RestaurantsActivity extends BaseNavigationDrawerActivity
        implements OnNavigationListener, Listener {
    @InjectView(R.id.root)
    DrawerLayout mDrawerLayout;
    /**
     * Position of selected sort option.
     */
    @Icicle
    int mSort;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar ab = getActionBar();
        ab.setDisplayShowTitleEnabled(false);
        ab.setNavigationMode(NAVIGATION_MODE_LIST);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.restaurants_navigation,
                R.id.sort, res().getStringArray(R.array.restaurants_sort));
        adapter.setDropDownViewResource(R.layout.restaurants_navigation_item);
        ab.setListNavigationCallbacks(adapter, this);
        ab.setSelectedNavigationItem(mSort); // restore when rotating with navigation drawer open
        setContentView(R.layout.restaurants_activity);
        setDrawerLayout(mDrawerLayout);
    }

    private static final String[] sSortEventLabels = {"by name", "by last visit", "by distance",
            "by rating"};

    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        if (mSort != itemPosition) { // reload if option changed
            mSort = itemPosition;
            restaurants().sort(itemPosition);
            event("restaurants", "sort", sSortEventLabels[itemPosition]);
        }
        return true;
    }

    @Override
    public boolean onRestaurantsOptionsMenu() {
        return !mDrawerLayout.isDrawerOpen(START);
    }

    @Override
    public void onRestaurantsSearch(String query) {
        mDrawerLayout.setDrawerLockMode(query.length() > 0 ? LOCK_MODE_LOCKED_CLOSED
                : LOCK_MODE_UNLOCKED, START);
    }

    @Override
    public void onRestaurantClick(View view, long id) {
        RestaurantActivity.sPlaceholder =
                ((RestaurantHolder) ViewHolder.get(view)).photo.getDrawable();
        startActivity(new Intent(this, RestaurantActivity.class).putExtra(EXTRA_ID, id),
                ActivityOptions.makeScaleUpAnimation(view, 0, 0, view.getWidth(), view.getHeight())
                        .toBundle());
    }

    @Override
    public Set<ActionModePresenter> getActionModePresenters() {
        return ImmutableSet.of((ActionModePresenter) restaurants());
    }

    private RestaurantsFragment restaurants() {
        return (RestaurantsFragment) getFragmentManager().findFragmentById(R.id.restaurants);
    }
}
