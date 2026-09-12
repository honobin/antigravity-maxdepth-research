from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = 'io.github.muntashirakon.AppManager.debug'
ADMIN = 'io.github.muntashirakon.AppManager.dpc.AppManagerDeviceAdminReceiver'
ACTIVITY = 'io.github.muntashirakon.AppManager.dpc.ClearOwnerInstrumentation'


def test_manifest_is_same_package_update():
    text = (ROOT / 'AndroidManifest.xml').read_text()
    assert f'package="{TARGET}"' in text
    assert 'android:versionCode="1000000"' in text
    assert '<instrumentation' not in text


def test_manifest_preserves_exact_device_owner_component():
    text = (ROOT / 'AndroidManifest.xml').read_text()
    assert f'android:name="{ADMIN}"' in text
    assert 'android.permission.BIND_DEVICE_ADMIN' in text
    assert 'android.app.device_admin' in text


def test_launcher_exposes_self_deprovision_ui():
    text = (ROOT / 'AndroidManifest.xml').read_text()
    assert f'android:name="{ACTIVITY}"' in text
    assert 'android.intent.action.MAIN' in text
    assert 'android.intent.category.LAUNCHER' in text


def test_activity_checks_owner_and_clears_only_own_package():
    text = (ROOT / 'src/dev/openai/clearowner/ClearOwnerInstrumentation.java').read_text()
    assert 'isDeviceOwnerApp(getPackageName())' in text
    assert 'clearDeviceOwnerApp(getPackageName())' in text


def test_updater_does_not_factory_reset_or_wipe_data():
    text = (ROOT / 'src/dev/openai/clearowner/ClearOwnerInstrumentation.java').read_text()
    forbidden = ['wipeData(', 'factoryReset', 'RecoverySystem.rebootWipeUserData']
    assert all(term not in text for term in forbidden)


if __name__ == '__main__':
    test_manifest_is_same_package_update()
    test_manifest_preserves_exact_device_owner_component()
    test_launcher_exposes_self_deprovision_ui()
    test_activity_checks_owner_and_clears_only_own_package()
    test_updater_does_not_factory_reset_or_wipe_data()
    print('same-package updater contract checks passed')
