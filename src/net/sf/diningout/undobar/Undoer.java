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

package net.sf.diningout.undobar;

import net.sf.diningout.data.Status;
import net.sf.diningout.provider.Contract.Columns;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Parcelable;

import com.cocosw.undobar.UndoBarController;
import com.cocosw.undobar.UndoBarController.UndoBar;
import com.cocosw.undobar.UndoBarController.UndoListener;

/**
 * Updates the status of objects, shows an {@link UndoBar}, and undoes the update on user request.
 */
public class Undoer implements UndoListener {
	private final ContentResolver mCr;
	private final Uri mContentUri;
	private final long[] mIds;
	private final Status mUndo;
	private final boolean mDirty;

	/**
	 * Show an {@link UndoBar} with the message, update the objects to the status, and revert to the
	 * undo status on user request. The change will be synchronised with the server.
	 */
	public Undoer(Activity activity, CharSequence message, Uri contentUri, long[] ids,
			Status update, Status undo) {
		this(activity, message, contentUri, ids, update, undo, true);
	}

	/**
	 * Show an {@link UndoBar} with the message, update the objects to the status, and revert to the
	 * undo status on user request.
	 * 
	 * @param dirty
	 *            false if the change does not need to be synchronised with the server
	 */
	public Undoer(Activity activity, CharSequence message, Uri contentUri, long[] ids,
			Status update, Status undo, boolean dirty) {
		mCr = activity.getContentResolver();
		mContentUri = contentUri;
		mIds = ids;
		mUndo = undo;
		mDirty = dirty;
		update(update);
		UndoBarController.show(activity, message, this);
	}

	/**
	 * Update the objects to the status.
	 */
	private void update(Status status) {
		ContentValues vals = new ContentValues(2);
		vals.put(Columns.STATUS_ID, status.id);
		if (mDirty) {
			vals.put(Columns.DIRTY, 1);
		}
		for (long id : mIds) {
			mCr.update(ContentUris.withAppendedId(mContentUri, id), vals, null, null);
		}
		onUpdate(mContentUri, mIds, status);
	}

	/**
	 * The objects have been updated to the status.
	 */
	protected void onUpdate(Uri contentUri, long[] ids, Status status) {
	}

	@Override
	public void onUndo(Parcelable token) {
		update(mUndo);
	}
}
