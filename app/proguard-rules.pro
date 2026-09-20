# ==================================================
# 🔒 SEMUA DILINDUNGI TAPI TETAP ADA — TIDAK ADA YANG DIHAPUS!
# ==================================================

# ✅ HUMLA ASLI — SEMUA TETAP UTUH, TIDAK DIUBAH SATU PUN!
-keep class se.lublin.humla.** { *; }
-keep interface se.lublin.humla.** { *; }
-keepnames class se.lublin.humla.** { *; }
-keepclassmembers class se.lublin.humla.** { *; }

# ✅ PUNYA KITA — NAMA BOLEH BERUBAH TAPI ISI TETAP
-keepnames class ofaid.ahmad.ptt.** { *; }
-keepnames class ofaid.ahmad.ptt.ofa.** { *; }
-keepclassmembers class ofaid.ahmad.ptt.** { *; }

# ✅ PUSTAKA PENDUKUNG — SEMUA TETAP BERFUNGSI
-keep class org.spongycastle.** { *; }
-keepnames class org.spongycastle.** { *; }
-keep class pl.droidsonroids.gif.** { *; }
-dontwarn pl.droidsonroids.gif.**

# ✅ FUNGSI SAMBUNG SERVER — TIDAK BOLEH HILANG!
-keepclassmembers class se.lublin.humla.model.ServerInfo {
    public java.lang.String getAddress();
    public int getPort();
}
-keepclassmembers class se.lublin.humla.model.** { *; }

# ✅ SEMUA METODE & BAGIAN YANG DIJALANKAN — TETAP ADA!
-keepclasseswithmembers class * {
    public <init>(android.content.Context);
}
-keepclassmembers class * {
    void on*(...);
    <methods>;
    <fields>;
}

# ✅ LAYANAN & AKTIVITAS — TETAP BISA DIBUKA
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends androidx.fragment.app.Fragment { *; }

# ✅ PELACAKAN
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SumberOFAID
