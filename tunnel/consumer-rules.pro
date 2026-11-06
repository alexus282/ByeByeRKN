# JNI symbols in libwg-go.so are bound to these exact class and method names.
-keep class org.amnezia.awg.GoBackend { native <methods>; }
-keep class org.amnezia.awg.backend.GoBackend$VpnService { *; }
