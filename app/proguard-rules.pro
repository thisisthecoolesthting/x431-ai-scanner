# Default ProGuard rules. Keep Kotlin serialization metadata.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.caseforge.scanner.**$$serializer { *; }
-keepclassmembers class com.caseforge.scanner.** {
    *** Companion;
}
-keepclasseswithmembers class com.caseforge.scanner.** {
    kotlinx.serialization.KSerializer serializer(...);
}
