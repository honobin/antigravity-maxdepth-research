# USB DM Helper

Minimal Android helper for Samsung/MediaTek devices where Samsung USB Settings exists but cannot be launched from the ADB shell.

The app temporarily qualifies for the Android default dialer role and calls the public `TelephonyManager.sendDialerSpecialCode("0808")` API. Android Telephony therefore originates the special-code request rather than ADB's `shell` UID.

## Install

```sh
adb install -r app-debug.apk
```

Open **USB DM Helper** and tap **Make temporary default dialer**. Alternatively, after installation:

```sh
adb shell cmd role add-role-holder --user 0 android.app.role.DIALER dev.usbdmhelper
adb shell telecom get-default-dialer
adb shell am start -n dev.usbdmhelper/.MainActivity
```

The default dialer query should return:

```text
dev.usbdmhelper
```

Then tap **Send 0808**.

## Restore Samsung dialer immediately afterward

```sh
adb shell cmd role add-role-holder --user 0 android.app.role.DIALER com.samsung.android.dialer
adb shell telecom get-default-dialer
```

The result should be:

```text
com.samsung.android.dialer
```

After restoring the Samsung dialer, the helper can be removed:

```sh
adb uninstall dev.usbdmhelper
```

## Scope

This helper does not directly write `sys.usb.config`, bypass SELinux, or require root. It only invokes Android's documented dialer special-code API while it is the current default dialer. Whether Samsung accepts `0808` and opens its private USB settings remains firmware-dependent.
