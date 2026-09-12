@echo off
set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
set "ANDROID_HOME=C:\Users\yashv\android-sdk"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call "%~dp0tools\gradle-9.3.1\bin\gradle.bat" %*
