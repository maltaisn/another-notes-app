
# kotlinx.serialization rules
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.maltaisn.notes.**$$serializer { *; }
-keepclassmembers class com.maltaisn.notes.** {
    *** Companion;
}
-keepclasseswithmembers class com.maltaisn.notes.** {
    kotlinx.serialization.KSerializer serializer(...);
}
