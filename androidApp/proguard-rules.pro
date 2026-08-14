# What R8 must not remove.
#
# R8 decides what is reachable by following calls. Everything below is reached some other way —
# by reflection, by a generated lookup, or by a name in a file — and is therefore invisible to it.
# Each rule says which, because a keep rule with no reason is one nobody can ever safely delete.

# ---- kotlinx.serialization ------------------------------------------------------------
#
# Every `@Serializable` class gets a generated `Companion.serializer()`, and the plugin's runtime
# finds it **by name** when a type is serialized through a `KSerializer` obtained reflectively —
# which is what `Json.encodeToString(value)` does for a polymorphic hierarchy. R8 sees a companion
# nobody calls and removes it, and the failure arrives at runtime as a missing-serializer crash on
# whichever screen happens to decode first.
#
# This is not hypothetical for this app: `Item` is a sealed hierarchy with a `type` discriminator,
# and it is the shape of every bag payload.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class ** {
    public static ** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# The model and protocol types themselves. Their **field names are the wire format** — a `GameSave`
# is read back from a save file written by an older build, and `@SerialName` values map to keys in
# `cards.json` and in every payload. Obfuscating the class names is harmless; removing a field
# because nothing in the app reads it yet is not, because the file on disk still has it.
-keep class com.tripletriad.model.** { *; }
-keep class com.tripletriad.protocol.** { *; }

# ---- Ktor -----------------------------------------------------------------------------
#
# Ktor's client picks its engine through a `ServiceLoader`, which is a name in a resource file and
# not a call R8 can follow.
-keep class io.ktor.client.engine.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**

# ---- Compose resources ----------------------------------------------------------------
#
# The generated `Res` accessors are addressed by path at runtime. `verifyComposeAssets` already
# guards the *packaging* of these files; this guards the code that reads them.
-keep class tripletriad.shared.generated.resources.** { *; }
