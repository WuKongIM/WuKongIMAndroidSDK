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
-keep class com.wukong.im.WKIM {*;}
-keep class com.wukong.im.protocol.WKMessageContent {*;}
-keep class com.wukong.im.protocol.WKMsgEntity {*;}
-keep class com.wukong.im.message.type.WKMsgContentType { *; }
-keep class com.wukong.im.entity.WKChannelType { *; }
-keep class com.wukong.im.message.type.WKSendMsgResult { *; }
-keep class com.wukong.im.message.type.WKConnectStatus { *; }
-keep class com.wukong.im.message.type.WKConnectReason { *; }

-keep class com.wukong.im.entity.* { *; }
-keep class com.wukong.im.interfaces.** { *; }
-keep class com.wukong.im.msgmodel.** { *; }
-keep class com.wukong.im.manager.** { *; }
-keep class org.whispersystems.curve25519.** { *; }
-keepclassmembers class com.wukong.im.db.WKDBHelper$DatabaseHelper {
   public *;
}
# sqlcipher
-keep,includedescriptorclasses class net.sqlcipher.** { *; }
-keep,includedescriptorclasses interface net.sqlcipher.** { *; }

