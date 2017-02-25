/*
 * Copyright 2015-2017 Barun Saha, http://barunsaha.me
 * Released under GPLv3. See LICENSE.txt for details.
 */
package routing;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import util.Tuple;

/**
 * Simulated Annealing-based Routing (SeeR) for OMNs. This router works based on
 * locally available information, and aims to minimize the average message
 * delivery latency. This implementation *assumes* that TTL is specified in
 * minutes.
 *
 * If you are using SeeR in your work, please cite it as follows:
 *
 * B. K. Saha, S. Misra, and S. Pal, "SeeR: Simulated Annealing-based Routing
 * in Opportunistic Mobile Networks," IEEE Transactions on Mobile Computing,
 * DOI: 10.1109/TMC.2017.2673842, 2017.
 *
 * @author barun
 * @date 4 Aug, 2015
 */
public class SeerRouter extends ActiveRouter {

    /**
     * Average ICT of a node; initially taken to be 1 hour
     */
    private double ict = 3600;
    /**
     * ICT manager to store <node address, time contact went down>
     */
    private HashMap<Integer, Integer> ictManager;
    /**
     * When to reset the ICT counters next?
     */
    private int nextIctResetAt = -1;
    /**
     * Historical list of messages that were replicated to this node
     */
    private HashMap<String, Integer> seenMessages;
    /**
     * The initial temperature of each message
     */
    private double initialTemperature = 15000.0;
    /**
     * How quickly to cool down the temperature?
     */
    private double coolingCoefficient = 0.95;
    private double boltzmannConstant = 1;
    /**
     * Random number generator for ICT reset
     */
    private Random rng = null;
    /**
     * Lower temperature threshold to stop simulated annealing
     */
    private static final double ZERO_TEMPERATURE = 0.001;
    private static final double ICT_CURRENT_WEIGHT = 0.6;
    /**
     * ICT counter reset interval (in seconds)
     */
    public static final int ICT_RESET_LOW = 10 * 3600;
    public static final int ICT_RESET_HIGH = 12 * 3600;
    /**
     * Temperature property of a message
     */
    public static final String TEMPERATURE = "temperature";
    private static final String BOLTZMANN_CONSTANT_S = "boltzmannConstant";
    private static final String COOLING_COEEFICIENT_S = "coolingCoefficient";
    private static final String INITIAL_TEMPERATURE_S = "initialTemeperature";
    /**
     * SeeR router's setting namespace ({@value})
     */
    public static final String SEER_NS = "SeerRouter";

    /**
     * Create a new message router based on the settings in the given Settings
     * object.
     *
     * @param s The settings object
     */
    public SeerRouter(Settings s) {
        super(s);

        Settings seer = new Settings(SEER_NS);

        if (seer.contains(BOLTZMANN_CONSTANT_S)) {
            boltzmannConstant = seer.getDouble(BOLTZMANN_CONSTANT_S);
        }

        if (seer.contains(COOLING_COEEFICIENT_S)) {
            coolingCoefficient = seer.getDouble(COOLING_COEEFICIENT_S);
        }

        if (seer.contains(INITIAL_TEMPERATURE_S)) {
            initialTemperature = seer.getDouble(INITIAL_TEMPERATURE_S);
        }

        ictManager = new HashMap<Integer, Integer>();
    }

    /**
     * Copy constructor.
     *
     * @param r The router prototype where setting values are copied from
     */
    protected SeerRouter(SeerRouter r) {
        super(r);

        boltzmannConstant = r.boltzmannConstant;
        coolingCoefficient = r.coolingCoefficient;
        initialTemperature = r.initialTemperature;
        ictManager = new HashMap<Integer, Integer>();
    }

    @Override
    public boolean createNewMessage(Message m) {
        boolean createStatus = super.createNewMessage(m);
        m.addProperty(TEMPERATURE, initialTemperature);

        return createStatus;
    }

    @Override
    public void changedConnection(Connection con) {
        super.changedConnection(con);

        int otherAddress = con.getOtherNode(getHost()).getAddress();
        int currentTime = SimClock.getIntTime();

        if (con.isUp()) {
            // In reality, messages are checked for replication or
            // non-replication actions only when a connection is up. Therefore,
            // temperature of all the messages are decreased as soon as a
            // connection goes up. Replication decisions are taken at a later
            // point of time.
            decreaseTemperature();

            if (ictManager.containsKey(otherAddress)) {
                // There has been a previous contact with the other node
                int lastContactEndedAt = ictManager.get(otherAddress);

                if (lastContactEndedAt > 0) {
                    double delta = currentTime - lastContactEndedAt;
                    if (delta > 1) {
                        ict = ICT_CURRENT_WEIGHT * delta
                                + (1 - ICT_CURRENT_WEIGHT) * ict;
                    }
                }
                // An inter-contact event is over
                ictManager.remove(otherAddress);
            }
        } else {
            // Connection down event
            if (!ictManager.containsKey(otherAddress)) {
                ictManager.put(otherAddress, SimClock.getIntTime());
            }
        }
    }

    @Override
    public void update() {
        // Must be called before resetting ICT timers
        resetSeenMessages();
        resetIctManager();

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
        int currentTime = SimClock.getIntTime();

        for (Connection con : getConnections()) {
            DTNHost other = con.getOtherNode(getHost());
            SeerRouter othRouter = (SeerRouter) other.getRouter();

            if (othRouter.isTransferring()) {
                continue; // skip hosts that are transferring
            }

            for (Message m : msgCollection) {
                if (othRouter.hasMessage(m.getId())) {
                    continue; // skip messages that the other one has
                }

                int hopCount = m.getHopCount();
                double ttlDelta = m.getInitialTtl() * 60
                        - 2 * (currentTime - m.getCreationTime());

                // Consider replicating if othRouter.ict < ttlDelta;
                // skip otherwise
                boolean pruneNeighbor = (ttlDelta < othRouter.ict);

                // Temporal neighborhood pruning
                if (pruneNeighbor
                        //> || m.getHops().contains(other)
                        || othRouter.seenMessagePreviously(m.getId())) {
                    continue;
                }

                double temperature = (Double) m.getProperty(TEMPERATURE);

                if (temperature < ZERO_TEMPERATURE) {
                    continue;
                }

                double cost1;
                double cost2;

                cost1 = ict * (1 + hopCount);
                cost2 = othRouter.ict * (2 + hopCount);

                double delta = cost2 - cost1;
                double otherwise = Math.exp(-delta / (boltzmannConstant
                        * temperature));

                // Max double otherwise = Math.exp(delta / (temperature));
                double prob = Math.random();

                if (delta <= 0 || prob < otherwise) {
                    // the other node has higher probability of delivery
                    messages.add(new Tuple<Message, Connection>(m, con));
                }
            }
        }

        if (messages.isEmpty()) {
            return null;
        }

        return tryMessagesForConnected(messages);	// try to send messages
    }

    @Override
    public SeerRouter replicate() {
        return new SeerRouter(this);
    }

    public int getConnectionManagerSize() {
        int size = 0;

        if (ictManager != null) {
            size = ictManager.size();
        }

        return size;
    }

    private void resetIctManager() {
        int currentTime = SimClock.getIntTime();
        // Initialize the random number generator
        if (rng == null) {
            rng = new Random(currentTime);
        }

        if (currentTime > nextIctResetAt) {
            if (isTransferring()) {
                return;
            }

            // Reset the ICT counters and calculate the next reset time
            ictManager.clear();
            nextIctResetAt += rng.nextInt(ICT_RESET_HIGH - ICT_RESET_LOW + 1)
                    + ICT_RESET_LOW;
        }
    }

    @Override
    public Message messageTransferred(String id, DTNHost from) {
        if (seenMessages == null) {
            seenMessages = new HashMap<String, Integer>();
        }

        Message m = super.messageTransferred(id, from);
        // By default, TTL is specified as MINUTES in the simulation settings
        int residualTtl = m.getTtl() * 60;

        if (residualTtl > 0) {
            // Add an entry and its valididty to MLT
            seenMessages.put(id, SimClock.getIntTime() + residualTtl);
        }

        return m;
    }

    private boolean seenMessagePreviously(String msgId) {
        return seenMessages != null && seenMessages.containsKey(msgId);
    }

    private void resetSeenMessages() {
        int currentTime = SimClock.getIntTime();

        if (currentTime > nextIctResetAt) {
            if (!isTransferring() && seenMessages != null) {
                ArrayList<String> forDelete = new ArrayList<String>();

                for (String id : seenMessages.keySet()) {
                    int expiryTime = (Integer) seenMessages.get(id);

                    if (expiryTime > currentTime) {
                        forDelete.add(id);
                    }
                }

                for (String id : forDelete) {
                    seenMessages.remove(id);
                }

                forDelete.clear();
            }
        }
    }

    public int getSeenMessagesSize() {
        int size = 0;

        if (seenMessages != null) {
            size = seenMessages.size();
        }

        return size;
    }

    private void decreaseTemperature() {
        for (Message m : getMessageCollection()) {
            double temperature = (Double) m.getProperty(TEMPERATURE);

            if (temperature < ZERO_TEMPERATURE) {
                continue;
            }

            temperature *= coolingCoefficient;
            m.updateProperty(TEMPERATURE, temperature);
        }
    }
}
