// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.dpc;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import androidx.annotation.NonNull;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PackageSignatureUtils {
    private PackageSignatureUtils() {}

    @NonNull
    static String getCurrentSignerDigest(@NonNull PackageManager pm, @NonNull String packageName)
            throws Exception {
        final PackageInfo info;
        final Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
            if (info.signingInfo == null) {
                throw new PackageManager.NameNotFoundException("No signingInfo for " + packageName);
            }
            signatures = info.signingInfo.getApkContentsSigners();
        } else {
            //noinspection deprecation
            info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            //noinspection deprecation
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length == 0) {
            throw new PackageManager.NameNotFoundException("No signer for " + packageName);
        }
        List<String> digests = new ArrayList<>(signatures.length);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (Signature signature : signatures) {
            byte[] digest = md.digest(signature.toByteArray());
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            digests.add(sb.toString());
            md.reset();
        }
        Collections.sort(digests);
        StringBuilder joined = new StringBuilder();
        for (String digest : digests) {
            if (joined.length() > 0) joined.append(',');
            joined.append(digest);
        }
        return joined.toString();
    }
}
