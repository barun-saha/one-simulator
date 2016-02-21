/*
 * Copyright 2015, 2016 Barun Saha (http://barunsaha.me)
 *
 * Distributed under GNU GPLv3 license.
 */
package routing;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import static routing.SprayAndWaitRouter.MSG_COUNT_PROPERTY;
import util.Tuple;

/**
 * An example routing class that combines the traditional Spray-and-Wait routing
 * scheme with a simple utility. This template should be the starting point for
 * implementation of a broad class of routing mechanisms that replicate messages
 * by considering the following two types of information: 1. One or properties
 * of the messages 2. Some additional node-specific utility values
 *
 * This example is based on Chapter 3, #TheOMNBook. Refer to this class for
 * solving Exercise 2.12, Chapter 2.
 *
 * @author barun
 */
public class SprayAndWaitUtilityRouter extends SprayAndWaitRouter {

    protected HashMap<Integer, Integer> utilities;

    public SprayAndWaitUtilityRouter(Settings s) {
        // Inherit the settings of SnW
        super(s);
        utilities = new HashMap<Integer, Integer>();
    }

    public SprayAndWaitUtilityRouter(SprayAndWaitUtilityRouter r) {
        super(r);
        utilities = new HashMap<Integer, Integer>();
    }

    @Override
    public SprayAndWaitUtilityRouter replicate() {
        // Always override this method with return type of this class
        return new SprayAndWaitUtilityRouter(this);
    }

    @Override
    public void changedConnection(Connection con) {
        super.changedConnection(con);

        if (con.isUp()) {
            DTNHost me = getHost();
            DTNHost otherHost = con.getOtherNode(me);
            int otherAddress = otherHost.getAddress();

            int utility = 1;

            // Increase the number of contacts with the other node
            if (utilities.containsKey(otherAddress)) {
                // Already had some contact(s) with this node
                // Get the current utility value for the node
                utility += utilities.get(otherAddress);
            }

            // Update the hash table with the new utility value
            // for the concerned node
            utilities.put(otherAddress, utility);
        }
    }

    protected double getUtility(int address) {
        double utility = 0;

        if (utilities.containsKey(address)) {
            utility = utilities.get(address);
        }

        return (1.0 + utility) * (1.0 + utilities.size());
    }

    protected int getCopiesLeft(Message m) {
        return (Integer) m.getProperty(MSG_COUNT_PROPERTY);
    }

    @Override
    public void update() {
        super.update();

        tryOtherMessages();
    }

    @Override
    protected List<Message> getMessagesWithCopiesLeft() {
        // This is required to override the functionality of the superclass
        // where a message is replicated just if nrofCopies > 1
        // (Exercise 3.7, Chapter 3, #TheOMNBook) Returning null here would
        // result in runtime exception since the SprayAndWait class uses the
        // list returned. So, just return an empty list.
        return new ArrayList<Message>();
    }

    private Tuple<Message, Connection> tryOtherMessages() {
        List<Tuple<Message, Connection>> messages =
                new ArrayList<Tuple<Message, Connection>>();

        Collection<Message> msgCollection = getMessageCollection();

        /* for all connected hosts collect all messages that have a higher
         probability of delivery by the other host */
        for (Connection con : getConnections()) {
            DTNHost other = con.getOtherNode(getHost());
            SprayAndWaitUtilityRouter othRouter = (SprayAndWaitUtilityRouter) other.getRouter();

            if (othRouter.isTransferring()) {
                continue; // skip hosts that are transferring
            }

            for (Message m : msgCollection) {
                if (othRouter.hasMessage(m.getId())) {
                    continue; // skip messages that the other one has
                }

                int destination = m.getTo().getAddress();

                if (getCopiesLeft(m) > 1
                        && othRouter.getUtility(destination)
                        > getUtility(destination)) {
                    // the other node has higher probability of delivery
                    messages.add(new Tuple<Message, Connection>(m, con));
                }
            }
        }

        if (messages.isEmpty()) {
            return null;
        }

        // sort the message-connection tuples
        //Collections.sort(messages, new ProphetRouter.TupleComparator());
        return tryMessagesForConnected(messages);	// try to send messages
    }
}