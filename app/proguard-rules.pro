# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep data classes
-keepclassmembers class com.geosit.gnss.data.model.** { *; }
-keepclassmembers class com.geosit.gnss.data.gnss.** { *; }
-keep class com.geosit.gnss.data.model.** { *; }
-keep class com.geosit.gnss.data.gnss.** { *; }

# OSMDroid
-keep class org.osmdroid.** { *; }
-keep class org.apache.commons.** { *; }
-keep class org.slf4j.** { *; }
-keep class android.database.sqlite.SQLiteOpenHelper { *; }
-dontwarn org.osmdroid.**

# Kotlin
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * {
    *;
}
-keep @androidx.room.Dao class *
-keepclassmembers @androidx.room.Dao class * {
    *;
}

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.hilt.android.qualifiers.ApplicationContext class * { *; }

# USB Serial
-keep class com.hoho.android.usbserial.** { *; }
-keep class com.felhr.usbserial.** { *; }

# Compose
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }
-keep @androidx.compose.runtime.Composable class * { *; }
-dontwarn androidx.compose.material.**

# DataStore
-keep class androidx.datastore.*.** { *; }

# Navigation
-keepnames class androidx.navigation.fragment.NavHostFragment

# Timber
-assumenosideeffects class timber.log.Timber* {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Remove debug logs in release
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}

# Google Play Services
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep custom views
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Serialization
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable
-dontnote kotlinx.serialization.AnnotationsKt

# Enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}