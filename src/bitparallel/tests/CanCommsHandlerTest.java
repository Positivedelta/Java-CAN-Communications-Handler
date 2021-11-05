package bitparallel.tests;

//
// (c) Bit Parallel Ltd, November 2021
//

import java.io.IOException;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import bitparallel.communication.CanCommsHandler;
import bitparallel.communication.CanFilter;
import bitparallel.communication.CanMessage;
import bitparallel.communication.CanMessageListener;
import bitparallel.communication.CanReadErrorListener;

public class CanCommsHandlerTest implements CanMessageListener, CanReadErrorListener
{
    private static final Logger logger = LogManager.getLogger(CanCommsHandlerTest.class);

    public CanCommsHandlerTest(final String device) throws IOException
    {
        logger.info("Listening on device: " + device);

        // as an example of kernel based filtering you can add and instantiate them as follows
        //
//      final CanFilter[] filters = new CanFilter[] {new CanFilter(0xfff, 0x400), new CanFilter(0xfff, 0x410)};
        final CanFilter[] filters = new CanFilter[0];

        final CanCommsHandler handler = new CanCommsHandler(device, filters);
        handler.addMessageListener(this);
        handler.addReadErrorListener(this);
        handler.start();

        // notes 1, when testing with a linux client consider using "candump -L can0" or equivelent to see this message
        //       2, transmit() throws an IOException if it fails, if this happens it is likely that the handler will need restarting
        //
        final CanMessage message = new CanMessage(0x200, new byte[] {(byte)1, (byte)2, (byte)3, (byte)4, (byte)5, (byte)6, (byte)7, (byte)8});
        handler.transmit(message);

        final Thread t = new Thread(() -> {
            try
            {
                Thread.sleep(15000);
                handler.stop();
            }
            catch (final InterruptedException ignored)
            {
            }
        });

        t.start();
    }

    //
    // method required by the CanMessageListener interface
    //

    public final void rxedCanMessage(final CanMessage message)
    {
        logger.info(message.toString());
    }

    //
    // method required by the CanErrorListener interface
    //

    public final void notifyReadError(final Exception ex)
    {
        // notes 1, this method is intended be used to stop and restart the handler
        //       2, the native and java threads associated with the async reads will have been signalled to exit
        //
        logger.error("The CAN bus handler has reported a native read exception");
        logger.error(ex.getMessage());
    }

    public static final void main(String[] args)
    {
        try
        {
            final String device = args[0];
            final CanCommsHandlerTest test = new CanCommsHandlerTest(device);
        }
        catch (final IOException ex)
        {
            logger.error("Unable to complete test, reason: " + ex.getMessage());
        }
    }
}
