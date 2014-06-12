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

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.AdapterView.INVALID_POSITION;
import static net.sf.diningout.provider.Contract.Restaurants.SEARCH_TYPES;
import static net.sf.sprockets.google.Places.Params.RankBy.DISTANCE;
import static net.sf.sprockets.google.Places.Request.NEARBY_SEARCH;
import static net.sf.sprockets.view.animation.Interpolators.ACCELERATE;
import static net.sf.sprockets.view.animation.Interpolators.ANTI_OVER;
import static net.sf.sprockets.view.animation.Interpolators.OVERSHOOT;
import icepick.Icicle;

import java.util.List;

import net.sf.diningout.R;
import net.sf.diningout.provider.Contract.Restaurants;
import net.sf.diningout.widget.RestaurantPlacesAdapter;
import net.sf.sprockets.app.ui.SprocketsFragment;
import net.sf.sprockets.content.GooglePlacesLoader;
import net.sf.sprockets.google.LocalPlacesParams;
import net.sf.sprockets.google.Place;
import net.sf.sprockets.google.Places.Params;
import net.sf.sprockets.google.Places.Response;
import net.sf.sprockets.view.Views;
import net.sf.sprockets.widget.GooglePlacesAdapter;
import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Loader;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.TextView;
import butterknife.InjectView;
import butterknife.Optional;

import com.google.common.base.Predicate;

/**
 * Displays a list of nearby restaurants after {@link #filter(Predicate)} is called.
 */
public class RestaurantsNearbyFragment extends SprocketsFragment implements
		LoaderCallbacks<Response<List<Place>>>, OnItemClickListener {
	@Optional
	@InjectView(R.id.header)
	TextView mHeader;
	@InjectView(R.id.progress)
	View mProgress;
	@InjectView(R.id.empty)
	ViewStub mEmptyStub;
	@InjectView(R.id.list)
	GridView mGrid;
	@InjectView(R.id.attribs)
	TextView mAttribs;
	@Icicle
	String mSearch;
	private Listener mListener;
	private Predicate<Place> mFilter;
	private TextView mEmpty;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		if (activity instanceof Listener) {
			mListener = (Listener) activity;
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
		return inflater.inflate(R.layout.restaurants_nearby, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		if (mHeader != null && !TextUtils.isEmpty(mSearch)) { // loader data is search results
			mHeader.setText(R.string.search_results_title);
		}
		mGrid.setAdapter(new RestaurantPlacesAdapter(mGrid));
		mGrid.setOnItemClickListener(this);
		mAttribs.setMovementMethod(LinkMovementMethod.getInstance()); // clickable hrefs
	}

	/**
	 * Only display restaurants for which the filter returns true.
	 * 
	 * @param filter
	 *            may be null to not apply a filter
	 */
	void filter(Predicate<Place> filter) {
		mFilter = filter;
		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public Loader<Response<List<Place>>> onCreateLoader(int id, Bundle args) {
		mProgress.setVisibility(VISIBLE);
		Params params = new LocalPlacesParams(getActivity()).types(SEARCH_TYPES);
		if (TextUtils.isEmpty(mSearch)) {
			params.rankBy(DISTANCE);
		} else {
			params.keyword(mSearch);
		}
		if (mFilter != null) {
			params.filter(mFilter);
		}
		return new GooglePlacesLoader<List<Place>>(getActivity(), NEARBY_SEARCH, params,
				Restaurants.searchFields());
	}

	@Override
	public void onLoadFinished(Loader<Response<List<Place>>> loader, Response<List<Place>> resp) {
		if (mGrid == null) {
			return;
		}
		if (resp != null) {
			switch (resp.getStatus()) {
			case OK:
				Views.gone(mEmpty);
				GooglePlacesAdapter adapter = (GooglePlacesAdapter) mGrid.getAdapter();
				adapter.swapPlaces(resp.getResult());
				if (mListener != null) {
					int pos = mGrid.getCheckedItemPosition();
					if (pos != INVALID_POSITION) { // restore state from when it was first clicked
						mListener.onRestaurantClick(adapter.getItem(pos));
					}
				}
				if (mGrid.getAlpha() < 1.0f) { // fade in the list
					mGrid.animate().alpha(1.0f).withLayer();
				}
				/* animate any listings attributions into place */
				List<String> attribs = resp.getHtmlAttributions();
				if (attribs != null) {
					mAttribs.setText(Html.fromHtml(attribs.get(0)));
				}
				if (mAttribs.getTranslationY() > 0.0f) { // slide bottom bar up
					mAttribs.animate().translationY(0.0f).setInterpolator(OVERSHOOT)
							.setStartDelay(2700);
				}
				break;
			case ZERO_RESULTS:
				if (mEmpty == null) {
					mEmpty = (TextView) mEmptyStub.inflate();
				}
				mEmpty.setText(Html.fromHtml(getString(R.string.search_results_empty,
						TextUtils.htmlEncode(mSearch))));
				Views.visible(mEmpty);
				break;
			}
		}
		if (mProgress.getVisibility() == VISIBLE) { // slide progress bar off screen
			mProgress.animate().translationX(mProgress.getWidth()).setInterpolator(ACCELERATE)
					.setStartDelay(1500).withEndAction(new Runnable() {
						@Override
						public void run() {
							if (mProgress != null) {
								mProgress.setVisibility(GONE);
								mProgress.postDelayed(new Runnable() {
									@Override
									public void run() {
										if (mProgress != null) {
											mProgress.setTranslationX(0.0f);
										}
									}
								}, 600L); // reset after layout transition completes
							}
						}
					});
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		if (mListener != null) {
			mListener.onRestaurantClick(((GooglePlacesAdapter) mGrid.getAdapter())
					.getItem(position));
		}
	}

	/**
	 * Search for nearby restaurants with the name.
	 */
	void search(String name) {
		if (mHeader != null && TextUtils.isEmpty(mSearch)) { // flip to search results first time
			mHeader.animate().rotationXBy(360.0f).setInterpolator(ANTI_OVER).setDuration(1200L)
					.setStartDelay(900L);
			mHeader.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (mHeader != null) {
						mHeader.setText(R.string.search_results_title);
					}
				}
			}, 1500L);
		}
		mSearch = name;
		mGrid.animate().alpha(0.0f).withLayer().withEndAction(new Runnable() {
			@Override
			public void run() {
				if (mGrid != null) {
					mGrid.smoothScrollToPosition(0);
				}
			}
		});
		getLoaderManager().restartLoader(0, null, this);
	}

	@Override
	public void onLoaderReset(Loader<Response<List<Place>>> loader) {
		if (mGrid != null) {
			((GooglePlacesAdapter) mGrid.getAdapter()).swapPlaces(null);
		}
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mListener = null;
	}

	/**
	 * Receives notifications for {@link RestaurantsNearbyFragment} events.
	 */
	interface Listener {
		/**
		 * A restaurant has been chosen.
		 */
		void onRestaurantClick(Place place);
	}
}
