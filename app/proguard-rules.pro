# ==================================================
# 🔒 OFAID — DILINDUNGI, NAMA BOLEH BERUBAH
# ==================================================
-keepnames class ofaid.ahmad.ptt.** { *; }
-keepnames class ofaid.ahmad.ptt.ofa.** { *; }
-keepclassmembers class ofaid.ahmad.ptt.** { *; }

# ==================================================
# ✅ MUMLA/HUMLA — TETAP UTUH
# ==================================================
-keep class se.lublin.humla.** { *; }
-keep interface se.lublin.humla.** { *; }
-keepclassmembers class se.lublin.humla.** { *; }

# ==================================================
# ✅ SPONGYCASTLE — TETAP UTUH + TAMBAHAN KELAS
# ==================================================
-keep class org.spongycastle.** { *; }
-keepclassmembers class org.spongycastle.** { *; }
-dontwarn org.spongycastle.**

# Kelas pendukung yang hilang
-keep class javax.naming.** { *; }
-keep class javax.naming.directory.** { *; }
-dontwarn javax.naming.**
-dontwarn javax.naming.directory.**

# ==================================================
# ✅ PUSTAKA LAINNYA
# ==================================================
-keep class pl.droidsonroids.gif.** { *; }
-dontwarn pl.droidsonroids.gif.**

# ==================================================
# ✅ FUNGSI SAMBUNG SERVER
# ==================================================
-keepclassmembers class se.lublin.humla.model.** {
    public java.lang.String getAddress();
    public int getPort();
    <methods>;
}

# ==================================================
# ✅ AKTIVITAS & LAYANAN — TETAP BISA DIBUKA
# ==================================================
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

# ==================================================
# ✅ PELACAKAN
# ==================================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SumberOFAID
