# Android v7a 编译、签名与 ADB 安装命令

本项目当前常用 APK 变体：

- ABI：`armeabi-v7a`
- Brand：`generic`
- Mode：`normal` / Java 版
- Gradle task：`assembleArmeabiGenericNormalRelease`
- 包名：`com.github.tvbox.osc.tk`
- 电视 IP：`192.168.8.103`
- ADB 端口：`5555`

以下命令默认在 Windows `cmd` 中执行。

## 1. 进入项目目录

```bat
cd /d D:\code\Box
```

## 2. 编译 v7a APK

项目需要使用 JDK 17 编译：

```bat
set "JAVA_HOME=C:\Users\xuan\.jdks\ms-17.0.15"
set "PATH=%JAVA_HOME%\bin;%PATH%"

gradlew.bat assembleArmeabiGenericNormalRelease
```

编译后的未签名 APK：

```text
app\build\outputs\apk\armeabiGenericNormal\release\TVBox_release-armeabi-generic-java.apk
```

## 2.1 快速调试编译（推荐排查问题时用）

Release 任务会跑 R8/minify，耗时很长。排查播放问题时可以先用 Debug 变体，通常更快，并且会自动使用 debug keystore 签名：

```bat
set "JAVA_HOME=C:\Users\xuan\.jdks\ms-17.0.15"
set "PATH=%JAVA_HOME%\bin;%PATH%"

gradlew.bat assembleArmeabiGenericNormalDebug
```

Debug APK 路径：

```text
app\build\outputs\apk\armeabiGenericNormal\debug\TVBox_debug-armeabi-generic-java.apk
```

安装：

```bat
C:\Users\xuan\AppData\Local\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\armeabiGenericNormal\debug\TVBox_debug-armeabi-generic-java.apk
```

注意：只有遇到 dex 缺失、构建缓存异常等问题时才执行 `clean`，平时不要每次 clean，否则会显著变慢。

## 3. 对 APK 进行 zipalign + debug 签名

```bat
set "BT=C:\Users\xuan\AppData\Local\Android\Sdk\build-tools\34.0.0"
set "APK=app\build\outputs\apk\armeabiGenericNormal\release\TVBox_release-armeabi-generic-java.apk"
set "ALIGNED=app\build\outputs\apk\armeabiGenericNormal\release\TVBox_release-armeabi-generic-java-aligned.apk"
set "SIGNED=app\build\outputs\apk\armeabiGenericNormal\release\TVBox_release-armeabi-generic-java-signed.apk"

"%BT%\zipalign.exe" -p -f 4 "%APK%" "%ALIGNED%"

"%BT%\apksigner.bat" sign ^
  --ks "%USERPROFILE%\.android\debug.keystore" ^
  --ks-type PKCS12 ^
  --ks-key-alias androiddebugkey ^
  --ks-pass pass:android ^
  --key-pass pass:android ^
  --out "%SIGNED%" ^
  "%ALIGNED%"
```

签名后的 APK：

```text
app\build\outputs\apk\armeabiGenericNormal\release\TVBox_release-armeabi-generic-java-signed.apk
```

## 4. 校验 APK 签名

```bat
"%BT%\apksigner.bat" verify --verbose --print-certs "%SIGNED%"
```

看到类似下面结果说明签名正常：

```text
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
Number of signers: 1
```

## 5. ADB 远程连接电视

```bat
C:\Users\xuan\AppData\Local\Android\Sdk\platform-tools\adb.exe connect 192.168.8.103:5555
```

查看设备是否连接成功：

```bat
C:\Users\xuan\AppData\Local\Android\Sdk\platform-tools\adb.exe devices
```

正常会看到：

```text
192.168.8.103:5555    device
```

## 6. 安装签名 APK

如果还在项目根目录，并且上一节的 `%SIGNED%` 变量还在当前 cmd 窗口中：

```bat
C:\Users\xuan\AppData\Local\Android\Sdk\platform-tools\adb.exe install -r "%SIGNED%"
```

也可以直接使用完整相对路径：

```bat
C:\Users\xuan\AppData\Local\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\armeabiGenericNormal\release\TVBox_release-armeabi-generic-java-signed.apk
```

## 7. 卸载 APK

```bat
C:\Users\xuan\AppData\Local\Android\Sdk\platform-tools\adb.exe uninstall com.github.tvbox.osc.tk
```

## 8. 一次性完整命令

如果只想复制一整段执行：

```bat
cd /d D:\code\Box

set "JAVA_HOME=C:\Users\xuan\.jdks\ms-17.0.15"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "BT=C:\Users\xuan\AppData\Local\Android\Sdk\build-tools\34.0.0"
set "ADB=C:\Users\xuan\AppData\Local\Android\Sdk\platform-tools\adb.exe"
set "APK=app\build\outputs\apk\armeabiGenericNormal\release\TVBox_release-armeabi-generic-java.apk"
set "ALIGNED=app\build\outputs\apk\armeabiGenericNormal\release\TVBox_release-armeabi-generic-java-aligned.apk"
set "SIGNED=app\build\outputs\apk\armeabiGenericNormal\release\TVBox_release-armeabi-generic-java-signed.apk"

gradlew.bat assembleArmeabiGenericNormalRelease

"%BT%\zipalign.exe" -p -f 4 "%APK%" "%ALIGNED%"

"%BT%\apksigner.bat" sign ^
  --ks "%USERPROFILE%\.android\debug.keystore" ^
  --ks-type PKCS12 ^
  --ks-key-alias androiddebugkey ^
  --ks-pass pass:android ^
  --key-pass pass:android ^
  --out "%SIGNED%" ^
  "%ALIGNED%"

"%BT%\apksigner.bat" verify --verbose --print-certs "%SIGNED%"

"%ADB%" connect 192.168.8.103:5555
"%ADB%" devices
"%ADB%" install -r "%SIGNED%"
```

## 9. 常见问题

### 启动闪退：ClassNotFoundException / No original dex files found

如果安装后应用一打开就闪退，logcat 中看到类似：

```text
java.lang.ClassNotFoundException: Didn't find class "com.github.tvbox.osc.base.App"
Suppressed: java.io.IOException: No original dex files found for dex location .../base.apk
```

先检查 APK 中是否包含主 dex：

```bat
tar -tf app\build\outputs\apk\armeabiGenericNormal\release\TVBox_release-armeabi-generic-java-signed.apk | findstr classes
```

正常至少应该有：

```text
classes.dex
classes2.dex
```

如果只看到 `classes3.dex`、`classes4.dex`，说明增量构建产物异常。执行 clean build：

```bat
set "JAVA_HOME=C:\Users\xuan\.jdks\ms-17.0.15"
set "PATH=%JAVA_HOME%\bin;%PATH%"

gradlew.bat clean assembleArmeabiGenericNormalRelease
```

然后重新 zipalign、签名、安装。

### INSTALL_PARSE_FAILED_NO_CERTIFICATES

说明安装的是未签名 APK。请安装：

```text
TVBox_release-armeabi-generic-java-signed.apk
```

不要安装：

```text
TVBox_release-armeabi-generic-java.apk
```

### DELETE_FAILED_INTERNAL_ERROR

如果执行卸载时报：

```text
Failure [DELETE_FAILED_INTERNAL_ERROR]
```

先确认包是否存在：

```bat
C:\Users\xuan\AppData\Local\Android\Sdk\platform-tools\adb.exe shell pm list packages | findstr tvbox
```

当前本项目包名是：

```text
com.github.tvbox.osc.tk
```
