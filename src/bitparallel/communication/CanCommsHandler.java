package bitparallel.communication;

//
// (c) Bit Parallel Ltd, November 2021
//

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class CanCommsHandler
{
    public static final int RECEIVER_MESSAGE_QUEUE_SIZE = 1024;
    public static final long RECEIVER_QUEUE_POLL_TIMEOUT_MS = 100;

    private static final Logger logger = LogManager.getLogger(CanCommsHandler.class);

    static
    {
        // FIXME! may need to include "sun.arch.data.model", not sure...
        //
        final String osName = System.getProperty("os.name");
        final String osArch = System.getProperty("os.arch");
        if ("Linux".equals(osName) && "arm".equals(osArch))
        {
            loadNativeLibrary("libcan_comms_handler_linux_arm32.so");
        }
        else if ("Linux".equals(osName) && "aarch64".equals(osArch))
        {
            loadNativeLibrary("libcan_comms_handler_linux_arm64.so");
        }
        else
        {
            final StringBuffer sb = new StringBuffer();
            sb.append("The detected OS and architecture combination is not supported: ");
            sb.append(osName);
            sb.append(" (");
            sb.append(osArch);
            sb.append(")");

            throw new UnsatisfiedLinkError(sb.toString());
        }
    }

    private final String device;
    private final long deviceFd;
    private final AtomicBoolean rxNativeTaskRunning, rxListenerTaskRunning;
    private final Runnable rxNativeTask, rxListenerTask;
    private final LinkedBlockingQueue<CanMessage> receiverQueue;
    private final CopyOnWriteArrayList<CanMessageListener> canMessageListeners;
    private final CopyOnWriteArrayList<CanReadErrorListener> canReadErrorListeners;
    private Thread rxNativeThread, rxListenerThread;

    public CanCommsHandler(final String device, final CanFilter[] filters) throws IOException
    {
        this.device = device;

        deviceFd = nativeOpen(device, filters);

        canMessageListeners = new CopyOnWriteArrayList<CanMessageListener>();
        canReadErrorListeners = new CopyOnWriteArrayList<CanReadErrorListener>();
        receiverQueue = new LinkedBlockingQueue<CanMessage>(RECEIVER_MESSAGE_QUEUE_SIZE);
        rxNativeTaskRunning = new AtomicBoolean(false);
        rxListenerTaskRunning = new AtomicBoolean(false);

        rxNativeThread = new Thread();
        rxNativeTask = () -> {
            // note, if the native task fails it will exit and will also signal the listener task to exit
            //
            logger.info("The native CAN receiver task is running");
            nativeReceiveTask(receiverQueue, rxNativeTaskRunning, deviceFd);
        };

        rxListenerThread = new Thread();
        rxListenerTask = () -> {
            logger.info("The CAN receiver listener task is running");
            while(rxListenerTaskRunning.get())
            {
                // wait for a mesage and then transmit it to the subscribed listeners
                //
                try
                {
                    final CanMessage message = receiverQueue.poll(RECEIVER_QUEUE_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (message == null) continue;

                    for (CanMessageListener listener : canMessageListeners)
                    {
                        try
                        {
                            listener.rxedCanMessage(message);
                        }
                        catch (final Exception ex)
                        {
                            logger.error("Unexpected exception in CAN listener, reason: " + ex.getMessage(), ex);
                        }
                    }
                }
                catch (final InterruptedException ignored)
                {
                }
            }
        };
    }

    private native long nativeOpen(final String device, final CanFilter[] filters) throws IOException;
    private native void nativeTransmit(final CanMessage message, final long deviceFd) throws IOException;
    private native void nativeReceiveTask(final LinkedBlockingQueue<CanMessage> receiveQueue, final AtomicBoolean running, final long deviceFd);
    private native void nativeClose(final String device, final long deviceFd) throws IOException;

    public void transmit(final CanMessage message) throws IOException
    {
        nativeTransmit(message, deviceFd);
    }

    public final boolean start()
    {
        final boolean nativeThreadReady = (rxNativeThread.getState() == Thread.State.NEW) || (rxNativeThread.getState() == Thread.State.TERMINATED);
        final boolean listenerThreadReady = (rxListenerThread.getState() == Thread.State.NEW) || (rxListenerThread.getState() == Thread.State.TERMINATED);
        boolean success = nativeThreadReady && listenerThreadReady;
        if (success)
        {
            //
            // note, this task is started first as it's possible to the native task to stop it if it fails to read from the CAN socket
            // start draining the receiver queue, passing the ByteBuffer messages to the subscribed listeners
            //

            rxListenerThread = new Thread(rxListenerTask);
            rxListenerThread.setDaemon(true);
            rxListenerTaskRunning.set(true);
            rxListenerThread.start();

            //
            // start populating the receiver queue with ByteBuffer messages
            //

            rxNativeThread = new Thread(rxNativeTask);
            rxNativeThread.setDaemon(true);

            rxNativeTaskRunning.set(true);
            rxNativeThread.start();
        }

        return success;
    }

    public final boolean stop()
    {
        boolean success = true;
        try
        {
            rxNativeTaskRunning.set(false);
            rxNativeThread.join();
        }
        catch (final InterruptedException ignored)
        {
        }

        try
        {
            rxListenerTaskRunning.set(false);
            rxListenerThread.join();
        }
        catch (final InterruptedException ignored)
        {
        }

        try
        {
            nativeClose(device, deviceFd);
        }
        catch (final IOException ex)
        {
            logger.error("Unable to close the native file descriptor, reason: " + ex.getMessage(), ex);
            success = false;
        }

        return success;
    }

    public void addMessageListener(final CanMessageListener canMessageListener)
    {
        canMessageListeners.add(canMessageListener);
    }

    public void removeMessageListener(final CanMessageListener canMessageListener)
    {
        canMessageListeners.remove(canMessageListener);
    }

    public void clearMessageListeners()
    {
        canMessageListeners.clear();
    }

    public void addReadErrorListener(final CanReadErrorListener canReadErrorListener)
    {
        canReadErrorListeners.add(canReadErrorListener);
    }

    public void removeReadErrorListener(final CanReadErrorListener canReadErrorListener)
    {
        canReadErrorListeners.remove(canReadErrorListener);
    }

    public void clearReadErrorListeners()
    {
        canReadErrorListeners.clear();
    }

    private final void nativeReadErrorHandler(final int errorCode)
    {
        // unable to read from the underlying socketCAN file descriptor
        // 1, allow the native thread to exit
        // 2, allow the queued message receiver thread to exit
        // 3, inform any listening clients, allowing them to stop and restart the handler
        //
        rxNativeTaskRunning.set(false);
        rxListenerTaskRunning.set(false);
        logger.warn("The native and receiver queue threads have been signalled to exit");

        // note, this method is called from within the native receiver thread
        // so it mustn't block whilst notifying the registered error listeners
        //
        final IOException error = new IOException("Error whilst reading from the socketCAN file descriptor, reason: " + errorCode);
        final Runnable notifyTask = () -> {
            for (CanReadErrorListener listener : canReadErrorListeners)
            {
                try
                {
                    listener.notifyReadError(error);
                }
                catch (final Exception ex)
                {
                    logger.error("Unexpected exception whilst notifying a CanErrorReadListener, reason: " + ex.getMessage(), ex);
                }
            }
        };

        final Thread notifyThread = new Thread(notifyTask);
        notifyThread.setDaemon(true);
        notifyThread.start();
    }

    // native library loading helper method, see the static initialiser above
    //
    private static final void loadNativeLibrary(final String libraryName) throws UnsatisfiedLinkError
    {
        try
        {
            final File javaTemp = new File(System.getProperty("java.io.tmpdir"));
            final File tempDir = new File(javaTemp, CanCommsHandler.class.getName());
            if (!tempDir.isDirectory()) tempDir.mkdir();

            final File nativeLib = new File(tempDir, libraryName);
            final InputStream is = CanCommsHandler.class.getClassLoader().getResourceAsStream(libraryName);
            Files.copy(is, nativeLib.toPath(), StandardCopyOption.REPLACE_EXISTING);
            is.close();

            // note, if using loadLibrary() on linux the actual file name must be prefixed with 'lib', it's a naming convention, but not on windows!
            //
            System.load(tempDir.getPath() + File.separator + libraryName);
        }
        catch (final Exception ex)
        {
            final StringBuffer sb = new StringBuffer();
            sb.append("Failed to install the native library, reason: ");
            sb.append(ex.getMessage());

            throw new UnsatisfiedLinkError(sb.toString());
        }
    }
}
