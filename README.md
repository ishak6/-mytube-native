# mytube-native

Native Android video app built with Kotlin, Jetpack Compose, and AndroidX Media3.

## Implemented in v0.2.0

- Five working bottom navigation destinations: Home, Search, Shorts, Library, Premium
- Settings screen and dark/light theme switch
- Import local video files using Android's document picker
- Persist imported video references across app restarts
- Search imported videos by filename
- Play local videos using AndroidX Media3 (ExoPlayer / PlayerView)
- Remove videos from the in-app library
- No WebView dependency

## Not yet implemented

- YouTube/online home feed, trending, categories, and Shorts feed
- Provider account login, cloud sync, and online subscriptions
- Background playback service and offline downloads from external providers
- Cross-device library sync

These require authorized provider APIs, backend work, and/or additional media-service implementation. The app does not bypass ads, DRM, Premium restrictions, or provider download controls.

## Build

GitHub Actions builds a debug APK from `.github/workflows/android.yml`.
