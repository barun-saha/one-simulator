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
import core.SimClock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import util.Tuple;

/**
 * Implementation of PTU that can interact with a PRoPHET router. See Chapter 8,
 * #TheOMNBook, for details.
 *
 * @author barun
 */
public class ProphetPtuRouter extends ActiveRouter {

    /**
     * delivery predictability initialization constant
     */
    public static final double P_INIT = 0.75;
    /**
     * delivery predictability transitivity scaling constant default value
     */
    public static final double DEFAULT_BETA = 0.25;
    /**
     * delivery predictability aging constant
     */
    public static final double GAMMA = 0.98;
    /**
     * Prophet router's setting namespace ({@value})
     */
    public static final String PROPHET_NS = "ProphetRouter";
    /**
     * Number of seconds in time unit -setting id ({@value}). How many seconds
     * one time unit is when calculating aging of delivery predictions. Should
     * be tweaked for the scenario.
     */
    public static final String SECONDS_IN_UNIT_S = "secondsInTimeUnit";
    /**
     * Transitivity scaling constant (beta) -setting id ({@value}). Default
     * value for setting is {@link #DEFAULT_BETA}.
     */
    public static final String BETA_S = "beta";
    /**
     * the value of nrof seconds in time unit -setting
     */
    private int secondsInTimeUnit;
    /**
     * value of beta setting
     */
    private double beta;
    /**
     * delivery predictabilities
     */
    private Map<DTNHost, Double> preds;
    /**
     * last delivery predictability update (sim)time
     */
    private double lastAgeUpdate;
    /**
     * identifier for the initial number of copies setting ({@value})
     */
    public static final String NROF_COPIES = "nrofCopies";
    /**
     * identifier for the binary-mode setting ({@value})
     */
    public static final String BINARY_MODE = "binaryMode";
    /**
     * SprayAndWait router's settings name space ({@value})
     */
    public static final String SPRAYANDWAIT_NS = "SprayAndWaitRouter";
    /**
     * Message property key
     */
    public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + "."
            + "copies";
    protected int initialNrofCopies;
    protected boolean isBinary;
    /**
     * Stores nodewise routing protocol. Avoid runtime checking of the protocol
     * using instanceof operator
     */
    protected String[] protocolMap;
    protected static final String SIGNATURE_PROPHET =
            "class routing.CompatibleProphetRouter";
    protected static final String SIGNATURE_SNW =
            "class routing.CompatibleSnwRouter";
    protected static final String SIGNATURE_PROPHET_PTU =
            "class routing.ProphetPtuRouter";
    protected static final String SIGNATURE_SNW_PTU =
            "class routing.SnwPtuRouter";

    public ProphetPtuRouter(Settings s) {
        super(s);
        Settings prophetSettings = new Settings(PROPHET_NS);
        secondsInTimeUnit = prophetSettings.getInt(SECONDS_IN_UNIT_S);
        if (prophetSettings.contains(BETA_S)) {
            beta = prophetSettings.getDouble(BETA_S);
        } else {
            beta = DEFAULT_BETA;
        }

        initPreds();

        Settings snwSettings = new Settings(SPRAYANDWAIT_NS);

        initialNrofCopies = snwSettings.getInt(NROF_COPIES);
        isBinary = snwSettings.getBoolean(BINARY_MODE);

        int nrofHosts = new Settings("Scenario").getInt("nrofHosts");
        this.protocolMap = new String[nrofHosts];
    }

    public ProphetPtuRouter(ProphetPtuRouter r) {
        super(r);
        this.secondsInTimeUnit = r.secondsInTimeUnit;
        this.beta = r.beta;
        initPreds();

        this.initialNrofCopies = r.initialNrofCopies;
        this.isBinary = r.isBinary;

        int nrofHosts = new Settings("Scenario").getInt("nrofHosts");
        this.protocolMap = new String[nrofHosts];
    }

    /**
     * Initializes predictability hash
     */
    private void initPreds() {
        this.preds = new HashMap<DTNHost, Double>();
    }

    @Override
    public void changedConnection(Connection con) {
        super.changedConnection(con);

        if (con.isUp()) {
            DTNHost otherHost = con.getOtherNode(getHost());
            MessageRouter otherRouter = otherHost.getRouter();

//            if (otherRouter instanceof CompatibleSnWRouter) {
//                return;
//            }

            // Record the protocol of the other node for checking in future
            int address = otherHost.getAddress();
            if (this.protocolMap[address] == null) {
                this.protocolMap[address] = otherRouter.getClass().toString();
            }

            if (!(this.protocolMap[address].equals(SIGNATURE_SNW))) {
                updateDeliveryPredFor(otherHost);
                updateTransitivePreds(otherHost);
            }
        }
    }

    /**
     * Updates delivery predictions for a host.
     * <CODE>P(a,b) = P(a,b)_old + (1 - P(a,b)_old) * P_INIT</CODE>
     *
     * @param host The host we just met
     */
    private void updateDeliveryPredFor(DTNHost host) {
        double oldValue = getPredFor(host);
        double newValue = oldValue + (1 - oldValue) * P_INIT;
        preds.put(host, newValue);
    }

    /**
     * Returns the current prediction (P) value for a host or 0 if entry for the
     * host doesn't exist.
     *
     * @param host The host to look the P for
     * @return the current P value
     */
    public double getPredFor(DTNHost host) {
        ageDeliveryPreds(); // make sure preds are updated before getting
        if (preds.containsKey(host)) {
            return preds.get(host);
        } else {
            return 0;
        }
    }

    /**
     * Updates transitive (A->B->C) delivery predictions.
     * <CODE>P(a,c) = P(a,c)_old + (1 - P(a,c)_old) * P(a,b) * P(b,c) * BETA
     * </CODE>
     *
     * @param host The B host who we just met
     */
    private void updateTransitivePreds(DTNHost host) {
        MessageRouter otherRouter = host.getRouter();
        assert otherRouter instanceof CompatibleProphetRouter
                || otherRouter instanceof ProphetPtuRouter
                || otherRouter instanceof SnwPtuRouter :
                "PRoPHET PTU router only works "
                + " with other routers of same type: " + otherRouter;

        double pForHost = getPredFor(host); // P(a,b)
        Map<DTNHost, Double> othersPreds;

        int address = host.getAddress();
        if (this.protocolMap[address].equals(SIGNATURE_PROPHET)) {
            othersPreds = ((CompatibleProphetRouter) otherRouter).getDeliveryPreds();
        } else if (this.protocolMap[address].equals(SIGNATURE_PROPHET_PTU)) {
            othersPreds = ((ProphetPtuRouter) otherRouter).getDeliveryPreds();
        } else {
            othersPreds = ((SnwPtuRouter) otherRouter).getDeliveryPreds();
        }

        for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
            if (e.getKey() == getHost()) {
                continue; // don't add yourself
            }

            double pOld = getPredFor(e.getKey()); // P(a,c)_old
            double pNew = pOld + (1 - pOld) * pForHost * e.getValue() * beta;
            preds.put(e.getKey(), pNew);
        }
    }

    /**
     * Ages all entries in the delivery predictions.
     * <CODE>P(a,b) = P(a,b)_old * (GAMMA ^ k)</CODE>, where k is number of time
     * units that have elapsed since the last time the metric was aged.
     *
     * @see #SECONDS_IN_UNIT_S
     */
    private void ageDeliveryPreds() {
        double timeDiff = (SimClock.getTime() - this.lastAgeUpdate)
                / secondsInTimeUnit;

        if (timeDiff == 0) {
            return;
        }

        double mult = Math.pow(GAMMA, timeDiff);
        for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
            e.setValue(e.getValue() * mult);
        }

        this.lastAgeUpdate = SimClock.getTime();
    }

    /**
     * Returns a map of this router's delivery predictions
     *
     * @return a map of this router's delivery predictions
     */
    protected Map<DTNHost, Double> getDeliveryPreds() {
        ageDeliveryPreds(); // make sure the aging is done
        return this.preds;
    }

    @Override
    protected int startTransfer(Message m, Connection con) {
        MessageRouter otherRouter = con.getOtherNode(getHost()).getRouter();
        if (otherRouter instanceof CompatibleSnwRouter) {
            Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
            if (nrofCopies == null) {
                m.addProperty(MSG_COUNT_PROPERTY, initialNrofCopies);
            }
        } else {
            m.removeProperty(MSG_COUNT_PROPERTY);
        }
        return super.startTransfer(m, con);

    }

    // For SnW
    @Override
    public Message messageTransferred(String id, DTNHost from) {
        Message msg = super.messageTransferred(id, from);
        Integer nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);

        //assert nrofCopies != null : "Not a SnW message: " + msg;

        if (nrofCopies != null) {
            if (isBinary) {
                /* in binary S'n'W the receiving node gets ceil(n/2) copies */
                nrofCopies = (int) Math.ceil(nrofCopies / 2.0);
            } else {
                /* in standard S'n'W the receiving node gets only single copy */
                nrofCopies = 1;
            }

            msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
        }
        return msg;
    }

    // For SnW
    @Override
    protected void transferDone(Connection con) {
        MessageRouter otherRouter = con.getOtherNode(getHost()).getRouter();
        if (otherRouter instanceof CompatibleSnwRouter) {
            Integer nrofCopies;
            String msgId = con.getMessage().getId();
            /* get this router's copy of the message */
            Message msg = getMessage(msgId);

            if (msg == null) { // message has been dropped from the buffer after..
                return; // ..start of transfer -> no need to reduce amount of copies
            }

            /* reduce the amount of copies left */
            nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);
//            assert nrofCopies != null:
//                    "Msg received from CompatibleSnWRouter has no nrofCopies!";
            if (nrofCopies != null) {
                if (isBinary) {
                    nrofCopies /= 2;
                } else {
                    nrofCopies--;
                }
                msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
            }
        }
    }

    @Override
    public void update() {
        super.update();
        if (!canStartTransfer() || isTransferring()) {
            return; // nothing to transfer or is currently transferring 
        }

        // try messages that could be delivered to final recipient
        if (exchangeDeliverableMessages() != null) {
            return;
        }

        tryOtherMessages();
    }

    /**
     * Tries to send all other messages to all connected hosts ordered by their
     * delivery probability
     *
     * @return The return value of {@link #tryMessagesForConnected(List)}
     */
    private Tuple<Message, Connection> tryOtherMessages() {
        List<Tuple<Message, Connection>> messages =
                new ArrayList<Tuple<Message, Connection>>();

        Collection<Message> msgCollection = getMessageCollection();

        /* for all connected hosts collect all messages that have a higher
         probability of delivery by the other host */
        for (Connection con : getConnections()) {
            DTNHost other = con.getOtherNode(getHost());
            ActiveRouter othRouter = (ActiveRouter) other.getRouter();
            int address = other.getAddress();

            if (othRouter.isTransferring()) {
                continue; // skip hosts that are transferring
            }

            Integer nrofCopies;

            if (this.protocolMap[address].equals(SIGNATURE_PROPHET)) {
                CompatibleProphetRouter pr = (CompatibleProphetRouter) othRouter;
                // At first, go through the PROPHET msgs
                for (Message m : msgCollection) {
                    if (othRouter.hasMessage(m.getId())) {
                        continue; // skip messages that the other one has
                    }

                    nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
                    if (nrofCopies == null) {
                        if (pr.getPredFor(m.getTo()) > getPredFor(m.getTo())) {
                            // the other node has higher probability of delivery
                            messages.add(new Tuple<Message, Connection>(m, con));
                        }
                    }
                }
                for (Message m : msgCollection) {
                    if (othRouter.hasMessage(m.getId())) {
                        continue; // skip messages that the other one has
                    }
                    // If the message is a SnW msg, and this node has a single
                    // copy of it, then don't forward
                    nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);

                    if (nrofCopies != null && nrofCopies > 1) {
                        // TODO: Remove SnW header nrofCopies
                        messages.add(new Tuple<Message, Connection>(m, con));
                    }
                }
                // sort the message-connection tuples
                Collections.sort(messages, new ProphetPtuRouter.TupleComparator());
            } //            else if (this.protocolMap[address].equals(SIGNATURE_SNW_PTU)) {
            //                // TODO: What if it is a SnW router?                
            //                SnWPTURouter sptur = (SnWPTURouter) othRouter;
            //                for (Message m : msgCollection) {
            //                    if (othRouter.hasMessage(m.getId())) {
            //                        continue; // skip messages that the other one has
            //                    }
            //                    
            //                    // If the message is a SnW msg, and this node has a single
            //                    // copy of it, then don't forward
            //                    nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
            //                    if (nrofCopies == null || nrofCopies <= 1) {
            //                        continue;
            //                    }
            //                    
            //                    if (sptur.getPredFor(m.getTo()) > getPredFor(m.getTo())) {
            //                        // the other node has higher probability of delivery
            //                        messages.add(new Tuple<Message, Connection>(m,con));
            //                    }
            //                }
            //            }
            else if (this.protocolMap[address].equals(SIGNATURE_SNW)) {
                // SnW msgs
                for (Message m : msgCollection) {
                    if (othRouter.hasMessage(m.getId())) {
                        continue; // skip messages that the other one has
                    }
                    // If the message is a SnW msg, and this node has a single
                    // copy of it, then don't forward
                    nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);

                    if (nrofCopies != null && nrofCopies > 1) {
                        // TODO: Remove SnW header nrofCopies
                        messages.add(new Tuple<Message, Connection>(m, con));
                    }
                }

                // PROPHET msgs
                for (Message m : msgCollection) {
                    if (othRouter.hasMessage(m.getId())) {
                        continue; // skip messages that the other one has
                    }

                    nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
                    if (nrofCopies == null) {
                        m.addProperty(MSG_COUNT_PROPERTY, initialNrofCopies);
                        messages.add(new Tuple<Message, Connection>(m, con));
                    }
                }
            } else if (this.protocolMap[address].equals(SIGNATURE_PROPHET_PTU)) {
                ProphetPtuRouter ptur = (ProphetPtuRouter) othRouter;
                for (Message m : msgCollection) {
                    if (othRouter.hasMessage(m.getId())) {
                        continue; // skip messages that the other one has
                    }

                    // If the message is a SnW msg, and this node has a single
                    // copy of it, then don't forward
                    nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
                    if (nrofCopies != null && nrofCopies > 1) {
                        messages.add(new Tuple<Message, Connection>(m, con));
                        continue;
                    }

                    if (nrofCopies == null) {
                        if (ptur.getPredFor(m.getTo()) > getPredFor(m.getTo())) {
                            // the other node has higher probability of delivery
                            messages.add(new Tuple<Message, Connection>(m, con));
                        }
                    }
                }
            }
            // sort the message-connection tuples
            Collections.sort(messages, new ProphetPtuRouter.TupleComparator());
        }

        if (messages.isEmpty()) {
            return null;
        }

        return tryMessagesForConnected(messages);	// try to send messages
    }

    /**
     * Comparator for Message-Connection-Tuples that orders the tuples by their
     * delivery probability by the host on the other side of the connection
     * (GRTRMax)
     */
    private class TupleComparator implements Comparator<Tuple<Message, Connection>> {

        public int compare(Tuple<Message, Connection> tuple1,
                Tuple<Message, Connection> tuple2) {
            // delivery probability of tuple1's message with tuple1's connection

            MessageRouter r1 = tuple1.getValue().
                    getOtherNode(getHost()).getRouter();
            MessageRouter r2 = tuple2.getValue().
                    getOtherNode(getHost()).getRouter();
            double p1;
            double p2;

            if (r1 instanceof CompatibleProphetRouter) {
                p1 = ((CompatibleProphetRouter) r1).getPredFor(tuple1.getKey().getTo());
            } else if (r1 instanceof ProphetPtuRouter) {
                p1 = ((ProphetPtuRouter) r1).getPredFor(
                        tuple1.getKey().getTo());
            } else if (r1 instanceof SnwPtuRouter) {
                p1 = ((SnwPtuRouter) r1).getPredFor(tuple1.getKey().getTo());
            } else {
                // SnW router
                p1 = 1;
            }

            // -"- tuple2...
            if (r2 instanceof CompatibleProphetRouter) {
                p2 = ((CompatibleProphetRouter) r2).getPredFor(tuple2.getKey().getTo());
            } else if (r2 instanceof ProphetPtuRouter) {
                p2 = ((ProphetPtuRouter) r2).getPredFor(
                        tuple2.getKey().getTo());
            } else if (r2 instanceof SnwPtuRouter) {
                p2 = ((SnwPtuRouter) r2).getPredFor(tuple2.getKey().getTo());
            } else {
                // SnW router
                p2 = 0;
            }

            // bigger probability should come first
            if (p2 - p1 == 0) {
                /* equal probabilities -> let queue mode decide */
                return compareByQueueMode(tuple1.getKey(), tuple2.getKey());
            } else if (p2 - p1 < 0) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    @Override
    public MessageRouter replicate() {
        return new ProphetPtuRouter(this);
    }
}
