#!/bin/bash

#
# candump -L can0
# cansend can1 100#1122334455667788
# cangen can1 -g 10 -n 1000 -I 100 -D 11223344DEADBEEF -L 8
#

sudo rmmod mcp251x.ko
sudo insmod ../dual_mcp251x_driver/mcp251x.ko device=can0 mode1=1,1 mask1=0xfff,0xfff filt1=0x100,0x101,0x102,0x103,0x104,0x105

sudo ip link set can0 type can restart-ms 100
sudo ip link set can0 up type can bitrate 125000
sudo ifconfig can0 txqueuelen 65536

sudo ip link set can1 type can restart-ms 100
sudo ip link set can1 up type can bitrate 125000
sudo ifconfig can1 txqueuelen 65536
