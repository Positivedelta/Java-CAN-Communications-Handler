CC=g++
CXX_COMPILE_FLAGS=-std=c++17 -Wall -O3 -I include -I $(JAVA_HOME)/include -I $(JAVA_HOME)/include/linux

# note, update the mv to reflect underlying architecture, i.e. a 32 / 64 bit so
#
all: libcan_comms_handler_linux_arm.so
	mv libcan_comms_handler_linux_arm.so libcan_comms_handler_linux_arm64.so
#	mv libcan_comms_handler_linux_arm.so libcan_comms_handler_linux_arm32.so

clean:
	rm -rf *.o *.so

libcan_comms_handler_linux_arm.o: libcan_comms_handler_linux_arm.cpp
libcan_comms_handler_linux_arm.so: libcan_comms_handler_linux_arm.o

%.o: %.cpp
	$(CC) $(CXX_COMPILE_FLAGS) -fPIC -c $<

%.so: %.o
	$(CC) $(CXX_COMPILE_FLAGS) -shared -o $@ $<
