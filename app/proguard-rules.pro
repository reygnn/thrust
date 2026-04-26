# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Kotlin data classes used in GameState – keep for coroutines/reflection
-keepclassmembers class com.github.reygnn.thrust.domain.model.** { *; }

# Keep sealed interfaces
-keep class com.github.reygnn.thrust.domain.model.GamePhase { *; }
-keep class com.github.reygnn.thrust.domain.model.GamePhase$* { *; }
-keep class com.github.reygnn.thrust.ui.game.NavEvent { *; }
-keep class com.github.reygnn.thrust.ui.game.NavEvent$* { *; }
