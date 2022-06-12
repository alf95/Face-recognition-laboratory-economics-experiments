LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

include C:\Users\alfre\Desktop\FaceRecognizer-master\opencv-android-sdk\native\jni\OpenCV.mk

LOCAL_MODULE    := facerecognizer
LOCAL_SRC_FILES := facerecognizer.cpp

include $(BUILD_SHARED_LIBRARY)
