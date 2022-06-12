package com.alfre.facerecognition;

import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import android.app.Fragment;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.alfre.R;
import com.alfre.facedetection.FaceDetector;
import com.alfre.facesmanagement.peopledb.PeopleDatabase;
import com.alfre.facesmanagement.peopledb.Person;
import com.alfre.utilities.EIMPreferences;
import com.alfre.utilities.FaceRecognizerMainActivity;
import com.alfre.utilities.FaceRecognizerMainActivity.OnOpenCVLoaded;
import com.alfre.utilities.Swipeable;

public class FaceRecognitionFragment extends Fragment implements Swipeable,
		OnOpenCVLoaded, CvCameraViewListener2, SeekBar.OnSeekBarChangeListener {

	private static final String TAG = "FaceRecognitionFragment";
	private static final Scalar FACE_RECT_COLOR = new Scalar(23, 150, 0, 255);
	private static final Scalar FACE_UNKNOWN_RECT_COLOR = new Scalar(240, 44,
			0, 255);

	private static int mDistanceThreshold;

	public enum Type {
		LBPH, FISHER, EIGEN
	}

	private FaceRecognizerMainActivity activity;

	private ControlledJavaCameraView mCameraView;

	private int mCurrentCameraIndex = ControlledJavaCameraView.CAMERA_ID_BACK;

	private Mat mRgba;
	private Mat mSceneForRecognizer;
	private LabelledRect[] mLabelsForDrawer = new LabelledRect[0];

	private SparseArray<Mat> thumbnails;
	private int mThumbnailSize = 25;
	private int mHeight;
	public boolean mMultithread = true;

	private FaceDetector mFaceDetector;
	private AllFaceRecognizer mFaceRecognizer;
	private PeopleDatabase mPeopleDatabase;

	private SeekBar mThresholdBar;
	private TextView mThresholdTextView;
	private ImageButton mSwitchButton;
	private int mMaxThreshold;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		return inflater.inflate(R.layout.fragment_face_recognition, container,
				false);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		activity = (FaceRecognizerMainActivity) getActivity();

		mPeopleDatabase = PeopleDatabase.getInstance(activity);

		mDistanceThreshold = EIMPreferences.getInstance(activity)
				.recognitionThreshold();

		mCameraView = (ControlledJavaCameraView) activity
				.findViewById(R.id.face_recognition_surface_view);
		mCameraView.setCvCameraViewListener(this);
		mCameraView.setCameraIndex(mCurrentCameraIndex);

		mSwitchButton = (ImageButton) activity
				.findViewById(R.id.switch_camera_button);
		mSwitchButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mCurrentCameraIndex == ControlledJavaCameraView.CAMERA_ID_BACK)
					mCurrentCameraIndex = ControlledJavaCameraView.CAMERA_ID_FRONT;
				else
					mCurrentCameraIndex = ControlledJavaCameraView.CAMERA_ID_BACK;
				mCameraView.disableView();
				mCameraView.setCameraIndex(mCurrentCameraIndex);
				mCameraView.enableView();
			}
		});

		mThresholdTextView = (TextView) activity
				.findViewById(R.id.threshold_text);
		mThresholdBar = (SeekBar) activity.findViewById(R.id.threshold_bar);
		mThresholdBar.setOnSeekBarChangeListener(this);
		mThresholdBar.setProgress(mDistanceThreshold);
		mMaxThreshold = mThresholdBar.getMax();

		thumbnails = new SparseArray<Mat>();
	}

	@Override
	public void swipeIn(boolean right) {
		if (activity.isOpenCVLoaded()) {
			setupFaceRecognition();
			mCameraView.enableView();
		}
	}

	@Override
	public void swipeOut(boolean right) {
		mCameraView.disableView();

		if (thumbnails != null) {
			for (int i = 0, l = thumbnails.size(); i < l; i++)
				thumbnails.valueAt(i).release();

			thumbnails.clear();
		}

		mFaceRecognizer = null;
	}

	@Override
	public void onResume() {
		super.onResume();
		if (activity.isOpenCVLoaded() && getUserVisibleHint())
			mCameraView.enableView();
	}

	@Override
	public void onPause() {
		if (activity.isOpenCVLoaded())
			mCameraView.disableView();

		super.onPause();
	}

	public void onOpenCVLoaded() {
		if (mCameraView != null && getUserVisibleHint())
			mCameraView.enableView();
	}

	@Override
	public void onCameraViewStarted(int width, int height) {
		setupFaceRecognition();

		mRgba = new Mat();
		mSceneForRecognizer = new Mat();

		mHeight = height;

		mSwitchButton.setEnabled(true);
		mThresholdBar.setEnabled(false);

		mRecognitionThread = new Thread(mRecognitionWorker);
		mRecognitionThread.start();
	}

	@Override
	public void onCameraViewStopped() {
		mSwitchButton.setEnabled(false);
		mThresholdBar.setEnabled(false);

		if (mRecognitionThread != null)
			mRecognitionThread.interrupt();

		mSceneForRecognizer.release();
		mRgba.release();
	}

	private Thread mRecognitionThread = null;
	private Runnable mRecognitionWorker = new Runnable() {
		@Override
		public void run() {
			if (!mMultithread)
				return;
			Mat myMat = new Mat();
			while (!Thread.interrupted()) {
				mSceneForRecognizer.copyTo(myMat);
				Rect[] facesArray = mFaceDetector.detect(mSceneForRecognizer);
				mLabelsForDrawer = recognizeFaces(myMat, facesArray);
			}
			myMat.release();
			mRecognitionThread = null;
		}
	};

	@Override
	public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

		mRgba = inputFrame.rgba();
		mSceneForRecognizer = inputFrame.gray();

		if (mCurrentCameraIndex == ControlledJavaCameraView.CAMERA_ID_FRONT)
			Core.flip(mRgba, mRgba, 1);

		if (!mMultithread) {
			Rect[] facesArray = mFaceDetector.detect(mSceneForRecognizer);
			mLabelsForDrawer = recognizeFaces(mSceneForRecognizer, facesArray);
		}

		for (LabelledRect faceAndLabel : mLabelsForDrawer) {
			drawLabel(mRgba, faceAndLabel);
		}

		return mRgba;
	}

	private LabelledRect[] recognizeFaces(Mat scene, Rect[] facesArray) {

		LabelledRect[] recognizedPeople = new LabelledRect[facesArray.length];

		for (int i = 0; i < facesArray.length; i++) {
			Rect faceRect = facesArray[i];

			try {
				Mat face = scene.submat(faceRect);
				int[] predictedLabel = new int[1];
				double[] distance = new double[1];

				mFaceRecognizer.predict(face, predictedLabel, distance);
				Log.v(TAG, "predictedLabel: " + predictedLabel[0]
						+ " distance: " + distance[0]);
				face.release();

				if (mCurrentCameraIndex == ControlledJavaCameraView.CAMERA_ID_FRONT)
					faceRect.x = scene.cols() - (faceRect.x + faceRect.width);

				int confidence = (int) (100 * (1 - distance[0] / mMaxThreshold));

				// Inserire dati relativi all'utente
				Person guess = mPeopleDatabase.getPerson(predictedLabel[0]);				
				if (guess == null) {
					recognizedPeople[i] = new LabelledRect(faceRect, null, null, confidence);
					continue;
				}
				
				//modificato
				if (distance[0] < mDistanceThreshold) //soglia
					recognizedPeople[i] = new LabelledRect(faceRect,
							guess.getName(), getThumbnail(predictedLabel[0]), confidence);
				else
					recognizedPeople[i] = new LabelledRect(faceRect,
							guess.getName()
									+ " "
									+ String.valueOf(((Double) distance[0])
											.intValue()), null, confidence);
			} catch (CvException e) {
				Log.e(TAG, "faceRect in " + faceRect.x + ", " + faceRect.y
						+ " " + faceRect.width + "x" + faceRect.height);
				e.printStackTrace();
			}
		}

		return recognizedPeople;
	}

	private void drawLabel(Mat frame, LabelledRect info) {
		if (info == null)
			return;

		Scalar color = (info.thumbnail == null) ? FACE_UNKNOWN_RECT_COLOR
				: FACE_RECT_COLOR;
		
		int borderWidth = 4;

		// Bounding box
		Core.rectangle(frame, info.rect.tl(), info.rect.br(), color, 3);
		
		// Confidence Level
		/*int meterXStart = info.rect.x + info.rect.width;
		int meterXEnd = (int) (info.rect.x + info.rect.width*1.05);
		int meterYStart = info.rect.y;
		int meterYEnd = info.rect.y + info.rect.height;
		
		int meterYStop = meterYEnd - info.confidence * info.rect.height / 100;
		int meterYThreshold = meterYEnd - (int) ((1 - (double) mDistanceThreshold / mMaxThreshold) * info.rect.height);
		
		meterYThreshold = (meterYEnd < meterYThreshold) ? meterYEnd : meterYThreshold;
		
		Point emptyStart = new Point(meterXStart, meterYStart);
		Point emptyEnd = new Point(meterXEnd - borderWidth/2, meterYStop);
		
		Point thresholdStart = new Point(meterXStart, meterYThreshold);
		Point thresholdEnd = new Point(meterXEnd, meterYThreshold);
		
		Point fillStart = new Point(meterXStart, meterYStop);
		Point fillEnd = new Point(meterXEnd, meterYEnd);
		
		Core.rectangle(frame, emptyStart, emptyEnd, color, borderWidth);
		Core.rectangle(frame, fillStart, fillEnd, color, Core.FILLED);
		Core.line(frame, thresholdStart, thresholdEnd, Scalar.all(0), borderWidth);*/
		
		if (info.text == null)
			return;

		// Text...
		int fontFace = Core.FONT_HERSHEY_COMPLEX_SMALL;
		double fontScale = 4;
		int thickness = 3;

		Size textSize = Core.getTextSize(info.text, fontFace, fontScale,
				thickness, null);

		// ... under the box centered ...
		Point textOrigin = new Point();
		textOrigin.x = info.rect.tl().x - (textSize.width - info.rect.width)
				/ 2;
		textOrigin.y = info.rect.br().y + textSize.height + 20;

		// ... with semi-transparent white background rectangle
		double padding = 20;
		Point rectangleTL = new Point(textOrigin.x, textOrigin.y
				- textSize.height);
		Point rectangleBR = new Point(textOrigin.x + textSize.width,
				textOrigin.y);

		rectangleTL.x -= padding;
		rectangleTL.y -= padding;

		rectangleBR.x += padding;
		rectangleBR.y += padding;

		//Core.rectangle(frame, rectangleTL, rectangleBR, new Scalar(255, 255,
			//	255, 150), Core.FILLED);

		Core.putText(frame, info.text, textOrigin, fontFace, fontScale, color,
				thickness);
         //aggiunta nuova riga
		/*Core.putText(frame, info.text, new Point(textOrigin.x,textOrigin.y+50), fontFace, fontScale, color,
				thickness);*/

		// Thumbnail
		if (info.thumbnail != null) {
			Rect thumbnailPosition = new Rect(info.rect.x, info.rect.y,
					mThumbnailSize, mThumbnailSize);
			info.thumbnail.copyTo(frame.submat(thumbnailPosition));
		}

	}

	private Mat getThumbnail(int id) {
		if (thumbnails.get(id) != null)
			return thumbnails.get(id);

		Bitmap mBitmap = PeopleDatabase.getInstance(activity).getPerson(id)
				.getPhotos().valueAt(0).getBitmap();
		Mat thumbnail = new Mat();
		Mat transparentThumbnail = new Mat();
		Utils.bitmapToMat(mBitmap, thumbnail);
		Mat newThumbnail = new Mat();

		double absoluteFaceSize = mHeight
				* EIMPreferences.getInstance(activity)
						.detectionMinRelativeFaceSize();
		mThumbnailSize = (int) (absoluteFaceSize * 0.6);
		Core.subtract(thumbnail, new Scalar(0, 0, 0, 100), transparentThumbnail);

		Imgproc.resize(transparentThumbnail, newThumbnail, new Size(
				mThumbnailSize, mThumbnailSize));
		thumbnail.release();
		transparentThumbnail.release();

		thumbnails.put(id, newThumbnail);
		return newThumbnail;
	}

	private void setupFaceRecognition() {
		mFaceDetector = activity.getFaceDetector();
		mFaceRecognizer = activity.getFaceRecognizer();
	}

	public class LabelledRect {
		public LabelledRect(Rect rect, String text, Mat thumbnail, int confidence) {
			super();
			this.rect = rect;
			this.text = text;
			this.thumbnail = thumbnail;
			this.confidence = confidence;
		}

		public Rect rect;
		public String text;
		public Mat thumbnail;
		public int confidence;
	}

	@Override
	public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
		mDistanceThreshold = arg1;
		mThresholdTextView.setText(String.valueOf(mDistanceThreshold));
	}

	@Override
	public void onStartTrackingTouch(SeekBar arg0) {
	}

	@Override
	public void onStopTrackingTouch(SeekBar arg0) {
	}
}
