# Reglas de ofuscación (R8).
-keep class com.cyberagent.app.db.** { *; }
-keep class androidx.security.crypto.** { *; }
-keepclassmembers class * extends android.app.Service { *; }
