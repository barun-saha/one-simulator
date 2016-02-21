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
import java.util.List;

/**
 * This is an implementation of the SprayAndWaitRouter except that it does not 
 * throw a runtime exception when another node has PRoPHET router 
 * (CompatibleProphetRouter). Additionally, it can interact with a PTU. 
 * See Chapter 8, #TheOMNBook, for details.
 * 
 * @author barun
 */
public class CompatibleSnwRouter extends ActiveRouter {

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

    public CompatibleSnwRouter(Settings s) {
        super(s);
        Settings snwSettings = new Settings(SPRAYANDWAIT_NS);

        initialNrofCopies = snwSettings.getInt(NROF_COPIES);
        isBinary = snwSettings.getBoolean(BINARY_MODE);
    }

    /**
     * Copy constructor.
     *
     * @param r The router prototype where setting values are copied from
     */
    protected CompatibleSnwRouter(CompatibleSnwRouter r) {
        super(r);
        this.initialNrofCopies = r.initialNrofCopies;
        this.isBinary = r.isBinary;
    }

    @Override
    protected int startTransfer(Message m, Connection con) {
        MessageRouter otherRouter = con.getOtherNode(getHost()).getRouter();
        if (otherRouter instanceof CompatibleProphetRouter) {
            return -123;
        }
        return super.startTransfer(m, con);

    }

    @Override
    public Message messageTransferred(String id, DTNHost from) {
        Message msg = super.messageTransferred(id, from);
        Integer nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);

        assert nrofCopies != null : "Not a SnW message: " + msg
                + " sent by " + from + " with " + from.getRouter();

        if (nrofCopies == null) {
            nrofCopies = initialNrofCopies / 2;
        }
        if (isBinary) {
            /* in binary S'n'W the receiving node gets ceil(n/2) copies */
            nrofCopies = (int) Math.ceil(nrofCopies / 2.0);
        } else {
            /* in standard S'n'W the receiving node gets only single copy */
            nrofCopies = 1;
        }

        msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
        return msg;
    }

    @Override
    public boolean createNewMessage(Message msg) {
        makeRoomForNewMessage(msg.getSize());

        msg.setTtl(this.msgTtl);
        msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
        addToMessages(msg, true);
        return true;
    }

    @Override
    public void update() {
        super.update();
        if (!canStartTransfer() || isTransferring()) {
            return; // nothing to transfer or is currently transferring 
        }

        /* try messages that could be delivered to final recipient */
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
            Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
            assert nrofCopies != null : "SnW message " + m + " didn't have "
                    + "nrof copies property!";
            if (nrofCopies > 1) {
                list.add(m);
            }
        }

        return list;
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
    public CompatibleSnwRouter replicate() {
        return new CompatibleSnwRouter(this);
    }
}
