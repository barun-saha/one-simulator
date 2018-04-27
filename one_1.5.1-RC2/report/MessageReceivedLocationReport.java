/*
 * Copyright 2014, 2015, 2016 Barun Saha, http://barunsaha.me
 * Released under GPLv3. See LICENSE.txt for details.
 */
package report;

import core.Coord;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.Settings;
import routing.FloatingContentRouter;
import routing.LucidRouter;

/**
 *
 * @author barun
 */
public class MessageReceivedLocationReport extends Report implements
        MessageListener {

    public static final String MRL_REPORT_NS = "MessageReceivedLocationReport";
    public static final String RANGE_S = "localityRange";
    /** If set to true, individual message reception records would be written
     * to the report file. Size of the file would be ~ 60 MB. */
    private static boolean shouldPrintRecords = false;
    private double range;
    /**
     * # of messages received within a given locality
     */
    private int nInside;
    private int nOutside;
    private int nCreated;
    /** Values from Floating Content */
    private double a = -1;
    private double r = -1;

    public MessageReceivedLocationReport() {
        init();
    }

    @Override
    public void init() {
        super.init();

        Settings s = new Settings(MRL_REPORT_NS);
        range = s.getDouble(RANGE_S);
    }

    @Override
    public void newMessage(Message m) {
        ++nCreated;
    }

    @Override
    public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
        if (a == -1 && m.getProperty(FloatingContentRouter.FC_A) != null) {
            a = (Double) m.getProperty(FloatingContentRouter.FC_A);
        }

        if (r == -1 && m.getProperty(FloatingContentRouter.FC_R) != null) {
            r = (Double) m.getProperty(FloatingContentRouter.FC_R);
        }
    }

    @Override
    public void messageDeleted(Message m, DTNHost where, boolean dropped) {
    }

    @Override
    public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
    }

    @Override
    public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean
            firstDelivery) {
        String key = m.getId() + to.toString();

        Coord origin = null;
        Coord currentLoc = to.getLocation().clone();

        origin = (Coord) m.getProperty(LucidRouter.ORIG_LOCATION_PROPERTY);

        double distance = -1;
        String status = "";
        if (origin != null) {
            distance = origin.distance(currentLoc);

            if (distance <= range) {
                status = "OK";
                nInside += 1;
            } else {
                nOutside += 1;
                //write(m.getHopCount() + " " + distance);
            }
        }

        if (shouldPrintRecords) {
            String record = getSimTime() + " "
                    + m.getId() + " ";
            record += origin + " "
                    + currentLoc + " "
                    + m.getInitialTtl() + " "
                    + distance + " " + status;
            write(record);
        }

    }

    @Override
    public void done() {
        write("report_locality: " + range);
        write("FC_A: " + a);
        write("FC_R: " + r);
        write("created: " + nCreated);
        write("rcvd_inside: " + nInside);
        write("rcvd_outside: " + nOutside);
        super.done();
    }
}
