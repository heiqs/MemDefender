package org.uniHD.memory.util;

import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Simple client for triggering a dump of live objects into a passed local file.
 * 
 * @author Felix Langner
 * @since 01/22/2013
 */

public class LOMClient {

	/**
	 * @param args - [0] file name to dump to
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		
		// check argument validity
		if (args.length > 2 || args.length < 2) {
			
			System.err.println("Wrong number of Arguments!");
			System.out.println("usage: java -jar LOMClient.jar <fileName> <experimentInfo>");
			System.exit(1);
		}
		
		System.exit(createSnapshots(args[0], args[1]));
	}
	
	public static int createSnapshots(final String fileName, final String expInfo) throws Exception {
		
		int portModifier = 0;
		int exitCode = 0;
		
		while (portModifier >= 0) {
			final Socket serverSocket = new Socket();
			try {
				serverSocket.connect(new InetSocketAddress(InetAddress.getByName(null), 
														   Constants.SERVER_PORT + portModifier));
				portModifier++;
			} catch (Exception e) {
				
				// we could not even contact one server
				if (portModifier == 0) {
					throw e;
			
				// all available servers have been contacted
				} else {
					break;
				}
			}
			final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(serverSocket.getOutputStream()));
			final InputStreamReader reader = new InputStreamReader(serverSocket.getInputStream());
			try {
				
				// send the fileName to the server
				writer.write(fileName + Constants.MESSAGE_SEPARATOR + expInfo);
				writer.newLine();
				writer.flush();
				
				// receive the exit code
				exitCode += reader.read();
	
			} finally {
				if (reader != null) reader.close();
				if (writer != null) writer.close();
				serverSocket.close();
			}
		}
		return exitCode;
	}
}