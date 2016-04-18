/*
 * Copyright 2015, 2016 Barun Saha (http://barunsaha.me)
 *
 * Distributed under the GNU GPL v3 license used by the ONE simulator.
 */
package report;

import core.ConnectionListener;
import core.DTNHost;
import core.Settings;

/**
 * Reports the node location during a contact. Syntax:<br>
 * <code>time node_id xpos ypos</code>
 * 
 * This module is a part of implementation of the protocol described in 
 * Section 4.4, Chapter 4, #TheOMNBook.
 *
 * @author barun
 */
public class ContactLocationsReportSingleLine extends Report
        implements ConnectionListener {

    /**
     * Granularity -setting id ({@value}). Defines how many simulated seconds
     * are grouped in one reported interval.
     */
    public static final String GRANULARITY = "granularity";
    /**
     * How many seconds are grouped in one group
     */
    protected double granularity;

    /**
     * Constructor.
     */
    public ContactLocationsReportSingleLine() {
        Settings settings = getSettings();
        if (settings.contains(GRANULARITY)) {
            this.granularity = settings.getDouble(GRANULARITY);
        } else {
            this.granularity = 1.0;
        }

        init();
    }

    @Override
    protected void init() {
        super.init();
        //this.connections = new HashMap<ConnectionInfo,ConnectionInfo>();
        //this.nrofContacts = new Vector<Integer>();
    }

    @Override
    public void hostsConnected(DTNHost host1, DTNHost host2) {
        if (isWarmup()) {
            return;
        }
        //addConnection(host1, host2);
        write(getSimTime() + " " + host1 + " " + host1.getLocation().getX()
                + " " + host1.getLocation().getY() + " " + host2 + " "
                + host2.getLocation().getX() + " " + host2.getLocation().getY());
    }

    @Override
    public void hostsDisconnected(DTNHost host1, DTNHost host2) {
        //newEvent();
        //ConnectionInfo ci = removeConnection(host1, host2);
        //if (ci == null) {
        //	return; /* the connection was started during the warm up period */
        //}
        //ci.connectionEnd();
        //increaseTimeCount(ci.getConnectionTime());		
    }

    @Override
    public void done() {
        super.done();
    }
}
