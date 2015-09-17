/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */

package applications;

import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Random;

import movement.MovementModel;

import core.Application;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.SimScenario;
import core.World;
import core.Coord;
import core.DTNSim;
import routing.FloatingContentRouter;

/**
 * Simple floating application to demonstrate the application support. The 
 * application can be configured to send floatings with a fixed interval or to only
 * answer to floatings it receives. When the application receives a floating it sends
 * a pong message in response.
 * 
 * The corresponding <code>FloatingAppReporter</code> class can be used to record
 * information about the application behavior.
 * 
 * @see FloatingAppReporter
 * @author jo
 */
public class FloatingApplication extends Application {
    /** Destination address */
    public static final String FLOATING_DESTINATION = "destination";
    /** Seed for the app's random number generator */
    public static final String FLOATING_SIZE = "messageSize";
    /** Seed for the app's random number generator */
    public static final String FLOATING_START = "startTime";
    /** Seed for the app's random number generator */
    public static final String FLOATING_SEED = "seed";
    /** (minimal) interval between two floating messages per node */
    public static final String FLOATING_INTERVAL = "interval";
    /** anchor zone for the message */
    public static final String FLOATING_ANCHOR = "anchor";
    public static final String FLOATING_ANCHOR_MAX = "anchorMax";
    public static final String FLOATING_ANCHOR_GRAN = "anchorGranularity";
    /** anchor zone for the message */
    public static final String FLOATING_TTL = "ttl";
    /** factor determining how deep a floating message generator needs to be inside the anchor zone */
    public static final String FLOATING_IN_ANCHOR_ZONE_FACTOR = "inAnchorZoneFactor";
    /** shall node enter/leave events for an anchor zone be collected? */
    public static final String FLOATING_FLUX = "flux";
    /** mode selection */
    public static final String FLOATING_MODE = "mode";
    public static final int MODE_FIXED_ANCHOR = 0;
    public static final int MODE_VARIABLE_ANCHOR = 1;
    
    private static  LinkedList<Message> msgs = new LinkedList<Message> ();
    
    private double  lastMessage   = 0;
    private double  interval      = 500;
    private double  ttl           = 3600;
    private double  ttl_max       = ttl;
    private double  ttl_gran      = ttl;
    private int	    seed          = 0;
    private int	    destination   = 0;
    private double  start         = 0.0;
    private int	    msgSize	  = 1;
    private int     msgSizeMax    = msgSize;
    private int     msgSizeGran   = msgSize;
    private Coord   anchor        = new Coord (0,0);
    
    private Coord   anchor_max;
    private Coord   anchor_gran   = new Coord (1, 1);
    private double  anchor_r      = 200;
    private double  anchor_r_max  = anchor_r;
    private double  anchor_r_gran = 1;
    private double  anchor_a      = 0;         /* implies: use a = r! */
    private double  anchor_a_max  = anchor_a;
    private double  anchor_a_gran =1;
    private double  in_anchor_zone_factor = 0.5;
    private Random  rng;
    private Random  rng_ttl;
    private Random  rng_size;
    private Random  rng_a;
    private Random  rng_r;
    private int     mode        = MODE_FIXED_ANCHOR;

    private double  anchor_r2   = anchor_r * anchor_r;
    private boolean in_anchor_zone = false;   
    private boolean flux        = false;

    private static int msg_seq_no = 0;

    
    /** Application ID */
    public static final String APP_ID = "fi.tkk.netlab.FloatingApplication";
    
	/**
	 * Creates a new floating application with the given settings.
	 * 
	 * @param s Settings to use for initializing the application.
	 */
	public FloatingApplication(Settings s) {
		Settings mSettings = new Settings(MovementModel.MOVEMENT_MODEL_NS);
		int worldSize[] = mSettings.getCsvInts(MovementModel.WORLD_SIZE, 2);
		this.anchor_max = new Coord(worldSize[0], worldSize[1]);

		/* random seed initialization */
		if (s.contains(FLOATING_SEED)) {
			seed = s.getInt(FLOATING_SEED);
		}
		/*
		 * at which point in time do we start generating floating content
		 * messages
		 */
		if (s.contains(FLOATING_START)) {
			start = s.getDouble(FLOATING_START);
		}
		/* in which intervals are new messages generated on average */
		if (s.contains(FLOATING_INTERVAL)) {
			interval = s.getDouble(FLOATING_INTERVAL);
		}
		/* define the TTL of our floating messages */
		if (s.contains(FLOATING_TTL)) {
			int[] ttl_array = s.getCsvInts(FLOATING_TTL);
			ttl = (double) ttl_array[0];
			ttl_gran = 1;
			if (ttl_array.length == 1) {
				ttl_max = ttl_array[0];
			} else {
				ttl_max = ttl_array[1];
				if (ttl_array.length > 2)
					ttl_gran = ttl_array[2];
			}
		}
		/* define the size of the floating messages */
		if (s.contains(FLOATING_SIZE)) {
			int[] size = s.getCsvInts(FLOATING_SIZE);
			msgSize = size[0];
			msgSizeGran = 1;
			if (size.length == 1) {
				msgSizeMax = size[0];
				msgSizeGran = 0;
			} else {
				msgSizeMax = size[1];
				if (size.length > 2)
					msgSizeGran = size[2];
			}
		}
		/*
		 * messages need to have an unreachable destination or they would get
		 * delivered
		 */
		if (s.contains(FLOATING_DESTINATION)) {
			destination = s.getInt(FLOATING_DESTINATION);
		}
		/*
		 * configure the anchor zone: min, max positions, r, a, and the
		 * intervals
		 */
		if (s.contains(FLOATING_ANCHOR)) {
			int[] destination = s.getCsvInts(FLOATING_ANCHOR, 4);
			anchor.setLocation(destination[0], destination[1]);
			anchor_r = destination[2];
			anchor_a = destination[3];
			anchor_r2 = anchor_r * anchor_r;
		}
		if (s.contains(FLOATING_ANCHOR_MAX)) {
			int[] destination = s.getCsvInts(FLOATING_ANCHOR_MAX, 4);
			anchor_max.setLocation(destination[0], destination[1]);
			anchor_r_max = destination[2] > anchor_r ? destination[2]
			        : anchor_r;
			anchor_a_max = destination[3] > anchor_a ? destination[3]
			        : anchor_a;
		}
		if (s.contains(FLOATING_ANCHOR_GRAN)) {
			int[] destination = s.getCsvInts(FLOATING_ANCHOR_GRAN, 4);
			anchor_gran.setLocation(destination[0], destination[1]);
			anchor_r_gran = destination[2];
			anchor_a_gran = destination[3];
		}
		/*
		 * for fixed anchor points, how close does a node have to be to the
		 * center
		 */
		if (s.contains(FLOATING_IN_ANCHOR_ZONE_FACTOR)) {
			in_anchor_zone_factor = s.getDouble(FLOATING_IN_ANCHOR_ZONE_FACTOR);
		}
		/*
		 * determining the flux of nodes into/out of an anchor zone; not used in
		 * regular operation
		 */
		if (s.contains(FLOATING_FLUX)) {
			flux = s.getBoolean(FLOATING_FLUX);
		}
		/* do we use one fixed anchor positions or random ones */
		if (s.contains(FLOATING_MODE)) {
			if (s.getSetting(FLOATING_MODE).equals("fixed"))
				mode = MODE_FIXED_ANCHOR;
			else if (s.getSetting(FLOATING_MODE).equals("variable")) {
				mode = MODE_VARIABLE_ANCHOR;
			} else
				throw new core.SettingsError("Unknown mode: " + 
						s.getSetting(FLOATING_MODE));		
		}
		/* Some sanity checks to make simulations run smoothly */
		if (anchor_r_max < anchor_r)
			anchor_r_max = anchor_r;
		if (anchor_a_max < anchor_a)
			anchor_a_max = anchor_a;

		if (anchor_max.getX() - anchor.getX() < anchor_gran.getX())
			anchor_gran.setLocation(anchor_max.getX() - anchor.getX(),
			        anchor_gran.getY());
		if (anchor_max.getY() - anchor.getY() < anchor_gran.getY())
			anchor_gran.setLocation(anchor_gran.getX(), anchor_max.getY()
			        - anchor.getY());

		if (anchor_r_max - anchor_r < anchor_r_gran)
			anchor_r_gran = anchor_r_max - anchor_r;
		if (anchor_r_gran == 0)
			anchor_r_gran = 1;
		if (anchor_a_max - anchor_a < anchor_a_gran)
			anchor_a_gran = anchor_a_max - anchor_a;
		if (anchor_a_gran == 0)
			anchor_a_gran = 1;

		rng = null;
		super.setAppID(APP_ID);

	}
    
    /** 
     * Copy-constructor
     * 
     * @param a
     */
    public FloatingApplication(FloatingApplication a) {
	super(a);
	lastMessage = a.getLastMessage();
	interval = a.getInterval();
	destination = a.getDestination();
	seed = a.getSeed();
	start = a.getStartTime();

	msgSize = a.getMessageSize();
	msgSizeMax = a.getMessageSizeMax();
	msgSizeGran = a.getMessageSizeGran();

	anchor = a.getAnchor();
	anchor_max = a.getAnchorMax();
	anchor_gran = a.getAnchorGran();

	anchor_r = a.getR();
	anchor_r_max = a.getRMax();
	anchor_r_gran = a.getRGran();
	anchor_r2 = anchor_r * anchor_r;

	anchor_a = a.getA();
	anchor_a_max = a.getAMax();
	anchor_a_gran = a.getAGran ();

	ttl = a.getTtl();
	ttl_max = a.getTtlMax();
	ttl_gran = a.getTtlGran();

	in_anchor_zone_factor = a.getInAnchorZoneFactor();
	in_anchor_zone = a.getInAnchorZone();
	flux = a.getFlux();
	rng      = null;
	rng_ttl  = null;
	rng_size = null;
	rng_a    = null;
	rng_r    = null;
	mode = a.getMode();
    }
    
    /** 
     * Handles an incoming message.  Nothing done in this basic case where messages are only posted,
     * but not replied to.  This may serve as a hook to create more complex application logic.
     * 
     * @param msg	message received by the router
     * @param host	host to which the application instance is attached
     */
    @Override
	public Message handle(Message msg, DTNHost host) {
	String type = (String)msg.getProperty("type");
	if (type==null || !type.equalsIgnoreCase("floating")) return msg; // Not a floating message
	
	return msg;
    }
    
    /** 
     * Return the target host (a 'virtual' host) for the floating message
     * This should be a host somewhere at an unreachable position
     * 
     * @return host
     */
    private DTNHost destinationHost() {
	World w = SimScenario.getInstance().getWorld();
	return w.getNodeByAddress(destination);
    }
    
    @Override
	public Application replicate() {
	return new FloatingApplication(this);
    }
    
    /** 
     * Sends floating messages according to the parameterization provided in the config file.
     * 
     * @param host to which the application instance is attached
     */
    @Override
	public void update(DTNHost host) {
	double curTime = SimClock.getTime();
	Coord  location;
	double distance;
	String node_plus_anchor;
	FloatingContentRouter fcr = (FloatingContentRouter) (host.getRouter());


	if (rng == null) {
	    rng      = new Random (host.getAddress()*10000+seed);
	    rng_ttl  = new Random (host.getAddress()*20000+seed);
	    rng_size = new Random (host.getAddress()*30000+seed);
	    rng_a    = new Random (host.getAddress()*40000+seed);
	    rng_r    = new Random (host.getAddress()*50000+seed);

	    /* this is the first time update is called -> dither the start time for the initial message */
	    lastMessage = start - (rng.nextDouble ()) * interval;
	}

	// Check location for the node flux and density calculation
	// Note: Here we use the real location, not the perceived one
	if (flux == true) {
	    if (mode == MODE_FIXED_ANCHOR) {
		location = host.getLocation();
		if (location.distance2 (anchor) > anchor_r2) {
		    if (in_anchor_zone) {
			node_plus_anchor = anchor + " " + location;
			super.sendEventToListeners ("leave", node_plus_anchor, host);
		    }
		    in_anchor_zone = false;
		} else {
		    if (!in_anchor_zone) {
			node_plus_anchor = anchor + " " + location;
			super.sendEventToListeners ("enter", node_plus_anchor, host);
		    }
		    in_anchor_zone = true;
		}
	    } else {
		ListIterator i = FloatingApplication.msgs.listIterator ();
		Message      m, m_del = null;
		double       anchor_r2;
		Coord        anchor;
		double       ttl;

		while (i.hasNext()) {
		    m          = (Message) i.next ();
		    anchor     = (Coord) m.getProperty ("anchor");
		    anchor_r2  = (Double) m.getProperty ("r");
		    anchor_r2 *= anchor_r2;
		    ttl        = (Double) m.getProperty ("ttl");

		    location = host.getLocation();
		    if (curTime < ttl) {
			if (location.distance2 (anchor) <= anchor_r2) {
			    node_plus_anchor = m.toString () + " " + anchor + " " + location;
			    super.sendEventToListeners ("in", node_plus_anchor, host);
			}
		    } else {
			if (m_del == null)
			    m_del = m;
		    }
		}
		if (m_del != null)
		    FloatingApplication.msgs.remove (m_del);
	    }
	}

	switch (mode) {
	case MODE_FIXED_ANCHOR:
	    // Note: Also here we use the real location and not the perceived one
	    if (curTime - lastMessage >= interval) {
		location = host.getLocation();
		distance = location.distance (anchor);
		
		if (distance < anchor_r * in_anchor_zone_factor) {
		    // Now we are allowed to create a new floating message
		    Message m = new Message(host, destinationHost(), "floating" + msg_seq_no++ + "-" +
					    SimClock.getIntTime() + "-" + host.getAddress(),
					    getMessageSize());
		    m.addProperty("type", "floating");
		    m.addProperty("anchor", anchor.clone());
		    m.addProperty("r", getR());
		    m.addProperty("a", getA());
		    m.addProperty("ttl", SimClock.getTime() + ttl);
		    m.addProperty("ttlval", ttl);
		    m.setAppID(APP_ID);
		    host.createNewMessage(m);
		    
		    lastMessage = curTime + (rng.nextDouble () - 0.5) * interval/2;
		    m.setTtl ((int) (ttl));
		}
	    }
	    break;
	    
	case MODE_VARIABLE_ANCHOR:
	    /* reinterpret the anchor value:
	       Use r as the minimal replication range.
	       Use anchor as the middle point +/- a into X and Y direction defines the area in which messages are generated
	    */
	    /* ttl: ttl, .., ttl_max in ttl_gran steps */
	    double   msgttl = (ttl_max == ttl) ? ttl : (rng_ttl.nextInt (((int) ttl_max - (int) ttl) / (int) ttl_gran + 1)) * ttl_gran + ttl;
	    
	    /* anchors: a/r, ..., a_max/r_max in a_gran/r_gran steps*/
	    double   r      = (anchor_r_max == anchor_r) ? anchor_r : (rng_r.nextInt (((int) anchor_r_max - (int) anchor_r) / (int) anchor_r_gran + 1)) * anchor_r_gran + anchor_r;
	    double   a      = r;  /* default behavior: no buffer zone */
	    double   anchor_a_min = 0;

	    /* size: 100, 200, ..., 1000 KB */
	    int      msgsize= (msgSizeMax == msgSize) ? msgSize : (rng_size.nextInt ((msgSizeMax - msgSize) / msgSizeGran + 1)) * msgSizeGran + msgSize;

	    if (anchor_a > 0) {
		if (anchor_a == anchor_a_max || anchor_a_max == 0) {
		    if (anchor_a > r)
			a = anchor_a;
		} else {
		    /* pick a random value between MAX (r, anchor_a) and anchor_a_max */
		    anchor_a_min = (r > anchor_a) ? r : anchor_a;
		    a = (rng_a.nextInt (((int) anchor_a_max - (int) anchor_a_min) / (int) anchor_a_gran + 1)) * anchor_a_gran + anchor_a_min;
		    if (a < r)
			a = r;
		}
	    }

	    if (curTime - lastMessage >= interval &&
		(fcr.getLocationSource() == FloatingContentRouter.LOC_SRC_GPS || fcr.getLastKnownLocation() != null)) {
		/* 
		 * "Snap to grid" yet to be implemented.  This would use the anchor_gran values for x and y
		 * and make the message snap to the closest coordinate.
		 *
		 * Again, for this check, we use the real location and not the perceived one.
		 * The perceived location will be inserted into the floating content message inside
		 * the FloatingContentRouter class, so there is nothing to do here.  The only exception is the
		 * anchor point.
		 */
		if (host.getLocation ().getX () >= anchor.getX () &&
		    host.getLocation ().getX () <= anchor_max.getX () &&
		    host.getLocation ().getY () >= anchor.getY () &&
		    host.getLocation ().getY () <= anchor_max.getY ()) {

		    Message m = new Message(host, destinationHost(), "floating" + msg_seq_no++ + "-" +
					    SimClock.getIntTime() + "-" + host.getAddress(),
					    msgsize);
		    m.addProperty("type", "floating");
		    m.addProperty("r", r);
		    m.addProperty("a", a);
		    m.addProperty("ttl", SimClock.getTime() + msgttl);
		    m.addProperty("ttlval", msgttl);
		    m.setTtl ((int) (msgttl));  /* need to redo this after host.createNewMessage () */
		    m.setAppID(APP_ID);

		    if (fcr.getLocationSource() == FloatingContentRouter.LOC_SRC_GPS)
			m.addProperty("anchor", fcr.getLocation().clone());
		    else
			m.addProperty("anchor", fcr.getLastKnownLocation().clone());
		    host.createNewMessage(m);
		    /* ttl needs to be set after message creation */
		    m.setTtl ((int) (msgttl));
		    
		    if (flux)
			FloatingApplication.msgs.add (m);
		    lastMessage = curTime + (rng.nextDouble () - 0.5) * interval/2;
		}
	    }	    
	    break;

	default:
	    System.out.print ("Unknown mode for FloatingApplication");
	}	    
    }
    
    /**
     * @return the lastFloating
     */
    public double getLastMessage() {
	return lastMessage;
    }
    
    /**
     * @param lastFloating the lastFloating to set
     */
    public void setLastMessage(double lastMessage) {
      this.lastMessage = lastMessage;
    }
    
    /**
     * @return the start time
     */
    public double getStartTime() {
      return start;
    }
    
    /**
     * @params the startTime to set
     */
    public void setStartTime(double start) {
      this.start = start;
    }
    
    /**
     * @return the interval
     */
    public double getInterval() {
	return interval;
    }
    
    /**
     * @param ttl the ttl to set
     */
    public void setTtl(double ttl) {
	this.ttl = ttl;
    }

     /**
     * @return the ttl
     */
    public double getTtl() {
	return ttl;
    }
    
     /**
     * @return the ttl_max
     */
    public double getTtlMax() {
	return ttl_max;
    }
    
     /**
     * @return the ttl_gran
     */
    public double getTtlGran() {
	return ttl_gran;
    }
    
    /**
     * @param interval the interval to set
     */
    public void setInterval(double interval) {
	this.interval = interval;
    }

    /**
     * @return the destination
     */
    public int getDestination() {
	return destination;
    }
    
    /**
     * @param destMin the destMin to set
	 */
    public void setDestination(int destination) {
	this.destination = destination;
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
    
    
    /**
     */
    public int getMessageSize() {
	return msgSize;
    }
    
    /**
     */
    public int getMessageSizeMax() {
	return msgSizeMax;
    }
    
    /**
     */
    public int getMessageSizeGran() {
	return msgSizeGran;
    }
    
    /**
     * @param messageSize the messageSize to set
     */
    public void setMessageSize(int messageSize) {
	this.msgSize = messageSize;
    }
    
    /**
     * @return the anchor coordinates
     */
    public Coord getAnchor() {
	return anchor;
    }
    
    /**
     * @return the anchor max coordinates
     */
    public Coord getAnchorMax() {
	return anchor_max;
    }
    
    /**
     * @return the anchorcoordinate granularity 
     */
    public Coord getAnchorGran() {
	return anchor_gran;
    }
    
    /**
     * @return the anchor zone radius
     */
    public double getR() {
	return anchor_r;
    }

    /**
     * @return the max anchor zone radius
     */
    public double getRMax() {
	return anchor_r_max;
    }

    /**
     * @return the anchor zone radius granularity
     */
    public double getRGran() {
	return anchor_r_gran;
    }

    /**
     * @return the anchor zone radius square
     */
    public double getR2() {
	return anchor_r2;
    }
    
    /**
     * @return the availability distance
     */
    public double getA() {
	return anchor_a;
    }
    
    /**
     * @return the max availability distance
     */
    public double getAMax() {
	return anchor_a_max;
    }
    
    /**
     * @return the availability distance granularity
     */
    public double getAGran() {
	return anchor_a_gran;
    }
    
    /**
     * @param the anchor coords
     */
    public void setAnchor(Coord c) {
	this.anchor.setLocation (c.getX(), c.getY());
    }
    
    /**
     * @param the anchor X coord
     */
    public void setX(double x) {
	this.anchor.setLocation (x, this.anchor.getY());
    }
    
    /**
     * @param the anchor Y coord
     */
    public void setY(double y) {
	this.anchor.setLocation (this.anchor.getX(), y);
    }
    
    /**
     * @param the anchor zone radius
     */
    public void setR(int r) {
	this.anchor_r = r;
	this.anchor_r2 = this.anchor_r * this.anchor_r;
    }
    
    /**
     * @param the availability distance
     */
    public void setA(int a) {
	this.anchor_a = a;
    }
    
    /**
     * @return the in-anchor-zone factor
     */
    public double getInAnchorZoneFactor() {
	return in_anchor_zone_factor;
    }
    
    /**
     * @param the in-anchor-zone factor to set
     */
    public void setInAnchorZoneFactor(double factor) {
	this.in_anchor_zone_factor = factor;
    }

    public boolean getInAnchorZone() {
	return in_anchor_zone;
    }

    public boolean getFlux() {
	return flux;
    }

    public int getMode() {
	return mode;
    }

    static {
	DTNSim.registerForReset(FloatingApplication.class.getCanonicalName());
	reset();
    }

    public static void reset() {
	/* do your reset'ing here */
	msgs.clear();
    }
}
