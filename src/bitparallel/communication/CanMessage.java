package bitparallel.communication;

//
// (c) Bit Parallel Ltd, November 2021
//

public class CanMessage
{
    private final int id;
    private final byte[] payload;

    public CanMessage(final int id, final byte[] payload)
    {
        this.id = id;
        this.payload = payload;
    }

    public int getId()
    {
        return id;
    }

    public byte[] getPayload()
    {
        return payload;
    }
}
