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
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
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
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import net.sf.diningout.R;
import net.sf.diningout.data.Sync.Type;
import net.sf.diningout.provider.Contract.Contacts;
import net.sf.diningout.provider.Contract.RestaurantPhotos;
import net.sf.diningout.provider.Contract.Syncs;
import net.sf.diningout.provider.Contract.SyncsJoinAll;
import net.sf.sprockets.app.ui.SprocketsFragment;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.database.sqlite.SQLite;
import net.sf.sprockets.util.StringArrays;
import net.sf.sprockets.view.ViewHolder;
import net.sf.sprockets.widget.GridCard;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import butterknife.Optional;

import static android.text.format.DateUtils.FORMAT_ABBREV_ALL;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static net.sf.diningout.data.Status.ACTIVE;
import static net.sf.diningout.data.Sync.Type.REVIEW;
import static net.sf.diningout.data.Sync.Type.USER;
import static net.sf.diningout.picasso.OverlayTransformation.DOWN;
import static net.sf.sprockets.database.sqlite.SQLite.alias;
import static net.sf.sprockets.database.sqlite.SQLite.aliased;

/**
 * Displays a list of notifications. Activities that attach this must implement {@link Listener}.
 */
public class NotificationsFragment extends SprocketsFragment implements LoaderCallbacks<Cursor>,
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
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String[] proj = {SyncsJoinAll.SYNC__ID, alias(SyncsJoinAll.SYNC_TYPE_ID),
                SQLite.millis(Syncs.ACTION_ON), alias(SyncsJoinAll.RESTAURANT__ID),
                alias(SyncsJoinAll.RESTAURANT_NAME), alias(SyncsJoinAll.CONTACT__ID),
                Contacts.ANDROID_LOOKUP_KEY, Contacts.ANDROID_ID, alias(SyncsJoinAll.CONTACT_NAME)};
        String sel = SyncsJoinAll.SYNC_TYPE_ID + " = ? AND " + SyncsJoinAll.REVIEW_STATUS_ID
                + " = ? AND " + SyncsJoinAll.RESTAURANT_STATUS_ID + " = ? OR "
                + SyncsJoinAll.SYNC_TYPE_ID + " = ? AND " + SyncsJoinAll.CONTACT_STATUS_ID + " = ?";
        String[] selArgs = StringArrays.from(REVIEW.id, ACTIVE.id, ACTIVE.id, USER.id, ACTIVE.id);
        String order = Syncs.ACTION_ON + " DESC";
        return new CursorLoader(getActivity(), SyncsJoinAll.CONTENT_URI, proj, sel, selArgs, order);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (mGrid != null) {
            if (data.getCount() == 0 && mGrid.getEmptyView() == null) {
                View view = getView();
                mGrid.setEmptyView(((ViewStub) view.findViewById(R.id.empty)).inflate());
                ButterKnife.inject(this, view);
            }
            ((CursorAdapter) mGrid.getAdapter()).swapCursor(new EasyCursor(data));
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        EasyCursor c = (EasyCursor) parent.getItemAtPosition(position);
        Type type = Type.get(c.getInt(aliased(SyncsJoinAll.SYNC_TYPE_ID)));
        switch (type) {
            case USER:
                id = c.getLong(aliased(SyncsJoinAll.CONTACT__ID));
                break;
            case REVIEW:
                id = c.getLong(aliased(SyncsJoinAll.RESTAURANT__ID));
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
        Activity a = getActivity();
        startActivity(new Intent(a, FriendsActivity.class));
        a.finish();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
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
    private class NotificationsAdapter extends ResourceCursorAdapter {
        /**
         * Notification photo is resized according to these measurements.
         */
        private final GridCard mCard;

        private NotificationsAdapter(GridView view) {
            super(view.getContext(), R.layout.notifications_adapter, null, 0);
            mCard = new GridCard(view, R.dimen.notification_card_height);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            NotificationHolder notif = NotificationHolder.from(view);
            EasyCursor c = (EasyCursor) cursor;
            Uri photo = null;
            int placeholder = 0;
            String contact = c.getString(aliased(SyncsJoinAll.CONTACT_NAME));
            if (contact == null) {
                contact = context.getString(R.string.non_contact);
            }
            CharSequence action = null;
            switch (Type.get(c.getInt(aliased(SyncsJoinAll.SYNC_TYPE_ID)))) {
                case USER:
                    String androidKey = c.getString(Contacts.ANDROID_LOOKUP_KEY);
                    long androidId = c.getLong(Contacts.ANDROID_ID);
                    if (androidKey != null && androidId > 0) {
                        photo = ContactsContract.Contacts.getLookupUri(androidId, androidKey);
                    }
                    placeholder = R.drawable.placeholder2;
                    action = Html.fromHtml(context.getString(R.string.new_friend,
                            TextUtils.htmlEncode(contact)));
                    break;
                case REVIEW:
                    photo = RestaurantPhotos.uriForRestaurant(
                            c.getLong(aliased(SyncsJoinAll.RESTAURANT__ID)));
                    placeholder = R.drawable.placeholder1;
                    action = context.getString(R.string.new_review, contact,
                            c.getString(aliased(SyncsJoinAll.RESTAURANT_NAME)));
                    break;
            }
            Picasso.with(context).load(photo).resize(mCard.getWidth(), mCard.getHeight())
                    .centerCrop().transform(DOWN).placeholder(placeholder).into(notif.mPhoto);
            notif.mAction.setText(action);
            long now = System.currentTimeMillis();
            long when = c.getLong(Syncs.ACTION_ON);
            notif.mTime.setText(now - when > MINUTE_IN_MILLIS
                    ? DateUtils.getRelativeTimeSpanString(when, now, 0, FORMAT_ABBREV_ALL)
                    : context.getString(R.string.recent_time));
        }
    }

    static class NotificationHolder extends ViewHolder {
        @InjectView(R.id.photo)
        ImageView mPhoto;
        @InjectView(R.id.action)
        TextView mAction;
        @InjectView(R.id.time)
        TextView mTime;

        static NotificationHolder from(View view) {
            NotificationHolder holder = get(view);
            return holder != null ? holder
                    : (NotificationHolder) new NotificationHolder().inject(view);
        }
    }
}
