package com.alfre.facesmanagement;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Pair;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.Toast;

import com.alfre.R;
import com.alfre.facedetection.FaceDetectionActivity;
import com.alfre.facerecognition.AllFaceRecognizer;
import com.alfre.facesmanagement.PeopleAdapter.PeopleAdapterListener;
import com.alfre.facesmanagement.PhotoGallery.PhotoGalleryListener;
import com.alfre.facesmanagement.peopledb.PeopleDatabase;
import com.alfre.facesmanagement.peopledb.Person;
import com.alfre.facesmanagement.peopledb.Photo;
import com.alfre.utilities.FaceRecognizerMainActivity;
import com.alfre.utilities.FaceRecognizerMainActivity.OnOpenCVLoaded;
import com.alfre.utilities.PhotoAdapter;
import com.alfre.utilities.Swipeable;

public class FacesManagementFragment extends Fragment implements Swipeable,
		OnOpenCVLoaded, PeopleAdapterListener {
	private static final String TAG = "FacesManagementFragment";
	private static final int FACE_DETECTION_AND_EXTRACTION = 1;

	private boolean retrainModel;

	private FaceRecognizerMainActivity activity;
	private ExpandableListView mPeopleList;
	private PeopleAdapter mPeopleAdapter;
	private TextView addPerson, noPeopleMessage;
	private View mainLayout;
	private AllFaceRecognizer mFaceRecognizer;
	private Pair<Integer, String[]> photosToBeAdded;

	private PeopleDatabase mPeopleDatabase;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		mainLayout = inflater.inflate(R.layout.fragment_faces_management,
				container, false);

		return mainLayout;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		activity = (FaceRecognizerMainActivity) getActivity();

		mPeopleDatabase = PeopleDatabase.getInstance(activity);

		addPerson = (TextView) mainLayout
				.findViewById(R.id.faces_management_add_person);
		addPerson.setOnClickListener(addPersonListener);

		noPeopleMessage = (TextView) mainLayout
				.findViewById(R.id.faces_management_no_people);

		mPeopleList = (ExpandableListView) mainLayout
				.findViewById(R.id.faces_management_people_list);
		mPeopleAdapter = new PeopleAdapter(activity, R.layout.person_list_item,
				R.layout.person_view, mPeopleDatabase.getPeople(), this,
				mPhotoGalleryListener);

		mPeopleList.setAdapter(mPeopleAdapter);
		if (mPeopleAdapter.getGroupCount() == 0)
			noPeopleMessage.setVisibility(View.VISIBLE);

		if (activity.isOpenCVLoaded() && getUserVisibleHint())
			setupFaceRecognizer();
	}

	@Override
	public void onOpenCVLoaded() {
		if (activity != null && getUserVisibleHint()) {
			setupFaceRecognizer();
			if (retrainModel)
				retrainRecognizer();
			if (photosToBeAdded != null) {
				addPhotos(photosToBeAdded.first, photosToBeAdded.second);
				photosToBeAdded = null;
			}
		}
	}

	private void retrainRecognizer() {
		mFaceRecognizer = activity.recreateFaceRecognizer();
		mFaceRecognizer.trainWithLoading(activity, mPeopleAdapter.getPeople());
		retrainModel = false;
	}

	@Override
	public void swipeOut(boolean toRight) {
	}

	@Override
	public void swipeIn(boolean fromRight) {
		if (activity.isOpenCVLoaded())
			setupFaceRecognizer();
	}

	private void setupFaceRecognizer() {
		mFaceRecognizer = activity.getFaceRecognizer();
	}

	OnClickListener addPersonListener = new OnClickListener() {
		EditPersonDialog mEditPersonDialog;

		@Override
		public void onClick(View v) {
			mEditPersonDialog = new EditPersonDialog("", addPersonListener,
					null, addPersonListener);
			mEditPersonDialog.show(getFragmentManager(), TAG);
		}

		DialogInterface.OnClickListener addPersonListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				switch (which) {
				case DialogInterface.BUTTON_POSITIVE:
					addPerson(mEditPersonDialog.getInsertedName());
					break;
				default:
					break;
				}
			}
		};
	};

	/**
	 * Remove all the people
	 */
	public void clearPeople() {
		SparseArray<Person> people = mPeopleAdapter.getPeople();
		for (int i = 0, l = people.size(); i < l; i++)
			mPeopleDatabase.removePerson(people.keyAt(i));

		mPeopleAdapter.setPeople(null);
		noPeopleMessage.setVisibility(View.VISIBLE);

		// All person have been removed: reset the network
		mFaceRecognizer.resetModel();
	}

	/**
	 * Retrain the model if a parameter is changed
	 */
	public void recognitionSettingsChanged() {
		if (activity.isOpenCVLoaded())
			retrainRecognizer();
		else
			// the model will be trained on opencv load
			retrainModel = true;
	}

	public void addPerson(String name) {
		if (name == null || name.length() == 0) {
			Toast.makeText(activity,
					activity.getString(R.string.error_person_name_not_valid),
					Toast.LENGTH_SHORT).show();
			return;
		}

		int id = mPeopleDatabase.addPerson(name);

		if (id == -1) {
			Toast.makeText(activity,
					activity.getString(R.string.error_person_already_present),
					Toast.LENGTH_SHORT).show();
			return;
		}

		mPeopleAdapter.addPerson(id, new Person(name));

		if (mPeopleAdapter.getGroupCount() != 0)
			noPeopleMessage.setVisibility(View.GONE);
	}

	// PeopleAdapterListener
	@Override
	public void editPersonName(int id, String newName) {
		if (mPeopleAdapter.editPersonName(id, newName) == false) {
			Toast.makeText(activity,
					activity.getString(R.string.error_person_already_present),
					Toast.LENGTH_SHORT).show();
			return;
		}

		mPeopleDatabase.editPersonName(id, newName);
	}

	@Override
	public void removePerson(int id) {
		mPeopleAdapter.removePerson(id);
		if (mPeopleAdapter.getGroupCount() == 0)
			noPeopleMessage.setVisibility(View.VISIBLE);

		mPeopleDatabase.removePerson(id);

		// A person has been removed: retrain the entire network
		mFaceRecognizer.trainWithLoading(activity, mPeopleAdapter.getPeople());
	}

	public void addPhotos(int personId, String[] photoUrls) {

		for (String url : photoUrls) {
			int photoId = mPeopleDatabase.addPhoto(personId, url);
			mPeopleAdapter.addPhoto(personId, photoId, new Photo(url));
		}

		// A photo has been added: incrementally train the network
		if (mFaceRecognizer.getType().isIncrementable())
			mFaceRecognizer.incrementalTrainWithLoading(activity, photoUrls,
					personId);
		else
			mFaceRecognizer.trainWithLoading(activity,
					mPeopleAdapter.getPeople());
	}

	public void removePhoto(int personId, int photoId) {
		mPeopleAdapter.removePhoto(personId, photoId);
		mPeopleDatabase.removePhoto(personId, photoId);

		// A photo has been removed: retrain the entire network
		mFaceRecognizer.trainWithLoading(activity, mPeopleAdapter.getPeople());
	}

	public void removePhotos(int personId, List<Integer> toBeDeleted) {
		for (Integer photoId : toBeDeleted) {
			mPeopleAdapter.removePhoto(personId, photoId);
			mPeopleDatabase.removePhoto(personId, photoId);
		}

		// A photo has been removed: retrain the entire network
		mFaceRecognizer.trainWithLoading(activity, mPeopleAdapter.getPeople());
	}

	PhotoGalleryListener mPhotoGalleryListener = new PhotoGalleryListener() {

		@Override
		public void addPhoto(PhotoGallery gallery) {
			int id = (Integer) gallery.getTag();
			String name = mPeopleAdapter.getPersonById(id).getName();

			Intent mIntent = new Intent(activity, FaceDetectionActivity.class);
			mIntent.putExtra(FaceDetectionActivity.PERSON_ID, id);
			mIntent.putExtra(FaceDetectionActivity.PERSON_NAME, name);
			startActivityForResult(mIntent, FACE_DETECTION_AND_EXTRACTION);
		}

		@Override
		public void removeSelectedPhotos(PhotoGallery gallery) {
			PhotoAdapter mPhotoAdapter = (PhotoAdapter) gallery.getAdapter();
			List<Integer> toBeDeleted = new ArrayList<Integer>();

			int personId = (Integer) gallery.getTag();
			Person mPerson = mPeopleAdapter.getPersonById(personId);
			SparseArray<Photo> photos = mPerson.getPhotos();

			// i = 0 is add/delete
			for (int i = 1, l = mPhotoAdapter.getCount(); i < l; i++)
				if (mPhotoAdapter.isSelected(i))
					toBeDeleted.add(photos.keyAt(i - 1));

			removePhotos(personId, toBeDeleted);
		}

	};

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode != Activity.RESULT_OK)
			return;

		switch (requestCode) {
		case FACE_DETECTION_AND_EXTRACTION:
			Bundle extras = data.getExtras();
			if (extras == null)
				return;

			int personId = extras.getInt(FaceDetectionActivity.PERSON_ID);
			String[] photoPaths = data.getExtras().getStringArray(
					FaceDetectionActivity.PHOTO_PATHS);

			// photos cannot be loaded until opencv loaded
			photosToBeAdded = new Pair<Integer, String[]>(personId, photoPaths);
			break;
		}
	}
}
