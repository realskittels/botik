# Claude SDK: models are (de)serialized by Jackson via reflection.
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class kotlin.Metadata { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* <fields>;
    @com.fasterxml.jackson.annotation.* <init>(...);
    @com.fasterxml.jackson.annotation.* <methods>;
}

-dontwarn com.fasterxml.jackson.**
-dontwarn com.github.victools.**
-dontwarn io.swagger.**
-dontwarn com.standardwebhooks.**
-dontwarn org.slf4j.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn java.beans.**
-dontwarn javax.annotation.**
-dontwarn kotlin.reflect.jvm.internal.**
