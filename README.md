MemDefender is a tool for allocation monitoring and leak injection for Java applications.

Allocation monitoring:

The allocation monitoring is built on top of Java Allocation Instrumenter (https://github.com/google/allocation-instrumenter). It collects for each allocation site, number of allocated and deallocated objects, amount of memory allocated in Bytes, and the number of allocated and deallocated objects in each generation of the garbage collection.

Leak injection:

To use MemDefender as a leak injector, a config file is required.

It should be placed under the lib directory of the script or application which uses LiveObjectMonitoring (LOM).
