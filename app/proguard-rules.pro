# Keep Shizuku reflection & Binder interfaces intact
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# Keep FramePulse entry points & provider
-keep class com.shin.framepulse.FramePulseApp { *; }
-keep class com.shin.framepulse.MainActivity { *; }
-keep class com.shin.framepulse.CrashActivity { *; }
-keep class com.shin.framepulse.OverlayService { *; }
-keep class com.shin.framepulse.FramePulseTileService { *; }
