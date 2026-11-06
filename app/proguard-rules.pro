# The tunnel library is small; keep it intact rather than chase reflection/JNI edge cases.
-keep class org.amnezia.awg.** { *; }

# OkHttp ships its own consumer rules; these silence optional platform warnings.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
