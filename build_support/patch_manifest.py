#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])
manifest = root / 'app/src/main/AndroidManifest.xml'
text = manifest.read_text(encoding='utf-8')
marker = '        <activity\n            android:name=".filters.FinderActivity"'
if marker not in text:
    raise SystemExit('manifest insertion point not found')
if '.dpc.AppManagerDeviceAdminReceiver' not in text:
    block = '''        <!-- Device Owner / Device Admin package-management extension. -->
        <receiver
            android:name=".dpc.AppManagerDeviceAdminReceiver"
            android:description="@string/app_name"
            android:exported="true"
            android:label="AM Device Manager"
            android:permission="android.permission.BIND_DEVICE_ADMIN">
            <meta-data
                android:name="android.app.device_admin"
                android:resource="@xml/app_manager_device_admin" />
            <intent-filter>
                <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
            </intent-filter>
        </receiver>

        <receiver
            android:name=".dpc.GameInstallResultReceiver"
            android:exported="false" />

        <activity
            android:name=".dpc.PrivilegedAppManagerActivity"
            android:enableOnBackInvokedCallback="true"
            android:exported="true"
            android:label="AM Device Manager">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

'''
    text = text.replace(marker, block + marker, 1)
manifest.write_text(text, encoding='utf-8')
