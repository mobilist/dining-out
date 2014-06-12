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

import static android.content.Intent.ACTION_DIAL;
import static android.content.Intent.ACTION_VIEW;
import static android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE;
import static net.sf.diningout.app.ui.RestaurantActivity.EXTRA_ID;
import static net.sf.diningout.picasso.OverlayTransformation.LEFT;
import net.sf.diningout.R;
import net.sf.diningout.provider.Contract.RestaurantPhotos;
import net.sf.diningout.provider.Contract.Restaurants;
import net.sf.sprockets.app.ui.SprocketsFragment;
import net.sf.sprockets.content.Intents;
import net.sf.sprockets.database.EasyCursor;
import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentUris;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import butterknife.InjectView;
import butterknife.OnClick;

import com.squareup.picasso.Callback.EmptyCallback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

/**
 * Displays a restaurant's details and reviews. The attaching Activity must have
 * {@link RestaurantActivity#EXTRA_ID} in its Intent extras.
 */
public class RestaurantFragment extends SprocketsFragment implements LoaderCallbacks<Cursor> {
	@InjectView(R.id.photo)
	ImageView mPhoto;
	@InjectView(R.id.name)
	TextView mNameView;
	@InjectView(R.id.address)
	TextView mVicinity;
	@InjectView(R.id.phone)
	TextView mLocalPhone;
	@InjectView(R.id.website)
	TextView mWebsite;
	private long mId;
	private String mGoogleUrl;
	private String mName;
	private String mAddress;
	private double mLat;
	private double mLong;
	private String mIntlPhone;
	private String mUrl;
	private boolean mPhotoLoaded;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		mId = activity.getIntent().getLongExtra(EXTRA_ID, 0L);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
		return inflater.inflate(R.layout.restaurant_fragment, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		if (RestaurantActivity.sPlaceholder != null) {
			mPhoto.setImageDrawable(RestaurantActivity.sPlaceholder);
		} else {
			mPhoto.setImageResource(R.drawable.placeholder1);
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		String[] proj = { Restaurants.GOOGLE_URL, Restaurants.NAME, Restaurants.ADDRESS,
				Restaurants.VICINITY, Restaurants.LATITUDE, Restaurants.LONGITUDE,
				Restaurants.INTL_PHONE, Restaurants.LOCAL_PHONE, Restaurants.URL };
		return new CursorLoader(getActivity(), ContentUris.withAppendedId(Restaurants.CONTENT_URI,
				mId), proj, null, null, null);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		Activity a = getActivity();
		if (mNameView != null && data.moveToFirst()) {
			@SuppressWarnings("resource")
			EasyCursor c = new EasyCursor(data);
			mGoogleUrl = c.getString(Restaurants.GOOGLE_URL);
			mName = c.getString(Restaurants.NAME);
			mNameView.setText(mName);
			mAddress = c.getString(Restaurants.ADDRESS);
			mVicinity.setText(c.getString(Restaurants.VICINITY));
			mLat = c.getDouble(Restaurants.LATITUDE);
			mLong = c.getDouble(Restaurants.LONGITUDE);
			mIntlPhone = c.getString(Restaurants.INTL_PHONE);
			mLocalPhone.setText(c.getString(Restaurants.LOCAL_PHONE));
			mUrl = c.getString(Restaurants.URL);
			if (!TextUtils.isEmpty(mUrl)) {
				mWebsite.setText(Uri.parse(mUrl).getHost());
			}
			if (TextUtils.isEmpty(a.getTitle())) { // set transparent title while tab fragments load
				SpannableString title = new SpannableString(mName);
				title.setSpan(new ForegroundColorSpan(0), 0, mName.length(),
						SPAN_INCLUSIVE_INCLUSIVE);
				a.setTitle(title);
			}
		}
		if (mPhoto != null && !mPhotoLoaded) {
			RequestCreator req = Picasso.with(a).load(RestaurantPhotos.uriForRestaurant(mId)).fit()
					.centerCrop().transform(LEFT);
			if (RestaurantActivity.sPlaceholder != null) {
				req.placeholder(RestaurantActivity.sPlaceholder);
				RestaurantActivity.sPlaceholder = null; // only use once, bounds can be reset later
			} else {
				req.placeholder(R.drawable.placeholder1);
			}
			req.error(R.drawable.placeholder1).into(mPhoto, new EmptyCallback() {
				@Override
				public void onSuccess() {
					mPhotoLoaded = true;
				}
			});
		}
	}

	@OnClick({ R.id.name, R.id.address, R.id.phone, R.id.website })
	public void onClick(TextView view) {
		Intent intent = null;
		switch (view.getId()) {
		case R.id.name:
			intent = new Intent(ACTION_VIEW, Uri.parse(mGoogleUrl != null ? mGoogleUrl
					: "https://plus.google.com/s/" + Uri.encode(mName))); // search
			break;
		case R.id.address:
			if (!TextUtils.isEmpty(mAddress)) {
				intent = new Intent(ACTION_VIEW, Uri.parse("geo:" + mLat + ',' + mLong + "?q="
						+ Uri.encode(mAddress)));
			}
			break;
		case R.id.phone:
			if (!TextUtils.isEmpty(mIntlPhone)) {
				intent = new Intent(ACTION_DIAL, Uri.parse("tel:" + Uri.encode(mIntlPhone)));
			}
			break;
		case R.id.website:
			if (!TextUtils.isEmpty(mUrl)) {
				intent = new Intent(ACTION_VIEW, Uri.parse(mUrl));
			}
			break;
		}
		if (intent != null && Intents.hasActivity(getActivity(), intent)) {
			startActivity(intent);
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
	}

	@Override
	public void onDestroyView() {
		Picasso.with(getActivity()).cancelRequest(mPhoto);
		super.onDestroyView();
	}
}
