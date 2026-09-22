package nodomain.freeyourgadget.gadgetbridge.devices.huawei.packets;

import java.util.Objects;

import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiPacket;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiTLV;

/** Band 10 phone alarm commands verified with one manual start and dismiss probe. */
public final class PhoneAlarm {
    public static final int ALARM_ID = 1;
    public static final int DISMISS_ACTION_ID = 2;

    private PhoneAlarm() {
    }

    public static final class Start extends HuaweiPacket {
        public static final byte COMMAND_ID = 0x09;

        public Start(final ParamsProvider paramsProvider, final long alarmTime) {
            super(Objects.requireNonNull(paramsProvider, "paramsProvider"));
            serviceId = Alarms.id;
            commandId = COMMAND_ID;
            tlv = new HuaweiTLV()
                    .put(0x01, ALARM_ID)
                    .put(0x02, "Phone alarm")
                    .put(0x03, alarmTime)
                    .put(0x04, (byte) 10)
                    .put(0x05, (byte) 3);
            complete = true;
        }
    }

    public static final class Dismiss extends HuaweiPacket {
        public static final byte COMMAND_ID = 0x0a;

        public Dismiss(final ParamsProvider paramsProvider) {
            super(Objects.requireNonNull(paramsProvider, "paramsProvider"));
            serviceId = Alarms.id;
            commandId = COMMAND_ID;
            tlv = new HuaweiTLV()
                    .put(0x01, ALARM_ID)
                    .put(0x02, (byte) DISMISS_ACTION_ID);
            complete = true;
        }
    }
}
