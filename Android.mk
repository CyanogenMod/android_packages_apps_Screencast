LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES += $(call all-java-files-under, src)

# remove the dns sd txt record class which allows android studio to build, but
# causes build errors in CM
LOCAL_SRC_FILES := $(filter-out src/android/net/nsd/DnsSdTxtRecord.java, $(LOCAL_SRC_FILES))

LOCAL_PACKAGE_NAME := Screencast
LOCAL_PROGUARD_FLAG_FILES := proguard.flags
LOCAL_PRIVILEGED_MODULE := true

# Sign the package when not using test-keys
ifneq ($(DEFAULT_SYSTEM_DEV_CERTIFICATE),build/target/product/security/testkey)
LOCAL_CERTIFICATE := cyngn-app
endif

include $(BUILD_PACKAGE)
