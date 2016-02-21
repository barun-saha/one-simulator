/*
 * Copyright 2015, 2016 Barun Saha (http://barunsaha.me)
 *
 * Distributed under the original license of the ONE simulator.
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
import routing.util.RoutingInfo;
import util.Tuple;

/**
 * This is an implementation of the ProphetRouter except that it does not throw
 * a runtime exception when another node has SnW router (CompatibleSnwRouter).
 * Additionally, it can interact with a PTU. See Chapter 8, #TheOMNBook, for
 * details.
 * 
 * @author barun
 */
public class CompatibleProphetRouter extends ActiveRouter {

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
     * Constructor. Creates a new message router based on the settings in the
     * given Settings object.
     *
     * @param s The settings object
     */
    public CompatibleProphetRouter(Settings s) {
        super(s);
        Settings prophetSettings = new Settings(PROPHET_NS);
        secondsInTimeUnit = prophetSettings.getInt(SECONDS_IN_UNIT_S);
        if (prophetSettings.contains(BETA_S)) {
            beta = prophetSettings.getDouble(BETA_S);
        } else {
            beta = DEFAULT_BETA;
        }

        initPreds();
    }

    /**
     * Copyconstructor.
     *
     * @param r The router prototype where setting values are copied from
     */
    protected CompatibleProphetRouter(CompatibleProphetRouter r) {
        super(r);
        this.secondsInTimeUnit = r.secondsInTimeUnit;
        this.beta = r.beta;
        initPreds();
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

            if (otherRouter instanceof CompatibleSnwRouter) {
                return;
            }
            updateDeliveryPredFor(otherHost);
            updateTransitivePreds(otherHost);
        }
    }

    @Override
    protected int startTransfer(Message m, Connection con) {
        MessageRouter otherRouter = con.getOtherNode(getHost()).getRouter();
        if (otherRouter instanceof CompatibleSnwRouter) {
            return -123;
        }
        return super.startTransfer(m, con);

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
                || otherRouter instanceof ProphetPtuRouter :
                //||                otherRouter instanceof SnWPTURouter: 
                "PRoPHET only works "
                + "with other routers of same type: " + otherRouter;

        double pForHost = getPredFor(host); // P(a,b)
        Map<DTNHost, Double> othersPreds;
        if (otherRouter instanceof CompatibleProphetRouter) {
            othersPreds = ((CompatibleProphetRouter) otherRouter).getDeliveryPreds();
        } else if (otherRouter instanceof ProphetPtuRouter) {
            othersPreds = ((ProphetPtuRouter) otherRouter).getDeliveryPreds();
        } else {
            assert false : "SnWPTURouter is not used!";
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

            if (othRouter.isTransferring()) {
                continue; // skip hosts that are transferring
            }

            if (othRouter instanceof CompatibleProphetRouter) {
                CompatibleProphetRouter pr = (CompatibleProphetRouter) othRouter;
                for (Message m : msgCollection) {
                    if (othRouter.hasMessage(m.getId())) {
                        continue; // skip messages that the other one has
                    }
                    if (pr.getPredFor(m.getTo()) > getPredFor(m.getTo())) {
                        // the other node has higher probability of delivery
                        messages.add(new Tuple<Message, Connection>(m, con));
                    }
                }
            } else if (othRouter instanceof ProphetPtuRouter) {
                ProphetPtuRouter ptur = (ProphetPtuRouter) othRouter;
                for (Message m : msgCollection) {
                    if (othRouter.hasMessage(m.getId())) {
                        continue; // skip messages that the other one has
                    }
                    if (ptur.getPredFor(m.getTo()) > getPredFor(m.getTo())) {
                        // the other node has higher probability of delivery
                        messages.add(new Tuple<Message, Connection>(m, con));
                    }
                }
            } else if (othRouter instanceof SnwPtuRouter) {
                assert false : "SnWPTURouter is not used";

                SnwPtuRouter sptur = (SnwPtuRouter) othRouter;
                for (Message m : msgCollection) {
                    if (othRouter.hasMessage(m.getId())) {
                        continue; // skip messages that the other one has
                    }
                    if (sptur.getPredFor(m.getTo()) > getPredFor(m.getTo())) {
                        // the other node has higher probability of delivery
                        messages.add(new Tuple<Message, Connection>(m, con));
                    }
                }
            }
        }

        if (messages.isEmpty()) {
            return null;
        }

        // sort the message-connection tuples
        Collections.sort(messages, new CompatibleProphetRouter.TupleComparator());
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
            } else {
                // if (r1 instanceof SnWPTURouter)
                assert false : "SnWPTURouter is not used";
                p1 = ((SnwPtuRouter) r1).getPredFor(tuple1.getKey().getTo());
            }

            // -"- tuple2...
            if (r2 instanceof CompatibleProphetRouter) {
                p2 = ((CompatibleProphetRouter) r2).getPredFor(tuple2.getKey().getTo());
            } else if (r2 instanceof ProphetPtuRouter) {
                p2 = ((ProphetPtuRouter) r2).getPredFor(
                        tuple2.getKey().getTo());
            } else {
                // if (r2 instanceof SnWPTURouter)
                assert false : "SnWPTURouter is not used";
                p2 = ((SnwPtuRouter) r2).getPredFor(tuple2.getKey().getTo());
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
    public RoutingInfo getRoutingInfo() {
        ageDeliveryPreds();
        RoutingInfo top = super.getRoutingInfo();
        RoutingInfo ri = new RoutingInfo(preds.size()
                + " delivery prediction(s)");

        for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
            DTNHost host = e.getKey();
            Double value = e.getValue();

            ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f",
                    host, value)));
        }

        top.addMoreInfo(ri);
        return top;
    }

    @Override
    public MessageRouter replicate() {
        CompatibleProphetRouter r = new CompatibleProphetRouter(this);
        return r;
    }
}
