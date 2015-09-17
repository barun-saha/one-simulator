/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */

package report;

import applications.FloatingApplication;
import core.Application;
import core.ApplicationListener;
import core.DTNHost;

/**
 * Reporter for the <code>FloatingApplication</code>. 
 * Provides information about when a node enters/leaves a specific anchor zone
 *
 * This is only used for the "flux" mode of operation to assess how many nodes
 * enter and leave a single anchor zone.
 * 
 * @author jo
 */
public class FloatingAppReporter extends Report implements ApplicationListener {
	
    public void gotEvent(String event, Object params, Application app,
			 DTNHost host) {
	// Check that the event is sent by correct application type
	if (!(app instanceof FloatingApplication)) return;
	
	// Increment the counters based on the event type
	if (event.equals("enter")) {
	    write (format (getSimTime ()) + " ENTER " + host.toString () + " " + (String) params);
	} else if (event.equals("leave")) {
	    write (format (getSimTime ()) + " LEAVE " + host.toString () + " " + (String) params);
	} else if (event.equals("in")) {
	    write (format (getSimTime ()) + " IN " + host.toString () + " " + (String) params);
	}
    }
	
    @Override
	public void done() {
	write ("\n");
	super.done();
    }
}
