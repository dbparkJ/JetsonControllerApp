# VWorld API key setup

The Android app reads the VWorld key at build time in this order:

1. Gradle property `VWORLD_API_KEY`
2. Environment variable `VWORLD_API_KEY`
3. Ignored local file `local.properties` with `vworld.apiKey=...`

For a developer or Jetson-hosted Android build, use the interactive helper:

```bash
./scripts/configure-vworld-key.sh
```

The helper disables terminal echo, never prints the key, writes the ignored
`local.properties` file atomically, and restricts it to the current user. Do not
pass the key on the command line because shell history and process listings may
retain it.

The value is compiled into the Android APK because VWorld tile requests are made
directly by the app. Treat it as a client key: apply the narrowest VWorld client
or package restrictions available, and rotate it if an APK is distributed beyond
the intended devices.
