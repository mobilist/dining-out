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

import static android.app.SearchManager.QUERY;
import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.ACTION_WEB_SEARCH;
import static android.content.Intent.EXTRA_SUBJECT;
import static android.content.Intent.EXTRA_TEXT;
import static android.provider.BaseColumns._ID;
import static android.widget.AbsListView.CHOICE_MODE_MULTIPLE_MODAL;
import static net.sf.diningout.data.Review.Type.PRIVATE;
import static net.sf.diningout.data.Status.ACTIVE;
import static net.sf.diningout.data.Status.DELETED;
import static net.sf.diningout.provider.Contract.AUTHORITY_URI;
import static net.sf.diningout.provider.Contract.CALL_UPDATE_RESTAURANT_LAST_VISIT;
import static net.sf.diningout.provider.Contract.CALL_UPDATE_RESTAURANT_RATING;
import static net.sf.sprockets.database.sqlite.SQLite.millis;
import icepick.Icicle;
import net.sf.diningout.R;
import net.sf.diningout.app.ui.RestaurantActivity.TabListFragment;
import net.sf.diningout.data.Review.Type;
import net.sf.diningout.data.Status;
import net.sf.diningout.provider.Contract.Contacts;
import net.sf.diningout.provider.Contract.ReviewDrafts;
import net.sf.diningout.provider.Contract.Reviews;
import net.sf.diningout.provider.Contract.ReviewsJoinContacts;
import net.sf.diningout.undobar.Undoer;
import net.sf.diningout.widget.ReviewAdapter;
import net.sf.sprockets.content.Intents;
import net.sf.sprockets.content.res.Themes;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.util.SparseArrays;
import net.sf.sprockets.util.StringArrays;
import net.sf.sprockets.view.ViewHolder;
import net.sf.sprockets.view.inputmethod.InputMethods;
import android.app.Activity;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ShareActionProvider;
import android.widget.Spinner;
import android.widget.TextView;
import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import butterknife.Optional;

/**
 * Displays a list of reviews for a restaurant. Activities that attach this may implement
 * {@link Listener} to prevent the add/edit review input method from being displayed when this
 * Fragment is not visible.
 */
public class ReviewsFragment extends TabListFragment implements LoaderCallbacks<Cursor> {
	private static final int LOADER_REVIEWS = 0;
	private static final int LOADER_REVIEW_DRAFT = 1;
	private static final int DEFAULT_RATING_POS = 2;

	@Icicle
	long mRestaurantId;
	@Icicle
	int mTypeId;
	/** True if a review is being added or edited. */
	@Icicle
	boolean mEditing;
	@Icicle
	long mReviewId;
	@Icicle
	int mRatingPos = -1;
	@Icicle
	CharSequence mComments;
	@Icicle
	long mDraftVersion = -1L;
	private Listener mListener;
	private ActionMode mActionMode;
	private final Intent mShare = new Intent(ACTION_SEND).setType("text/plain");

	/**
	 * Display the type of reviews for the restaurant.
	 */
	static ReviewsFragment newInstance(long restaurantId, Type type) {
		ReviewsFragment frag = new ReviewsFragment();
		frag.mRestaurantId = restaurantId;
		frag.mTypeId = type.id;
		return frag;
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		if (activity instanceof Listener) {
			mListener = (Listener) activity;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (mTypeId == PRIVATE.id) {
			setHasOptionsMenu(true);
		}
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		ListView list = getListView();
		list.setDrawSelectorOnTop(true);
		list.setChoiceMode(CHOICE_MODE_MULTIPLE_MODAL);
		list.setMultiChoiceModeListener(new ChoiceListener());
		setListAdapter(new ReviewAdapter(getActivity()));
		if (mEditing) {
			list.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (!isResumed()) {
						return;
					}
					editReview(TextUtils.getTrimmedLength(mComments) > 0);
				}
			}, 500L); // wait for list to settle so scroll, focus, and showing input method may work
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		LoaderManager lm = getLoaderManager();
		lm.initLoader(LOADER_REVIEWS, null, this);
		if (mTypeId == PRIVATE.id) {
			lm.initLoader(LOADER_REVIEW_DRAFT, null, this);
		}
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		Uri uri = null;
		String[] proj = null;
		String sel = null;
		String[] selArgs = null;
		String order = null;
		switch (id) {
		case LOADER_REVIEWS:
			switch (Type.get(mTypeId)) {
			case PRIVATE:
				uri = ReviewsJoinContacts.CONTENT_URI;
				proj = new String[] { ReviewsJoinContacts.REVIEW_ID, Reviews.CONTACT_ID,
						Contacts.NAME, Reviews.COMMENTS, Reviews.RATING, millis(Reviews.WRITTEN_ON) };
				sel = Reviews.RESTAURANT_ID + " = ? AND " + Reviews.TYPE_ID + " = ? AND "
						+ ReviewsJoinContacts.REVIEW_STATUS_ID + " = ?";
				break;
			case GOOGLE:
				uri = Reviews.CONTENT_URI;
				proj = new String[] { _ID, Reviews.AUTHOR_NAME, Reviews.COMMENTS, Reviews.RATING,
						millis(Reviews.WRITTEN_ON) };
				sel = Reviews.RESTAURANT_ID + " = ? AND " + Reviews.TYPE_ID + " = ? AND "
						+ Reviews.STATUS_ID + " = ?";
				break;
			}
			selArgs = StringArrays.from(mRestaurantId, mTypeId, ACTIVE.id);
			order = Reviews.WRITTEN_ON + " DESC";
			break;
		case LOADER_REVIEW_DRAFT:
			uri = ContentUris.withAppendedId(ReviewDrafts.CONTENT_URI, mRestaurantId);
			proj = new String[] { ReviewDrafts.COMMENTS, ReviewDrafts.RATING, ReviewDrafts.VERSION };
			sel = ReviewDrafts.STATUS_ID + " = ?";
			selArgs = new String[] { String.valueOf(ACTIVE.id) };
			break;
		}
		return new CursorLoader(getActivity(), uri, proj, sel, selArgs, order);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		switch (loader.getId()) {
		case LOADER_REVIEWS:
			((CursorAdapter) getListAdapter()).swapCursor(new EasyCursor(data));
			updateActionMode();
			Activity a = getActivity();
			switch (Type.get(mTypeId)) {
			case PRIVATE:
				if (!mEditing && data.getCount() == 0) {
					editReview(false);
				} else {
					a.invalidateOptionsMenu(); // show add/share now
				}
				break;
			case GOOGLE:
				ListView list = getListView();
				if (data.getCount() == 0) {
					if (list.getHeaderViewsCount() == 1) { // add header View if not already added
						View view = a.getLayoutInflater().inflate(R.layout.reviews_public_empty,
								list, false);
						list.addHeaderView(view, null, false);
						ButterKnife.inject(this, view);
					}
				} else if (list.getHeaderViewsCount() > 1) { // no longer need header View
					list.removeHeaderView(list.getAdapter().getView(1, null, list));
				}
				break;
			}
			break;
		case LOADER_REVIEW_DRAFT:
			if (data.moveToFirst()) {
				@SuppressWarnings("resource")
				EasyCursor c = new EasyCursor(data);
				long version = c.getLong(ReviewDrafts.VERSION);
				if (version != mDraftVersion) {
					EditReviewHolder review = !mEditing ? editReview(false) : getReview();
					review.mRatings.setSelection(c.getInt(ReviewDrafts.RATING) - 1);
					review.mComments.setText(c.getString(ReviewDrafts.COMMENTS));
					mDraftVersion = version;
				}
			}
			break;
		}
	}

	/**
	 * Update the ActionMode title, refresh the menu, and prepare the share Intent.
	 */
	private void updateActionMode() {
		if (mActionMode != null) {
			Activity a = getActivity();
			ListView list = getListView();
			int count = list.getCheckedItemCount();
			if (count > 0) {
				mActionMode.setTitle(getString(R.string.n_selected, count));
				mActionMode.invalidate();
				String subject = getResources().getQuantityString(R.plurals.reviews_share, count,
						a.getTitle());
				mShare.putExtra(EXTRA_SUBJECT, subject);
				StringBuilder text = new StringBuilder(512 * count);
				for (int i : SparseArrays.trueKeys(list.getCheckedItemPositions())) {
					if (text.length() > 0) { // separate sequential reviews
						text.append("\n\n");
					}
					EasyCursor c = (EasyCursor) list.getItemAtPosition(i);
					text.append(ReviewAdapter.name(a, c)).append(" (")
							.append(ReviewAdapter.time(a, c)).append(") ★")
							.append(c.getString(Reviews.RATING));
					CharSequence comments = ReviewAdapter.comments(c);
					if (comments.length() > 0) {
						text.append("\n").append(comments);
					}
				}
				mShare.putExtra(EXTRA_TEXT, text.toString());
			}
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.reviews, menu);
		if (mEditing || getListAdapter().getCount() == 0) {
			menu.findItem(R.id.add).setEnabled(false).setVisible(false);
			MenuItem share = menu.findItem(R.id.share); // external item
			if (share != null) {
				share.setEnabled(false).setVisible(false);
			}
		}
		if (!mEditing) {
			menu.findItem(R.id.done).setEnabled(false).setVisible(false);
			menu.findItem(R.id.discard).setEnabled(false).setVisible(false);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.add:
			editReview(true);
			return true;
		case R.id.done:
			item.setEnabled(false); // prevent double tap due to delayed menu invalidate
			saveReview(); // and then fall through to discard draft
		case R.id.discard:
			discardReview();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Add a header View for adding or editing a review and, optionally, scroll to it.
	 */
	private EditReviewHolder editReview(boolean scroll) {
		Activity a = getActivity();
		ListView list = getListView();
		View view = a.getLayoutInflater().inflate(R.layout.reviews_edit, list, false);
		EditReviewHolder r = EditReviewHolder.from(view);
		r.mTitle.setText(mReviewId == 0 ? R.string.add_review_title : R.string.edit_review_title);
		r.mRatings.setAdapter(ArrayAdapter.createFromResource(a, R.array.ratings, R.layout.rating));
		r.mRatings.setSelection(mRatingPos >= 0 ? mRatingPos : DEFAULT_RATING_POS);
		r.mComments.setText(mComments);
		list.addHeaderView(view, null, false);
		((BaseAdapter) getListAdapter()).notifyDataSetChanged(); // ListView only tells own Observer
		if (scroll) { // to the new header View (below ActionBar and tabs)
			list.smoothScrollToPositionFromTop(1, Themes.getActionBarSize(a) * 2 + mDividerHeight);
			r.mComments.requestFocus();
			if (mListener == null || mListener.isVisible(this)) {
				InputMethods.show(r.mComments);
			}
		}
		mEditing = true;
		a.invalidateOptionsMenu();
		return r;
	}

	/**
	 * Add a new review or update an existing one.
	 */
	private void saveReview() {
		EditReviewHolder review = getReview();
		ContentValues vals = new ContentValues(4);
		vals.put(Reviews.COMMENTS, review.mComments.getText().toString().trim());
		vals.put(Reviews.RATING, review.mRatings.getSelectedItemPosition() + 1);
		ContentResolver cr = getActivity().getContentResolver();
		String restaurantId = String.valueOf(mRestaurantId);
		if (mReviewId == 0) {
			vals.put(Reviews.RESTAURANT_ID, mRestaurantId);
			vals.put(Reviews.TYPE_ID, mTypeId);
			cr.insert(Reviews.CONTENT_URI, vals);
			cr.call(AUTHORITY_URI, CALL_UPDATE_RESTAURANT_LAST_VISIT, restaurantId, null);
		} else {
			vals.put(Reviews.DIRTY, 1);
			cr.update(ContentUris.withAppendedId(Reviews.CONTENT_URI, mReviewId), vals, null, null);
		}
		cr.call(AUTHORITY_URI, CALL_UPDATE_RESTAURANT_RATING, restaurantId, null);
	}

	/**
	 * Remove or reset the header View for adding or editing a review.
	 */
	private void discardReview() {
		final ListView view = getListView();
		InputMethods.hide(view);
		view.postDelayed(new Runnable() {
			@Override
			public void run() {
				if (!isResumed()) {
					return;
				}
				BaseAdapter adapter = (BaseAdapter) getListAdapter();
				if (adapter.getCount() > 0) {
					view.removeHeaderView(view.getAdapter().getView(1, null, view));
					adapter.notifyDataSetChanged(); // ListView only tells own Observer
					mEditing = false;
					mReviewId = 0L;
					getActivity().invalidateOptionsMenu();
				} else { // just reset it
					EditReviewHolder review = getReview();
					review.mRatings.setSelection(DEFAULT_RATING_POS);
					review.mComments.setText(null);
				}
			}
		}, 500L); // after input method hidden and cursor hopefully reloaded on slower devices
		mRatingPos = DEFAULT_RATING_POS;
		mComments = null;
		deleteDraft();
	}

	/**
	 * Get the ViewHolder of the header View for adding or editing a review.
	 * 
	 * @return null if the header View is not added
	 */
	private EditReviewHolder getReview() {
		ListView view = getListView();
		return mEditing && view.getHeaderViewsCount() > 1 ? EditReviewHolder.from(view.getAdapter()
				.getView(1, null, view)) : null;
	}

	/**
	 * Search the web for reviews of the restaurant.
	 */
	@Optional
	@OnClick(R.id.search)
	void searchWeb() {
		Activity a = getActivity();
		Intent intent = new Intent(ACTION_WEB_SEARCH).putExtra(QUERY,
				getString(R.string.reviews_public_search, a.getTitle()));
		if (Intents.hasActivity(a, intent)) {
			startActivity(intent);
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		((CursorAdapter) getListAdapter()).swapCursor(null);
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mReviewId == 0 && !getActivity().isChangingConfigurations()) { // save add review draft
			EditReviewHolder review = getReview();
			if (review != null) {
				if (TextUtils.getTrimmedLength(review.mComments.getText()) > 0) {
					saveDraft(review);
				} else {
					deleteDraft();
				}
			}
		}
	}

	private void saveDraft(EditReviewHolder review) {
		ContentResolver cr = getActivity().getContentResolver();
		ContentValues vals = draftValues(review.mComments.getText().toString(),
				review.mRatings.getSelectedItemPosition() + 1, ACTIVE);
		if (cr.update(ContentUris.withAppendedId(ReviewDrafts.CONTENT_URI, mRestaurantId), vals,
				null, null) == 0) {
			vals.put(ReviewDrafts.RESTAURANT_ID, mRestaurantId);
			cr.insert(ReviewDrafts.CONTENT_URI, vals);
		}
	}

	private void deleteDraft() {
		ContentValues vals = draftValues(null, null, DELETED);
		getActivity().getContentResolver().update(
				ContentUris.withAppendedId(ReviewDrafts.CONTENT_URI, mRestaurantId), vals, null,
				null);
	}

	private ContentValues draftValues(String comments, Integer rating, Status status) {
		ContentValues vals = new ContentValues(5);
		vals.put(ReviewDrafts.COMMENTS, comments);
		vals.put(ReviewDrafts.RATING, rating);
		vals.put(ReviewDrafts.STATUS_ID, status.id);
		vals.put(ReviewDrafts.DIRTY, 1);
		return vals;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		EditReviewHolder review = getReview();
		if (review != null) { // adapter Views not saved/restored by framework
			mRatingPos = review.mRatings.getSelectedItemPosition();
			mComments = review.mComments.getText();
		}
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mListener = null;
	}

	/**
	 * Receives notifications for {@link ReviewsFragment} events.
	 */
	interface Listener {
		/**
		 * Return true if the instance is currently visible.
		 */
		boolean isVisible(ReviewsFragment frag);
	}

	/**
	 * Manages the CAB while reviews are selected.
	 */
	private class ChoiceListener implements MultiChoiceModeListener {
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			mActionMode = mode;
			mode.getMenuInflater().inflate(R.menu.reviews_cab, menu);
			menu.findItem(R.id.edit).setEnabled(false).setVisible(false);
			ShareActionProvider share = (ShareActionProvider) menu.findItem(R.id.share)
					.getActionProvider();
			share.setShareHistoryFileName("review_share_history.xml");
			share.setShareIntent(mShare);
			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			boolean updated = false;
			if (mTypeId == PRIVATE.id) {
				ListView list = getListView();
				switch (list.getCheckedItemCount()) {
				case 1:
					if (getReview() != null) { // can't edit when already add/editing
						break;
					}
					int pos = SparseArrays.firstTrueKey(list.getCheckedItemPositions());
					if (pos < 0 || pos >= list.getCount()) { // guard against rare NPE/IOOBE
						break;
					}
					EasyCursor c = (EasyCursor) list.getItemAtPosition(pos);
					if (c != null && c.isNull(Reviews.CONTACT_ID)) {
						menu.findItem(R.id.edit).setEnabled(true).setVisible(true);
						updated = true;
					}
					break;
				case 2:
					menu.findItem(R.id.edit).setEnabled(false).setVisible(false);
					updated = true;
					break;
				}
			}
			return updated;
		}

		@Override
		public void onItemCheckedStateChanged(ActionMode mode, int pos, long id, boolean checked) {
			updateActionMode();
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			switch (item.getItemId()) {
			case R.id.edit:
				ListView list = getListView();
				EasyCursor c = (EasyCursor) list.getItemAtPosition(SparseArrays.firstTrueKey(list
						.getCheckedItemPositions()));
				mReviewId = c.getLong(_ID);
				mRatingPos = c.getInt(Reviews.RATING) - 1;
				mComments = c.getString(Reviews.COMMENTS);
				editReview(true);
				mode.finish();
				return true;
			case R.id.delete:
				long[] ids = getListView().getCheckedItemIds();
				new Undoer(getActivity(), getString(R.string.n_deleted, ids.length),
						Reviews.CONTENT_URI, ids, DELETED, ACTIVE, mTypeId == PRIVATE.id) {
					@Override
					protected void onUpdate(Uri contentUri, long[] ids, Status status) {
						super.onUpdate(contentUri, ids, status);
						ContentResolver cr = getActivity().getContentResolver();
						String id = String.valueOf(mRestaurantId);
						cr.call(AUTHORITY_URI, CALL_UPDATE_RESTAURANT_RATING, id, null);
						if (mTypeId == PRIVATE.id) {
							cr.call(AUTHORITY_URI, CALL_UPDATE_RESTAURANT_LAST_VISIT, id, null);
						}
					}
				};
				mode.finish(); // ensure mActionMode is null before updateActionMode is called
				return true;
			}
			return false;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			mActionMode = null;
		}
	}

	static class EditReviewHolder extends ViewHolder {
		@InjectView(R.id.title)
		TextView mTitle;
		@InjectView(R.id.ratings)
		Spinner mRatings;
		@InjectView(R.id.comments)
		EditText mComments;

		private static EditReviewHolder from(View view) {
			EditReviewHolder holder = get(view);
			return holder != null ? holder : (EditReviewHolder) new EditReviewHolder().inject(view);
		}
	}
}
