# ==================================================
# ✅ PELINDUNG OFAID — JANGAN DIUBAH!
# ==================================================

-keep class ofaid.ahmad.ptt.** { *; }
-keepnames class ofaid.ahmad.ptt.**
-keepclassmembers class ofaid.ahmad.ptt.** { *; }

-keepclassmembers class se.lublin.mumla.util.AvatarUtil { *; }
-keepclassmembers class ofaid.ahmad.ptt.util.OfaIdentity { *; }

-keep class pl.droidsonroids.gif.** { *; }
-dontwarn pl.droidsonroids.gif.**

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SumberOFAID
