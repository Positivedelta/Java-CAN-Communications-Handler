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

public class CanCommsHandlerTest implements CanMessageListener
{
    private static final Logger logger = LogManager.getLogger(CanCommsHandlerTest.class);

    public CanCommsHandlerTest(final String device) throws IOException
    {
        logger.info("Listening on device: " + device);

        final CanFilter[] filters = new CanFilter[0];
        final CanCommsHandler handler = new CanCommsHandler(device, filters);
        handler.addListener(this);
        handler.start();

        // note, when testing with a linux client consider using "candump -L can0" or equivelent to see this message
        //
        final CanMessage message = new CanMessage(0x200, new byte[] {(byte)1, (byte)2, (byte)3, (byte)4, (byte)5, (byte)6, (byte)7, (byte)8});
        handler.transmit(message);

        final Thread t = new Thread(() -> {
            try
            {
                Thread.sleep(15000);
                handler.stop();
            }
            catch (final Exception ex)
            {
                logger.error("Unexpected exception, reason: " + ex.getMessage(), ex);
            }
        });

        t.start();
    }

    public final void rxedCanMessage(final CanMessage message)
    {
        final byte[] data = message.getPayload();
        logger.info("     Id: " + String.format("%02x", message.getId()) + ", Length: " + data.length);

        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++)
        {
            sb.append(", ");
            sb.append(String.format("%02x", data[i]));
        }

        logger.info("Payload: " + sb.toString().substring(1));
    }

    public static final void main(String[] args) throws IOException
    {
        final String device = args[0];
        final CanCommsHandlerTest test = new CanCommsHandlerTest(device);
    }
}
