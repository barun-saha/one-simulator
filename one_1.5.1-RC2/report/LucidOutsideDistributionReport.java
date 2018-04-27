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
import routing.LucidRouter;

/**
 *
 * @author barun
 */
public class LucidOutsideDistributionReport extends Report implements MessageListener {

    private double localityRange;

    public LucidOutsideDistributionReport() {
        init();

        Settings s = new Settings(MessageReceivedLocationReport.MRL_REPORT_NS);
        localityRange = s.getDouble(MessageReceivedLocationReport.RANGE_S);
    }

    @Override
    public void init() {
        super.init();
    }

    @Override
    public void newMessage(Message m) {
    }

    @Override
    public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
    }

    @Override
    public void messageDeleted(Message m, DTNHost where, boolean dropped) {
    }

    @Override
    public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
    }

    @Override
    public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean firstDelivery) {
        Coord orig = (Coord) m.getProperty(LucidRouter.ORIG_LOCATION_PROPERTY);
        Coord cur = to.getLocation();
        double distance = orig.distance(cur);

        if (distance > localityRange) {
            write("" + distance);
        }
    }

    @Override
    public void done() {
        super.done();
    }
}
