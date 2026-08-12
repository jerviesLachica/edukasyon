# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlinx Serialization (Retrofit DTOs)
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers class com.edukasyon.studentai.core.network.** {
    <fields>;
}
-keepclassmembers class com.edukasyon.studentai.core.backup.** {
    <fields>;
}
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-if class *
-keepclassmembers class <1> {
    static ** Companion;
}
-if class **$Companion
-keepclassmembers class <2> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.**

# Hilt
-dontwarn com.google.errorprone.annotations.*

# Coil
-keep class coil.** { *; }
-keep class coil.compose.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable implementations
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    private void readObjectNoData();
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep custom views
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# Keep data classes
-keepclassmembers class ** {
    @androidx.room.* <fields>;
}

# Keep model classes (data classes used with serialization/room)
-keepclassmembers class com.edukasyon.studentai.domain.model.** {
    <fields>;
}
-keep class com.edukasyon.studentai.domain.model.** { *; }