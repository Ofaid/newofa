# PAKSA SEMUA CLASS TETAP MASUK (ANTI-DISCARD)
-keep class * { *; }
-keepclassmembers class * { *; }
-keepresources *

# Keep SpongyCastle & NetCipher (ATURAN LAMA CAK TETAP DIPAKAI)
-keep class org.spongycastle.** { *; }
-keep class info.guardianproject.netcipher.** { *; }
-dontwarn org.spongycastle.**
-dontwarn info.guardianproject.**

# Keep Humla/Mumla Core
-keep class se.lublin.humla.** { *; }
-keep class se.lublin.mumla.** { *; }
-keep class ofaid.ahmad.ptt.** { *; }

# Keep Enum & Parcelable
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}
