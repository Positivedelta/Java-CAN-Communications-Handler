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
        this.id = id & 0x1fffffff;
        if (isEFF) this.id = this.id | 0x80000000;
        if (isRTR) this.id = this.id | 0x40000000;

        this.payload = payload;
    }

    public final int getId()
    {
        // exclude the SFF/EFF, RTR, ERR flags
        //
        return id & 0x1fffffff;
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
        return (id & 0x80000000) != 0;
    }

    public final boolean isStandardId()
    {
        return (id & 0x80000000) == 0;
    }

    public final boolean isDataFrame()
    {
        return (id & 0x20000000) == 0;
    }

    public final boolean isErrorFrame()
    {
        return (id & 0x20000000) != 0;
    }

    // intended to save time and complexity by letting a CAN controller store a pre-formatted message and send it
    // immediately on request, potentailly without any microcontroller involvement (if the harware supports it)
    //
    public final boolean isRemoteTransmissionRequest()
    {
        return (id & 0x40000000) != 0;
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
        if (isErrorFrame()) sb.append(", ERR");
        sb.append("]");

        return sb.toString();
    }
}
