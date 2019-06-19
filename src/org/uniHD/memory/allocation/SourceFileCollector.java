package org.uniHD.memory.allocation;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class SourceFileCollector {

	private SourceFileCollector() { /* supports static referencing only */ }
	
	/**
	 * @param basePaths - roots of the source files.
	 * @return a hash set of fully qualified source file paths with class style directory separators available for the 
	 * 					investigation.
	 */
    static final Set<String> collectSourceFile(String[] basePaths) {

        final Set<String> result = new HashSet<String>();
        
        final List<Entry> fileList = new ArrayList<Entry>();
        
        for (String basePath : basePaths) {
        	
	        // iterator for all files and directories at the given basePath
	        for (Entry e = new Entry(new File(basePath), "") ; 
	        	   (e != null) ; 
	        	   e = ((fileList.isEmpty()) ? null : fileList.remove(fileList.size() - 1))) {
	        	
	        	// iterator for all files and directories within a selected sub-directory of the basePath
	        	for (File f : e.file.listFiles()) {
	                
	        		// put it to the list of iterable elements of the first iterator
	                if (f.isDirectory()) {
	                	
	                    fileList.add(new Entry(f, e.sourcePath + f.getName() + "." ));
	                    
	                // add files to the result set (skip non-java sources)
	                } else if (f.isFile() && f.getName().endsWith(".java")) {
	                	
	                    result.add(e.sourcePath + f.getName().substring(0, f.getName().indexOf(".")));
	                } 
	        	}
	        }
	        
	        // just to make sure there is nothing left on the file list
	        assert (fileList.isEmpty());
        }
        
        return result;
    }
    
    private static final class Entry {
    	
    	private final File file;
    	private final String sourcePath;
    	
    	private Entry(File file, String sourcePath) {
			
    		this.file = file;
    		this.sourcePath = sourcePath;
		}
    }
}
