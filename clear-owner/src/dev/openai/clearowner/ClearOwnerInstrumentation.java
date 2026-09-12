package io.github.muntashirakon.AppManager.dpc;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

@SuppressWarnings("deprecation")
public final class ClearOwnerInstrumentation extends Activity {
    private DevicePolicyManager dpm;
    private TextView statusView;
    private Button clearButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("App Manager Device Owner");
        title.setTextSize(24f);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView explanation = new TextView(this);
        explanation.setText("This update runs as the existing App Manager package. The button below asks Android DevicePolicyManager to let App Manager voluntarily relinquish Device Owner.");
        explanation.setTextSize(16f);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = dp(18);
        root.addView(explanation, textParams);

        statusView = new TextView(this);
        statusView.setTextSize(16f);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(18);
        root.addView(statusView, statusParams);

        clearButton = new Button(this);
        clearButton.setText("Remove Device Owner");
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = dp(24);
        root.addView(clearButton, buttonParams);

        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmAndClear();
            }
        });

        setContentView(root);
        refresh();
    }

    private void refresh() {
        boolean owner = dpm != null && dpm.isDeviceOwnerApp(getPackageName());
        statusView.setText("Package: " + getPackageName() + "\nDevice Owner: " + owner);
        clearButton.setEnabled(owner);
    }

    private void confirmAndClear() {
        new AlertDialog.Builder(this)
                .setTitle("Remove Device Owner?")
                .setMessage("This cannot be undone without provisioning Device Owner again. Some policies may remain until they are separately cleared or the device is reset.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> clearOwner())
                .show();
    }

    private void clearOwner() {
        if (dpm == null) {
            showResult("DevicePolicyManager is unavailable.");
            return;
        }
        try {
            if (!dpm.isDeviceOwnerApp(getPackageName())) {
                showResult("This package is no longer Device Owner.");
                refresh();
                return;
            }
            dpm.clearDeviceOwnerApp(getPackageName());
            boolean remainsOwner = dpm.isDeviceOwnerApp(getPackageName());
            showResult(remainsOwner
                    ? "Android returned from the clear request, but Device Owner is still active."
                    : "Device Owner was cleared. Verify with: adb shell dpm list-owners");
            refresh();
        } catch (Throwable t) {
            showResult(t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
            refresh();
        }
    }

    private void showResult(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
