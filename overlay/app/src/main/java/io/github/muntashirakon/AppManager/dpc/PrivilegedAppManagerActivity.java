// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.dpc;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandleHidden;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;

public final class PrivilegedAppManagerActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private DevicePolicyManager dpm;
    private ComponentName admin;
    private TextView statusView;
    private TextView selectedStatusView;
    private Spinner appSpinner;
    private CheckBox keepData;
    private final List<AppEntry> entries = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        admin = new ComponentName(this, AppManagerDeviceAdminReceiver.class);
        setTitle("AM Device Manager");
        setContentView(buildUi());
        refreshAll();
        executor.execute(() -> {
            try {
                Ops.init(getApplicationContext(), false);
            } catch (Throwable ignored) {
            }
            runOnUiThread(this::refreshStatus);
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @NonNull
    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Device Owner + ADB/Root package controls");
        title.setTextSize(20f);
        root.addView(title, matchWrap());

        statusView = new TextView(this);
        statusView.setTextIsSelectable(true);
        root.addView(statusView, matchWrap());

        root.addView(button("Activate Device Admin", v -> activateDeviceAdmin()), matchWrap());
        root.addView(button("Refresh privilege/app state", v -> refreshAll()), matchWrap());

        appSpinner = new Spinner(this);
        root.addView(appSpinner, matchWrap());
        appSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                refreshSelectedStatus();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        selectedStatusView = new TextView(this);
        selectedStatusView.setTextIsSelectable(true);
        root.addView(selectedStatusView, matchWrap());

        keepData = new CheckBox(this);
        keepData.setText("Keep app data during uninstall (privileged path)");
        root.addView(keepData, matchWrap());

        root.addView(button("UNINSTALL — current user", v -> uninstall(false)), matchWrap());
        root.addView(button("UNINSTALL — all users", v -> uninstall(true)), matchWrap());
        root.addView(button("DISABLE app (ADB/root)", v -> disableApp()), matchWrap());
        root.addView(button("ENABLE app (ADB/root)", v -> enableApp()), matchWrap());
        root.addView(button("Hide app (Device Owner)", v -> setHidden(true)), matchWrap());
        root.addView(button("Unhide app (Device Owner)", v -> setHidden(false)), matchWrap());
        root.addView(button("Suspend app (Device Owner)", v -> setSuspended(true)), matchWrap());
        root.addView(button("Unsuspend app (Device Owner)", v -> setSuspended(false)), matchWrap());
        root.addView(button("Block uninstall (Device Owner)", v -> setUninstallBlocked(true)), matchWrap());
        root.addView(button("Allow uninstall (Device Owner)", v -> setUninstallBlocked(false)), matchWrap());
        root.addView(button("REINSTALL AS GAME", v -> reinstallAsGame()), matchWrap());
        root.addView(button("Set CATEGORY_GAME (already owned)", v -> setGameCategory(true)), matchWrap());
        root.addView(button("Clear game category hint", v -> setGameCategory(false)), matchWrap());

        TextView note = new TextView(this);
        note.setText("Device Owner is orthogonal to App Manager's existing ADB/root mode. "
                + "Real DISABLED_USER requires package-manager authority; Device Owner-only devices "
                + "use the separate Hide/Suspend controls. Reinstall-as-game keeps the target's "
                + "original APK signature and split APKs, then verifies installer ownership before "
                + "applying CATEGORY_GAME.");
        root.addView(note, matchWrap());
        return scroll;
    }

    private void activateDeviceAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Enables Device Admin. Device Owner itself must be provisioned with dpm/managed-device setup.");
        startActivity(intent);
    }

    private void refreshAll() {
        refreshStatus();
        executor.execute(() -> {
            List<AppEntry> loaded = new ArrayList<>();
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS);
            for (ApplicationInfo info : apps) {
                String label = String.valueOf(pm.getApplicationLabel(info));
                loaded.add(new AppEntry(label, info.packageName));
            }
            Collections.sort(loaded, Comparator.comparing(e -> e.label.toLowerCase(java.util.Locale.ROOT)));
            runOnUiThread(() -> {
                entries.clear();
                entries.addAll(loaded);
                ArrayAdapter<AppEntry> adapter = new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_item, entries);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                appSpinner.setAdapter(adapter);
                refreshSelectedStatus();
            });
        });
    }

    private void refreshStatus() {
        boolean adminActive = dpm != null && dpm.isAdminActive(admin);
        boolean owner = dpm != null && dpm.isDeviceOwnerApp(getPackageName());
        String component = getPackageName() + "/" + admin.getClassName();
        String mode;
        try {
            mode = String.valueOf(Ops.getInferredMode(this));
        } catch (Throwable e) {
            mode = Ops.getMode();
        }
        statusView.setText("Package: " + getPackageName()
                + "\nDevice Admin: " + adminActive
                + "\nDevice Owner: " + owner
                + "\nApp Manager privileged mode: " + mode
                + "\n\nDevice Owner provisioning command (fresh/provisionable device):\n"
                + "adb shell dpm set-device-owner " + component);
    }

    private void refreshSelectedStatus() {
        String pkg = selectedPackage();
        if (pkg == null) {
            selectedStatusView.setText("No app selected");
            return;
        }
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS);
            StringBuilder s = new StringBuilder(pkg)
                    .append("\nenabled=").append(info.enabled);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                s.append("\ncategory=").append(info.category)
                        .append(info.category == ApplicationInfo.CATEGORY_GAME ? " (GAME)" : "")
                        .append("\ninstallerOwnedByManager=")
                        .append(GameAppInstaller.isInstallerOfRecord(this, pkg));
            }
            if (dpm != null && dpm.isDeviceOwnerApp(getPackageName())) {
                try {
                    s.append("\nhidden=").append(dpm.isApplicationHidden(admin, pkg));
                } catch (Throwable ignored) { }
                try {
                    s.append("\nuninstallBlocked=").append(dpm.isUninstallBlocked(admin, pkg));
                } catch (Throwable ignored) { }
            }
            selectedStatusView.setText(s.toString());
        } catch (Throwable e) {
            selectedStatusView.setText(pkg + "\nstate unavailable: " + e.getMessage());
        }
    }

    private void uninstall(boolean allUsers) {
        String pkg = selectedPackage();
        if (!targetAllowed(pkg, "uninstall")) return;
        runPrivileged("Uninstall " + pkg, () -> {
            int userId = allUsers ? UserHandleHidden.USER_ALL : UserHandleHidden.myUserId();
            return PackageInstallerCompat.getNewInstance().uninstall(pkg, userId, keepData.isChecked());
        });
    }

    private void disableApp() {
        String pkg = selectedPackage();
        if (!targetAllowed(pkg, "disable")) return;
        runPrivileged("Disable " + pkg, () -> {
            FreezeUtils.freeze(pkg, UserHandleHidden.myUserId(), FreezeUtils.FREEZE_DISABLE);
            ApplicationInfo info = getPackageManager().getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS);
            return !info.enabled;
        });
    }

    private void enableApp() {
        String pkg = selectedPackage();
        if (!targetAllowed(pkg, "enable")) return;
        runPrivileged("Enable " + pkg, () -> {
            FreezeUtils.unfreeze(pkg, UserHandleHidden.myUserId());
            ApplicationInfo info = getPackageManager().getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS);
            return info.enabled;
        });
    }

    private void setHidden(boolean hidden) {
        String pkg = selectedPackage();
        if (!targetAllowed(pkg, hidden ? "hide" : "unhide")) return;
        if (!requireDeviceOwner()) return;
        runPrivileged((hidden ? "Hide " : "Unhide ") + pkg, () -> {
            boolean result = dpm.setApplicationHidden(admin, pkg, hidden);
            return result && dpm.isApplicationHidden(admin, pkg) == hidden;
        });
    }

    private void setSuspended(boolean suspended) {
        String pkg = selectedPackage();
        if (!targetAllowed(pkg, suspended ? "suspend" : "unsuspend")) return;
        if (!requireDeviceOwner()) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            toast("Package suspension requires Android 7.0+");
            return;
        }
        runPrivileged((suspended ? "Suspend " : "Unsuspend ") + pkg, () -> {
            String[] failed = dpm.setPackagesSuspended(admin, new String[]{pkg}, suspended);
            return failed == null || failed.length == 0;
        });
    }

    private void setUninstallBlocked(boolean blocked) {
        String pkg = selectedPackage();
        if (!targetAllowed(pkg, blocked ? "block uninstall" : "allow uninstall")) return;
        if (!requireDeviceOwner()) return;
        runPrivileged((blocked ? "Block uninstall " : "Allow uninstall ") + pkg, () -> {
            dpm.setUninstallBlocked(admin, pkg, blocked);
            return dpm.isUninstallBlocked(admin, pkg) == blocked;
        });
    }

    private void reinstallAsGame() {
        String pkg = selectedPackage();
        if (!targetAllowed(pkg, "reinstall as game")) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            toast("CATEGORY_GAME installer hint requires Android 8.0+");
            return;
        }
        executor.execute(() -> {
            try {
                int sessionId = GameAppInstaller.reinstallAsGame(this, pkg);
                runOnUiThread(() -> toast("Reinstall session " + sessionId + " committed for " + pkg));
            } catch (Throwable e) {
                runOnUiThread(() -> toast("Reinstall-as-game failed: " + e.getMessage()));
            }
        });
    }

    private void setGameCategory(boolean game) {
        String pkg = selectedPackage();
        if (!targetAllowed(pkg, game ? "set game category" : "clear game category")) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            toast("Category hints require Android 8.0+");
            return;
        }
        runPrivileged((game ? "Set game category " : "Clear category ") + pkg, () -> {
            GameAppInstaller.setGameCategory(this, pkg, game);
            return true;
        });
    }

    private boolean requireDeviceOwner() {
        if (dpm == null || !dpm.isDeviceOwnerApp(getPackageName())) {
            toast("This operation requires this APK to be Device Owner");
            return false;
        }
        return true;
    }

    private boolean targetAllowed(String pkg, String operation) {
        if (TextUtils.isEmpty(pkg)) {
            toast("Select an app first");
            return false;
        }
        if (getPackageName().equals(pkg)) {
            toast("Refusing to " + operation + " the manager itself");
            return false;
        }
        return true;
    }

    private String selectedPackage() {
        int position = appSpinner == null ? -1 : appSpinner.getSelectedItemPosition();
        if (position < 0 || position >= entries.size()) return null;
        return entries.get(position).packageName;
    }

    private void runPrivileged(String action, BoolOperation operation) {
        executor.execute(() -> {
            try {
                boolean success = operation.run();
                runOnUiThread(() -> {
                    toast(action + (success ? " succeeded" : " failed"));
                    refreshAll();
                });
            } catch (Throwable e) {
                runOnUiThread(() -> toast(action + " failed: " + e.getMessage()));
            }
        });
    }

    private Button button(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private interface BoolOperation { boolean run() throws Exception; }

    private static final class AppEntry {
        final String label;
        final String packageName;
        AppEntry(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
        @Override public String toString() { return label + "\n" + packageName; }
    }
}
