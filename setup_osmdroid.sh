sed -i 's/implementation(libs.maps.compose)//g' app/build.gradle.kts
sed -i 's/implementation(libs.play.services.maps)//g' app/build.gradle.kts
sed -i '/implementation(libs.androidx.compose.ui.graphics)/a \ \ implementation("org.osmdroid:osmdroid-android:6.1.18")' app/build.gradle.kts

sed -i '/<meta-data android:name="com.google.android.geo.API_KEY"/d' app/src/main/AndroidManifest.xml

# Add Internet permission if not present
if ! grep -q "android.permission.INTERNET" app/src/main/AndroidManifest.xml; then
  sed -i '/<application/i \ \ \ \ <uses-permission android:name="android.permission.INTERNET" />\n    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />' app/src/main/AndroidManifest.xml
fi

