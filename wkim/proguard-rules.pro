# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile


-dontwarn org.xsocket.**
-keep class org.xsocket.** { *; }
-keep class javax.ws.rs.** { *; }
-keep class com.xinbida.wukongim.WKIM {*;}
-keep class com.xinbida.wukongim.protocol.WKMessageContent {*;}
-keep class com.xinbida.wukongim.protocol.WKMsgEntity {*;}
-keep class com.xinbida.wukongim.message.type.WKMsgContentType { *; }
-keep class com.xinbida.wukongim.entity.WKChannelType { *; }
-keep class com.xinbida.wukongim.message.type.WKSendMsgResult { *; }
-keep class com.xinbida.wukongim.message.type.WKConnectStatus { *; }
-keep class com.xinbida.wukongim.message.type.WKConnectReason { *; }

-keep class com.xinbida.wukongim.entity.* { *; }
-keep class com.xinbida.wukongim.interfaces.** { *; }
-keep class com.xinbida.wukongim.msgmodel.** { *; }
-keep class com.xinbida.wukongim.manager.** { *; }
-keep class org.whispersystems.curve25519.** { *; }
-keepclassmembers class com.xinbida.wukongim.db.WKDBHelper$DatabaseHelper {
   public *;
}

#--------- 混淆dh curve25519-------
-keep class org.whispersystems.curve25519.**{*;}
-keep class org.whispersystems.** { *; }
-keep class org.thoughtcrime.securesms.** { *; }

# sqlcipher
-keep,includedescriptorclasses class net.sqlcipher.** { *; }
-keep,includedescriptorclasses interface net.sqlcipher.** { *; }

