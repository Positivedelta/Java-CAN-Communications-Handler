# Java CAN Communications Handler
A simple Java CAN bus communications class API written using JNI (with C/C++) that supports Linux

The code included in this repository has been tested using,
- Oracle Java 8 and 17
- The Apache Ant build tool
- GCC 9+
- A Raspberry Pi 4 running 64 and 32-bit Linux (Buster)
  - The Waveshare dual CAN bus expansion board (or equivalent)
    - See, https://www.waveshare.com/2-ch-can-hat.htm
  - Requires a CAN Kernel driver
    - See, https://github.com/Positivedelta/Dual-MCP251x.git

Please note, this code was written for fun and probably contains bugs!  
However, I plan to evolve and support this code as I use it in my other projects  

Please feel free to e-mail me if you need any assistance using this repository  

Notes,
- Use `make` to build the JNI shared library, then use `ant jar` to build the Java API
- Use `ant -p` to display all of the available Java build targets
- Use `ant test` to build and run a simple test harness, note that the resulting JAR includes Log4J2 support
  - In conjunction with `can-utils`,
    - e.g. Listen using `candump -L can0`
    - e.g. Transmit using `cansend can0 123#1122334455667788`
    - See, https://github.com/linux-can/can-utils
  - Intended to provide a simple demonstration of the handler API
