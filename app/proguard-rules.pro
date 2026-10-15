# Strip android.util.Log calls from release builds (SEC-04).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
# SQLCipher JNI entry points.
-keep class net.zetetic.database.** { *; }

# PdfBox-Android: the JPEG 2000 codec is an optional dependency; reports never contain JPX images.
-dontwarn com.gemalto.jp2.**
# SecurityHandlerFactory instantiates handlers through reflection on their constructors.
-keepclassmembers class com.tom_roush.pdfbox.pdmodel.encryption.** { <init>(...); }
