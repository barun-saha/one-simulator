/*
 * Copyright 2017, 2018 Barun Saha, http://barunsaha.me
 * Released under GPLv3. See LICENSE.txt for details.
 */
package routing;

import core.Connection;
import core.Coord;
import core.DTNHost;
import core.Message;
import core.Settings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import util.Tuple;

/**
 * Implementation of the Locality-boUnded Content and Information
 * Dissemination (LUCID) scheme. In LUCID, any node with a message replicates
 * that to other nodes probabilistically.
 *
 * @author barun
 */
public class LucidV7Router extends LucidRouter {

    private static final String MAX_HOP_COUNT_S = "maxHopCount";
    private int maxHopCount = 5;

    public LucidV7Router(Settings s) {
        super(s);

        Settings ls = new Settings(LUCID_NS);
        if (ls.contains(MAX_HOP_COUNT_S)) {
            maxHopCount = ls.getInt(MAX_HOP_COUNT_S);
        }
    }

    public LucidV7Router(LucidV7Router r) {
        super(r);
        this.maxHopCount = r.maxHopCount;
    }

    @Override
    protected Tuple<Message, Connection> tryMessageDissemination() {
        ArrayList<Tuple<Message, Connection>> messages =
                new ArrayList<Tuple<Message, Connection>>();
        Collection<Message> msgCollection = getMessageCollection();

        for (Connection con : getConnections()) {
            DTNHost other = con.getOtherNode(getHost());
            LucidV7Router othRouter = (LucidV7Router) other.getRouter();

            if (othRouter.isTransferring()) {
                continue; // skip hosts that are transferring
            }

            Coord currentLocation = getLocation();

            for (Message m : msgCollection) {
                if (othRouter.hasMessage(m.getId())) {
                    continue; // skip messages that the other one has
                }

                double distance = currentLocation.distance((Coord) m.getProperty(INIT_LOCATION_PROPERTY));
                boolean shouldReplicate = (rng.nextDouble()
                        < getReplicationProbability(distance))
                        && (m.getHopCount() <= maxHopCount);

                if (shouldReplicate) {
                    messages.add(new Tuple<Message, Connection>(m, con));
                }
            }
        }

        if (messages.isEmpty()) {
            return null;
        }

        return tryMessagesForConnected(messages);
    }

    @Override
    protected double getReplicationProbability(double distance) {
        double p = 0;

        if (distance > localityRange) {
            p = 0;
        } else if (distance < 3 * localityRange / 4) {
            p = 1;
        } else {
            p = Math.pow(0.99, distance * distance / localityRange);
        }

        return p;
    }

    protected Set<String> getMessageIds() {
        HashSet<String> ids = new HashSet<String>();

        for (Message m : getMessageCollection()) {
            ids.add(m.getId());
        }

        return ids;
    }

    @Override
    public LucidV7Router replicate() {
        return new LucidV7Router(this);
    }
}
