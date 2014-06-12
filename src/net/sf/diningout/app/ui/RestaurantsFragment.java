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

import static android.provider.BaseColumns._ID;
import static net.sf.diningout.data.Status.ACTIVE;
import static net.sf.diningout.data.Status.DELETED;
import static net.sf.sprockets.database.sqlite.SQLite.millis;
import static net.sf.sprockets.database.sqlite.SQLite.normalise;
import icepick.Icicle;
import net.sf.diningout.R;
import net.sf.diningout.provider.Contract.Restaurants;
import net.sf.diningout.undobar.Undoer;
import net.sf.diningout.view.Views;
import net.sf.diningout.widget.RestaurantCursorAdapter;
import net.sf.sprockets.app.ui.SprocketsFragment;
import net.sf.sprockets.content.LocalCursorLoader;
import net.sf.sprockets.content.Managers;
import net.sf.sprockets.database.EasyCursor;
import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CursorAdapter;
import android.widget.GridView;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import butterknife.InjectView;

import com.github.amlcurran.showcaseview.ShowcaseView.Builder;
import com.github.amlcurran.showcaseview.targets.ActionItemTarget;

/**
 * Displays a list of the user's restaurants. Activities that attach this must implement
 * {@link Listener}.
 */
public class RestaurantsFragment extends SprocketsFragment implements LoaderCallbacks<Cursor>,
		OnItemClickListener {
	/** Loader arg for the position of the selected sort option. */
	private static final String SORT = "sort";
	/** Loader arg for restaurant name to search for. */
	private static final String SEARCH_QUERY = "search_query";

	@InjectView(R.id.list)
	GridView mGrid;
	@Icicle
	Bundle mLoaderArgs;
	private Listener mListener;
	private SearchView mSearch;
	private boolean mShowcaseInserted;
	private ActionMode mActionMode;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		mListener = (Listener) activity;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getActivity().getActionBar().setIcon(R.drawable.logo); // expanded SearchView uses icon
		setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
		return inflater.inflate(R.layout.restaurants_fragment, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		mGrid.setAdapter(new RestaurantCursorAdapter(mGrid));
		mGrid.setOnItemClickListener(this);
		mGrid.setMultiChoiceModeListener(new ChoiceListener());
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		mLoaderArgs = args;
		CursorLoader loader = null;
		final String[] proj = { _ID, Restaurants.NAME, Restaurants.RATING };
		StringBuilder sel = new StringBuilder(Restaurants.STATUS_ID).append(" = ?");
		String[] selArgs;
		String order = null;
		String searchQuery = args != null ? args.getString(SEARCH_QUERY) : null;
		if (!TextUtils.isEmpty(searchQuery)) {
			sel.append(" AND ").append(Restaurants.NORMALISED_NAME).append(" LIKE ?");
			String filter = '%' + normalise(searchQuery) + '%';
			selArgs = new String[] { String.valueOf(ACTIVE.id), filter, filter.substring(1) };
			order = Restaurants.NORMALISED_NAME + " LIKE ? DESC, " + Restaurants.NAME;
		} else {
			selArgs = new String[] { String.valueOf(ACTIVE.id) };
			switch (args != null ? args.getInt(SORT) : 0) {
			case 0:
				order = Restaurants.NAME + " = '', " + Restaurants.NAME; // placeholders at end
				break;
			case 1:
				proj[2] = millis(Restaurants.LAST_VISIT_ON);
				order = Restaurants.LAST_VISIT_ON;
				break;
			case 2:
				order = Restaurants.DISTANCE + " IS NULL, " + Restaurants.DISTANCE;
				loader = new LocalCursorLoader(getActivity(), Restaurants.CONTENT_URI, proj,
						sel.toString(), selArgs, order) {
					@Override
					protected void onLocation(Location location) {
						proj[2] = Restaurants.distance(location);
					}
				};
				break;
			case 3:
				order = Restaurants.RATING + " DESC";
				break;
			}
		}
		return loader != null ? loader : new CursorLoader(getActivity(), Restaurants.CONTENT_URI,
				proj, sel.toString(), selArgs, order);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		if (mGrid != null) {
			((CursorAdapter) mGrid.getAdapter()).swapCursor(new EasyCursor(data));
			updateTitle();
			if (!mShowcaseInserted
					&& data.getCount() == 0
					&& (mLoaderArgs == null || TextUtils.isEmpty(mLoaderArgs
							.getString(SEARCH_QUERY)))) {
				Activity a = getActivity();
				new Builder(a, true).setTarget(new ActionItemTarget(a, R.id.add))
						.setContentTitle(R.string.restaurants_showcase_title)
						.setContentText(R.string.restaurants_showcase_detail).build();
				mShowcaseInserted = true; // guard against multiple loads on config change
			}
		}
	}

	/**
	 * Update the ActionMode title with the number of restaurants selected.
	 */
	private void updateTitle() {
		if (mActionMode != null) {
			int count = mGrid.getCheckedItemCount();
			if (count > 0) {
				mActionMode.setTitle(getString(R.string.n_selected, count));
			}
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		if (mListener.onRestaurantsOptionsMenu()) {
			Activity a = getActivity();
			inflater.inflate(R.menu.restaurants, menu);
			MenuItem item = menu.findItem(R.id.search);
			mSearch = Views.setBackground((SearchView) item.getActionView());
			mSearch.setSearchableInfo(Managers.search(a).getSearchableInfo(a.getComponentName()));
			mSearch.setOnQueryTextListener(new SearchListener());
			restoreSearchView(item);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.add:
			startActivity(new Intent(getActivity(), RestaurantAddActivity.class));
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Sort the restaurants by the sort option.
	 */
	void sort(int position) {
		initLoaderArgs().putInt(SORT, position);
		mLoaderArgs.remove(SEARCH_QUERY);
		getLoaderManager().restartLoader(0, mLoaderArgs, this);
		mGrid.smoothScrollToPosition(0);
	}

	/**
	 * Initialise {@link #mLoaderArgs} if it isn't already.
	 */
	private Bundle initLoaderArgs() {
		if (mLoaderArgs == null) {
			mLoaderArgs = new Bundle(2);
		}
		return mLoaderArgs;
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		if (mSearch != null) {
			mSearch.clearFocus(); // hiding input method later causes placeholder Drawable to resize
		}
		mListener.onRestaurantClick(view, id);
	}

	@Override
	public AbsListView getAbsListView() {
		return mGrid;
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
	 * Receives notifications for {@link RestaurantsFragment} events.
	 */
	interface Listener {
		/**
		 * The restaurants options menu is being created. Return true to add the menu items or false
		 * to skip them.
		 */
		boolean onRestaurantsOptionsMenu();

		/**
		 * Restaurants are being searched for names that match the query.
		 */
		void onRestaurantsSearch(String query);

		/**
		 * A restaurant has been chosen.
		 */
		void onRestaurantClick(View view, long id);
	}

	/**
	 * Filters the restaurants by name as the search query changes.
	 */
	private class SearchListener implements OnQueryTextListener {
		@Override
		public boolean onQueryTextChange(String newText) {
			mListener.onRestaurantsSearch(newText);
			initLoaderArgs().putString(SEARCH_QUERY, newText);
			getLoaderManager().restartLoader(0, mLoaderArgs, RestaurantsFragment.this);
			mGrid.smoothScrollToPosition(0);
			return false;
		}

		@Override
		public boolean onQueryTextSubmit(String query) {
			mSearch.clearFocus();
			return true;
		}
	}

	/**
	 * Manages the CAB while restaurants are selected.
	 */
	private class ChoiceListener implements MultiChoiceModeListener {
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			mActionMode = mode;
			mode.getMenuInflater().inflate(R.menu.restaurants_cab, menu);
			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false;
		}

		@Override
		public void onItemCheckedStateChanged(ActionMode mode, int position, long id,
				boolean checked) {
			updateTitle();
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			switch (item.getItemId()) {
			case R.id.delete:
				long[] ids = mGrid.getCheckedItemIds();
				new Undoer(getActivity(), getString(R.string.n_deleted, ids.length),
						Restaurants.CONTENT_URI, ids, DELETED, ACTIVE);
				return true;
			}
			return false;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			mActionMode = null;
		}
	}
}
