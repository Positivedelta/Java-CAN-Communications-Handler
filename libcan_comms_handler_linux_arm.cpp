//
// (c) Bit Parallel Ltd, November 2021
//

#include <linux/can/raw.h>
#include <linux/can/error.h>
#include <net/if.h>
#include <stdint.h>
#include <unistd.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/socket.h>

#include <iomanip>
#include <string>
#include <sstream>

#include "bitparallel_communication_CanCommsHandler.h"

extern "C"
{
    // used in transmit()
    //
    union CanFrame
    {
        can_frame frame;
        uint8_t bytes[sizeof(can_frame)];
    };

    JNIEXPORT jlong JNICALL Java_bitparallel_communication_CanCommsHandler_nativeOpen(JNIEnv* env, jobject self, jstring device, jobjectArray filters)
    {
        // convert the java strings to C++ strings
        // note, first convert jstring to char* and then to std::string
        //
        const char *rawDevice = env->GetStringUTFChars(device, NULL);
        const std::string cppDevice = std::string(rawDevice);
        env->ReleaseStringUTFChars(device, rawDevice);

        // create the CAN socket
        //
        const jlong deviceFd = socket(PF_CAN, SOCK_RAW, CAN_RAW);
        if (deviceFd < 0)
        {
            std::stringstream errMsg;
            errMsg << "Unable to create the unbound CAN socket, native ERRNO: " << errno;
            const jclass jEx = env->FindClass("java/io/IOException");
            env->ThrowNew(jEx, errMsg.str().c_str());
            return -1;
        }

        // get the socket details
        //
        ifreq ifRequest;
        strcpy(ifRequest.ifr_name, cppDevice.c_str());
        if (ioctl(static_cast<int32_t>(deviceFd), SIOCGIFINDEX, &ifRequest) < 0)
        {
            std::stringstream errMsg;
            errMsg << "Unable to obtain the CAN socket details for device " << cppDevice << ", native ERRNO: " << errno;

            const jclass jEx = env->FindClass("java/io/IOException");
            env->ThrowNew(jEx, errMsg.str().c_str());
            return -1;
        }

        // bind the CAN socket
        //
        sockaddr_can socketCan;
        memset(&socketCan, 0, sizeof(socketCan));
        socketCan.can_family = AF_CAN;
        socketCan.can_ifindex = ifRequest.ifr_ifindex;
        if (bind(static_cast<int32_t>(deviceFd), (sockaddr*)&socketCan, sizeof(socketCan)) < 0)
        {
            std::stringstream errMsg;
            errMsg << "Unable to bind the CAN socket to device " << cppDevice << ", native ERRNO: " << errno;

            const jclass jEx = env->FindClass("java/io/IOException");
            env->ThrowNew(jEx, errMsg.str().c_str());
            return -1;
        }

        // FIXME! is this needed? i.e. is everything on by default?
        // enable bus-off and controller errors
        //
        can_err_mask_t errorMask = CAN_ERR_CRTL | CAN_ERR_BUSOFF;
//      can_err_mask_t errorMask = CAN_ERR_MASK; //CAN_ERR_CRTL | CAN_ERR_BUSOFF;
        if (setsockopt(static_cast<int32_t>(deviceFd), SOL_CAN_RAW, CAN_RAW_ERR_FILTER, &errorMask, sizeof(errorMask)) < 0)
        {
            std::stringstream errMsg;
            errMsg << "Unable to apply the CAN socket error filters to device " << cppDevice << ", native ERRNO: " << errno;

            const jclass jEx = env->FindClass("java/io/IOException");
            env->ThrowNew(jEx, errMsg.str().c_str());
            return -1;
        }

        // kernel based CAN filtering, don't use for high speed messages!
        //
        const jsize length = env->GetArrayLength(filters);
        if (length > 0)
        {
            const jclass filterClass = env->FindClass("bitparallel/communication/CanFilter");
            const jmethodID getMaskId = env->GetMethodID(filterClass, "getMask", "()I");
            const jmethodID getFilterId = env->GetMethodID(filterClass, "getFilter", "()I");

            can_filter cppFilters[length];
            for (jsize i = 0; i < length; i++)
            {
                const jobject filter = env->GetObjectArrayElement(filters, i);
                cppFilters[i].can_mask = env->CallIntMethod(filter, getMaskId);
                cppFilters[i].can_id = env->CallIntMethod(filter, getFilterId);
            }

            if (setsockopt(static_cast<int32_t>(deviceFd), SOL_CAN_RAW, CAN_RAW_FILTER, cppFilters, length) < 0)
            {
                std::stringstream errMsg;
                errMsg << "Unable to apply the CAN socket filters to device " << cppDevice << ", native ERRNO: " << errno;

                const jclass jEx = env->FindClass("java/io/IOException");
                env->ThrowNew(jEx, errMsg.str().c_str());
                return -1;
            }
        }

        return deviceFd;
    }

    JNIEXPORT void JNICALL Java_bitparallel_communication_CanCommsHandler_nativeTransmit(JNIEnv* env, jobject self, jobject message, jlong deviceFd)
    {
        const jclass messageClass = env->GetObjectClass(message);
        const jmethodID getIdId = env->GetMethodID(messageClass, "getId", "()I");
        const jmethodID getPayloadId = env->GetMethodID(messageClass, "getPayload", "()[B");
        const jbyteArray payload = reinterpret_cast<jbyteArray>(env->CallObjectMethod(message, getPayloadId));

        CanFrame msg;
        msg.frame.can_id = env->CallIntMethod(message, getIdId);
        msg.frame.can_dlc = env->GetArrayLength(payload);
        env->GetByteArrayRegion(payload, 0, msg.frame.can_dlc, reinterpret_cast<jbyte*>(msg.frame.data));

        // make sure that all of the message bytes get written
        //
        int32_t txedBytes, i = 0, size = sizeof(CanFrame);
        while ((size > 0) && (txedBytes = write(static_cast<int32_t>(deviceFd), &msg.bytes[i], size)) != size)
        {
            if (txedBytes < 0)
            {
                // note, EAGAIN and EWOULDBLOCK often have the same value, but not guaranteed, so check both
                //
                if ((errno == EINTR) || (errno == EAGAIN) || (errno == EWOULDBLOCK)) continue;

                std::stringstream errMsg;
                errMsg << "Error writing CAN message bytes, native EERNO: " << errno;

                const jclass jEx = env->FindClass("java/io/IOException");
                env->ThrowNew(jEx, errMsg.str().c_str());
                break;
            }

            size -= txedBytes;
            i += txedBytes;
        }
    }

    JNIEXPORT void JNICALL Java_bitparallel_communication_CanCommsHandler_nativeReceiveTask(JNIEnv* env, jobject self, jobject rxQueue, jobject running, jlong deviceFd)
    {
        // allows an oppertunity for the thread to exit every 100ms
        //
        timeval timeout;
        timeout.tv_sec = 0;
        timeout.tv_usec = 100000;

        const int32_t maxFd = 1 + static_cast<int32_t>(deviceFd);
        fd_set readFdSet;

        // for info(), warn() and error() log4j logger access
        //
        const jclass selfClass = env->GetObjectClass(self);
        const jobject logger = env->GetStaticObjectField(selfClass, env->GetStaticFieldID(selfClass, "logger", "Lorg/apache/logging/log4j/Logger;"));
        const jclass loggerClass = env->GetObjectClass(logger);
        const jmethodID warnId = env->GetMethodID(loggerClass, "warn", "(Ljava/lang/String;)V");
        const jmethodID errorId = env->GetMethodID(loggerClass, "error", "(Ljava/lang/String;)V");

        // used to report CAN read errors
        //
        const jmethodID errorCallbackId = env->GetMethodID(selfClass, "nativeReadErrorHandler", "(I)V");

        // used when creating CanMessage instances and the adding them to the rxQueue by invoking offer()
        //
        const jmethodID offerId = env->GetMethodID(env->GetObjectClass(rxQueue), "offer", "(Ljava/lang/Object;)Z");
        const jclass canMessageClass = env->FindClass("bitparallel/communication/CanMessage");
        const jmethodID canMessageConstructorId = env->GetMethodID(canMessageClass, "<init>", "(I[B)V");

        // to access running.get() from the provided AtomicBoolean instance
        //
        const jmethodID getId = env->GetMethodID(env->GetObjectClass(running), "get", "()Z");
        while (env->CallBooleanMethod(running, getId))
        {
            FD_ZERO(&readFdSet);
            FD_SET(static_cast<int32_t>(deviceFd), &readFdSet);

            int32_t fdCount = select(maxFd, &readFdSet, NULL, NULL, &timeout);
            if (fdCount > 0 && FD_ISSET(static_cast<int32_t>(deviceFd), &readFdSet))
            {
                can_frame frame;
                int32_t bytesRead = read(static_cast<int32_t>(deviceFd), &frame, sizeof(can_frame));
                if (bytesRead < 0)
                {
                    // something has gone wrong, log this and let the outside world know
                    //
                    std::stringstream errorMsg;
                    errorMsg << "Error reading from CAN device, status: " << errno;
                    env->CallVoidMethod(logger, errorId, env->NewStringUTF(errorMsg.str().c_str()));

                    // the Java callback will set running to false, allowing this handler to exit
                    //
                    env->CallVoidMethod(self, errorCallbackId, errno);
                    continue;
                }

                // create and queue a CanMessage instance
                //
                jbyteArray payload = env->NewByteArray(frame.can_dlc);
                env->SetByteArrayRegion(payload, 0, frame.can_dlc, reinterpret_cast<jbyte*>(frame.data));
                jobject canMessage = env->NewObject(canMessageClass, canMessageConstructorId, frame.can_id, payload);
                if (!env->CallBooleanMethod(rxQueue, offerId, canMessage))
                {
                    std::stringstream warnMsg;
                    warnMsg << "The receiver queue is full, discarding CAN message [id: 0x" << std::hex << std::setw(4) << std::setfill('0') << frame.can_id << std::dec << "]";
                    env->CallVoidMethod(logger, warnId, env->NewStringUTF(warnMsg.str().c_str()));
                }
            }
        }
    }

    JNIEXPORT void JNICALL Java_bitparallel_communication_CanCommsHandler_nativeClose(JNIEnv* env, jobject self, jstring device, jlong deviceFd)
    {
        if (close(static_cast<int32_t>(deviceFd)) < 0)
        {
            // convert the java strings to C++ strings
            // note, first convert jstring to char* and then to std::string
            //
            const char *rawDevice = env->GetStringUTFChars(device, NULL);
            const std::string cppDevice = std::string(rawDevice);
            env->ReleaseStringUTFChars(device, rawDevice);

            std::stringstream errMsg;
            errMsg << "Unable to close the CAN socket associated with device " << cppDevice << ", native ERRNO: " << errno;

            const jclass jEx = env->FindClass("java/io/IOException");
            env->ThrowNew(jEx, errMsg.str().c_str());
        }
    }
}
