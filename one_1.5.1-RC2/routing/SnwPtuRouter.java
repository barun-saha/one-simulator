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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of Protocol Translation Unit (PTU) router that is compatible
 * with a Spray-and-Wait router. For details on PTU, please refer to Chapter 8,
 * #TheOMNBook.
 * 
 * @author barun
 */
public class SnwPtuRouter extends ActiveRouter {

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
    public static final String PROPHET_NS = "CompatibleProphetRouter";
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
            "class routing.SprayAndWaitRouter";
    protected static final String SIGNATURE_PROPHET_PTU =
            "class routing.ProphetPtuRouter";
    protected static final String SIGNATURE_SNW_PTU =
            "class routing.SnwPtuRouter";

    public SnwPtuRouter(Settings s) {
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

    public SnwPtuRouter(SnwPtuRouter r) {
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
    public void update() {
        super.update();
        if (!canStartTransfer() || isTransferring()) {
            return; // nothing to transfer or is currently transferring 
        }

        // try messages that could be delivered to final recipient
        if (exchangeDeliverableMessages() != null) {
            return;
        }

        /* create a list of SAWMessages that have copies left to distribute */
        @SuppressWarnings(value = "unchecked")
        List<Message> copiesLeft = sortByQueueMode(getMessagesWithCopiesLeft());

        if (copiesLeft.size() > 0) {
            /* try to send those messages */
            this.tryMessagesToConnections(copiesLeft, getConnections());
        }
    }

    /**
     * Creates and returns a list of messages this router is currently carrying
     * and still has copies left to distribute (nrof copies > 1).
     *
     * @return A list of messages that have copies left
     */
    protected List<Message> getMessagesWithCopiesLeft() {
        List<Message> list = new ArrayList<Message>();

        for (Message m : getMessageCollection()) {
            Integer nrofCopies = (Integer) m.getProperty(
                    SprayAndWaitRouter.MSG_COUNT_PROPERTY);
            if (nrofCopies == null) {
                m.addProperty(SprayAndWaitRouter.MSG_COUNT_PROPERTY,
                        new Integer(initialNrofCopies));
                nrofCopies = initialNrofCopies;
            }
//			assert nrofCopies != null : "SnW message " + m + " didn't have " + 
//				"nrof copies property!";
            if (nrofCopies > 1) {
                list.add(m);
            }
        }

        return list;
    }

    @Override
    public boolean createNewMessage(Message msg) {
        makeRoomForNewMessage(msg.getSize());

        msg.setTtl(this.msgTtl);
        msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
        addToMessages(msg, true);
        return true;
    }

    /**
     * Called just before a transfer is finalized (by
     * {@link ActiveRouter#update()}). Reduces the number of copies we have left
     * for a message. In binary Spray and Wait, sending host is left with
     * floor(n/2) copies, but in standard mode, nrof copies left is reduced by
     * one.
     */
    @Override
    protected void transferDone(Connection con) {
        Integer nrofCopies;
        String msgId = con.getMessage().getId();
        /* get this router's copy of the message */
        Message msg = getMessage(msgId);

        if (msg == null) { // message has been dropped from the buffer after..
            return; // ..start of transfer -> no need to reduce amount of copies
        }

        /* reduce the amount of copies left */
        nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);
        if (isBinary) {
            nrofCopies /= 2;
        } else {
            nrofCopies--;
        }
        msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
    }

    @Override
    public MessageRouter replicate() {
        return new SnwPtuRouter(this);
    }
}
