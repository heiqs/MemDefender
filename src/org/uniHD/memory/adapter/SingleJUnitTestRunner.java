package org.uniHD.memory.adapter;

import org.junit.runner.JUnitCore;

import org.uniHD.memory.adapter.LOMRunListener;
import org.uniHD.memory.util.LiveObjectDumpGenerator;

public class SingleJUnitTestRunner {

	public static void main(String... args) throws Exception, NoClassDefFoundError {
	    String classAndMethod = args[0];
    	JUnitCore core= new JUnitCore();
    	core.addListener(new LOMRunListener());
		LiveObjectDumpGenerator.dumpToFile(classAndMethod, "beforeRun");    		
		core.run(Class.forName(classAndMethod));
		LiveObjectDumpGenerator.dumpToFile(classAndMethod, "afterRun");
	}
}
