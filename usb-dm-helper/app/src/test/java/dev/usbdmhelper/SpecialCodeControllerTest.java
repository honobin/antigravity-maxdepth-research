package dev.usbdmhelper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SpecialCodeControllerTest {
    @Test
    public void sendUsbSettingsCode_sendsExactly0808() {
        RecordingSender sender = new RecordingSender();
        SpecialCodeController controller = new SpecialCodeController(sender);

        controller.sendUsbSettingsCode();

        assertEquals(1, sender.callCount);
        assertEquals("0808", sender.lastCode);
    }

    private static final class RecordingSender implements SpecialCodeController.Sender {
        int callCount;
        String lastCode;

        @Override
        public void send(String code) {
            callCount++;
            lastCode = code;
        }
    }
}
