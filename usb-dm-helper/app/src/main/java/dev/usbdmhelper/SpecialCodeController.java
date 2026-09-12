package dev.usbdmhelper;

import java.util.Objects;

public final class SpecialCodeController {
    public interface Sender {
        void send(String code);
    }

    private final Sender sender;

    public SpecialCodeController(Sender sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    public void sendUsbSettingsCode() {
        sender.send("0808");
    }
}
