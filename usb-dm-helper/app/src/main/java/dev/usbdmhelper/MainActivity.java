package dev.usbdmhelper;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final int REQUEST_DIALER_ROLE = 1001;

    private TextView statusView;
    private Button sendButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContentView());
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private LinearLayout buildContentView() {
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, pad, pad, pad);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText("USB DM Helper");
        title.setTextSize(24);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView explanation = new TextView(this);
        explanation.setText("Temporarily make this app the default dialer, then send Samsung special code 0808 through Android Telephony.");
        explanation.setTextSize(16);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = pad;
        root.addView(explanation, textParams);

        statusView = new TextView(this);
        statusView.setTextSize(16);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = pad;
        root.addView(statusView, statusParams);

        Button roleButton = new Button(this);
        roleButton.setText("Make temporary default dialer");
        roleButton.setOnClickListener(v -> requestDialerRole());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = pad;
        root.addView(roleButton, buttonParams);

        sendButton = new Button(this);
        sendButton.setText("Send 0808");
        sendButton.setOnClickListener(v -> send0808());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sendParams.topMargin = pad / 2;
        root.addView(sendButton, sendParams);

        return root;
    }

    private void requestDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            if (roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                setStatus("DIALER role is unavailable on this device.");
                return;
            }
            if (roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                setStatus("This app is already the default dialer. You can send 0808 now.");
                return;
            }
            startActivityForResult(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER),
                    REQUEST_DIALER_ROLE);
            return;
        }

        Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
        intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE, getPackageName());
        startActivityForResult(intent, REQUEST_DIALER_ROLE);
    }

    private void send0808() {
        if (!isDefaultDialer()) {
            setStatus("Blocked: make USB DM Helper the default dialer first.");
            return;
        }

        TelephonyManager telephonyManager = getSystemService(TelephonyManager.class);
        if (telephonyManager == null) {
            setStatus("Telephony service is unavailable.");
            return;
        }

        try {
            SpecialCodeController controller = new SpecialCodeController(
                    telephonyManager::sendDialerSpecialCode);
            controller.sendUsbSettingsCode();
            setStatus("0808 was submitted to Android Telephony. Check for Samsung USB Settings.");
        } catch (SecurityException e) {
            setStatus("Telephony rejected the caller: " + e.getMessage());
        } catch (RuntimeException e) {
            setStatus("0808 failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private boolean isDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            return roleManager != null && roleManager.isRoleHeld(RoleManager.ROLE_DIALER);
        }
        TelecomManager telecomManager = getSystemService(TelecomManager.class);
        return telecomManager != null && getPackageName().equals(telecomManager.getDefaultDialerPackage());
    }

    private void updateStatus() {
        if (statusView == null || sendButton == null) {
            return;
        }
        boolean held = isDefaultDialer();
        sendButton.setEnabled(held);
        setStatus(held
                ? "Default dialer: USB DM Helper. Ready to send 0808."
                : "Default dialer role not held yet.");
    }

    private void setStatus(String text) {
        if (statusView != null) {
            statusView.setText(text);
        }
    }
}
