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
import java.util.List;
import java.util.Random;
import report.LucidLocationDeviationReport;
import report.MessageReceivedLocationReport;
import util.Tuple;

/**
 * Only source replicates messages within
 * the concerned locality.
 *
 * Note: This version is not used directly; use {@link LucidV7Router} instead.
 *
 * @author barun
 */
public class LucidRouter extends ActiveRouter {

    /**
     * Location of a message where it was created
     */
    public static final String INIT_LOCATION_PROPERTY = "initLocation";
    /**
     * Original location where this message was created. Only used for reporting
     * purpose.
     * @see LucidLocationDeviationReport
     */
    public static final String ORIG_LOCATION_PROPERTY = "origLocation";
    public static final String LUCID_NS = "LucidRouter";
    /**
     * Radius of the circular locality
     */
    protected double localityRange;
    protected Random rng;

    public LucidRouter(Settings s) {
        super(s);

        Settings settings = new Settings(MessageReceivedLocationReport.MRL_REPORT_NS);
        localityRange = settings.getDouble(
                MessageReceivedLocationReport.RANGE_S);
        rng = new Random();
    }

    public LucidRouter(LucidRouter r) {
        super(r);

        localityRange = r.localityRange;
        rng = new Random();
    }

    @Override
    public void update() {
        super.update();
        if (!canStartTransfer() || isTransferring()) {
            return; // nothing to transfer or is currently transferring
        }

        tryMessageDissemination();
    }

    protected Tuple<Message, Connection> tryMessageDissemination() {
        List<Tuple<Message, Connection>> messages =
                new ArrayList<Tuple<Message, Connection>>();
        Collection<Message> msgCollection = getMessageCollection();

        for (Connection con : getConnections()) {
            DTNHost other = con.getOtherNode(getHost());
            LucidRouter othRouter = (LucidRouter) other.getRouter();

            if (othRouter.isTransferring()) {
                continue; // skip hosts that are transferring
            }

            Coord currentLocation = getLocation();

            for (Message m : msgCollection) {
                if (othRouter.hasMessage(m.getId())) {
                    continue; // skip messages that the other one has
                }

                /*
                 * INIT_LOCATION_PROPERTY of a message is maintained by source
                 * as well as any other node receiving it. In the latter case,
                 * location of message reception is stored.
                 */
                double distance = currentLocation.distance((Coord) m.getProperty(INIT_LOCATION_PROPERTY));

                if (m.getFrom().equals(getHost())) {
                    // This node is the source of the message and
                    // replicates it within the locality range
                    if (distance < localityRange) {
                        messages.add(new Tuple<Message, Connection>(m, con));
                    }
                }
            }
        }

        if (messages.isEmpty()) {
            return null;
        }

        return tryMessagesForConnected(messages);
    }

    @Override
    public Message messageTransferred(String id, DTNHost from) {
        Message msg = super.messageTransferred(id, from);
        msg.updateProperty(INIT_LOCATION_PROPERTY, getLocation());

        return msg;
    }

    protected double getReplicationProbability(double distance) {
        if (localityRange < 300) {
            return Math.pow(0.99, distance * distance / localityRange);
        } else {
            return Math.pow(0.992, distance * distance / localityRange);
        }
    }

    protected Coord getLocation() {
        return getHost().getLocation().clone();
    }

    @Override
    public LucidRouter replicate() {
        return new LucidRouter(this);
    }

    @Override
    public boolean createNewMessage(Message m) {
        m.addProperty(INIT_LOCATION_PROPERTY, getLocation());
        m.addProperty(ORIG_LOCATION_PROPERTY, getLocation());

        return super.createNewMessage(m);
    }
}
