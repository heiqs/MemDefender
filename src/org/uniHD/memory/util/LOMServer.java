package org.uniHD.memory.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import static org.uniHD.memory.util.LiveObjectDumpGenerator.dumpToFile;
import static org.uniHD.memory.util.Constants.FORCE_GC_BEFORE_DUMP;
import static org.uniHD.memory.util.Constants.GC_WAITING_TIME;

/**
 * Server to trigger dumps of JVM live in memory objects.
 * 
 * @author Felix Langner
 * @since 01/22/2013
 */

public class LOMServer extends Thread {

	private final ServerSocket	socket;
	private final ThreadGroup 	applicationCtrl;
	private final String 		appName;
	
	/**
	 * 
	 * @param port
	 * @param appName
	 * @throws IOException
	 */
	@SuppressWarnings("deprecation")
	public LOMServer(int port, final String appName) throws IOException {
		// we need to ensure that the ThreadGroups of the LOMServer and application are orthogonal in respect to the 
		// ThreadGroup hierarchy 
		super(new ThreadGroup(Thread.currentThread().getThreadGroup().getParent(), "LOMEssentials"), "LOMServer");
		setDaemon(true);
		getThreadGroup().allowThreadSuspension(false);
		
		// find a socket to bind to
		ServerSocket socket = null;
		while (socket == null) {
			try {
				socket = new ServerSocket(port, 0, InetAddress.getByName(null));
			} catch (BindException be) {
				port++;
			}
		}
		
		this.socket = socket;
		this.applicationCtrl = Thread.currentThread().getThreadGroup();
		this.appName = appName;
	}
	
	/*
	 * (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	@SuppressWarnings("deprecation")
	@Override
	public void run() {
		super.run();
		
		try {
			for (;;) {
				
				final Socket clientSocket = this.socket.accept();
				final BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				final OutputStreamWriter writer = new OutputStreamWriter(clientSocket.getOutputStream());
				try {
					// pause the application, run the GC (to get more clean results)
					// evil stuff is going on here, but we should be fine since the LOM and the application under study 
					// do not interfere
					if (FORCE_GC_BEFORE_DUMP) {
						
						applicationCtrl.suspend();
					}
					
					System.gc();
					sleep(GC_WAITING_TIME);
					
					// save the dumps to disk
					String[] payload = reader.readLine().split("\\" + Constants.MESSAGE_SEPARATOR);
					assert(payload.length == 2);
					dumpToFile(appName + "." + payload[0], payload[1]);

					// send status code
					writer.write(0);
					writer.flush();

				} finally {
					if (FORCE_GC_BEFORE_DUMP) applicationCtrl.resume();
					if (reader != null) reader.close();
					if (writer != null) writer.close();
					clientSocket.close();
				}
			}
		} catch (Exception e) {
			// server is going down
		}
		
		try {
			
			this.socket.close();
		} catch (IOException e) {
			// ignored
		}
		
		System.out.println("Live object dump service stopped.");
	}
	
	/**
	 * Method to stop the server.
	 * 
	 * @throws IOException
	 */
	public void shutdown() throws IOException {
		
		this.socket.close();
	}
}