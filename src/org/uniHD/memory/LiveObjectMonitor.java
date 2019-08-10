package org.uniHD.memory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

import org.uniHD.memory.allocation.LiveObjectMonitoringSampler;
import org.uniHD.memory.util.Configuration;
import org.uniHD.memory.util.LOMServer;

import com.google.monitoring.runtime.instrumentation.AllocationRecorder;

import static org.uniHD.memory.util.Constants.*;

@Deprecated
public class LiveObjectMonitor {

	/**
	 * @param args - @see LiveObjectMonitor.printUsage()
	 * 
	 * @throws Throwable 
	 */
	public static void main(String[] args) throws Throwable {
		
		// check # of arguments
		if (args.length < 3) {
			
			System.err.println("Wrong number of Arguments!");
			printUsage();
			System.exit(1);
		}
		
		// parse arguments
		final String[] sourcePaths = args[0].split(":");
		for (final String sourcePath : sourcePaths) {
			if (!new File(sourcePath).exists()) {
				
				System.err.println("Source path '" + sourcePath + "' does not exist!");
				System.exit(1);
			}
		}
		
		final String[] appJarPaths = args[1].split(":");
		final URL[] appJars = new URL[appJarPaths.length];
		for (int i=0; i < appJarPaths.length; i++) {
			
			final File appJar = new File(appJarPaths[i]);
			if (!appJar.exists() || !appJar.isFile() || !appJar.getName().endsWith(".jar")) {
				
				System.err.println("Application path '" + appJarPaths[i] + "' does not point to a jar archive!");
				System.exit(1);
			}
			appJars[i] = appJar.toURI().toURL();
		}
		final String appMain = args[2];
		final String[] appArgs = new String[args.length - 3];
		System.arraycopy(args, 3, appArgs, 0, appArgs.length);
		
		// instrument the application
		final LOMServer server = instrument(sourcePaths, appMain);
		
		// run the application in its own thread
		final Thread applicationThread = runApplication(appJars, appMain, appArgs);
		
		System.out.println("The Live Object Monitor is now operational.");
				
		// wait for the SUT to finish execution
		applicationThread.join();
		
		System.out.println("The application finished execution.");
				
		// stop the server
		server.shutdown();
	}
	
	/**
	 * Setup for the instrumentation of the application. Also a server is started to handle live object dump requests.
	 * 
	 * @param sourcePaths
	 * @param appMain
	 * @return a {@link LOMServer} handle.
	 * @throws IOException
	 */
	private final static LOMServer instrument(final String[] sourcePaths, final String appMain) throws IOException {

		Configuration dummyConfiguration = new Configuration();
		// initialize the object allocation sampler
		AllocationRecorder.addSampler(new LiveObjectMonitoringSampler(sourcePaths, dummyConfiguration));
		System.out.println("Allocation sampler installed!");
		
		// prepare the dump-request server
		final LOMServer server = new LOMServer(SERVER_PORT, appMain);
		server.start();
		System.out.println("Live Object Dump service started!");
		
		return server;
	}

	/**
	 * Method to setup the SUT. The application is going to be encapsulated within its own thread.
	 * 
	 * @param appJars
	 * @param appMain
	 * @param appArgs
	 * @return a {@link Thread} handle.
	 * @throws ClassNotFoundException
	 */
	private final static Thread runApplication(final URL[] appJars, final String appMain, final String[] appArgs) 
			throws ClassNotFoundException {
		
		// asynchronously start the application under study
		final ClassLoader appCL = new URLClassLoader(appJars, null);
		final Thread applicationThread = new Thread(new Runnable() {
			
			/*
			 * (non-Javadoc)
			 * @see java.lang.Runnable#run()
			 */
			@Override
			public void run() {
				
				try { 
										
					final Class<?> appMainClass = appCL.loadClass(appMain);
					appMainClass.getMethod("main", String[].class).invoke(appMainClass, new Object[] { appArgs });	
					
					// TODO this will never show any results, since the thread and its objects are cleaned up by System.gc()
					//			   only daemon threads might get affected...
					// print all remaining zombie object allocations
//					System.gc();
//					dumpToFile(appMain+"_zombies");
					
				} catch (Throwable e) {
					
					System.err.println("Executing the given application failed!");
					throw new RuntimeException(e);
				}
			}
		});
		applicationThread.setContextClassLoader(appCL);
		applicationThread.start();
		
		return applicationThread;
	}
	
	/**
	 * Prints the usage information for the live object monitoring service.
	 */
	private final static void printUsage() {
		
		System.out.println("java -javaagent:lib/allocation.jar -jar LiveObjectMonitor.jar " +
										"<application_source_code_paths> <application_path> <application_main> [<params>]\n" +
										"\t<source_code_paths>\t- a colon separated list of source code root directory paths for the application\n" + 
										"\t<application_paths>\t- a colon separated list to the applications jar paths (containing the main class)\n" + 
										"\t<application_main>\t- qualified name to the main class using java package notion\n" + 
										"\t[<params>]\t\t- parameters passed to the application\n");
	}
}
