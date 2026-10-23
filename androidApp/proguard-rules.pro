# kotlinx.serialization ships its own consumer rules; keep our @Serializable models' companions just in case.
-keepclassmembers @kotlinx.serialization.Serializable class com.weatherwise.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
