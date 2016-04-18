/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package applications;

import core.Application;
import core.Coord;
import core.DTNHost;
import core.Message;
import core.ModuleCommunicationBus;
import core.Settings;
import core.SimClock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import movement.ZoneHelper;

/**
 * An app to simulate * human intelligence *. Well, mobile nodes exchange
 * coordinates of static nodes. This app, on receiving such a coordinate, sets
 * it as the next waypoint in the RWP model.
 *
 * Category 3: This app enforces a mobile node to visit a newly informed static,
 * as well as its 1-near neighbourhood, only once.
 *
 * Does * NOT * share info with other nodes -- only local actions
 *
 * Exceptions: - If a static node has been already visited by a mobile node, the
 * app cant force it to visit the node again. It can, however, visit the static
 * node on * its will * (as RWP dictates it).
 *
 * This module is a part of implementation of the protocol described in Section
 * 4.4, Chapter 4, #TheOMNBook.
 *
 * @author barun
 */
public class HumanIntelligenceApplication3 extends Application {

    /**
     * Address range of mobile nodes -- must be contiguous (excludes upper
     * limit)
     */
    public static final String HIAPP_NS = "humanIntApp";
    public static final String MOBILE_ADDRESS_RANGE_S = "mobileNodesAddr";
    /**
     * Indicates which level of intelligence is used
     */
    public static final String INTELLIGENCE_LEVEL_S = "intlLevel";
    public static final String K_FACTOR_S = "kFactor";
    /**
     * Application ID
     */
    public static final String APP_ID = "HumanIntelligenceApp3";
    /**
     * DB ID
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
    /** Current intelligence level */
    private int intlLevel;
    private static int kFactor = 1;
    //private DTNHost myself;

    /**
     * Creates a new HumanIntelligenceApplication3 with the given settings.
     *
     * @param s	Settings to use for initializing the application.
     */
    public HumanIntelligenceApplication3(Settings s) {
        int[] addrRange = s.getCsvInts(MOBILE_ADDRESS_RANGE_S, 2);
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
     * @param a An existing HumanIntelligenceApplication
     */
    public HumanIntelligenceApplication3(HumanIntelligenceApplication3 a) {
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
     * @param msg Message received by the router
     * @param host Host to which the application instance is attached
     */
    @Override
    public Message handle(Message msg, DTNHost host) {
        // NOTE: We don't need to check the destination of the message -- 
        // the information could be used by all mobile nodes
        //if (msg.getTo() != host)
        //   return msg;

        /*
         * NOTHING to be done here
         *
         //System.out.println(" + " + SimClock.getIntTime() + ": " + host + " " + msg.getId() + ", " + msg.getAppID() + ", " + msg.getFrom() + ", " + msg.getTo());

         // Make the database available to other modules
         ModuleCommunicationBus comBus = host.getComBus();		
		
         // I've received a new location from someone -- let me check if I already have that
         Coord newLocation = (Coord) msg.getProperty(NEW_LOC);
         boolean isExisting = false;
		
         //System.out.println(host + ": " + newLocation + " / " + this.database.entrySet());
         for (Map.Entry<Coord, Boolean> entry : this.database.entrySet()) {	
         //System.out.println(host + ": " + newLocation + " == " + entry.getKey() + " => " + newLocation.equals(entry.getKey()));
         if (newLocation.equals(entry.getKey())) {
         isExisting = true;
         break;
         }
         }

         if (! isExisting) {		    
         System.out.println("> " + msg.getTo() + " " + this.database + " received new loc " + newLocation + " from " + msg.getFrom());
         this.database.put(newLocation, false);
         //System.out.println(" => " + this.database);          
                                    
         }
         */

        return msg;
    }

    @Override
    public Application replicate() {
        return new HumanIntelligenceApplication3(this);
    }

    /**
     * Sends a ping packet if this is an active application instance.
     *
     * @param host to which the application instance is attached
     */
    @Override
    public void update(DTNHost host) {
        double curTime = SimClock.getTime();
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

                // Cool! Now inform MY mobility model that we have to go
                // to some neighbouring locations (NOT this location again)

                ArrayList<Coord> newLocations = new ArrayList<Coord>();
                //newLocations.add(newLocation);  // This location is always added

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
                        //System.out.print(" *** " + zoneID[0] + "," + zoneID[1] + " ~ ");

                        for (int[] neighbour : neighbours) {
                            Coord cx = ZoneHelper.getZoneCenter(neighbour);
                            newLocations.add(cx);
                            //System.out.print(neighbour[0] + "," + neighbour[1] + " ");
                        }
                        //System.out.println("");
                        break;

                    case INTL_L4:
                        // This app not only asks the node to visit a static location, 
                        // but also its 1-neighbourhood                    
                        neighbours = ZoneHelper.getNeighbouringZones(zoneID, 1);
                        //System.out.print(" *** " + zoneID[0] + "," + zoneID[1] + " ~ ");

                        for (int[] neighbour : neighbours) {
                            Coord cx = ZoneHelper.getZoneCenter(neighbour);
                            newLocations.add(cx);
                            //System.out.print(neighbour[0] + "," + neighbour[1] + " ");
                        }
                        //System.out.println("");
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

            /*
             * NO sharing of info with other nodes -- only local actions
             *
             if (sendMessage) {
             if (! this.database.containsKey(newLocation)) {
             this.database.put(newLocation, new Boolean(false));
             //System.out.println("Database updated: " + this.database);

             // Send message to all mobile nodes -- except myself, of course
             World w = SimScenario.getInstance().getWorld();
				
             for (int i = this.minAddr; i <= this.maxAddr; i++) {
             if (i == host.getAddress())
             continue;
                        
             Message m = new Message(host, w.getNodeByAddress(i), msgType +
             SimClock.getIntTime() + "-" + host.toString(),
             1);
             m.addProperty("type", "scan");        
             //m.addProperty("database", this.database);            
             m.updateProperty(NEW_LOC, newLocation);
             m.setAppID(APP_ID);
             host.createNewMessage(m);
             //System.out.println("HumanIntelligenceApplication3: update: msg created: " + m.getFrom() + " " + this.database + " sending " + newLocation + " -> " + m.getTo());  	        

             // Call listeners
             super.sendEventToListeners("SearchLocation", null, host);
             }                    
             }                                
             }
             */
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
