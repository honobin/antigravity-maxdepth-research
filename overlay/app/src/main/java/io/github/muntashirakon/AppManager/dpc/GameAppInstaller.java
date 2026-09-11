// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.dpc;

import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

final class GameAppInstaller {
    private static final int BUFFER_SIZE = 128 * 1024;

    private GameAppInstaller() {}

    @RequiresApi(Build.VERSION_CODES.O)
    static int reinstallAsGame(@NonNull Context context, @NonNull String packageName) throws Exception {
        if (context.getPackageName().equals(packageName)) {
            throw new SecurityException("Refusing to reinstall the manager itself");
        }
        PackageManager pm = context.getPackageManager();
        ApplicationInfo appInfo = pm.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS);
        String expectedSigner = PackageSignatureUtils.getCurrentSignerDigest(pm, packageName);

        List<File> apkFiles = new ArrayList<>();
        apkFiles.add(new File(appInfo.sourceDir));
        if (appInfo.splitSourceDirs != null) {
            for (String split : appInfo.splitSourceDirs) apkFiles.add(new File(split));
        }
        long totalSize = 0L;
        for (File apk : apkFiles) {
            if (!apk.isFile() || !apk.canRead()) {
                throw new IllegalStateException("Cannot read installed APK: " + apk);
            }
            totalSize += apk.length();
        }

        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(packageName);
        params.setSize(totalSize);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
            if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_OTHER);
        }

        PackageInstaller installer = pm.getPackageInstaller();
        int sessionId = installer.createSession(params);
        boolean committed = false;
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int index = 0;
            for (File apk : apkFiles) {
                String name = index++ == 0 ? "base.apk" : "split_" + index + ".apk";
                try (FileInputStream in = new FileInputStream(apk);
                     OutputStream out = session.openWrite(name, 0L, apk.length())) {
                    int n;
                    while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                    session.fsync(out);
                }
            }

            Intent resultIntent = new Intent(context, GameInstallResultReceiver.class)
                    .setAction(GameInstallResultReceiver.ACTION_GAME_INSTALL_RESULT)
                    .putExtra(GameInstallResultReceiver.EXTRA_PACKAGE_NAME, packageName)
                    .putExtra(GameInstallResultReceiver.EXTRA_EXPECTED_SIGNER, expectedSigner);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, sessionId, resultIntent, flags);
            session.commit(pendingIntent.getIntentSender());
            committed = true;
            return sessionId;
        } finally {
            if (!committed) {
                try {
                    installer.abandonSession(sessionId);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    static boolean isInstallerOfRecord(@NonNull Context context, @NonNull String packageName) {
        PackageManager pm = context.getPackageManager();
        try {
            String installer;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                InstallSourceInfo sourceInfo = pm.getInstallSourceInfo(packageName);
                installer = sourceInfo.getInstallingPackageName();
            } else {
                //noinspection deprecation
                installer = pm.getInstallerPackageName(packageName);
            }
            return context.getPackageName().equals(installer);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    static void setGameCategory(@NonNull Context context, @NonNull String packageName, boolean game)
            throws Exception {
        if (!isInstallerOfRecord(context, packageName)) {
            throw new SecurityException("App Manager is not installer of record for " + packageName);
        }
        int category = game ? ApplicationInfo.CATEGORY_GAME : ApplicationInfo.CATEGORY_UNDEFINED;
        context.getPackageManager().setApplicationCategoryHint(packageName, category);
        ApplicationInfo updated = context.getPackageManager().getApplicationInfo(
                packageName, PackageManager.MATCH_DISABLED_COMPONENTS);
        if (updated.category != category) {
            throw new IllegalStateException("Category verification failed for " + packageName);
        }
    }
}
