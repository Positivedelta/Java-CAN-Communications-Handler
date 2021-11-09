package bitparallel.communication;

//
// (c) Bit Parallel Ltd, November 2021
//

public interface CanNotificationListener
{
    public void notifyNativeReadError(final int error) throws Exception;
    public void notifyBusOffError() throws Exception;
    public void notifyControllerRestarted() throws Exception;
    public void notifyControllerError(final int error) throws Exception;
    public void notifyProtocolError(final int error) throws Exception;
}
