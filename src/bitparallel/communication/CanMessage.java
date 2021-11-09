package bitparallel.communication;

//
// (c) Bit Parallel Ltd, November 2021
//

//
// CAN Identifier details
//
// bit 0-28      : CAN identifier (11/29 bit)
// bit 29        : error frame flag (0 = data frame, 1 = error frame)
// bit 30        : remote transmission request flag (1 = rtr frame)
// bit 31        : frame format flag (0 = standard 11 bit, 1 = extended 29 bit)
//

public class CanMessage
{
    //
    // defined in can.h
    //

    public static final int CAN_SFF_MASK = 0x000007ff;                  // standard frame format (SFF)
    public static final int CAN_EFF_MASK = 0x1fffffff;                  // extended frame format (EFF)
    public static final int CAN_ERR_MASK = 0x1fffffff;                  // omit EFF, RTR, ERR flags

    public static final int CAN_EFF_FLAG = 0x80000000;                  // EFF/SFF is set in the MSB */
    public static final int CAN_RTR_FLAG = 0x40000000;                  // remote transmission request */
    public static final int CAN_ERR_FLAG = 0x20000000;                  // error message frame */

    //
    // defined in can/error.h, CAN controller error status - data[1], data[2]
    //

    public static final int CAN_ERR_CRTL = 0x00000004;                  // controller problems - data[1]
    public static final int CAN_ERR_PROT = 0x00000008;                  // can protocol errors and notifications - data[2]
    public static final int CAN_ERR_BUSOFF = 0x00000040;                // bus off
    public static final int CAN_ERR_RESTARTED = 0x00000100;             // controller restarted

    // data[1] bits
    //
    public static final byte CAN_ERR_CRTL_UNSPEC = (byte)0x00;          // unspecified
    public static final byte CAN_ERR_CRTL_RX_OVERFLOW = (byte)0x01;     // RX buffer overflow
    public static final byte CAN_ERR_CRTL_TX_OVERFLOW = (byte)0x02;     // TX buffer overflow
    public static final byte CAN_ERR_CRTL_RX_WARNING = (byte)0x04;      // reached warning level for RX errors
    public static final byte CAN_ERR_CRTL_TX_WARNING = (byte)0x08;      // reached warning level for TX errors

    // data[2] bits
    //
    public static final byte CAN_ERR_PROT_ACTIVE = (byte)0x40;          // can active error state announcement

    // passive error status, at least one error counter exceeds the protocol-defined level of 127
    //
    public static final byte CAN_ERR_CRTL_RX_PASSIVE = (byte)0x10;      // rx
    public static final byte CAN_ERR_CRTL_TX_PASSIVE = (byte)0x20;      // tx
    public static final byte CAN_ERR_CRTL_ACTIVE = (byte)0x40;          // recovered to error active state

    private int id;
    private final byte[] payload;

    // note, used by the native receiver task
    //
    public CanMessage(final int id, final byte[] payload)
    {
        this.id = id;
        this.payload = payload;
    }

    // intended to be used when transmitting
    //
    public CanMessage(final boolean isEFF, final boolean isRTR, final int id, final byte[] payload)
    {
        this.id = id;
        if (isEFF) this.id = this.id | CAN_EFF_FLAG;
        if (isRTR) this.id = this.id | CAN_RTR_FLAG;

        this.payload = payload;
    }

    public final int getId()
    {
        // exclude the SFF/EFF, RTR, ERR flags
        //
        return id & CAN_ERR_MASK;
    }

    public final int getRawId()
    {
        return id;
    }

    public final byte[] getPayload()
    {
        return payload;
    }

    public final boolean isExtendedId()
    {
        return (id & CAN_EFF_FLAG) != 0;
    }

    public final boolean isStandardId()
    {
        return (id & CAN_EFF_FLAG) == 0;
    }

    public final boolean isDataFrame()
    {
        return (id & CAN_ERR_FLAG) == 0;
    }

    public final boolean isErrorFrame()
    {
        return (id & CAN_ERR_FLAG) != 0;
    }

    public final boolean isBusOffError()
    {
        return (getId() & CAN_ERR_BUSOFF) != 0;
    }

    public final boolean isControllerError()
    {
        return (getId() & CAN_ERR_CRTL) != 0;
    }

    public final boolean isControllerRestarted()
    {
        return (getId() & CAN_ERR_RESTARTED) != 0;
    }

    public final boolean isProtocolError()
    {
        return (getId() & CAN_ERR_PROT) != 0;
    }

    // intended to save time and complexity by letting a CAN controller store a pre-formatted message and send it
    // immediately on request, potentailly without any microcontroller involvement (if the harware supports it)
    //
    public final boolean isRemoteTransmissionRequest()
    {
        return (id & CAN_RTR_FLAG) != 0;
    }

    public static String controllerErrorMessage(final int error)
    {
        String message = String.format("0x%02x", error) + " (Unexpected error code)"; 
        switch (error)
        {
            case CAN_ERR_CRTL_UNSPEC:
                message = "Unspecified [" + String.format("0x%02x]", error);
                break;

            case CAN_ERR_CRTL_RX_OVERFLOW:
                message = "RX Buffer overflow [" + String.format("0x%02x]", error);
                break;

            case CAN_ERR_CRTL_TX_OVERFLOW:
                message = "TX Buffer overflow [" + String.format("0x%02x]", error);
                break;

            case CAN_ERR_CRTL_RX_WARNING:
                message = "Reached RX warning threshold [" + String.format("0x%02x]", error);
                break;

            case CAN_ERR_CRTL_TX_WARNING:
                message = "Reached TX warning threshold [" + String.format("0x%02x]", error);
                break;

            case CAN_ERR_CRTL_RX_PASSIVE:
                message = "Reached RX passive threshold [" + String.format("0x%02x]", error);
                break;

            case CAN_ERR_CRTL_TX_PASSIVE:
                message = "Reached TX passive threshold [" + String.format("0x%02x]", error);
                break;

            case CAN_ERR_CRTL_ACTIVE:
                message = "Recovered to error active state [" + String.format("0x%02x]", error);
                break;
        }

        return message;
    }

    public static String protocolErrorMessage(final int error)
    {
        String message = String.format("0x%02x", error) + " (Unexpected error code)"; 
        switch (error)
        {
            case CAN_ERR_PROT_ACTIVE:
                message = "Active error state announcement [" + String.format("0x%02x]", error);
                break;
        }

        return message;
    }

    @Override
    public final String toString()
    {
        final StringBuffer sb = new StringBuffer();
        sb.append("Id: 0x");
        sb.append(String.format("%04x", getId()));
        sb.append(", Length: ");
        sb.append(payload.length);
        sb.append(", Data: ");
        for (byte b : payload) sb.append(String.format("0x%02x ", b));

        sb.append("[");
        if (isStandardId())
        {
            sb.append("SFF");
        }
        else
        {
            sb.append("EFF");
        }

        if (isRemoteTransmissionRequest()) sb.append(", RTR");
        if (isErrorFrame()) sb.append(", ERR");     // FIXME! expand this...
        sb.append("]");

        return sb.toString();
    }
}
