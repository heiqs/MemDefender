package org.uniHD.memory;

import static org.uniHD.memory.util.Constants.SERVER_PORT;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.List;

import org.uniHD.memory.allocation.LiveObjectMonitoringSampler;
import org.uniHD.memory.util.LOMServer;

import com.google.monitoring.runtime.instrumentation.AllocationInstrumenter;
import com.google.monitoring.runtime.instrumentation.AllocationRecorder;

import com.google.common.flogger.FluentLogger;

public class MemDefenderAgent {

	private static final FluentLogger logger = FluentLogger.forEnclosingClass();

	public static void premain(String agentArgs, Instrumentation inst) throws IOException {

		logger.atInfo().log("[LOM Agent] Entered premain, arguments: %s", agentArgs);

	    final List<String> args = Arrays.asList(agentArgs == null ? new String[0] : agentArgs.split(","));
		
	    // check # of arguments
/* 		if (args.size() < 2 || args.size() > 3) {
 			
 			System.err.println("Wrong number of Arguments!");
 			printUsage();
 			System.exit(1);
 		}*/
	 		
 		// parse arguments
 		final String[] sourcePaths = args.get(0).split(":");
 		for (final String sourcePath : sourcePaths) {
 			if (!new File(sourcePath).exists()) {
 				
 				System.err.println("Source path '" + sourcePath + "' does not exist!");
 				System.exit(1);
 			}
 		}
	 		
 		// get the optional application name
		String appName = "memDefender-report";
		if (args.size() > 1 ) {
			appName = args.get(1);
		}
 		
 		// parse the java-allocation-instrumenter args
 		String JAIArgs = "";
 		if (args.size() > 2 && args.get(2).equals("manualOnly")) {
 			JAIArgs = "manualOnly";
 		}
	    
 		// delegate to the JAI
		AllocationInstrumenter.premain(JAIArgs, inst);
		
		instrument(sourcePaths);
		
		startServer(appName);
	}
	
	/**
	 * Setup for the instrumentation of the application. Also a server is started to handle live object dump requests.
	 * 
	 * @param sourcePaths
	 * @return a {@link LOMServer} handle.
	 */
	private final static void instrument(final String[] sourcePaths)  {
		
		// initialize the object allocation sampler
		AllocationRecorder.addSampler(new LiveObjectMonitoringSampler(sourcePaths));
		System.out.println("Allocation sampler installed!");
	}
	
	private final static void startServer(final String appName) throws IOException {
						
		// prepare the dump-request server
		final LOMServer server = new LOMServer(SERVER_PORT, appName);
		server.start();
		System.out.println("Live Object Dump service started!");
	}
	
	/**
	 * Prints the usage information for the live object monitoring service.
	 */
	private final static void printUsage() {
		
		System.out.println("java -javaagent:MemDefender.jar='<application_source_code_paths>,application_name[,manualOnly]' <your_application>\n" +
										"\t<source_code_paths>\t- a colon separated list of source code root directory paths for the application\n" + 
										"\t<your_application>\t- all details needed to run your application.");
	}
}
