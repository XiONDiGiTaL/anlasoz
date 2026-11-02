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
