# AC Remote

Control a Panasonic MirAIe AC from the macOS menu bar and from Android Quick Settings.
The UI is modeled on @icanberk's status bar app (x.com/icanberk/status/2109031477484937571).
Both clients talk to the MirAIe cloud directly. The protocol is in [PROTOCOL.md](PROTOCOL.md).

## macOS (`mac/`)

```sh
cd mac
./build-app.sh                         # → dist/AC Remote.app (menu bar only, no Dock icon)
swift run MirAIeChecks                 # protocol checks
MIRAIE_USER=+91… MIRAIE_PASS=… swift run miraie-probe status   # dump raw AC status JSON
```

Click the AC icon in the menu bar and sign in with your MirAIe mobile number and password.
The login is stored in the keychain.

## Android (`android/`)

```sh
cd android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open the app once to sign in. Then edit Quick Settings and add the **AC** tile:
tap it to toggle power, long-press it to open the control panel.

Debug builds can show the panel with sample data, without an account:
`adb shell am start -n dev.adityadroid.acremote/.MainActivity --ez demo true`.

## Features

Power, set temperature (16–30 °C), mode, fan speed, vertical airflow, Eco, Powerful,
Convertible (110 % HC down to 40 %), and on/off timers that run on the AC itself.

## Known gaps

- Sleep mode has no known MirAIe key, so the fourth tile is **Powerful**. Run `miraie-probe status`
  while sleep is on in the MirAIe app to find the key.
- Repeating weekly schedules from the MirAIe app use an undocumented cloud API and aren't supported.

## Credits

Not affiliated with Panasonic. Protocol details come from the community projects
`rkzofficial/miraie-ac`, `miraie-ac-in` and the Raycast MirAIe extension.
The Android app bundles Nunito under the SIL Open Font License (`android/third_party/nunito/OFL.txt`).
