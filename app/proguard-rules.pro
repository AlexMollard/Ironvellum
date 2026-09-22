# Monarch R8 keep rules. The heavy lifting is already done by consumer rules
# each library ships inside its own artifact (verified in the Gradle cache):
#   - kotlinx.serialization: META-INF/proguard/kotlinx-serialization-common.pro
#     in kotlinx-serialization-core-jvm (matches any @Serializable class,
#     including our DTOs in com.ironvellum.app.data.cloud)
#   - Ktor 3.x: META-INF/proguard/ktor.pro in ktor-utils-jvm (volatile
#     AtomicFU fields + HttpClientEngineContainer ServiceLoader entries)
#   - OkHttp 5, Room, Compose, coroutines: all ship consumer rules
# Only rules we must add ourselves live here. No blanket -keep class ** { *; }.

# --- kotlinx.serialization (app DTOs) ---------------------------------------
# Our @Serializable DTOs live in app code, not a library, so mirror the rules
# from the kotlinx.serialization README for project classes. The library's
# consumer rules already cover these patterns globally; scoping them to
# com.ironvellum.app.** keeps them explicit and survives consumer-rule changes.
# Companion lookup + generated serializer() must survive shrinking or every
# Supabase decode throws SerializationException.
-keepclassmembers @kotlinx.serialization.Serializable class com.ironvellum.app.** {
    static ** Companion;
}
-keepclassmembers class com.ironvellum.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Generated $$serializer classes are only reached reflectively.
-keep,includedescriptorclasses class com.ironvellum.app.**$$serializer { *; }

# --- supabase-kt 3.8.0 -------------------------------------------------------
# supabase-kt ships NO consumer rules (verified in its AARs) and publishes none
# in its docs; it relies on kotlinx.serialization rules for wire types, which
# the section above provides for our DTO package. No extra keep rules needed.

# --- Room / Compose ----------------------------------------------------------
# Both ship consumer rules; AGP's bundled proguard-android-optimize.txt plus
# library rules cover them. Nothing to add.
