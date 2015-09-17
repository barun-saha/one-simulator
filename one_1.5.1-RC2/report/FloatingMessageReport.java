/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package report;

import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.World;
import routing.FloatingContentRouter;

/**
 * Floating Message Report
 * Reports one line per message when the message is CREATEd, replication STARTed, REPLICATEd, replication ABORTed, and DELETEd.
 *
 * BEWARE that these reports can easily grow to files multiple gigabytes in size.
 *
 * @author jo
 */
public class FloatingMessageReport extends Report implements MessageListener {
	public static final String HEADER =
	    "# messages: event-type org-node (loc-x,loc-y) (anchor-x,anchor-y) r=<core> a=<availability> ttl size [node-snd ( loc-x loc-y ) [node-rcv ( loc-x loc-y)]]";
	/** all message delays */
	
	/**
	 * Constructor.
	 */
	public FloatingMessageReport() {
		init();
	}
	
	@Override
	public void init() {
		super.init();
		write(HEADER);
	}
	
	public void newMessage(Message m) {
	    Object   o = m.getProperty ("dependency");
	    String   dep = "";
	    
	    if (o != null)
		dep = " (" + o + ")";

	    write(format (getSimTime ()) + " CREATE " + m.getFrom().toString() + " " + m.getId() + " " + format(m.getCreationTime()) + " " + m.getProperty (FloatingContentRouter.FC_SRCLOC) + " "
		  + m.getProperty(FloatingContentRouter.FC_ANCHOR) + " r=" + m.getProperty(FloatingContentRouter.FC_R) + " a=" + m.getProperty(FloatingContentRouter.FC_A) + " " + m.getProperty(FloatingContentRouter.FC_TTL) + " " + m.getSize() + " " + m.getProperty (FloatingContentRouter.FC_TTL_VAL) + dep);
	}
	
	public void messageTransferred(Message m, DTNHost from, DTNHost to,
			boolean firstDelivery) {
	    Object   o = m.getProperty ("dependency");
	    String   dep = "";
	    
	    if (o != null)
		dep = " (" + o + ")";

	    write(format (getSimTime ()) + " REPLICATE " + m.getFrom().toString() + " " + m.getId() + " " + format(m.getCreationTime()) + " " + m.getProperty (FloatingContentRouter.FC_SRCLOC) + " "
		  + m.getProperty(FloatingContentRouter.FC_ANCHOR) + " r=" + m.getProperty(FloatingContentRouter.FC_R) + " a=" + m.getProperty(FloatingContentRouter.FC_A) + " " + m.getProperty(FloatingContentRouter.FC_TTL) + " " + m.getSize() + " "
		  + from.toString() + " " + from.getLocation() + " " + to.toString() + " " + to.getLocation() + " " + m.getProperty (FloatingContentRouter.FC_TTL_VAL) + dep + " [" + m.getTtl() + "]");
	}

	public void messageDeleted(Message m, DTNHost where, boolean dropped) {
	    Object   o = m.getProperty ("dependency");
	    String   dep = "";
	    
	    if (o != null)
		dep = " (" + o + ")";

	    write(format(getSimTime()) + " DELETE " + m.getFrom().toString() + " " + m.getId() + " " + format(m.getCreationTime()) + " " + m.getProperty (FloatingContentRouter.FC_SRCLOC) + " "
		  + m.getProperty(FloatingContentRouter.FC_ANCHOR) + " r=" + m.getProperty(FloatingContentRouter.FC_R) + " a=" + m.getProperty(FloatingContentRouter.FC_A) + " " + m.getProperty(FloatingContentRouter.FC_TTL) + " " + m.getSize() + " "
		  + where.toString() + " " + where.getLocation() + " " + m.getProperty (FloatingContentRouter.FC_TTL_VAL) + dep + " [" + m.getTtl() + "]");
	}

	@Override
	public void done() {
	    super.done();
	}
	
	public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
	    Object   o = m.getProperty ("dependency");
	    String   dep = "";
	    
	    if (o != null)
		dep = " (" + o + ")";

	    write(format (getSimTime ()) + " ABORT " + m.getFrom().toString() + " " + m.getId() + " " + format(m.getCreationTime()) + " " + m.getProperty (FloatingContentRouter.FC_SRCLOC) + " "
		  + m.getProperty(FloatingContentRouter.FC_ANCHOR) + " r=" + m.getProperty(FloatingContentRouter.FC_R) + " a=" + m.getProperty(FloatingContentRouter.FC_A) + " " + m.getProperty(FloatingContentRouter.FC_TTL) + " " + m.getSize() + " "
		  + from.toString() + " " + from.getLocation() + " " + to.toString() + " " + to.getLocation() + " " + m.getProperty (FloatingContentRouter.FC_TTL_VAL) + dep + " [" + m.getTtl() + "]");
	}

	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
	    Object   o = m.getProperty ("dependency");
	    String   dep = "";
	    
	    if (o != null)
		dep = " (" + o + ")";

	    write(format (getSimTime ()) + " START " + m.getFrom().toString() + " " + m.getId() + " " + format(m.getCreationTime()) + " " + m.getProperty (FloatingContentRouter.FC_SRCLOC) + " "
		  + m.getProperty(FloatingContentRouter.FC_ANCHOR) + " r=" + m.getProperty(FloatingContentRouter.FC_R) + " a=" + m.getProperty(FloatingContentRouter.FC_A) + " " + m.getProperty(FloatingContentRouter.FC_TTL) + " " + m.getSize() + " "
		  + from.toString() + " " + from.getLocation() + " " + to.toString() + " " + to.getLocation() + " " + m.getProperty (FloatingContentRouter.FC_TTL_VAL) + dep + " [" + m.getTtl() + "]");
	}
}
