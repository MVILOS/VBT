# === Retrofit ===
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
# Metody interfejsów API z adnotacjami Retrofit
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
# Typy generyczne w Response<T> / Continuation<T> (suspend fun)
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# === Gson - modele API serializowane refleksją ===
-keep class com.vbt.app.data.remote.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn sun.misc.**

# === Room - encje lokalnej bazy ===
-keep class com.vbt.app.data.local.entity.** { *; }

# === Kotlin coroutines ===
-dontwarn kotlinx.coroutines.**

# === Nordic BLE ===
-dontwarn no.nordicsemi.android.**
