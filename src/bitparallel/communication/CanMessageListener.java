package bitparallel.communication;

//
// (c) Bit Parallel Ltd, November 2021
//

public interface CanMessageListener
{
    public void rxedCanMessage(final CanMessage message) throws Exception;
}
