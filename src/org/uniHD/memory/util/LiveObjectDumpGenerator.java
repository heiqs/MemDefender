package org.uniHD.memory.util;

import org.uniHD.memory.LiveObjectMap;
import org.uniHD.memory.LiveObjectMap.AllocationSiteDetails;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map.Entry;

import static org.uniHD.memory.util.Constants.COLUMN_SEPARATOR;
import static org.uniHD.memory.util.Constants.FILE_EXTENSION;

/**
 * Writes all currently registered live objects to a file.
 * 
 * @author Felix Langner
 * @since 01/22/2013
 */

public final class LiveObjectDumpGenerator {
	
	private final static String HEADER 	= "Source" + COLUMN_SEPARATOR + 
									      "Class" + COLUMN_SEPARATOR + 
									      "#AliveObjects" + COLUMN_SEPARATOR +
									      "#AllocatedObjects" + COLUMN_SEPARATOR +
									      "#DeAllocatedObjects" + COLUMN_SEPARATOR +
									      "Allocated Memory (bytes)" + COLUMN_SEPARATOR +
											"Generational Information";
	
	private LiveObjectDumpGenerator() { /* supports static referencing only */ }
	
	/**
	 * Method to concurrently dump live object information to the file given by its file name. This method will overwrite existing
	 * data.
	 * 
	 * @param fileName
	 * @param expDetails
	 * @throws IOException
	 */
	public final static void dumpToFile(final String fileName, final String expDetails) throws IOException {
		
		final File f = new File(fileName + FILE_EXTENSION);
		final BufferedWriter writer = new BufferedWriter(new FileWriter(f, true));
		try {
			
			if (f.length() == 0) {
				
				writer.write(HEADER);
				writer.newLine();
			}
			
			for (Entry<String, AllocationSiteDetails> entry : LiveObjectMap.INSTANCE) {
				
				// TODO filter 0,0 values to decrease the size of generated CSV files
				writer.write(String.format("%s%c%s%n",
						entry.getKey(),
						COLUMN_SEPARATOR,
						entry.getValue().toString(), 
						COLUMN_SEPARATOR,
						expDetails));
			}
		} finally {
			
			writer.close();
		}
	}
}