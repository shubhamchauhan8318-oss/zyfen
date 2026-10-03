# Add project specific ProGuard rules here.
# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Moshi & Retrofit
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.zyfen.music.data.** { *; }
-keep class com.squareup.moshi.** { *; }
-keep class retrofit2.** { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
