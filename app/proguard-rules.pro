# ==================================================
# ✅ SEMUA TETAP UTUH — TIDAK ADA YANG DIHAPUS
# ==================================================

# Humla/Mumla — SEMUA
-keep class se.lublin.humla.** { *; }
-keep interface se.lublin.humla.** { *; }
-keepclassmembers class se.lublin.humla.** { *; }

# Punya Kita
-keep class ofaid.ahmad.ptt.** { *; }
-keep class ofaid.ahmad.ptt.ofa.** { *; }

# SpongyCastle + kelas pendukung
-keep class org.spongycastle.** { *; }
-dontwarn org.spongycastle.**

-keep class javax.naming.** { *; }
-keep class javax.naming.directory.** { *; }
-dontwarn javax.naming.**
-dontwarn javax.naming.directory.**

# Pustaka lain
-keep class pl.droidsonroids.gif.** { *; }
-dontwarn pl.droidsonroids.gif.**

# Komponen aplikasi
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends androidx.fragment.app.Fragment { *; }
-keep class * extends android.content.BroadcastReceiver { *; }

# Semua metode tetap bisa dipanggil
-keepclassmembers class * {
    <methods>;
    <fields>;
    void on*(...);
}

# Sambung server
-keepclassmembers class se.lublin.humla.model.ServerInfo {
    public java.lang.String getAddress();
    public int getPort();
}
