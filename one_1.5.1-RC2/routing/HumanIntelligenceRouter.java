/*
 * Copyright 2015, 2016 Barun Saha (http://barunsaha.me)
 *
 * Distributed under the GNU GPL v3 license used by the ONE simulator.
 */
package routing;

import applications.HumanIntelligenceApplication;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.ModuleCommunicationBus;
import core.Settings;

/**
 * Passes locations of static nodes encountered by a mobile node to its
 * application.
 * 
 * This module is a part of implementation of the protocol described in 
 * Section 4.4, Chapter 4, #TheOMNBook.
 * 
 * @author barun
 */
public class HumanIntelligenceRouter extends EpidemicRouter {

    /**
     * First characters of group ID of static hosts
     */
    public static final String HIROUTER_NS = "hiRouter";
    public static final String STATIC_ID_S = "staticID";
    private static final String DB_ID = HumanIntelligenceApplication.DB_ID;
    public static final String NEW_LOC = HumanIntelligenceApplication.NEW_LOC;
    private char staticID;

    public HumanIntelligenceRouter(Settings s) {
        super(s);
        this.staticID = new Settings(HIROUTER_NS).getSetting(STATIC_ID_S).charAt(0);
    }

    protected HumanIntelligenceRouter(HumanIntelligenceRouter r) {
        super(r);
        this.staticID = r.staticID;
    }

    @Override
    protected int checkReceiving(Message m, DTNHost from) {
        int recvCheck = super.checkReceiving(m, from);

        if (recvCheck == RCV_OK) {
            /* don't accept a message that has already traversed this node */
            if (m.getHops().contains(getHost())) {
                recvCheck = DENIED_OLD;
            }
        }

        return recvCheck;
    }

    @Override
    public HumanIntelligenceRouter replicate() {
        return new HumanIntelligenceRouter(this);
    }

    @Override
    public void changedConnection(Connection con) {

        if (con.isUp()) {
            DTNHost me = getHost();
            DTNHost you = con.getOtherNode(me);

            ModuleCommunicationBus comBus = me.getComBus();

            // If I'm a mobile node and you are static, I would do * something *
            if (me.toString().charAt(0) != this.staticID && you.toString().charAt(0) == this.staticID) {
                // Application has already created the NEW_LOC property
                // Just inform the app that I met with a static node
                comBus.updateProperty(NEW_LOC, you.getLocation());
            }
        }

    }
}
