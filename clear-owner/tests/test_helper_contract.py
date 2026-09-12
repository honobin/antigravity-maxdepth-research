from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = 'io.github.muntashirakon.AppManager.debug'


def test_manifest_targets_exact_device_owner_package():
    text = (ROOT / 'AndroidManifest.xml').read_text()
    assert f'android:targetPackage="{TARGET}"' in text


def test_instrumentation_requires_current_device_owner_before_clear():
    text = (ROOT / 'src/dev/openai/clearowner/ClearOwnerInstrumentation.java').read_text()
    assert 'isDeviceOwnerApp(TARGET_PACKAGE)' in text
    assert 'clearDeviceOwnerApp(TARGET_PACKAGE)' in text


def test_helper_does_not_factory_reset_or_wipe_data():
    text = (ROOT / 'src/dev/openai/clearowner/ClearOwnerInstrumentation.java').read_text()
    forbidden = ['wipeData(', 'factoryReset', 'RecoverySystem.rebootWipeUserData']
    assert all(term not in text for term in forbidden)
