LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

#OPENCV_CAMERA_MODULES:=off
#OPENCV_INSTALL_MODULES:=off
#OPENCV_LIB_TYPE:=SHARED
include C:\Users\alfre\Desktop\FaceRecognizer-master\opencv-android-sdk\native\jni\OpenCV.mk

LOCAL_SRC_FILES  := DetectionBasedTracker_jni.cpp

LOCAL_LDLIBS     += -llog -ldl

LOCAL_MODULE     := nativedetector

include $(BUILD_SHARED_LIBRARY)
