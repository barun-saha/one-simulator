/* 
 * Copyright 2014, 2015, 2016 Barun Saha, http://barunsaha.me
 * Released under GPLv3. See LICENSE.txt for details. 
 */

package report;

import core.DTNHost;
import core.Message;
import core.MessageListener;
import java.util.LinkedList;

/**
 *
 * @author barun
 */
public class LucidDistanceReport extends Report implements MessageListener {

    private LinkedList<Double> locations;
    
    public LucidDistanceReport() {
        init();
    }

    @Override
    public void init() {
        super.init();
        locations = new LinkedList<Double>();
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
    }

}
