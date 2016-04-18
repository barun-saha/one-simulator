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

/**
 * An app to simulate "human intelligence" that affects how a node (user) should
 * move. Here, mobile nodes exchange coordinates of the known static nodes. 
 * This app, on receiving such a coordinate, sets that as the next waypoint in 
 * the controlled RWP mobility model.
 *
 * This version of the HumanIntelligenceApplication enforces a mobile node to 
 * visit a newly known static node only once. * 
 * Exceptions: If a static node has been already visited by a mobile node, the 
 * app cannot force it to visit the node again. It can, however, visit the 
 * static node on "its own will", i.e. as the RWP mobility model dictates it.
 * 
 * This module is a part of implementation of the protocol described in 
 * Section 4.4, Chapter 4, #TheOMNBook.
 *
 * @deprecated The functionality conceptualized here has later been moved to
 * {@link applications.HumanIntelligenceApplication2} and {@link applications.HumanIntelligenceApplication3}.
 * 
 * @author barun
 */
public class HumanIntelligenceApplication extends Application {

    /**
     * Address range of mobile nodes -- must be contiguous (excludes upper
     * limit)
     */
    public static final String HIAPP_NS = "humanIntApp";
    public static final String MOBILE_ADDRESS_RANGE_S = "mobileNodesAddr";
    /**
     * Application ID
     */
    public static final String APP_ID = "HumanIntelligenceApp";
    /**
     * DB ID
     */
    public static final String DB_ID = "dbStaticLocations";
    public static final String NEW_LOC = "lastStaticLocation";
    public static final String MOVEMENT_LOC = "newTarget";
    protected static final String msgType = "database";
    // Private vars
    protected int minAddr;
    protected int maxAddr;
    protected int seed = 0;
    protected Random rng;
    // Database of static locations; indicates visited or not
    protected HashMap<Coord, Boolean> database;
    protected ModuleCommunicationBus comBus;
    protected Coord lastLocation;       // Last static location informed by router
    protected DTNHost myself;

    /**
     * Creates a new ping application with the given settings.
     *
     * @param s	Settings to use for initializing the application.
     */
    public HumanIntelligenceApplication(Settings s) {
        int[] addrRange = s.getCsvInts(MOBILE_ADDRESS_RANGE_S, 2);
        this.minAddr = addrRange[0];
        this.maxAddr = addrRange[1] - 1;

        rng = new Random(this.seed);
        super.setAppID(APP_ID);
        this.database = new HashMap<Coord, Boolean>();
        this.lastLocation = new Coord(-1.0, -1.0);
    }

    /**
     * Copy-constructor
     *
     * @param a An existing HumanIntelligenceApplication
     */
    public HumanIntelligenceApplication(HumanIntelligenceApplication a) {
        super(a);

        this.minAddr = a.minAddr;
        this.maxAddr = a.maxAddr;

        this.seed = a.getSeed();
        this.rng = new Random(this.seed);
        this.database = new HashMap<Coord, Boolean>();
        this.lastLocation = new Coord(-1.0, -1.0);
    }

    /**
     * Handles an incoming message. Retrieve a location sent with this message,
     * store it, and instruct the mobility module to visit it subsequently. 
     *
     * @param msg Message received by the router
     * @param host Host to which the application instance is attached
     */
    @Override
    public Message handle(Message msg, DTNHost host) {
        // NOTE: We don't need to check the destination of the message -- 
        // the information could be used by all mobile nodes
        //if (msg.getTo() != host)
        //   return msg;

        // Make the database available to other modules
        ModuleCommunicationBus comBus = host.getComBus();

        // I've received a new location from someone. Now let me check if I 
        // already have that location.
        Coord newLocation = (Coord) msg.getProperty(NEW_LOC);
        boolean isExisting = false;

        for (Map.Entry<Coord, Boolean> entry : this.database.entrySet()) {
            if (newLocation.equals(entry.getKey())) {
                isExisting = true;
                break;
            }
        }

        if (!isExisting) {
//            System.out.print("> " + msg.getTo() + " " + this.database 
//                    + " received new loc " + newLocation + " from " 
//                    + msg.getFrom());
            this.database.put(newLocation, false);
            System.out.println(" => " + this.database);

            // Now inform the mobility model that we have to visit 
            // this new location
            ArrayList<Coord> newLocations = new ArrayList<Coord>();
            newLocations.add(newLocation);

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
        return new HumanIntelligenceApplication(this);
    }

    /**
     * Sends out messages to other mobile nodes informing the newly received
     * location of a static node.
     *
     * @param host to which the application instance is attached
     */
    @Override
    public void update(DTNHost host) {
        ModuleCommunicationBus comBus = host.getComBus();

        // First time
        if (!comBus.containsProperty(NEW_LOC)) {
            //System.out.println("update: comBus set and database added for " + host);
            //this.comBus.addProperty("myself", host.toString());
            comBus.addProperty(NEW_LOC, this.lastLocation);
        }

        //System.out.println("HumanIntelligenceApplication: update: " + host);
        Coord newLocation = (Coord) comBus.getProperty(NEW_LOC);
        boolean sendMessage = false;

        if (newLocation != null) {
            //System.out.println(newLocation + " " + this.lastLocation);
            if (!newLocation.equals(this.lastLocation)) {
                // Update last seen location
                this.lastLocation = newLocation;
                sendMessage = true;
            }

            if (sendMessage) {
                if (!this.database.containsKey(newLocation)) {
                    this.database.put(newLocation, false);
                    //System.out.println("Database updated: " + this.database);

                    // Send message to all mobile nodes -- except myself, of course
                    World w = SimScenario.getInstance().getWorld();

                    for (int i = this.minAddr; i <= this.maxAddr; i++) {
                        if (i == host.getAddress()) {
                            continue;
                        }

                        Message m = new Message(host, w.getNodeByAddress(i), 
                                msgType + SimClock.getIntTime() + "-" 
                                + host.toString(),
                                1);
                        m.addProperty("type", "scan");
                        //m.addProperty("database", this.database);            
                        m.updateProperty(NEW_LOC, newLocation);
                        m.setAppID(APP_ID);
                        host.createNewMessage(m);
                        System.out.println("hiApp: update: msg created: " 
                                + m.getFrom() + " " + this.database 
                                + " sending " + newLocation + " -> " 
                                + m.getTo());

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
