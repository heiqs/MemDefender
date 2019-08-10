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

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.LoggerConfig;


public class MemDefenderAgent {
	private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    // Setting logging properties for all files
	static {
		// Set logging format, see https://docs.oracle.com/javase/7/docs/api/java/util/logging/SimpleFormatter.html
		// System.setProperty("java.util.logging.SimpleFormatter.format", "[%4$-5s | %1$tF %1$tT %1$tL | %2$-10s]  %5$s %n");
		System.setProperty("java.util.logging.SimpleFormatter.format", "[%4$1.4s|%1$ty%1$tm%1$td %1$tT %1$tL] %5$s [%2$s] %n");

		// Set default logging format, applicable to all loggers (als flogger)
		setLevelForAllLoggers(Level.FINE);
		//LoggerConfig.of(logger).setLevel(Level.FINE);
		//logger.atInfo().log("[MD Agent] Current logging level is: %s. If you don't see expected  messages, read " +
		//		"http://bit.ly/2M9VhMH", LoggerConfig.of(logger).getLevel());
	}

	public static void premain(String agentArgs, Instrumentation inst) throws IOException {
		logger.atFine().log("[MD Agent] Entered premain, arguments: %s", agentArgs);

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

		logger.atFine().log("[MD Agent] Starting code instrumentation");
		instrument(sourcePaths);
		logger.atFine().log("[MD Agent] Instrumentation finished, starting Live Object Dump server");
		startServer(appName);
		logger.atFine().log("[MD Agent] Live Object Dump service started" );

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
	}
	
	private final static void startServer(final String appName) throws IOException {
		// prepare the dump-request server
		final LOMServer server = new LOMServer(SERVER_PORT, appName);
		server.start();
	}
	
	/**
	 * Prints the usage information for the live object monitoring service.
	 */
	private final static void printUsage() {
		
		System.out.println("java -javaagent:MemDefender.jar='<application_source_code_paths>,application_name[,manualOnly]' <your_application>\n" +
										"\t<source_code_paths>\t- a colon separated list of source code root directory paths for the application\n" + 
										"\t<your_application>\t- all details needed to run your application.");
	}


	public static void setLevelForAllLoggers(Level targetLevel) {
		Logger root = Logger.getLogger("");
		root.setLevel(targetLevel);
		for (Handler handler : root.getHandlers()) {
			handler.setLevel(targetLevel);
		}
		// System.out.println("Global logging level set to: " + targetLevel.getName());
	}


}
