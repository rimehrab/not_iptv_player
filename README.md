# IPTV Player

Simple IPTV player, one UI for phone and TV. Android 5 (API 21) and up.

- Playlist hardcoded: `https://iptv.rimehrab.qd.je`
- Remote: `CHANNEL_UP`/`CHANNEL_DOWN` change channel, D-pad up/down also change channel (fallback for remotes with no dedicated channel keys), D-pad center / Enter opens channel list, Back closes it.
- Big ExoPlayer buffer set in `MainActivity.kt` (`DefaultLoadControl`) to ride out slow/unstable streams.

## Build locally

Open in Android Studio, let it sync, run on device/emulator (minSdk 21).

Or from CLI, with Gradle installed:

```
gradle assembleDebug
```

APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions

`.github/workflows/build.yml` builds a debug APK on every push to `main` and uploads it as a workflow artifact — grab it from the Actions tab, no need to keep a local build.

## Notes

- No Gradle wrapper jar checked in (kept the zip small); Android Studio will regenerate one on first sync, and the CI workflow uses `gradle/actions/setup-gradle` so it doesn't need one either.
- `usesCleartextTraffic="true"` set in the manifest in case the playlist or a stream URL isn't HTTPS — drop it if everything's HTTPS only.
- Icon/banner are placeholder vector drawables — swap `ic_launcher.xml` / `tv_banner.xml` for real art whenever.
