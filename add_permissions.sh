if ! grep -q "ACCESS_FINE_LOCATION" app/src/main/AndroidManifest.xml; then
  sed -i '/<application/i \ \ \ \ <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />\n    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />' app/src/main/AndroidManifest.xml
fi
sed -i 's/\/\/ implementation(libs.accompanist.permissions)/implementation(libs.accompanist.permissions)/g' app/build.gradle.kts
