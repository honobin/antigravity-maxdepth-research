// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.dpc;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DpcConstantsTest {
    @Test
    public void gameResultActionIsStable() {
        assertEquals("io.github.muntashirakon.AppManager.action.GAME_INSTALL_RESULT",
                GameInstallResultReceiver.ACTION_GAME_INSTALL_RESULT);
    }
}
