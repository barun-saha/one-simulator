/*
 * Copyright 2012 Barun Saha, barun.saha04@gmail.com
 * Released under GPLv3. See LICENSE.txt for details.
 */

package movement;

import core.Coord;
import core.DTNSim;
import core.Settings;
import java.util.HashSet;

/**
 *
 * @author barun
 */
public class StaticNodesDistanceSeparated extends MovementModel {

    /** The location of the nodes */
    private Coord location;

    /** No. of nodes using this model */
    private static int nHosts;
    /** Min distance of separation among the nodes */
    private static final double minDistance = 600;
    /** Max attempts to place each node */
    private static final int maxAttempts = Integer.MAX_VALUE / 4;

    private static HashSet<Coord> initialLocations;
    /** List of all the node locations */
    private static Coord[] allLocations;
    /** No. of hosts successfully placed */
    private static int nSuccess = 0;

    private static final String HOSTS_S = "nHosts";
    private static final String NAMESPACE = "StaticNodesDistanceSeparated";

    static {
		DTNSim.registerForReset(StaticNodesDistanceSeparated
                .class.getCanonicalName());
		reset();
	}

    public static void reset() {
        nSuccess = 0;
        allLocations = null;
        initialLocations = null;
    }


    public StaticNodesDistanceSeparated(Settings settings) {
        super(settings);
        Settings s = new Settings(NAMESPACE);
        nHosts = s.getInt(HOSTS_S);
        allLocations = new Coord[nHosts];

        initialLocations = new HashSet<Coord>();
        generateLocations();
    }

    public StaticNodesDistanceSeparated(MovementModel mm) {
        super(mm);
    }

    private void generateLocations() {
        int curHosts = 0;
        /* To overcome the pitfall that curAttempts could get reinitialized
         * to 0, and, thereby, entering infinite loop.
         */
        int absoluteAttemptsCount = 0;

        while (curHosts < nHosts) {
            Coord c = randomCoord();
            int curAttempts = 0;
            boolean isSuccessfull = false;

            while (curAttempts < maxAttempts) {
                c = randomCoord();
                curAttempts += 1;
                absoluteAttemptsCount += 1;

                if (absoluteAttemptsCount == maxAttempts) {
                    String msg = "Exceeded the limit on absolute attempts" +
                            " to generate the locations!";
                    //throw new RuntimeException(msg);
                    throw new Error(msg);
                }

                boolean cancelCurrentAttempt = false;

                if (initialLocations.isEmpty()) {
                    initialLocations.add(c);
                    curHosts += 1;
                    continue;
                }

                // A futile attempt to introduce some bias
                if ((curAttempts % 1000) == 0) {
                    //System.out.println(curAttempts);
                    c = new Coord(0, c.getX());
                    initialLocations.clear();
                    curHosts = 0;
                    continue;
                }

                for (Coord loc : initialLocations) {
//                System.out.println(i + " " + nSuccess);
                    double distance = c.distance(loc);
                    if (distance < minDistance) {
                        // Failed for a single location, no use to check
                        // the other locations
                        cancelCurrentAttempt = true;
                        break;
                    }
                }

                if (cancelCurrentAttempt) {
                    continue;
                }

                isSuccessfull = true;
                break;
            }

            if ((curAttempts == maxAttempts) && ! isSuccessfull) {
                String msg = "After " + maxAttempts + " attempts, only " +
                        nSuccess + " stationary nodes could be placed!";
                //throw new RuntimeException(msg);
                throw new Error(msg);
            }

            initialLocations.add(c);
            curHosts += 1;
        }

        //System.out.println("StaticNodesDistanceSeparated: " + initialLocations);

        for (Coord loc : initialLocations) {
            for (Coord loc2 : initialLocations) {
                double distance = loc.distance(loc2);
                if (!loc.equals(loc2)) {
                    assert distance >= minDistance :
                            "Min distance not saisfied " +
                            "for " + loc + " " + loc2;
                }
            }

        }
    }

    @Override
    public Coord getInitialLocation() {
        Coord c = null;
        for (Coord loc : initialLocations) {
            c = loc;
            break;
        }
        initialLocations.remove(c);
        allLocations[nSuccess] = c;
        nSuccess += 1;

        return c;
    }

    /**
	 * Returns a single coordinate path (using the only possible coordinate)
	 * @return a single coordinate path
	 */
	@Override
	public Path getPath() {
		Path p = new Path(0);
		p.addWaypoint(this.location);
		return p;
	}

	@Override
	public double nextPathAvailable() {
		return Double.MAX_VALUE;	// no new paths available
	}

    @Override
    public MovementModel replicate() {
        return new StaticNodesDistanceSeparated(this);
    }

    protected Coord randomCoord() {
		return new Coord(rng.nextDouble() * getMaxX(),
				rng.nextDouble() * getMaxY());
	}

    public static Coord[] getLocations() {
        return allLocations;
    }
}
