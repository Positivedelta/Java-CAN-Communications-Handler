//
// (c) Bit Parallel Ltd, November 2021
//

#include <linux/can/raw.h>
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
            const std::string errMsg = "Unable to create the unbound CAN socket";
            const jclass jEx = env->FindClass("java/io/IOException");
            env->ThrowNew(jEx, errMsg.c_str());
            return -1;
        }

        // get the socket details
        //
        ifreq ifRequest;
        strcpy(ifRequest.ifr_name, cppDevice.c_str());
        ioctl(static_cast<int32_t>(deviceFd), SIOCGIFINDEX, &ifRequest);

        // bind the CAN socket
        //
        sockaddr_can socketCan;
        memset(&socketCan, 0, sizeof(socketCan));
        socketCan.can_family = AF_CAN;
        socketCan.can_ifindex = ifRequest.ifr_ifindex;
        if (bind(static_cast<int32_t>(deviceFd), (sockaddr*)&socketCan, sizeof(socketCan)) < 0)
        {
            std::stringstream errMsg;
            errMsg << "Unable to bind the CAN socket to device " << cppDevice;

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

            setsockopt(static_cast<int32_t>(deviceFd), SOL_CAN_RAW, CAN_RAW_FILTER, cppFilters, length);
        }

        return deviceFd;
    }

    JNIEXPORT void JNICALL Java_bitparallel_communication_CanCommsHandler_nativeTransmit(JNIEnv* env, jobject self, jobject message, jlong deviceFd)
    {
        const jclass messageClass = env->GetObjectClass(message);
        const jmethodID getIdId = env->GetMethodID(messageClass, "getId", "()I");
        const jmethodID getPayloadId = env->GetMethodID(messageClass, "getPayload", "()[B");
        const jbyteArray payload = reinterpret_cast<jbyteArray>(env->CallObjectMethod(message, getPayloadId));

        can_frame frame;
        frame.can_id = env->CallIntMethod(message, getIdId);
        frame.can_dlc = env->GetArrayLength(payload);
        env->GetByteArrayRegion(payload, 0, frame.can_dlc, reinterpret_cast<jbyte*>(frame.data));

        // FIXME! check to see if all of the message bytes get written
        //        not sure how to handle this, investigate... use ioctl with SIOCOUTQ to query the buffer etc?
        //
        int32_t bytesWritten = write(static_cast<int32_t>(deviceFd), &frame, sizeof(can_frame));
        if (bytesWritten < 0)
        {
            std::stringstream errMsg;
            errMsg << "Error writing CAN message bytes, status: " << bytesWritten;

            const jclass jEx = env->FindClass("java/io/IOException");
            env->ThrowNew(jEx, errMsg.str().c_str());
        }
    }

    JNIEXPORT void JNICALL Java_bitparallel_communication_CanCommsHandler_nativeReceiveTask(JNIEnv* env, jobject self, jobject rxQueue, jobject running, jobject stopListener, jlong deviceFd)
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
        const jmethodID infoId = env->GetMethodID(loggerClass, "info", "(Ljava/lang/String;)V");
        const jmethodID warnId = env->GetMethodID(loggerClass, "warn", "(Ljava/lang/String;)V");
        const jmethodID errorId = env->GetMethodID(loggerClass, "error", "(Ljava/lang/String;)V");

        // used when creating CanMessage instances and the adding them to the rxQueue by invoking offer()
        //
        const jmethodID offerId = env->GetMethodID(env->GetObjectClass(rxQueue), "offer", "(Ljava/lang/Object;)Z");
        const jclass canMessageClass = env->FindClass("bitparallel/communication/CanMessage");
        const jmethodID canMessageConstructorId = env->GetMethodID(canMessageClass, "<init>", "(I[B)V");

        // to get() and set() the provided AtomicBoolean instance
        //
        const jclass atomicBooleanClass = env->GetObjectClass(running);
        const jmethodID getId = env->GetMethodID(atomicBooleanClass, "get", "()Z");
        const jmethodID setId = env->GetMethodID(atomicBooleanClass, "set", "(Z)V");
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
                    // FIXME! improve this... add a callback to tell the outside world that this task has failed
                    //
                    // note, as this task will now exit, signal the receiver queue handler to also stop
                    //
                    env->CallVoidMethod(running, setId, false);
                    env->CallBooleanMethod(stopListener, setId, false);

                    std::stringstream errorMsg;
                    errorMsg << "Error reading from CAN device, status: " << bytesRead;
                    env->CallVoidMethod(logger, errorId, env->NewStringUTF(errorMsg.str().c_str()));

                    const std::string infoMsg = "Exiting native receiver task";
                    env->CallVoidMethod(logger, infoId, env->NewStringUTF(infoMsg.c_str()));
                    break;
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
            errMsg << "Unable to close the CAN socket associated with device " << cppDevice;

            const jclass jEx = env->FindClass("java/io/IOException");
            env->ThrowNew(jEx, errMsg.str().c_str());
        }
    }
}
