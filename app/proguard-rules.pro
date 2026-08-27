# kotlinx.serialization — keep generated serializers for the GeoJSON model classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class net.johnbiz.countyline.**$$serializer { *; }
-keepclassmembers class net.johnbiz.countyline.** {
    *** Companion;
}
-keepclasseswithmembers class net.johnbiz.countyline.** {
    kotlinx.serialization.KSerializer serializer(...);
}
