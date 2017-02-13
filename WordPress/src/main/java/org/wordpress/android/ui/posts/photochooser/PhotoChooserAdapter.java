package org.wordpress.android.ui.posts.photochooser;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v7.widget.RecyclerView;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.wordpress.android.R;

import java.util.ArrayList;

class PhotoChooserAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private class PhotoChooserItem {
        private long _id;
        private Uri imageUri;
    }

    private class UriList extends ArrayList<Uri> {
        private int indexOfUri(Uri imageUri) {
            for (int i = 0; i < size(); i++) {
                if (get(i).equals(imageUri)) {
                    return i;
                }
            }
            return -1;
        }
    }

    private final UriList mSelectedUris = new UriList();

    private final Context mContext;
    private final int mImageWidth;
    private final int mImageHeight;

    private boolean mIsMultiSelectEnabled;

    private final ThumbnailLoader mThumbnailLoader;
    private final PhotoChooserFragment.OnPhotoChooserListener mListener;
    private final ArrayList<PhotoChooserItem> mPhotoList = new ArrayList<>();

    private static final int VT_PHOTO   = 0;
    private static final int VT_EMPTY   = 1;

    public PhotoChooserAdapter(Context context,
                               int imageWidth,
                               int imageHeight,
                               PhotoChooserFragment.OnPhotoChooserListener listener) {
        super();
        mContext = context;
        mListener = listener;
        mImageWidth = imageWidth;
        mImageHeight = imageHeight;
        setHasStableIds(true);
        mThumbnailLoader = new ThumbnailLoader(context);
    }

    void loadDevicePhotos() {
        new BuildDevicePhotoListTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public int getItemCount() {
        if (hasPhotos()) {
            return mPhotoList.size();
        } else {
            return 1; // single VT_EMPTY cell
        }
    }

    @Override
    public long getItemId(int position) {
        int type = getItemViewType(position);
        if (type == VT_PHOTO) {
            return getPhotoItemAtPosition(position)._id;
        } else {
            return type;
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (hasPhotos()) {
            return VT_PHOTO;
        } else {
            return VT_EMPTY;
        }
    }

    private boolean hasPhotos() {
        return mPhotoList.size() > 0;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view;
        switch (viewType) {
            case VT_EMPTY:
                view = inflater.inflate(R.layout.photo_chooser_empty, parent, false);
                return new EmptyViewHolder(view);
            default:
                view = inflater.inflate(R.layout.photo_chooser_thumbnail, parent, false);
                return new PhotoViewHolder(view);
        }
    }

    /*
     * returns the photo item in the adapter at the passed position
     */
    private PhotoChooserItem getPhotoItemAtPosition(int position) {
        return mPhotoList.get(position);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof PhotoViewHolder) {
            PhotoChooserItem item = getPhotoItemAtPosition(position);
            PhotoViewHolder photoHolder = (PhotoViewHolder) holder;

            int selectedIndex = mSelectedUris.indexOfUri(item.imageUri);
            if (selectedIndex > -1) {
                photoHolder.selectedFrame.setVisibility(View.VISIBLE);
                photoHolder.txtSelectionCount.setVisibility(View.VISIBLE);
                photoHolder.txtSelectionCount.setText(Integer.toString(selectedIndex + 1));
            } else {
                photoHolder.selectedFrame.setVisibility(View.GONE);
                photoHolder.txtSelectionCount.setVisibility(View.GONE);
            }

            mThumbnailLoader.loadThumbnail(photoHolder.imgPhoto, item._id);
        }
    }

    void setMultiSelectEnabled(boolean enabled) {
        if (mIsMultiSelectEnabled == enabled) return;

        mIsMultiSelectEnabled = enabled;

        if (!enabled && getNumSelected() > 0) {
            mSelectedUris.clear();
            notifyDataSetChangedNoFade();
        }
    }

    boolean isMultiSelectEnabled() {
        return mIsMultiSelectEnabled;
    }

    /*
     * calls notifyDataSetChanged() with the ThumbnailLoader image fade-in disabled - used to
     * prevent unnecessary re-fade-in when changing existing items
     */
    private void notifyDataSetChangedNoFade() {
        mThumbnailLoader.temporarilyDisableFade();
        notifyDataSetChanged();
    }

    void togglePhotoSelection(Uri imageUri) {
        if (indexOfImageUri(imageUri) == -1) return;

        int selectedIndex = mSelectedUris.indexOfUri(imageUri);
        if (selectedIndex > -1) {
            mSelectedUris.remove(selectedIndex);
        } else {
            mSelectedUris.add(imageUri);
        }

        notifyDataSetChangedNoFade();
    }

    ArrayList<Uri> getSelectedImageURIs() {
        return mSelectedUris;
    }

    void setSelectedImageURIs(ArrayList<Uri> uriList) {
        mSelectedUris.clear();
        mSelectedUris.addAll(uriList);
        notifyDataSetChangedNoFade();
    }

    int getNumSelected() {
        return mIsMultiSelectEnabled ? mSelectedUris.size() : 0;
    }

    private int indexOfImageUri(Uri imageUri) {
        for (int i = 0; i < mPhotoList.size(); i++) {
            if (mPhotoList.get(i).imageUri.equals(imageUri)) {
                return i;
            }
        }
        return -1;
    }

    /*
     * ViewHolder containing a device photo
     */
    class PhotoViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imgPhoto;
        private final View selectedFrame;
        private final TextView txtSelectionCount;
        private final GestureDetector detector;

        public PhotoViewHolder(View view) {
            super(view);

            imgPhoto = (ImageView) view.findViewById(R.id.image_photo);
            selectedFrame = view.findViewById(R.id.selected_frame);
            txtSelectionCount = (TextView) view.findViewById(R.id.text_selection_count);

            selectedFrame.getLayoutParams().width = mImageWidth;
            selectedFrame.getLayoutParams().height = mImageHeight;

            imgPhoto.getLayoutParams().width = mImageWidth;
            imgPhoto.getLayoutParams().height = mImageHeight;

            detector = new GestureDetector(view.getContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    if (mListener != null) {
                        Uri imageUri = getPhotoItemAtPosition(getAdapterPosition()).imageUri;
                        mListener.onPhotoTapped(imageUri);
                    }
                    return true;
                }
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (mListener != null) {
                        Uri imageUri = getPhotoItemAtPosition(getAdapterPosition()).imageUri;
                        mListener.onPhotoDoubleTapped(imageUri);
                    }
                    return true;
                }
                @Override
                public void onLongPress(MotionEvent e) {
                    if (mListener != null) {
                        Uri imageUri = getPhotoItemAtPosition(getAdapterPosition()).imageUri;
                        mListener.onPhotoLongPressed(imageUri);
                    }
                }
            });

            imgPhoto.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    detector.onTouchEvent(event);
                    return true;
                }
            });
        }
    }

    /*
     * ViewHolder containing the message that appears when there are no device photos
     */
    class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(View view) {
            super(view);
            itemView.getLayoutParams().width = mImageWidth;
            itemView.getLayoutParams().height = mImageHeight;
        }
    }

    /*
     * builds the list of PhotoChooserItems from the device
     */
    private class BuildDevicePhotoListTask extends AsyncTask<Void, Void, Boolean> {
        private final ArrayList<PhotoChooserItem> tmpList = new ArrayList<>();
        private static final String ID_COL = MediaStore.Images.Media._ID;

        @Override
        protected Boolean doInBackground(Void... params) {
            String[] projection = { ID_COL };
            String orderBy = ID_COL + " DESC";

            // get external (SDCARD) images
            Cursor external = mContext.getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    orderBy);
            addImages(external, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

            // get internal images
            Cursor internal = mContext.getContentResolver().query(
                    MediaStore.Images.Media.INTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    orderBy);
            addImages(internal, MediaStore.Images.Media.INTERNAL_CONTENT_URI);

            return true;
        }

        private void addImages(Cursor cursor, Uri baseUri) {
            int idIndex = cursor.getColumnIndexOrThrow(ID_COL);
            while (cursor.moveToNext()) {
                PhotoChooserItem item = new PhotoChooserItem();
                item._id = cursor.getLong(idIndex);
                item.imageUri = Uri.withAppendedPath(baseUri, "" + item._id);
                tmpList.add(item);
            }
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            mPhotoList.clear();
            mPhotoList.addAll(tmpList);
            notifyDataSetChanged();
        }
    }
}