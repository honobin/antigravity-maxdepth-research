// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.dpc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

public final class GameInstallResultReceiver extends BroadcastReceiver {
    static final String ACTION_GAME_INSTALL_RESULT =
            "io.github.muntashirakon.AppManager.action.GAME_INSTALL_RESULT";
    static final String EXTRA_PACKAGE_NAME = "package_name";
    static final String EXTRA_EXPECTED_SIGNER = "expected_signer";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_GAME_INSTALL_RESULT.equals(intent.getAction())) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        if (packageName == null) return;

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirm);
                }
            } else {
                //noinspection deprecation
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirm);
                }
            }
            return;
        }

        if (status != PackageInstaller.STATUS_SUCCESS) {
            String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Toast.makeText(context, "Reinstall failed: " + status + (message == null ? "" : " / " + message),
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(context, "Reinstalled, but game category needs Android 8.0+", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            String expected = intent.getStringExtra(EXTRA_EXPECTED_SIGNER);
            String actual = PackageSignatureUtils.getCurrentSignerDigest(context.getPackageManager(), packageName);
            if (expected == null || !expected.equals(actual)) {
                throw new SecurityException("Signing certificate changed; category was not modified");
            }
            GameAppInstaller.setGameCategory(context, packageName, true);
            Toast.makeText(context, packageName + " reinstalled and verified as CATEGORY_GAME",
                    Toast.LENGTH_LONG).show();
        } catch (Throwable e) {
            Toast.makeText(context, "Reinstalled but game-category step failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }
}
