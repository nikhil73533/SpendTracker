# Release shrinking is enabled. Keep rules should be added only for libraries that
# fail their release verification; Hilt, Room, Navigation, and Play libraries ship
# their own consumer rules.

# Never retain diagnostic logs or stack traces in a release APK. Financial content
# must not become observable through release logging.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}
-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace(...);
}

# Razorpay Standard Checkout and callback bridge.
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-keepattributes JavascriptInterface,*Annotation*
-dontwarn com.razorpay.**
-keep class com.razorpay.** { *; }
-keepclasseswithmembers class * { public void onPayment*(...); }

# PDFBox's optional JPEG2000 codec is not bundled; PDFBox handles its absence.
# Keep release shrinking strict for every other missing class.
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder
