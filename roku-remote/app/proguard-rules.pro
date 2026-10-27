# Sideloaded app with no crash service: keep real names so a stack trace copied from the crash screen is readable.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# Classes libraries create by reflection whose consumer rules don't name the constructor. AGP 9 runs R8 with strict
# full-mode keep semantics, where a rule without members no longer keeps <init>, so these were stripped:
# WorkManager's database (started by androidx.startup on every launch: every launch crashed), its default input
# merger, and Glance action callbacks (widget taps). scripts/verify-release-dex.py checks they survive.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class * extends androidx.work.InputMerger { <init>(); }
-keep public class * extends androidx.glance.appwidget.action.ActionCallback { <init>(); }
