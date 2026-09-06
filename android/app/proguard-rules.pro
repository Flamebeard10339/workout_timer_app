# The page reaches the app through @JavascriptInterface methods, which are called by
# reflection from the WebView. R8 cannot see those call sites, so pin them.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
