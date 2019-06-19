package org.uniHD.memory.adapter;

import org.junit.runner.Description;
import org.junit.runner.notification.RunListener;

import static org.uniHD.memory.util.LiveObjectDumpGenerator.dumpToFile;

public class LOMRunListener extends RunListener {
	
	/*
	 * (non-Javadoc)
	 * @see org.junit.runner.notification.RunListener#testStarted(org.junit.runner.Description)
	 */
	@Override
	public void testRunStarted(Description description) throws Exception {
		
		dumpToFile(description.getDisplayName() + "-" + description.getMethodName(), 
				   description.getDisplayName() + "-" + description.getMethodName() + "-started");
		super.testRunStarted(description);
	}

}
