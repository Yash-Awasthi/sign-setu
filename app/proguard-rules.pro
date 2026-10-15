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
