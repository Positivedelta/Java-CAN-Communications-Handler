package bitparallel.communication;

//
// (c) Bit Parallel Ltd, November 2021
//

public interface CanReadErrorListener
{
    public void notifyReadError(final Exception ex) throws Exception;
}
