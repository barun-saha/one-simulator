/*
 * Copyright 2015, 2016 Barun Saha (http://barunsaha.me)
 *
 * Distributed under the original license of the ONE simulator.
 */
package movement;

import core.Coord;
import core.Settings;

/**
 * Restricted random waypoint movement model. Creates zig-zag paths within the
 * simulation area. New destinations lie outside a rectangular bounding box
 * placed inside the world.
 * 
 * Solution to Exercise 4.10, Chapter 4, #TheOMNBook. Note that the coordinates
 * of the obstacle zone are explicitly mentioned here.
 *
 * @author: barun
 */
public class RestrictedRandomWaypoint extends MovementModel {

    /**
     * How many waypoints should there be per path
     */
    private static final int PATH_LENGTH = 1;
    private Coord lastWaypoint;
    /**
     * Rectangular bounding box defining the restricted region
     */
    private double leftBottom[];
    private double rightTop[];
    public static final String LEFT_BOTTOM = "leftBottom";
    public static final String RIGHT_TOP = "rightTop";

    public RestrictedRandomWaypoint(Settings settings) {
        super(settings);

        double[] leftBottom = settings.getCsvDoubles(LEFT_BOTTOM, 2);
        double[] rightTop = settings.getCsvDoubles(RIGHT_TOP, 2);
        this.leftBottom = leftBottom;       // (x, y)
        this.rightTop = rightTop;
    }

    protected RestrictedRandomWaypoint(RestrictedRandomWaypoint rwp) {
        super(rwp);

        this.leftBottom = rwp.leftBottom;
        this.rightTop = rwp.rightTop;
    }

    /**
     * Returns a possible (random) placement for a host
     *
     * @return Random position on the map
     */
    @Override
    public Coord getInitialLocation() {
        assert rng != null : "MovementModel not initialized!";
        double x = rng.nextDouble() * this.leftBottom[0];
        double y = rng.nextDouble() * this.leftBottom[1];
        Coord c = new Coord(x, y);  //randomCoord();

        this.lastWaypoint = c;
        return c;
    }

    @Override
    public Path getPath() {
        Path p;
        p = new Path(generateSpeed());
        p.addWaypoint(lastWaypoint.clone());
        Coord c = lastWaypoint;

        for (int i = 0; i < PATH_LENGTH; i++) {
            //c = randomCoord();
            c = restrictedCoord();
            p.addWaypoint(c);
        }

        this.lastWaypoint = c;
        return p;
    }

    @Override
    public RestrictedRandomWaypoint replicate() {
        return new RestrictedRandomWaypoint(this);
    }

    /* TODO: Take the top and bottom bounded regions into consideration
     */
    protected Coord restrictedCoord() {
        Coord destination;

        /* There are 4 possible overlapping rectangular region along the 
         boundaries of the world
          _______________________________________
         |___|_______________________________|___|
         |   |                               |   |
         |   |                               |   |
         |   |                               |   |
         |___|_______________________________|___|
         |___|_______________________________|___|

         */

        // Iterate until a valid destination is found
        while (true) {
            double x = rng.nextDouble() * getMaxX();
            double y = rng.nextDouble() * getMaxY();

            System.err.println("" + x + "(<" + this.leftBottom[0] + ", >" 
                    + this.rightTop[0] + "), " + y 
                    + "(<" + this.leftBottom[1] + ", >" 
                    + this.rightTop[1] + ")");

            if (x > 0 && y > 0 && x < getMaxX() && y < getMaxY()) {
                // Note: To use assertions, you must enable it during execution
                assert (x > this.leftBottom[0]
                        && x < this.rightTop[0]
                        && y > this.leftBottom[1]
                        && y < this.rightTop[1]) : "Invalid";

                if (x > this.leftBottom[0]
                        && x < this.rightTop[0]
                        && y > this.leftBottom[1]
                        && y < this.rightTop[1]) {
                    continue;
                }

                destination = new Coord(x, y);
                break;
            }
        }

        return destination;
    }
}