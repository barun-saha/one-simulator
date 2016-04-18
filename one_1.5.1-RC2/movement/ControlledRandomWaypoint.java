/*
 * Copyright 2015, 2016 Barun Saha (http://barunsaha.me)
 *
 * Distributed under the GNU GPL v3 license used by the ONE simulator.
 */
package movement;

import applications.HumanIntelligenceApplication;
import core.ConnectionListener;
import core.Coord;
import core.DTNHost;
import core.ModuleCommunicationBus;
import core.Settings;
import java.util.ArrayList;

/**
 * This essentially is the RWP mobility model except when specific destination
 * coordinates are provided by the {@link applications.HumanIntelligenceApplication}.
 * In this latter case, a node moves to such pre-defined coordinates, which is
 * why this module is said to be "controlled" RWP.
 * 
 * This module is a part of implementation of the protocol described in 
 * Section 4.4, Chapter 4, #TheOMNBook.
 *
 * @author barun
 */
public class ControlledRandomWaypoint extends RandomWaypoint 
    implements ConnectionListener {

    /**
     * how many waypoints should there be per path
     */
    private static final int PATH_LENGTH = 1;
    private Coord lastWaypoint;
    public static final String MOVEMENT_LOC = HumanIntelligenceApplication.MOVEMENT_LOC;

    public ControlledRandomWaypoint(Settings settings) {
        super(settings);
    }

    protected ControlledRandomWaypoint(ControlledRandomWaypoint rwp) {
        super(rwp);
    }

    /**
     * Returns a possible (random) placement for a host
     *
     * @return Random position on the map
     */
    @Override
    public Coord getInitialLocation() {
        assert rng != null : "MovementModel not initialized!";
        Coord c = randomCoord();
        //System.out.println("Initial location: " + c);

        this.lastWaypoint = c;
        return c;
    }

    @Override
    public Path getPath() {
        Path p;
        p = new Path(generateSpeed());
        p.addWaypoint(lastWaypoint.clone());
        Coord c = lastWaypoint;
        ModuleCommunicationBus comBus = getComBus();
        boolean isNormalRwp = true;

        if (comBus.containsProperty(MOVEMENT_LOC)) {
            ArrayList<Coord> newLocations = (ArrayList<Coord>) comBus.getProperty(MOVEMENT_LOC);
            if (newLocations != null) {
                for (Coord nextCoord : newLocations) {
                    p.addWaypoint(nextCoord);
                    c = nextCoord;
                }
                comBus.updateProperty(MOVEMENT_LOC, null);
                isNormalRwp = false;
                //System.out.println(" Path of " + getHost() + ": " + p);
            }
        }

        if (isNormalRwp) {
            for (int i = 0; i < PATH_LENGTH; i++) {
                c = randomCoord();
                // For testing			    
                p.addWaypoint(c);
                //p.addWaypoint(new Coord(0.0, 0.0));
            }
        }

        this.lastWaypoint = c;
        return p;
    }

    @Override
    public ControlledRandomWaypoint replicate() {
        return new ControlledRandomWaypoint(this);
    }

    @Override
    protected Coord randomCoord() {
        return new Coord(rng.nextDouble() * getMaxX(),
                rng.nextDouble() * getMaxY());
    }

    @Override
    public void hostsConnected(DTNHost host1, DTNHost host2) {
    }

    @Override
    public void hostsDisconnected(DTNHost host1, DTNHost host2) {
    }
}
