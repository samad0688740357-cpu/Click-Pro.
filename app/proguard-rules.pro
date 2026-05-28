# Project R8 rules for release builds.
#
# The Android Gradle Plugin and most AndroidX libraries already ship consumer
# rules. Keep this file scoped to app-owned runtime entry points and reflection
# boundaries so minify/shrink can still do useful work.

# Keep readable release crash stacks while still hiding original source names.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Android framework entry points referenced from AndroidManifest.xml or by
# system components must keep stable class names and lifecycle members.
-keep class id.biz.rrndev.autoclicker.AutoClickerApp { *; }
-keep class id.biz.rrndev.autoclicker.ui.activity.MainActivity { *; }
-keep class id.biz.rrndev.autoclicker.ui.activity.FlowEditorActivity { *; }
-keep class id.biz.rrndev.autoclicker.service.AutoClickAccessibilityService { *; }

# Google Play Billing crosses a Play services boundary and uses callback/model
# classes from com.android.billingclient.api. Keep the small API surface stable
# so release builds behave the same as debug builds.
-keep class com.android.billingclient.api.** { *; }
-keep class id.biz.rrndev.autoclicker.billing.** { *; }

# Room generates code against these annotations/classes. Consumer rules cover
# most cases, but keeping app DB contracts explicit prevents fragile release
# failures after future schema/entity changes.
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class id.biz.rrndev.autoclicker.data.db.AppDatabase_Impl { *; }

# Import/export uses Gson without @SerializedName, so field names must remain
# stable for old and new JSON backups. This also protects premium folder/pin
# metadata when users move backups between Free and Premium installs.
-keep class id.biz.rrndev.autoclicker.data.entity.** { *; }
-keep class id.biz.rrndev.autoclicker.data.db.relation.** { *; }
-keep class id.biz.rrndev.autoclicker.data.repository.FlowRepositoryImpl$ExportPayload { *; }
-keep class id.biz.rrndev.autoclicker.data.model.** { *; }

# Preserve Gson annotation-based models if they are introduced later.
-keepclassmembers,allowoptimization class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
