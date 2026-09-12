package dev.openai.clearowner;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

@SuppressWarnings("deprecation")
public final class ClearOwnerInstrumentation extends Instrumentation {
    private static final String TARGET_PACKAGE = "io.github.muntashirakon.AppManager.debug";

    @Override
    public void onStart() {
        final Bundle result = new Bundle();
        try {
            final Context targetContext = getTargetContext();
            final DevicePolicyManager dpm =
                    (DevicePolicyManager) targetContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm == null) {
                result.putString("error", "DevicePolicyManager unavailable");
                finish(Activity.RESULT_CANCELED, result);
                return;
            }

            final boolean before = dpm.isDeviceOwnerApp(TARGET_PACKAGE);
            result.putBoolean("device_owner_before", before);
            if (!before) {
                result.putString("status", "target package is not current Device Owner; nothing changed");
                finish(Activity.RESULT_CANCELED, result);
                return;
            }

            dpm.clearDeviceOwnerApp(TARGET_PACKAGE);

            final boolean after = dpm.isDeviceOwnerApp(TARGET_PACKAGE);
            result.putBoolean("device_owner_after", after);
            result.putString("status", after ? "clear request returned but Device Owner remains" : "Device Owner cleared");
            finish(after ? Activity.RESULT_CANCELED : Activity.RESULT_OK, result);
        } catch (Throwable t) {
            result.putString("error", Log.getStackTraceString(t));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
