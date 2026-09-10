sed -i '/\[versions\]/a mapsCompose = "6.1.2"\nplayServicesMaps = "19.0.0"' gradle/libs.versions.toml
sed -i '/\[libraries\]/a maps-compose = { group = "com.google.maps.android", name = "maps-compose", version.ref = "mapsCompose" }\nplay-services-maps = { group = "com.google.android.gms", name = "play-services-maps", version.ref = "playServicesMaps" }' gradle/libs.versions.toml
