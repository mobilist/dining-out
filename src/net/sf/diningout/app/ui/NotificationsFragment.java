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

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CursorAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import net.sf.diningout.R;
import net.sf.diningout.data.Sync.Type;
import net.sf.diningout.picasso.Placeholders;
import net.sf.diningout.provider.Contract.Columns;
import net.sf.diningout.provider.Contract.Contacts;
import net.sf.diningout.provider.Contract.RestaurantPhotos;
import net.sf.diningout.provider.Contract.Syncs;
import net.sf.diningout.provider.Contract.SyncsJoinAll;
import net.sf.sprockets.app.ui.SprocketsFragment;
import net.sf.sprockets.content.EasyCursorLoader;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.util.StringArrays;
import net.sf.sprockets.view.ViewHolder;
import net.sf.sprockets.widget.GridCard;
import net.sf.sprockets.widget.ResourceEasyCursorAdapter;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import butterknife.Optional;

import static android.text.format.DateUtils.FORMAT_ABBREV_ALL;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static net.sf.diningout.data.Status.ACTIVE;
import static net.sf.diningout.data.Sync.Type.REVIEW;
import static net.sf.diningout.data.Sync.Type.USER;
import static net.sf.diningout.picasso.Transformations.BR;
import static net.sf.sprockets.sql.SQLite.alias_;
import static net.sf.sprockets.sql.SQLite.aliased_;
import static net.sf.sprockets.sql.SQLite.millis;

/**
 * Displays a list of notifications. Activities that attach this must implement {@link Listener}.
 */
public class NotificationsFragment extends SprocketsFragment implements LoaderCallbacks<EasyCursor>,
        OnItemClickListener {
    @InjectView(R.id.list)
    GridView mGrid;
    private Listener mListener;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mListener = (Listener) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        return inflater.inflate(R.layout.notifications_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mGrid.setAdapter(new NotificationsAdapter(mGrid));
        mGrid.setOnItemClickListener(this);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public Loader<EasyCursor> onCreateLoader(int id, Bundle args) {
        String[] proj = {SyncsJoinAll.SYNC__ID, alias_(SyncsJoinAll.SYNC_TYPE_ID),
                millis(Syncs.ACTION_ON), alias_(SyncsJoinAll.RESTAURANT__ID),
                alias_(SyncsJoinAll.RESTAURANT_NAME), alias_(SyncsJoinAll.CONTACT__ID),
                Contacts.ANDROID_LOOKUP_KEY, Contacts.ANDROID_ID, alias_(SyncsJoinAll.CONTACT_NAME),
                "coalesce(" + SyncsJoinAll.RESTAURANT_COLOR + "," + SyncsJoinAll.CONTACT_COLOR
                        + ") AS " + Columns.COLOR};
        String sel = SyncsJoinAll.SYNC_TYPE_ID + " = ? AND " + SyncsJoinAll.REVIEW_STATUS_ID
                + " = ? AND " + SyncsJoinAll.RESTAURANT_STATUS_ID + " = ? OR "
                + SyncsJoinAll.SYNC_TYPE_ID + " = ? AND " + SyncsJoinAll.CONTACT_STATUS_ID + " = ?";
        String[] selArgs = StringArrays.from(REVIEW.id, ACTIVE.id, ACTIVE.id, USER.id, ACTIVE.id);
        String order = Syncs.ACTION_ON + " DESC";
        return new EasyCursorLoader(a, SyncsJoinAll.CONTENT_URI, proj, sel, selArgs, order);
    }

    @Override
    public void onLoadFinished(Loader<EasyCursor> loader, EasyCursor c) {
        if (mGrid != null) {
            if (c.getCount() == 0 && mGrid.getEmptyView() == null) {
                View view = getView();
                mGrid.setEmptyView(((ViewStub) view.findViewById(R.id.empty)).inflate());
                ButterKnife.inject(this, view);
            }
            ((CursorAdapter) mGrid.getAdapter()).swapCursor(c);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        EasyCursor c = (EasyCursor) parent.getItemAtPosition(position);
        Type type = Type.get(c.getInt(aliased_(SyncsJoinAll.SYNC_TYPE_ID)));
        switch (type) {
            case USER:
                id = c.getLong(aliased_(SyncsJoinAll.CONTACT__ID));
                break;
            case REVIEW:
                id = c.getLong(aliased_(SyncsJoinAll.RESTAURANT__ID));
                break;
        }
        mListener.onNotificationClick(view, type, id);
    }

    /**
     * Go to {@link FriendsActivity}.
     */
    @Optional
    @OnClick(R.id.invite)
    void invite() {
        startActivity(new Intent(a, FriendsActivity.class));
        a.finish();
    }

    @Override
    public void onLoaderReset(Loader<EasyCursor> loader) {
        if (mGrid != null) {
            ((CursorAdapter) mGrid.getAdapter()).swapCursor(null);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     * Receives notifications for {@link NotificationsFragment} events.
     */
    interface Listener {
        /**
         * The notification was clicked.
         */
        void onNotificationClick(View view, Type type, long id);
    }

    /**
     * Translates notification rows to Views.
     */
    private class NotificationsAdapter extends ResourceEasyCursorAdapter {
        /**
         * Notification photo is resized according to these measurements.
         */
        private final GridCard mCard;

        private NotificationsAdapter(GridView view) {
            super(view.getContext(), R.layout.notifications_adapter, null, 0);
            mCard = new GridCard(view, R.dimen.notification_card_height);
        }

        @Override
        public void bindView(View view, Context context, EasyCursor c) {
            NotificationHolder notif = ViewHolder.get(view, NotificationHolder.class);
            Uri photo = null;
            String contact = c.getString(aliased_(SyncsJoinAll.CONTACT_NAME));
            if (contact == null) {
                contact = context.getString(R.string.non_contact);
            }
            CharSequence action = null;
            switch (Type.get(c.getInt(aliased_(SyncsJoinAll.SYNC_TYPE_ID)))) {
                case USER:
                    String androidKey = c.getString(Contacts.ANDROID_LOOKUP_KEY);
                    long androidId = c.getLong(Contacts.ANDROID_ID);
                    if (androidKey != null && androidId > 0) {
                        photo = ContactsContract.Contacts.getLookupUri(androidId, androidKey);
                    }
                    action = Html.fromHtml(context.getString(R.string.new_friend,
                            TextUtils.htmlEncode(contact)));
                    break;
                case REVIEW:
                    photo = RestaurantPhotos.uriForRestaurant(
                            c.getLong(aliased_(SyncsJoinAll.RESTAURANT__ID)));
                    action = context.getString(R.string.new_review, contact,
                            c.getString(aliased_(SyncsJoinAll.RESTAURANT_NAME)));
                    break;
            }
            Picasso.with(context).load(photo).resize(mCard.getWidth(), mCard.getHeight())
                    .centerCrop().transform(BR).placeholder(Placeholders.rect(c))
                    .into(notif.mPhoto);
            notif.mAction.setText(action);
            long now = System.currentTimeMillis();
            long when = c.getLong(Syncs.ACTION_ON);
            notif.mTime.setText(now - when > MINUTE_IN_MILLIS
                    ? DateUtils.getRelativeTimeSpanString(when, now, 0, FORMAT_ABBREV_ALL)
                    : context.getString(R.string.recent_time));
        }
    }

    public static class NotificationHolder extends ViewHolder {
        @InjectView(R.id.photo)
        ImageView mPhoto;
        @InjectView(R.id.action)
        TextView mAction;
        @InjectView(R.id.time)
        TextView mTime;

        @Override
        protected NotificationHolder newInstance() {
            return new NotificationHolder();
        }
    }
}
