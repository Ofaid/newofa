# ==================================================
# 🔒 PUNYA KITA — DILINDUNGI (nama berubah, isi tetap)
# ==================================================
# Hanya nama disembunyikan, TAPI isinya TIDAK dibuang!
-keepnames class ofaid.ahmad.ptt.** { *; }
-keepnames class ofaid.ahmad.ptt.ofa.** { *; }

# Khusus fungsi penting — biarkan tetap bisa dipanggil
-keepclassmembers class ofaid.ahmad.ptt.ofa.OfaIdentity {
    public <methods>;
}

# ==================================================
# ✅ MUMLA ASLI — TETAP UTUH, JANGAN DIUBAH!
# ==================================================
-keep class se.lublin.humla.** { *; }
-keep interface se.lublin.humla.** { *; }
-keepnames class se.lublin.humla.** { *; }

# ==================================================
# ✅ FUNGSI SAMBUNG SERVER — WAJIB UTUH!
# ==================================================
-keepclassmembers class se.lublin.humla.model.ServerInfo {
    public java.lang.String getAddress();
    public int getPort();
}

# ==================================================
# ✅ PUSTAKA PENDUKUNG — TETAP BERFUNGSI
# ==================================================
-keep class pl.droidsonroids.gif.** { *; }
-dontwarn pl.droidsonroids.gif.**

-keep class org.spongycastle.** { *; }
-dontwarn org.spongycastle.**

# ==================================================
# ✅ PELACAKAN — BOLEH DIUBAH NAMA
# ==================================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SumberOFAID
