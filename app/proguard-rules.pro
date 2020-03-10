
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.maltaisn.notes.app.**$$serializer { *; }
-keepclassmembers class com.maltaisn.notes.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.maltaisn.notes.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
