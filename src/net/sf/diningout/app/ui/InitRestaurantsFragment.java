/*
 * Copyright 2013 pushbit <pushbit@gmail.com>
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
import static com.google.common.base.Predicates.notNull;
import static net.sf.diningout.provider.Contract.Restaurants.SEARCH_RADIUS;
import static net.sf.diningout.provider.Contract.Restaurants.SEARCH_TYPES;
import static net.sf.sprockets.google.Places.Request.NEARBY_SEARCH;
import static net.sf.sprockets.google.Places.Response.Status.OK;
import static net.sf.sprockets.view.animation.Interpolators.ACCELERATE;
import static net.sf.sprockets.view.animation.Interpolators.OVERSHOOT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import net.sf.sprockets.util.SparseArrays;
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
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.TextView;
import butterknife.InjectView;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Displays a list of local restaurants for the user to choose from. Activities that attach this
 * must implement {@link Listener}.
 */
public class InitRestaurantsFragment extends SprocketsFragment implements
		LoaderCallbacks<Response<List<Place>>>, OnItemClickListener, OnScrollListener {
	@InjectView(R.id.progress)
	View mProgress;
	@InjectView(R.id.list)
	GridView mGrid;
	@InjectView(R.id.attribs)
	TextView mAttribs;
	private Listener mListener;
	/** Three lists of restaurants that are loaded on demand. */
	private final List<List<Place>> mPlaces = new ArrayList<>(3);
	/** Next page tokens that can be used to load more restaurants when not null. */
	private final String[] mTokens = new String[2];

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		mListener = (Listener) activity;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Collections.addAll(mPlaces, null, null, null); // make room for the restaurant lists
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
		return inflater.inflate(R.layout.init_restaurants_fragment, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		mGrid.setAdapter(new RestaurantPlacesAdapter(mGrid));
		mGrid.setOnItemClickListener(this);
		mGrid.setOnScrollListener(this);
		mAttribs.setMovementMethod(LinkMovementMethod.getInstance()); // clickable hrefs
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		getLoaderManager().initLoader(0, null, this); // load first batch on Activity creation
		getLoaderManager().initLoader(1, null, this); // possibly load batches on recreation
		getLoaderManager().initLoader(2, null, this);
	}

	@Override
	public Loader<Response<List<Place>>> onCreateLoader(int id, Bundle args) {
		Activity a = getActivity();
		Params params = null;
		if (id == 0) { // get the first batch of restaurants
			mProgress.setVisibility(VISIBLE);
			params = new LocalPlacesParams(a).radius(SEARCH_RADIUS).types(SEARCH_TYPES);
		} else { // get another batch if available
			String token = mTokens[id - 1];
			if (!TextUtils.isEmpty(token)) {
				params = new Params().pageToken(token);
				mTokens[id - 1] = null; // reset so not used again
			}
		}
		return new GooglePlacesLoader<List<Place>>(a, NEARBY_SEARCH, params,
				Restaurants.searchFields());
	}

	@Override
	public void onLoadFinished(Loader<Response<List<Place>>> loader, Response<List<Place>> resp) {
		if (mGrid == null) {
			return;
		}
		int id = loader.getId();
		if (resp != null && resp.getStatus() == OK) {
			mPlaces.set(id, resp.getResult());
			/* join the batches for the full list */
			Iterable<Place> places = Iterables.concat(Iterables.filter(mPlaces, notNull()));
			((GooglePlacesAdapter) mGrid.getAdapter()).swapPlaces(Lists.newArrayList(places));
			if (id == 0 && mGrid.getAlpha() < 1.0f) { // fade in the list
				mGrid.animate().alpha(1.0f).withLayer();
			}
			mListener.onRestaurantClick(mGrid.getCheckedItemCount());
			/* animate any listings attributions into place */
			List<String> attribs = resp.getHtmlAttributions();
			if (attribs != null) {
				mAttribs.setText(Html.fromHtml(attribs.get(0)));
			}
			if (id == 0 && mAttribs.getTranslationY() > 0.0f) { // slide bottom bar up
				mAttribs.animate().translationY(0.0f).setInterpolator(OVERSHOOT)
						.setStartDelay(3300);
			}
			if (id < mTokens.length) {
				mTokens[id] = resp.getNextPageToken(); // save for future batch load
			}
		}
		if (id == 0 && mProgress.getVisibility() == VISIBLE) { // slide progress bar off screen
			mProgress.animate().translationX(mProgress.getWidth()).setInterpolator(ACCELERATE)
					.setStartDelay(2100).withEndAction(new Runnable() {
						@Override
						public void run() {
							if (mProgress != null) {
								mProgress.setVisibility(GONE);
							}
						}
					});
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		mListener.onRestaurantClick(mGrid.getCheckedItemCount());
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
	}

	@Override
	public void onScroll(AbsListView view, int first, int visible, int total) {
		if (total - first - visible <= 6) { // load more when only 6 left
			if (!TextUtils.isEmpty(mTokens[0]) && mPlaces.get(1) == null) {
				getLoaderManager().restartLoader(1, null, this);
			} else if (!TextUtils.isEmpty(mTokens[1]) && mPlaces.get(2) == null) {
				getLoaderManager().restartLoader(2, null, this);
			}
		}
	}

	/**
	 * Get the restaurants that are checked.
	 * 
	 * @return null if none are checked
	 */
	Place[] getCheckedRestaurants() {
		if (mGrid.getCheckedItemCount() > 0) {
			int[] keys = SparseArrays.trueKeys(mGrid.getCheckedItemPositions());
			Place[] places = new Place[keys.length];
			GooglePlacesAdapter adapter = (GooglePlacesAdapter) mGrid.getAdapter();
			for (int i = 0; i < keys.length; i++) {
				places[i] = adapter.getItem(keys[i]);
			}
			return places;
		}
		return null;
	}

	@Override
	public void onLoaderReset(Loader<Response<List<Place>>> loader) {
		if (mGrid != null) {
			((GooglePlacesAdapter) mGrid.getAdapter()).swapPlaces(null);
		}
		int id = loader.getId();
		mPlaces.set(id, null);
		if (id < mTokens.length) {
			mTokens[id] = null;
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mPlaces.clear();
		Arrays.fill(mTokens, null);
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mListener = null;
	}

	/**
	 * Receives notifications for {@link InitRestaurantsFragment} events.
	 */
	interface Listener {
		/**
		 * A restaurant has been chosen and the new total number of restaurants selected is
		 * provided.
		 */
		void onRestaurantClick(int total);
	}
}
