# App Manager v4.1.1 Device Owner build harness

This branch is a reproducible build harness. It clones upstream App Manager `v4.1.1`, applies the extension from `overlay/` plus `build_support/patch_manifest.py`, runs unit tests, and builds a signed universal debug APK.

Added capabilities:
- Device Admin receiver and Device Owner detection/provisioning command.
- Existing App Manager ADB/root uninstall path, including current-user/all-users and keep-data modes.
- Real application disable/enable via App Manager's existing `FreezeUtils` / package-manager authority.
- Device Owner hide/unhide, suspend/unsuspend, and uninstall-block policies.
- Reinstall the currently installed base + split APKs without resigning them, verify signer continuity, become installer of record, and then set `ApplicationInfo.CATEGORY_GAME` on Android 8.0+.
- Self-protection against uninstalling, disabling, hiding, suspending, or reinstalling the manager itself.

The debug build uses the upstream development signing key and package suffix `.debug`; it installs alongside the release App Manager rather than overwriting an APK signed with the upstream release key.
