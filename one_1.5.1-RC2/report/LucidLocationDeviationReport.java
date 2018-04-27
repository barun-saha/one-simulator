/* 
 * Copyright 2014, 2015, 2016 Barun Saha, http://barunsaha.me
 * Released under GPLv3. See LICENSE.txt for details. 
 */

package report;

import core.Coord;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import java.util.LinkedList;
import routing.LucidRouter;

/**
 * Compute the average deviation of a message between its actual origin and
 * it origin perceived by a receiving node.
 * 
 * @author barun
 */
public class LucidLocationDeviationReport extends Report implements MessageListener {

    private LinkedList<Double> deviationsReceiver;
    private LinkedList<Double> deviationsSender;
    
    public LucidLocationDeviationReport() {
        init();
    }

    @Override
    public void init() {
        super.init();
        deviationsReceiver = new LinkedList<Double>();
        deviationsSender = new LinkedList<Double>();
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
        Coord origLoc = (Coord) m.getProperty(LucidRouter.ORIG_LOCATION_PROPERTY);
        Coord curLoc = to.getLocation();
	Coord senderLoc = from.getLocation();
        deviationsReceiver.add(origLoc.distance(curLoc));
	deviationsSender.add(origLoc.distance(senderLoc));
    }

    @Override
    public void done() {
        double average = 0;
        for (double value: deviationsReceiver) {
            average += value;
        }
        average /= deviationsReceiver.size();
        
        write("pld_receiver: " + average);

        average = 0;
        for (double value: deviationsSender) {
            average += value;
        }
        average /= deviationsSender.size();
        
        write("pld_sender: " + average);

        super.done();
    }

}
