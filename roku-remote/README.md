# Clicker

A Roku TV remote for Android that opens straight to the buttons, finds your TV on its own, and keeps up with your thumbs.

Built because the official app hides the remote behind a content feed, loses the TV whenever you switch apps, and lags. Clicker has no account, no ads, no feed, and no analytics. It talks to the TV directly over your Wi-Fi using Roku's documented [External Control Protocol](https://developer.roku.com/docs/developer-program/dev-tools/external-control-api.md).

## Features

- **Opens to the remote.** The last TV is remembered and the buttons work before the connection check finishes.
- **Doesn't lose the TV.** No long-lived session to die in the background. If your router hands the TV a new IP, Clicker finds it again by serial number (SSDP, then a subnet scan if multicast is blocked).
- **Fast buttons.** Presses fire on touch-down, go out strictly in order over a warm connection, and repeat while held for arrows, volume and channels. Haptics on every press.
- **Phone volume buttons** change the TV's volume while the app is open.
- **Power that works.** Turns the TV on from standby, or wakes it with Wake-on-LAN when it's asleep.
- **Channels and inputs.** Launch any channel with its real icon, pin favorites to the remote, and switch HDMI inputs by the names you gave them on the TV.
- **Keyboard** that mirrors what you type, including corrections.
- **Swipe pad** as an alternative to the D-pad.
- **Home screen widget** (power, volume, mute, play/pause) and a **Quick Settings tile** for play/pause, both working without opening the app.
- **Explains problems.** Tells you exactly which TV setting to change when the TV blocks remote apps.
- Light and dark themes, optional wallpaper colors, landscape, large fonts, and TalkBack labels on every control.

## Install

1. Download `clicker.apk` from the latest [Roku Remote CI run](https://github.com/Zanather/ai-slop/actions/workflows/roku-remote.yaml) (the `clicker.apk` artifact), or build it yourself (below).
2. Open it on your phone and allow your browser or file manager to install unknown apps when Android asks.

Every build is signed with the same key (`app/clicker.keystore`), so new versions install over old ones. That key is committed on purpose: this app is sideloaded, not published, and a stable key matters more here than a secret one.

## First run: one setting on the TV

Since Roku OS 14.1, Roku TVs ship with phone control set to **Limited**, which blocks remote buttons from any app but Roku's own. Clicker detects this and shows the fix, which you only do once:

> On the TV: **Home › Settings › System › Advanced system settings › Control by mobile apps › Network access › Enabled**

**Enabled** is enough; you don't need Permissive. While you're in Settings, turn on **System › Power › Fast TV start** so your phone can turn the TV on.

## Troubleshooting

- **"Can't reach your TV"**: the phone and TV must be on the same Wi-Fi network, not a guest network. Clicker pins its traffic to Wi-Fi to get past VPNs, but a VPN that forbids bypassing must allow LAN access (often called "Allow LAN" or split tunneling).
- **No TV found**: add it by IP address. On the TV, find it under **Settings › Network › About**.
- **Typing does nothing**: some channels, like YouTube and Prime Video, use their own keyboards and ignore typed text.

## Build

Requires JDK 17+ and the Android SDK (API 37).

```sh
./gradlew :app:assembleRelease          # app/build/outputs/apk/release/app-release.apk
./gradlew :ecp:test :app:testDebugUnitTest
./gradlew :app:recordRoborazziDebug     # re-record the screenshot goldens in app/src/test/screenshots
```

## How it's built

- **`ecp/`** is a plain Kotlin library for the protocol: the HTTP client, XML parsing (hardened against XXE), SSDP discovery, subnet scanning, Wake-on-LAN, and an in-order command queue. It has no Android dependencies, so its tests run on the JVM against a fake TV.
- **`app/`** is Jetpack Compose. `RokuSession` owns the connection: it probes the TV, re-finds it when it moves, wakes it, polls it every 4 seconds while the app is visible (just under Roku's 5-second idle cutoff, so the next press reuses a warm connection), and stops everything when the app is hidden.
- **`targetSdk` is 36, not 37.** Targeting Android 17 turns local network access into a runtime "Nearby devices" permission; a denied prompt would leave the remote dead. At 36 it's granted with internet access.
