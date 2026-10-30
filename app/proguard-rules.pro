# JavaScript calls these methods by annotation/name through WebView's bridge.
# Keep the bridge entry points while allowing the rest of the app to shrink.
-keepclassmembers,allowoptimization class * {
    @android.webkit.JavascriptInterface <methods>;
}
