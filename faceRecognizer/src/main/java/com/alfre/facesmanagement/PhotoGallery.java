package com.alfre.facesmanagement;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;

import com.alfre.R;
import com.alfre.facesmanagement.peopledb.Photo;
import com.alfre.utilities.NoScrollGridView;
import com.alfre.utilities.PhotoAdapter;
import com.alfre.utilities.PhotoAdapter.PhotoSelectionListener;
import com.alfre.utilities.EIMPreferences;

/**
 * A Gallery is an horizontal LinearLayout that can contain zero or more photos
 * and allows to add or delete them.
 */
public class PhotoGallery extends NoScrollGridView implements
		PhotoSelectionListener {
	private static final int colCount = 5;

	private PhotoAdapter galleryAdapter;
	private PhotoGalleryListener photoGalleryListener;
	private int photoCount;
	private Photo add, delete;
	private boolean addOrDelete;

	public PhotoGallery(Context context) {
		super(context);
		initView(context);
	}

	public PhotoGallery(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView(context);
	}

	public PhotoGallery(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initView(context);
	}

	private void initView(Context mContext) {
		setNumColumns(colCount);

		add = new Photo(null, BitmapFactory.decodeResource(
				mContext.getResources(), R.drawable.action_add_photo));
		delete = new Photo(null, BitmapFactory.decodeResource(
				mContext.getResources(), R.drawable.action_delete));

		EIMPreferences mPreferences = EIMPreferences.getInstance(mContext);
		final int rotation = ((WindowManager) mContext
				.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay()
				.getRotation();
		if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270)
			this.setNumColumns(mPreferences.numberOfGalleryColumnsLandscape());
		else
			this.setNumColumns(mPreferences.numberOfGalleryColumnsPortrait());

		galleryAdapter = new PhotoAdapter(mContext, null, this);
		addPhoto(add);
		setAdapter(galleryAdapter);

		setOnItemClickListener(galleryOnItemClickListener);
		photoCount = 0;

		showAddPhoto();
	}

	public void setPhotoGalleryListener(
			PhotoGalleryListener photoGalleryListener) {
		this.photoGalleryListener = photoGalleryListener;
	}

	private void showAddPhoto() {
		galleryAdapter.replacePhoto(0, add);
		addOrDelete = true;
	}

	private void showDeletePhoto() {
		galleryAdapter.replacePhoto(0, delete);
		addOrDelete = false;
	}

	/**
	 * Add a photo to the gallery
	 * 
	 * @param path
	 *            location of the image
	 * 
	 * @return view of the added image
	 */
	public void addPhoto(String path) {
		addPhoto(new Photo(path, null));
	}

	public void addPhoto(Photo photo) {
		if (photo == null)
			throw new IllegalArgumentException("photo cannot be null");

		galleryAdapter.addPhoto(photo);
		photoCount++;
	}

	/**
	 * Remove photo from the gallery
	 * 
	 * @param v
	 *            image to be removed
	 */
	public void removePhoto(int position) {
		if (position < 0)
			throw new IllegalArgumentException("position must be positive");

		photoCount--;
		galleryAdapter.removePhoto(position + 1);
	}

	private void removePhoto() {
		removePhoto(0);
	}

	/**
	 * Removes the images that are currently selected
	 */
	public void removeSelectedPhotos() {
		galleryAdapter.removeSelectedPhotos();
	}

	/**
	 * Remove all the images from the gallery
	 */
	public void removeAllPhotos() {
		while (photoCount > 0)
			removePhoto();
	}

	OnItemClickListener galleryOnItemClickListener = new OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position,
				long id) {
			if (position == 0) {
				if (addOrDelete)
					photoGalleryListener.addPhoto(PhotoGallery.this);
				else
					photoGalleryListener
							.removeSelectedPhotos(PhotoGallery.this);

				return;
			}

			galleryAdapter.setSelected(position,
					!galleryAdapter.isSelected(position));
		}
	};

	@Override
	public void photosSelectionChanged(boolean selected) {
		if (galleryAdapter == null)
			return;

		if (selected && galleryAdapter.getItem(0) == add)
			showDeletePhoto();
		else if (!selected && galleryAdapter.getItem(0) == delete)
			showAddPhoto();
	}

	public interface PhotoGalleryListener {

		/**
		 * A new photo must be added
		 * 
		 * @param gallery
		 *            source of the request
		 */
		public void addPhoto(PhotoGallery gallery);

		/**
		 * Selected photos must be deleted
		 * 
		 * @param gallery
		 *            source of the request
		 */
		public void removeSelectedPhotos(PhotoGallery gallery);
	}
}