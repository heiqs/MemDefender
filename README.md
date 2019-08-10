# MemDefender: An Allocation Monitor and Leak Injection Tool for Java

## What is it?
MemDefender is a tool (a java agent) for allocation monitoring and leak injection for Java applications. 
Its primary purpose is to provide support for automated leak detection, and facilitate evaluation of approaches for memory leak detection.


### Allocation monitoring
MemDefender reports data on memory usage for each object allocation site (i.e. code location which uses "new" or creates an object implicitly).
For each allocation site, following data is collected:
* Number of allocated and deallocated objects
* Amount of memory allocated in bytes,
* Number of allocated and deallocated objects in each generation of the garbage collection.

After run of an application, a report with these statistics is written to csv file.

### Leak injection
The second function of MemDefender is flexible injection of memory leaks. 
The major idea is to selectively prohibit object deallocation by inspecting each object to be deallocated. 
MemDefender can be configured to support multiple scenarios, e.g. leaking objects of each or only selected allocation sites, 
and leaking all or only a fraction of objects. These choices allow for application specific and realistic simulation of leak-related defects. 


## How to use MemDefender

### Basic usage
You can run your Java code as usual but you need to tell the JVM to use MemDefender as a 
[Java programming language agent](https://docs.oracle.com/javase/7/docs/api/java/lang/instrument/package-summary.html).
This is done as follows:
* Copy the file `MemDefender.jar` (directory `dist` of this project) to the directory where you start your Java application
* Start you application via:

`java` **`-javaagent:MemDefender.jar=<your-app-src-paths>,<your-app-name>`**  `<your normal run parameters>`

Here '<your-app-src-paths>' is a list of colon-separated paths to the code root directories of your application, and '<your-app-name>' is 
prefix of the file for reporting results.

Or, if you want to use non-standard settings for MemDefender or leak injection, via 

`java` **`-javaagent:MemDefender.jar=<path-to-config-file>`**  `<your normal run parameters>`

Here '<path-to-config-file>' is a path to a configuration file `config.properties` explained below.

### Configuring MemDefender
The tool can be configured via supplying a `config.properties` file. This file can contain the 
following entries:

```
general.sourcePaths=<your-app-src-paths>
general.appName=<your-app-name>
injector.on=true
injector.leakRatio=100
injector.selection=true
injector.sites=<position-of-allocation-site-1>[,<position-of-allocation-site-2>]
```

The parameters `general.sourcePaths` and `general.appName` specify respectively <your-app-src-paths> and <your-app-name> explained above.
The other parameters have the following meaning:
* `injector.on`: if true, leak injection is active
* `injector.leakRatio`: specifies the percent probability that an objected is not deallocated (i.e. leaked). 
E.g., if this value is 50, a leak is created with probability 0.5.
* ``injector.selection``: if true, only objects created by specific allocation sites create leaks. If false, any object
can be used as a leak. 
* ``injector.sites``: a comma-separated list of locations of allocation sites (used if ``injector.selection`` is true).
Each location has format <fully-qualified-java-class>:<line-number>, e.g. org.xerial.snappy.buffer.CachedBufferAllocator:48. 

### Building
If a source code is changed, you need to rebuild the file `dist/MemDefender.jar`. 
To this end we provide an Ant build file `build.xml`.  To build the tool, we can use the following command 
in the parent directory of the application:

`ant all`           
 

### Dependencies
This tool uses [Java Allocation Instrumenter](https://github.com/google/allocation-instrumenter), 
and [flogger](https://github.com/google/flogger), a fluent logging API for Java (both from Google).
These jars are included in the lib folder and will be included in the `dist/MemDefender.jar` if building 
via Ant.
