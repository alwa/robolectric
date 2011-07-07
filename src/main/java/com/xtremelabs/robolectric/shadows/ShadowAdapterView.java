package com.xtremelabs.robolectric.shadows;

import android.database.DataSetObserver;
import android.os.Handler;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import com.xtremelabs.robolectric.internal.Implementation;
import com.xtremelabs.robolectric.internal.Implements;
import com.xtremelabs.robolectric.internal.RealObject;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"UnusedDeclaration"})
@Implements(AdapterView.class)
public class ShadowAdapterView extends ShadowViewGroup {
    @RealObject private AdapterView realAdapterView;

    private Adapter adapter;
	private View mEmptyView;
    private AdapterView.OnItemSelectedListener onItemSelectedListener;
    private AdapterView.OnItemClickListener onItemClickListener;
    private boolean valid = false;
    private static int ignoreRowsAtEndOfList = 0;
    private int selectedPosition;
    private int itemCount = 0;

    private List<Object> previousItems = new ArrayList<Object>();

    @Implementation
    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;

        if (null != adapter) {
            adapter.registerDataSetObserver(new AdapterViewDataSetObserver());
        }

        invalidateAndScheduleUpdate();
        setSelection(0);
    }
    
    @Implementation
    public void setEmptyView(View emptyView) {
		this.mEmptyView = emptyView;
		updateEmptyStatus(adapter == null || adapter.isEmpty());
    }

    @Implementation
    public int getPositionForView(android.view.View view) {
        while(view.getParent() != null && view.getParent() != realView) {
            view = (View) view.getParent();
        }

        for (int i = 0; i < getChildCount(); i++) {
            if (view == getChildAt(i)) {
                return i;
            }
        }

        return AdapterView.INVALID_POSITION;
    }

    private void invalidateAndScheduleUpdate() {
        valid = false;
        itemCount = adapter == null ? 0 : adapter.getCount();
        updateEmptyStatus(itemCount == 0);
        
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                if (!valid) {
                    update();
                    valid = true;
                }
            }
        });
    }
    
    private void updateEmptyStatus(boolean empty) {
    	// code taken from the real AdapterView and commented out where not (yet?) applicable
    	
    	// we don't deal with filterMode yet...
//        if (isInFilterMode()) {
//            empty = false;
//        }

        if (empty) {
            if (mEmptyView != null) {
                mEmptyView.setVisibility(View.VISIBLE);
                setVisibility(View.GONE);
            } else {
                // If the caller just removed our empty view, make sure the list view is visible
                setVisibility(View.VISIBLE);
            }

            // leave layout for the moment...
//            // We are now GONE, so pending layouts will not be dispatched.
//            // Force one here to make sure that the state of the list matches
//            // the state of the adapter.
//            if (mDataChanged) {
//                this.onLayout(false, mLeft, mTop, mRight, mBottom);
//            }
        } else {
            if (mEmptyView != null) mEmptyView.setVisibility(View.GONE);
            setVisibility(View.VISIBLE);
        }
    }

    /**
     * Check if our adapter's items have changed without {@code onChanged()} or {@code onInvalidated()} having been called.
     *
     * @return true if the object is valid, false if not
     * @throws RuntimeException if the items have been changed without notification
     */
    public boolean checkValidity() {
        update();
        return valid;
    }

    /**
     * Set to avoid calling getView() on the last row(s) during validation. Useful if you are using a special
     * last row, e.g. one that goes and fetches more list data as soon as it comes into view. This sets a static
     * on the class, so be sure to call it again and set it back to 0 at the end of your test.
     *
     * @param countOfRows The number of rows to ignore at the end of the list.
     * @see com.xtremelabs.robolectric.shadows.ShadowAdapterView#checkValidity()
     */
    public static void ignoreRowsAtEndOfListDuringValidation(int countOfRows) {
        ignoreRowsAtEndOfList = countOfRows;
    }

    /**
     * Non-Android accessor.
     *
     * @return the index of the selected item
     */
    @Implementation
    public int getSelectedItemPosition() {
        return selectedPosition;
    }

    @Implementation
    public Object getSelectedItem() {
        int pos = getSelectedItemPosition();
        return getItemAtPosition(pos);
    }

    @Implementation
    public Adapter getAdapter() {
        return adapter;
    }

    @Implementation
    public int getCount() {
        return itemCount;
    }

    @Implementation
    public void setOnItemSelectedListener(AdapterView.OnItemSelectedListener listener) {
        this.onItemSelectedListener = listener;
    }

    @Implementation
    public final AdapterView.OnItemSelectedListener getOnItemSelectedListener() {
        return onItemSelectedListener;
    }

    @Implementation
    public void setOnItemClickListener(AdapterView.OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    @Implementation
    public final AdapterView.OnItemClickListener getOnItemClickListener() {
        return onItemClickListener;
    }

    @Implementation
    public Object getItemAtPosition(int position) {
        Adapter adapter = getAdapter();
        return (adapter == null || position < 0) ? null : adapter.getItem(position);
    }

    @Implementation
    public long getItemIdAtPosition(int position) {
        Adapter adapter = getAdapter();
        return (adapter == null || position < 0) ? AdapterView.INVALID_ROW_ID : adapter.getItemId(position);
    }

    @Implementation
    public void setSelection(final int position) {
        selectedPosition = position;
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                if (onItemSelectedListener != null) {
                    onItemSelectedListener.onItemSelected(realAdapterView, getChildAt(position), position, getAdapter().getItemId(position));
                }
            }
        });
    }

    @Implementation
    public boolean performItemClick(View view, int position, long id) {
        if (onItemClickListener != null) {
            onItemClickListener.onItemClick(realAdapterView, view, position, id);
            return true;
        }
        return false;
    }
    
    @Implementation
    public View getEmptyView() {
    	return mEmptyView;
    }

    private void update() {
        removeAllViews();

        Adapter adapter = getAdapter();
        if (adapter != null) {
            if (valid && (previousItems.size() - ignoreRowsAtEndOfList != adapter.getCount() - ignoreRowsAtEndOfList)) {
                throw new ArrayIndexOutOfBoundsException("view is valid but adapter.getCount() has changed from " + previousItems.size() + " to " + adapter.getCount());
            }

            List<Object> newItems = new ArrayList<Object>();
            for (int i = 0; i < adapter.getCount() - ignoreRowsAtEndOfList; i++) {
                newItems.add(adapter.getItem(i));
                addView(adapter.getView(i, null, realAdapterView));
            }

            if (valid && !newItems.equals(previousItems)) {
                throw new RuntimeException("view is valid but current items <" + newItems + "> don't match previous items <" + previousItems + ">");
            }
            previousItems = newItems;
        }
    }

    /**
     * Simple default implementation of {@code android.database.DataSetObserver}
     */
    protected class AdapterViewDataSetObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            invalidateAndScheduleUpdate();
        }

        @Override
        public void onInvalidated() {
            invalidateAndScheduleUpdate();
        }
    }
}
