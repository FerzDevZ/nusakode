# JGit memakai refleksi untuk transport, dan NetRCCredentialsProvider menyentuh kelas opsional.
-keep class org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**

# Kelas opsional yang tidak ada di Android (Kerberos/GSSAPI) dan binding SLF4J statis.
-dontwarn org.ietf.jgss.**
-dontwarn org.slf4j.impl.**
-dontwarn javax.naming.**

# kotlinx.serialization: serializer dibuat saat kompilasi, jangan dihapus.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class id.kodenusa.** { *** Companion; *** INSTANCE; }
-keepclasseswithmembers class id.kodenusa.** { kotlinx.serialization.KSerializer serializer(...); }
