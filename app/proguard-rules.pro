
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.maltaisn.notes.**$$serializer { *; }
-keepclassmembers class com.maltaisn.notes.** {
    *** Companion;
}
-keepclasseswithmembers class com.maltaisn.notes.** {
    kotlinx.serialization.KSerializer serializer(...);
}
