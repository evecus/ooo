#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "SoExecutor"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jint JNICALL
Java_com_example_soexecutor_MainActivity_nativeEntry(JNIEnv* env, jclass clazz) {
    LOGD("nativeEntry called");
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_soexecutor_MainActivity_nativeExecute(JNIEnv* env, jclass clazz, jstring soPath, jstring args) {
    const char* path = env->GetStringUTFChars(soPath, nullptr);
    const char* argStr = env->GetStringUTFChars(args, nullptr);

    LOGD("Executing: %s %s", path, argStr);

    env->ReleaseStringUTFChars(soPath, path);
    env->ReleaseStringUTFChars(args, argStr);

    return 0;
}

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGD("JNI_OnLoad called");
    return JNI_VERSION_1_6;
}