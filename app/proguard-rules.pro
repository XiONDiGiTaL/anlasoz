# AnlaSoz keeps default optimizations; Compose and Room ship consumer rules.
# Annotation-only references pulled in by Tink (androidx.security-crypto).
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# kotlinx.serialization models are resolved reflectively only through generated serializers.
-keepclassmembers class net.xiondigital.anlasoz.** {
    *** Companion;
}
-keepclasseswithmembers class net.xiondigital.anlasoz.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# JNI: llama.cpp entry points are matched by class and method name at runtime.
-keep class net.xiondigital.anlasoz.ai.LlamaBridge { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# JNI: generate() reports token progress through this callback, looked up by name.
# Without this the minified build renames onProgress and every on-device request
# dies with NoSuchMethodError the moment the first tokens arrive.
-keep interface net.xiondigital.anlasoz.ai.LlamaBridge$TokenProgress { *; }
-keepclassmembers class * implements net.xiondigital.anlasoz.ai.LlamaBridge$TokenProgress {
    public void onProgress(int);
}
