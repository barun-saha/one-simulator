/*
 * Copyright 2015, 2016 Barun Saha (http://barunsaha.me)
 *
 * Distributed under the GNU GPL v3 license used by the ONE simulator.
 */
package applications;

import core.Application;
import core.Coord;
import core.DTNHost;
import core.Message;
import core.ModuleCommunicationBus;
import core.Settings;
import core.SimClock;
import core.SimScenario;
import core.World;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import movement.ZoneHelper;

/**
 * An app to simulate "human intelligence" that affects how a node (user) should
 * move. Here, mobile nodes exchange coordinates of the known static nodes. This
 * app, on receiving such a coordinate, sets that as the next waypoint in the
 * controlled RWP mobility model. It uses opportunistic communication with other
 * nodes to share information. If a static node has been already visited by a
 * mobile node, the app cannot force it to visit the node again. It can,
 * however, visit the static node on "its own will", i.e. as the RWP mobility
 * model dictates it.
 *
 * This module is a part of implementation of the protocol described in Section
 * 4.4, Chapter 4, #TheOMNBook.
 *
 * @author barun
 */
public class HumanIntelligenceApplication2 extends Application {

    /**
     * Address range of mobile nodes -- must be contiguous (excludes upper
     * limit)
     */
    public static final String HIAPP_NS = "humanIntApp";
    public static final String MOBILE_ADDR_RANGE_S = "mobileNodesAddr";
    /**
     * Indicates which level of intelligence is used
     */
    public static final String INTELLIGENCE_LEVEL_S = "intlLevel";
    public static final String K_FACTOR_S = "kFactor";
    /**
     * Application ID
     */
    public static final String APP_ID = "HumanIntelligenceApp2";
    /**
     * Database ID
     */
    public static final String DB_ID = "dbStaticLocations";
    public static final String NEW_LOC = "lastStaticLocation";
    public static final String MOVEMENT_LOC = "newTarget";
    private static final String msgType = "database";
    // Intelligence levels
    /**
     * Go to the static node
     */
    private static final int INTL_L1 = 1;
    /**
     * L1 + go to a random zone in 1-neighbor
     */
    private static final int INTL_L2 = 2;
    /**
     * L1 + go to k-left neighbors
     */
    private static final int INTL_L3 = 3;
    /**
     * L1 + go to 1-neighbor zones
     */
    private static final int INTL_L4 = 4;
    /**
     * L1 + go to 2-neighbor zones
     */
    private static final int INTL_L5 = 5;
    // Private vars
    private int minAddr;
    private int maxAddr;
    private int seed = 0;
    private Random rng;
    /**
     * Database of static locations; indicates visited or not
     */
    private HashMap<Coord, Boolean> database;
    //private ModuleCommunicationBus comBus;
    /**
     * Last static location informed by router
     */
    private Coord lastLocation;
    /**
     * Current intelligence level
     */
    private int intlLevel;
    private static int kFactor = 1;
    //private DTNHost myself;

    /**
     * Creates a new HumanIntelligenceApplication2 with the given settings.
     *
     * @param s	Settings to use for initializing the application.
     */
    public HumanIntelligenceApplication2(Settings s) {
        int[] addrRange = s.getCsvInts(MOBILE_ADDR_RANGE_S, 2);
        this.minAddr = addrRange[0];
        this.maxAddr = addrRange[1] - 1;

        rng = new Random(this.seed);
        super.setAppID(APP_ID);
        this.lastLocation = new Coord(-1.0, -1.0);
        this.database = new HashMap<Coord, Boolean>();
        this.intlLevel = s.getInt(INTELLIGENCE_LEVEL_S);

        if (s.contains(K_FACTOR_S)) {
            kFactor = s.getInt(K_FACTOR_S);
        }
    }

    /**
     * Copy-constructor
     *
     * @param a
     */
    public HumanIntelligenceApplication2(HumanIntelligenceApplication2 a) {
        super(a);

        this.minAddr = a.minAddr;
        this.maxAddr = a.maxAddr;

        this.seed = a.getSeed();
        this.rng = new Random(this.seed);
        this.lastLocation = new Coord(-1.0, -1.0);
        this.database = new HashMap<Coord, Boolean>();
        this.intlLevel = a.intlLevel;
        //this.kFactor = a.kFactor;
    }

    /**
     * Handles an incoming message.
     *
     * @param msg	message received by the router
     * @param host	host to which the application instance is attached
     */
    @Override
    public Message handle(Message msg, DTNHost host) {
        // NOTE: We don't need to check the destination of the message -- 
        // the information could be used by all mobile nodes
        //if (msg.getTo() != host)
        //   return msg;

        //System.out.println(" + " + SimClock.getIntTime() + ": " + host + " " 
        // + msg.getId() + ", " + msg.getAppID() + ", " + msg.getFrom() + ", " 
        // + msg.getTo());

        // Make the database available to other modules
        ModuleCommunicationBus comBus = host.getComBus();

        // I've received a new location from someone -- 
        // let me check if I already have that in my database
        Coord newLocation = (Coord) msg.getProperty(NEW_LOC);
        boolean isExisting = false;

        //System.out.println(host + ": " + newLocation + " / " + this.database.entrySet());
        for (Map.Entry<Coord, Boolean> entry : this.database.entrySet()) {
            if (newLocation.equals(entry.getKey())) {
                isExisting = true;
                break;
            }
        }

        if (!isExisting) {
            this.database.put(newLocation, false);

            // Now inform the mobility model that we have to visit this 
            // new location and possibly some neighbouring areas
            ArrayList<Coord> newLocations = new ArrayList<Coord>();
            newLocations.add(newLocation);  // This location is always added

            ArrayList<int[]> neighbours = new ArrayList<int[]>();
            int[] zoneID = ZoneHelper.getZoneID(newLocation);

            // Process as per intelligence level
            switch (this.intlLevel) {
                case INTL_L2:
                    // A random zone in 1-neighbour
                    neighbours = ZoneHelper.getNeighbouringZones(zoneID, 1);
                    int idx = rng.nextInt(neighbours.size());
                    int[] randomNeighbour = neighbours.get(idx);
                    Coord rnx = ZoneHelper.getZoneCenter(randomNeighbour);
                    newLocations.add(rnx);
                    break;

                case INTL_L3:
                    // k-left zones                    
                    neighbours = ZoneHelper.getLeftNeighbouringZones(zoneID, kFactor);

                    for (int[] neighbour : neighbours) {
                        Coord cx = ZoneHelper.getZoneCenter(neighbour);
                        newLocations.add(cx);
                    }
                    break;

                case INTL_L4:
                    // This intelligence level not only asks the node to visit a 
                    // static location, but also its 1-neighbourhood                    
                    neighbours = ZoneHelper.getNeighbouringZones(zoneID, 1);

                    for (int[] neighbour : neighbours) {
                        Coord cx = ZoneHelper.getZoneCenter(neighbour);
                        newLocations.add(cx);
                    }
                    break;

                case INTL_L5:
                    break;

                case INTL_L1:
                default:
                    // Just location of the static node
                    break;
            }

            if (comBus.containsProperty(MOVEMENT_LOC)) {
                comBus.updateProperty(MOVEMENT_LOC, newLocations);
            } else {
                comBus.addProperty(MOVEMENT_LOC, newLocations);
            }
        }

        return msg;
    }

    @Override
    public Application replicate() {
        return new HumanIntelligenceApplication2(this);
    }

    /**
     * Sends location about static nodes to other mobile nodes.
     *
     * @param host to which the application instance is attached
     */
    @Override
    public void update(DTNHost host) {
        ModuleCommunicationBus comBus = host.getComBus();

        // First time
        if (!comBus.containsProperty(NEW_LOC)) {
            comBus.addProperty(NEW_LOC, this.lastLocation);
        }

        Coord newLocation = (Coord) comBus.getProperty(NEW_LOC);
        boolean sendMessage = false;

        if (newLocation != null) {
            if (!newLocation.equals(this.lastLocation)) {
                // Update last seen location
                this.lastLocation = newLocation;
                sendMessage = true;
            }

            if (sendMessage) {
                if (!this.database.containsKey(newLocation)) {
                    this.database.put(newLocation, false);

                    // Send message to all mobile nodes except myself, of course
                    World w = SimScenario.getInstance().getWorld();

                    for (int i = this.minAddr; i <= this.maxAddr; i++) {
                        if (i == host.getAddress()) {
                            continue;
                        }

                        Message m = new Message(host, w.getNodeByAddress(i), msgType
                                + SimClock.getIntTime() + "-" + host.toString(),
                                1);
                        m.addProperty("type", "scan");
                        //m.addProperty("database", this.database);            
                        m.updateProperty(NEW_LOC, newLocation);
                        m.setAppID(APP_ID);
                        host.createNewMessage(m);

                        // Call listeners
                        super.sendEventToListeners("SearchLocation", null, host);
                    }
                }
            }
        }
    }

    /**
     * @return the seed
     */
    public int getSeed() {
        return seed;
    }

    /**
     * @param seed the seed to set
     */
    public void setSeed(int seed) {
        this.seed = seed;
    }
}
