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

import static android.content.Intent.ACTION_SENDTO;
import static android.provider.BaseColumns._ID;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static net.sf.diningout.app.ReviewsService.EXTRA_ID;
import static net.sf.diningout.data.Status.ACTIVE;
import static net.sf.diningout.picasso.OverlayTransformation.UP;
import static net.sf.diningout.provider.Contract.ACTION_CONTACTS_SYNCED;
import static net.sf.diningout.provider.Contract.ACTION_CONTACTS_SYNCING;
import static net.sf.diningout.provider.Contract.SYNC_EXTRAS_CONTACTS_ONLY;
import static net.sf.sprockets.view.animation.Interpolators.ACCELERATE;
import static net.sf.sprockets.view.animation.Interpolators.ANTICIPATE;
import static net.sf.sprockets.view.animation.Interpolators.OVERSHOOT;
import icepick.Icicle;

import java.util.ArrayList;
import java.util.List;

import net.sf.diningout.R;
import net.sf.diningout.accounts.Accounts;
import net.sf.diningout.app.ReviewsService;
import net.sf.diningout.provider.Contract;
import net.sf.diningout.provider.Contract.Contacts;
import net.sf.sprockets.app.ui.SprocketsFragment;
import net.sf.sprockets.content.Content;
import net.sf.sprockets.content.Intents;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.database.ReadCursor;
import net.sf.sprockets.net.Uris;
import net.sf.sprockets.util.SparseArrays;
import net.sf.sprockets.view.ViewHolder;
import net.sf.sprockets.widget.GridCard;

import org.apache.commons.collections.primitives.ArrayLongList;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Intents.Insert;
import android.provider.ContactsContract.RawContacts;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
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
import butterknife.InjectView;

import com.squareup.picasso.Picasso;

/**
 * Displays contacts to follow and invite to join. Activities that attach this must implement
 * {@link Listener}.
 */
public class FriendsFragment extends SprocketsFragment implements LoaderCallbacks<Cursor>,
		OnItemClickListener {
	/** True if the user is initialising the app. */
	@Icicle
	boolean mInit;
	@InjectView(R.id.header)
	ViewStub mHeader;
	@InjectView(R.id.progress)
	View mProgress;
	@InjectView(R.id.list)
	GridView mGrid;
	private Listener mListener;
	private Receiver mReceiver;

	/**
	 * Create an instance that runs in app initialisation mode.
	 */
	public static FriendsFragment newInstance(boolean init) {
		FriendsFragment frag = new FriendsFragment();
		frag.mInit = init;
		return frag;
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		mListener = (Listener) activity;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mReceiver = new Receiver();
		IntentFilter filter = new IntentFilter(ACTION_CONTACTS_SYNCING);
		filter.addAction(ACTION_CONTACTS_SYNCED);
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mReceiver, filter);
		setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
		return inflater.inflate(R.layout.friends_fragment, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		if (mInit) {
			mHeader.inflate();
		} else { // no header, list needs parent margins
			int margin = getResources().getDimensionPixelOffset(R.dimen.cards_parent_margin);
			mGrid.setPadding(margin, margin, margin, margin);
		}
		mGrid.setAdapter(new FriendsAdapter());
		mGrid.setOnItemClickListener(this);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public void onResume() {
		super.onResume();
		Bundle extras = new Bundle();
		extras.putBoolean(SYNC_EXTRAS_CONTACTS_ONLY, true);
		Content.requestSyncNow(Accounts.selected(), Contract.AUTHORITY, extras);
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		String[] proj = { _ID, Contacts.GLOBAL_ID, Contacts.ANDROID_LOOKUP_KEY,
				Contacts.ANDROID_ID, Contacts.NAME, Contacts.EMAIL, Contacts.FOLLOWING };
		String sel = Contacts.STATUS_ID + " = ?";
		String[] selArgs = { String.valueOf(ACTIVE.id) };
		String order = Contacts.GLOBAL_ID + " IS NULL, " + Contacts.NAME + ", " + Contacts.EMAIL;
		return new CursorLoader(getActivity(), Contacts.CONTENT_URI, proj, sel, selArgs, order);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		if (mGrid != null) {
			((CursorAdapter) mGrid.getAdapter()).swapCursor(new ReadCursor(data));
			mListener.onFriendClick(mGrid.getCheckedItemCount());
		}
	}

	/** Add a new contact. */
	private static final Intent sAddIntent = new Intent(Insert.ACTION).setType(
			RawContacts.CONTENT_TYPE).putExtra("finishActivityOnSaveCompleted", true);

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		if (mListener.onFriendsOptionsMenu()) {
			inflater.inflate(R.menu.friends, menu);
			if (!Intents.hasActivity(getActivity(), sAddIntent)) {
				menu.removeItem(R.id.add);
			}
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.add:
			startActivity(sAddIntent);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		/* slide out text views, update their values, slide them back in */
		final FriendHolder friend = FriendHolder.from(view);
		final String name; // contact name or email address if clicked to invite
		EasyCursor c = (EasyCursor) mGrid.getItemAtPosition(position);
		final boolean isUser = !c.isNull(Contacts.GLOBAL_ID);
		final boolean isChecked = mGrid.isItemChecked(position);
		Animator anim; // just action anim when following users, action and name anims when inviting
		Animator actionAnim = ObjectAnimator.ofFloat(friend.mAction, "translationX",
				friend.mAction.getWidth()); // slide right off screen
		if (isUser) {
			name = null; // not changing
			anim = actionAnim;
		} else {
			name = c.getString(isChecked ? Contacts.EMAIL : Contacts.NAME);
			Animator nameAnim = ObjectAnimator.ofFloat(friend.mName, "translationX",
					-view.getWidth()); // slide left off screen
			AnimatorSet set = new AnimatorSet();
			set.playTogether(actionAnim, nameAnim);
			anim = set;
		}
		anim.setInterpolator(ANTICIPATE);
		anim.addListener(new AnimatorListenerAdapter() { // update view(s) and slide back into place
			@Override
			public void onAnimationEnd(Animator anim) {
				super.onAnimationEnd(anim);
				updateAction(friend.mAction, isChecked, isUser);
				Animator actionAnim = ObjectAnimator.ofFloat(friend.mAction, "translationX", 0.0f);
				if (isUser) {
					anim = actionAnim;
				} else {
					updateName(friend.mName, name, isChecked, isUser);
					Animator nameAnim = ObjectAnimator.ofFloat(friend.mName, "translationX", 0.0f);
					AnimatorSet set = new AnimatorSet();
					set.playTogether(actionAnim, nameAnim);
					anim = set;
				}
				anim.setInterpolator(OVERSHOOT);
				anim.start();
			}
		});
		anim.start();
		mListener.onFriendClick(mGrid.getCheckedItemCount());
	}

	/**
	 * Update the name View with the new value and choose the style based on the parameters.
	 */
	private void updateName(TextView view, String name, boolean isChecked, boolean isUser) {
		if (isChecked && !isUser) { // inviting by email address
			view.setText(name.replace("@", " @ ")); // word wrap before domain
			view.setTextAppearance(getActivity(), R.style.Cell_Title_Small);
		} else {
			view.setText(name != null ? name : getString(R.string.non_contact));
			view.setTextAppearance(getActivity(), R.style.Cell_Title);
		}
	}

	/**
	 * Update the action View text and icon based on its state.
	 */
	private void updateAction(TextView view, boolean isChecked, boolean isUser) {
		if (isChecked) {
			view.setText(isUser ? R.string.following : R.string.inviting);
			view.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_accept_small, 0, 0, 0);
		} else {
			view.setText(isUser ? R.string.follow : R.string.invite);
			view.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_new_small, 0, 0, 0);
		}
	}

	/**
	 * Start an email app to send an invitation to selected contacts.
	 */
	void invite() {
		if (mGrid.getCheckedItemCount() > 0) {
			List<String> addrs = null;
			int[] keys = SparseArrays.trueKeys(mGrid.getCheckedItemPositions());
			for (int pos : keys) {
				EasyCursor c = (EasyCursor) mGrid.getItemAtPosition(pos);
				if (c.isNull(Contacts.GLOBAL_ID)) {
					if (addrs == null) {
						addrs = new ArrayList<>(keys.length);
					}
					addrs.add(c.getString(Contacts.EMAIL));
					mGrid.setItemChecked(pos, false); // don't prompt to email again
				}
			}
			if (addrs != null) {
				Intent intent = new Intent(ACTION_SENDTO, Uris.mailto(addrs, null, null,
						getString(R.string.invite_subject), getString(R.string.invite_body)));
				if (Intents.hasActivity(getActivity(), intent)) {
					startActivity(intent);
				}
			}
		}
	}

	/**
	 * Get the IDs of contacts that are chosen to be followed.
	 * 
	 * @return null if none are checked
	 */
	long[] getFollowedFriends() {
		if (mGrid.getCheckedItemCount() > 0) {
			ArrayLongList ids = null;
			int[] keys = SparseArrays.trueKeys(mGrid.getCheckedItemPositions());
			for (int pos : keys) {
				EasyCursor c = (EasyCursor) mGrid.getItemAtPosition(pos);
				if (!c.isNull(Contacts.GLOBAL_ID)) {
					if (ids == null) {
						ids = new ArrayLongList(keys.length);
					}
					ids.add(c.getLong(_ID));
				}
			}
			return ids != null ? ids.toArray() : null;
		}
		return null;
	}

	@Override
	public void onPause() {
		super.onPause();
		if (!mInit) { // otherwise selections are saved by InitActivity
			Activity a = getActivity();
			if (!a.isChangingConfigurations()) {
				invite();
			}
			/* save any changes to followed contacts (always since adapter resets checks) */
			ContentValues vals = null;
			for (int i = 0; i < mGrid.getCount(); i++) {
				EasyCursor c = (EasyCursor) mGrid.getItemAtPosition(i);
				if (!c.isNull(Contacts.GLOBAL_ID)) {
					boolean following = c.getInt(Contacts.FOLLOWING) == 1;
					boolean checked = mGrid.isItemChecked(i);
					int change = -1;
					if (!following && checked) {
						change = 1;
					} else if (following && !checked) {
						change = 0;
					}
					if (change != -1) {
						if (vals == null) {
							vals = new ContentValues(2);
							vals.put(Contacts.DIRTY, 1);
						}
						vals.put(Contacts.FOLLOWING, change);
						a.getContentResolver().update(Uris.appendId(Contacts.CONTENT_URI, c), vals,
								null, null);
						if (change == 1) {
							a.startService(new Intent(a, ReviewsService.class).putExtra(EXTRA_ID,
									c.getLong(_ID)));
						}
					}
				}
			}
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		if (mGrid != null) {
			((CursorAdapter) mGrid.getAdapter()).swapCursor(null);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mReceiver);
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mListener = null;
	}

	/**
	 * Receives notifications for {@link FriendsFragment} events.
	 */
	interface Listener {
		/**
		 * The friends options menu is being created. Return true to add the menu items or false to
		 * skip them.
		 */
		boolean onFriendsOptionsMenu();

		/**
		 * A friend has been clicked and the new total number of friends selected is provided.
		 */
		void onFriendClick(int total);
	}

	/**
	 * Translates contact rows to Views.
	 */
	private class FriendsAdapter extends ResourceCursorAdapter {
		/** Contact photo is resized according to these measurements. */
		private final GridCard mCard = new GridCard(mGrid);

		private FriendsAdapter() {
			super(getActivity(), R.layout.friends_adapter, null, 0);
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			FriendHolder friend = FriendHolder.from(view);
			/* load contact photo */
			ReadCursor c = (ReadCursor) cursor;
			String key = c.getString(Contacts.ANDROID_LOOKUP_KEY);
			long id = c.getLong(Contacts.ANDROID_ID);
			Uri uri = key != null && id > 0 ? ContactsContract.Contacts.getLookupUri(id, key)
					: null;
			Picasso.with(context).load(uri).resize(mCard.getWidth(), mCard.getHeight())
					.centerCrop().transform(UP).placeholder(R.drawable.placeholder2)
					.into(friend.mPhoto);
			/* select if user already following or deselect if unfollowed remotely */
			boolean isUser = !c.isNull(Contacts.GLOBAL_ID);
			if (isUser && !c.wasRead()) {
				mGrid.setItemChecked(c.getPosition(), c.getInt(Contacts.FOLLOWING) == 1);
			}
			boolean isChecked = mGrid.isItemChecked(c.getPosition());
			String name = c.getString(isChecked && !isUser ? Contacts.EMAIL : Contacts.NAME);
			updateName(friend.mName, name, isChecked, isUser);
			updateAction(friend.mAction, isChecked, isUser);
		}
	}

	static class FriendHolder extends ViewHolder {
		@InjectView(R.id.photo)
		ImageView mPhoto;
		@InjectView(R.id.name)
		TextView mName;
		@InjectView(R.id.action)
		TextView mAction;

		private static FriendHolder from(View view) {
			FriendHolder holder = get(view);
			return holder != null ? holder : (FriendHolder) new FriendHolder().inject(view);
		}
	}

	/**
	 * Shows the progress bar when contacts are syncing with the server and hides it when finished.
	 */
	private class Receiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (mProgress != null) {
				if (intent.getAction() == ACTION_CONTACTS_SYNCING) {
					mProgress.setVisibility(VISIBLE);
				} else { // slide progress bar off screen
					mProgress.animate().translationX(mProgress.getWidth())
							.setInterpolator(ACCELERATE).setStartDelay(900)
							.withEndAction(new Runnable() {
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
		}
	}
}