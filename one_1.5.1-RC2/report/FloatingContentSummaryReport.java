/* 
 * Copyright 2013 Aalto University, Comnet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package report;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Collections;
import java.util.Comparator;
import java.util.ArrayList;
import java.lang.Runtime;

import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.World;
import core.SimScenario;
import core.Coord;
import routing.FloatingContentRouter;

class LifetimeSeries {
    int []           val = null;
    private int      n;
    private int      max;
    private int      last_t;

    LifetimeSeries () {
	val     = new int [FloatingContentSummaryReport.N_ELEM];
	n       = FloatingContentSummaryReport.N_ELEM - 1;
	max     = 1;
	last_t  = 0;
	for (int i = 0; i <= n; i++)
	    val [i] = 0;
    }
    LifetimeSeries (int entries, int ttlmax) {
	val     = new int [entries];
	n       = entries - 1;
	max     = ttlmax;
	last_t  = 0;
	for (int i = 0; i <= n; i++)
	    val [i] = 0;
    };

    /* adjust(), incr(), and decr() operate continuously and fill
     * gaps in the time series with copies of the previous value.
     * This only makes sense for monitoring the #message copies
     * per individual message when time is monotonically increasing.
     */
    public void adjust (double ttl, int delta) {
	int    i = (int) ((double) n * ttl / (double) max);

	if (i > last_t) {
	    for (int j = last_t + 1; j <= i; j++) {
		val [j] = val [last_t];
	    }
	    last_t = i;
	}
	val [i]  += delta;
    }
    public void incr (double ttl) {
	adjust (ttl, 1);
    };
    public void decr (double ttl) {
	adjust (ttl, -1);
    };
    /* record() just records the respective delta for exactly this spec'ed time instance.
     */
    public void record (double ttl, int delta) {
	val [(int) ((double) n * ttl / (double) max)] += delta;
    }
    public void add (LifetimeSeries o) {
	for (int i = 0; i <= n; i++)
	    this.val [i] += o.val [i];
    }
    public String toString (String label, String sep, double ratio_ref) {
	String    s = new String ();
	s += label + sep + max;
	for (int i = 0; i <= n; i++) {
	    if (ratio_ref == 1)
		s += sep + val [i];
	    else
		s += sep + ((double) val [i] / ratio_ref);
	}
	return s;
    }
};

class MdEntry {
    LifetimeSeries lifetime   = new LifetimeSeries ();
    LifetimeSeries lifecopies = new LifetimeSeries ();
    int      start      = 0;
    int      count      = 0;
    int      repl       = 0;
    int      abort      = 0;
    int      success    = 0;
    int      copies     = 0;
};

class MsgEntry {
    int              copies           = 0;    /* # copies of a message left when the ttl expires */
    int              copy_count       = 0;    /* # copies of a message at any given point in time */
    LifetimeSeries   copy_count_time  = null; /* time series of copy count in % of ttl */
    MdEntry          me               = null; /* point to the summary table entry for efficiency */
    String           idx              = null; /* index value of the summary table entry */
    int              ttlval           = 0;    /* the message parameter */
    double           ttl              = 0;    /*           "           */
    double           r                = 0;    /*           "           */
    double           a                = 0;    /*           "           */
    int              size             = 0;    /*           "           */
    Coord            anchor           = null; /*           "           */
    double           lifetime         = 0;    /* message stats collecting during the simulation */
    int              repl             = 0;    /*                     "                          */
    int              start            = 0;    /*                     "                          */
    int              abort            = 0;    /*                     "                          */

    MsgEntry (String s, int size, int ttlval, double ttl, Coord ap, double r, double a) {
	idx         = s;
        this.size   = size;
	this.ttlval = ttlval;
	this.ttl    = ttl;
	this.r      = r;
	this.a      = a;
	anchor      = ap;
	copy_count_time = new LifetimeSeries (FloatingContentSummaryReport.N_ELEM, ttlval);
    };
};


/**
 * Reports summaries for each Floating Content Message simulation (SUM) and diverse aggregates (TAB):
 * per message size, ttl, a, and r.  In the end, individual messages (MSG) are listed.
 */
public class FloatingContentSummaryReport extends Report implements MessageListener {
    public static final String HEADER =
	"# FloatingContentMessageSummaryReport";
    public static final int N_ELEM = 101;

    /** all message delays */
    private SimScenario scen;
    private int n_msg        = 0;
    private int repl         = 0;
    private int abort        = 0;
    private int start        = 0;
    private int success      = 0;
    private int copies       = 0;
    LifetimeSeries lifetime  = new LifetimeSeries ();
    LifetimeSeries lifecopies= new LifetimeSeries ();

    private HashMap<String,MdEntry>  md         = new HashMap<String,MdEntry>();
    
    private LinkedList<Integer>      ttls  = new LinkedList<Integer>();
    private LinkedList<Integer>      sizes = new LinkedList<Integer>();
    private LinkedList<Integer>      rs    = new LinkedList<Integer>();
    private LinkedList<Integer>      as    = new LinkedList<Integer>();
    private LinkedList<String>       mids  = new LinkedList<String>();

    /* match message ids to indices */
    private HashMap<String,MsgEntry> msgs  = new HashMap<String,MsgEntry>();

    /**
     * Constructor.
     */
    public FloatingContentSummaryReport() {
	init();
	scen = SimScenario.getInstance ();
    }
    
    @Override
	public void init() {
	super.init();
	write(HEADER);
    }

    private int msgno (Message m) {
	String     s = m.getId ();
        int        end = s.indexOf ('-', 8);
	String     ret = s.substring (8, end);
	return Integer.parseInt (ret);
    }
    
    private String index (Message m) {
	return m.getSize() + "-" + ((Double) m.getProperty(FloatingContentRouter.FC_TTL_VAL)).intValue () + "-" +
	    ((Double) m.getProperty(FloatingContentRouter.FC_R)).intValue () + "-" +
	    ((Double) m.getProperty(FloatingContentRouter.FC_A)).intValue ();
    }

    protected String index (int size, int ttl, int r, int a) {
	return size + "-" + ttl + "-" + r + "-" + a;
    }

    public void newMessage(Message m) {
	/* due to the creation process for messages, we cannot use m.getTtl () here */
        double     now    = getSimTime ();
        int        size   = m.getSize ();
	double     ttl    = (Double) m.getProperty(FloatingContentRouter.FC_TTL);
	int        ttlval = ((Double) m.getProperty(FloatingContentRouter.FC_TTL_VAL)).intValue ();
        int        r      = ((Double) m.getProperty(FloatingContentRouter.FC_R)).intValue ();
	int        a      = ((Double) m.getProperty(FloatingContentRouter.FC_A)).intValue ();
	Coord      anchor = (Coord) m.getProperty(FloatingContentRouter.FC_ANCHOR);
	String     idx    = index (m);
	MdEntry    me;
	MsgEntry   msg;

	if (scen.getEndTime () < ttl)
	    return;

	/* need to do the following check only here since creation always comes first */
	if ((me = md.get (idx)) == null) {
	    me = new MdEntry ();
	    md.put (idx, me);
	}

	n_msg++;
	me.count++;

	/* this is the first copy of this message */
	msg  = new MsgEntry (idx, size, ttlval, ttl, anchor, r, a);
	msg.me = me;
	msg.copy_count = 1;
	msg.copy_count_time.incr (0);      /* message-specific: use res_ttl */
	msgs.put (m.getId (), msg);

	/* remember which values we had for each dimension */
	if (!ttls.contains (ttlval))
	    ttls.add (ttlval);
	if (!sizes.contains (size))
	    sizes.add (size);
	if (!rs.contains (r))
	    rs.add (r);
	if (!as.contains (a))
	    as.add (a);
	mids.add (m.getId ());
    }
    
    public void messageTransferred(Message m, DTNHost from, DTNHost to,
				   boolean firstDelivery) {
	MsgEntry   msg    = msgs.get (m.getId());

	if (msg == null)
	    return;
	repl++;
	msg.me.repl++;
	msg.repl++;

	msg.copy_count_time.incr (msg.ttlval - m.getTtl ());      /* message-specific: use res_ttl */

	/* one more copy of the message */
	msg.copy_count++;
    }
    
    public void messageDeleted(Message m, DTNHost where, boolean dropped) {
	double     res_ttl= m.getTtl ();
	MsgEntry   msg    = msgs.get (m.getId ());
	int        c;
	double     fraction;

	if (msg == null)
	    return;
	if (res_ttl < 0)
	    res_ttl = 0;

	fraction = 1.0 - res_ttl / (double) msg.ttlval;
	msg.copy_count_time.decr (msg.ttlval - res_ttl);      /* message-specific: use res_ttl */

	/* do not remove copies when TTL has expired, or we will always get 0 copies -> undo the decrement
	 * We have to do the undo because if neither incr nor decr get called, the previous index value will not be copied
	 * within the data structure of the copy counts.
	 */
	if (res_ttl == 0) {
	    msg.copy_count_time.incr (msg.ttlval - res_ttl);  /* message-specific: use res_ttl */
	}

	/* one less copy of the message */
	if (--msg.copy_count == 0) {
	    /* this was the last copy */
	    lifetime.record (fraction, 1);    /* generic creation: use fraction */
	    msg.me.lifetime.record (fraction, 1); 

	    /* now update the time series for multidimensional table entry */
	    lifecopies.add (msg.copy_count_time);
	    msg.me.lifecopies.add (msg.copy_count_time);

	    msg.lifetime = (double) msg.ttlval - res_ttl;
	}

	if (res_ttl == 0) {
	    /* this message floated long enough -> success */

	    /* update the # of global copies until the end of lifetime */
	    copies++;
	    msg.me.copies++;

	    /* message-specific processing */
	    msg.copies++;
	    if (msg.copies == 1) {
		/* first one succeeding */
		success++;
		msg.me.success++;
	    }
	}
    }
    
    public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
	MsgEntry   msg    = msgs.get (m.getId ());

	if (msg == null)
	    return;
	abort++;
	msg.me.abort++;
	msg.abort++;
    }
    
    public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
	MsgEntry   msg    = msgs.get (m.getId ());

	if (msg == null)
	    return;
	start++;
	msg.me.start++;
	msg.start++;
    }
    
    protected Comparator<Integer> compare = new Comparator<Integer> () {
	public int compare (Integer i1, Integer i2) {
	if (i1 == i2)
	    return 0;
	return i1 < i2 ? -1 : 1;
	}
	public boolean equals (Integer i1, Integer i2) {
	    return i1 == i2;
	}
    };

    @Override
	public void done() {
	String   idx = null;
	MdEntry  me;
	MsgEntry msg;
	int      tmp_count;
	Runtime  rt = Runtime.getRuntime ();

	Collections.sort (sizes, compare);
	Collections.sort (ttls, compare);
	Collections.sort (as, compare);
	Collections.sort (rs, compare);

	System.gc ();
	write ("MEM\t" + rt.totalMemory () + "\t" + rt.freeMemory () + "\t" + (rt.totalMemory () - rt.freeMemory ()));
	write ("SUM\t0\t0\t0\t0\t" + n_msg + "\t" + (double) success / (double) n_msg + "\t" + (double) repl / (double) n_msg + "\t" +
	       (double) abort / (double) n_msg + "\t" + (double) copies / (double) n_msg + "\t" +
	       lifetime.toString ("LT", "\t", n_msg) + "\t" + lifecopies.toString ("CC", "\t", n_msg));
	for (int s : sizes) {
	    for (int t : ttls) {
		for (int r : rs) {
		    for (int a : as) {
			idx = index (s, t, r, a);
			me  = md.get (idx);
			if (me == null) {
			    me = new MdEntry ();
			    tmp_count = 1;
			} else
			    tmp_count = me.count;
			write ("TAB\t" + s + "\t" + t + "\t" + r + "\t" + a + "\t" + me.count + "\t" +
			       (double) me.success / (double) tmp_count + "\t" + (double) me.repl / (double) tmp_count + "\t" +
			       (double) me.abort / (double) tmp_count + "\t" + (double) me.copies / (double) tmp_count + "\t" +
			       me.lifetime.toString("LT", "\t", tmp_count) + "\t" + me.lifecopies.toString ("CC", "\t", tmp_count));
		    }
		}
	    }
	}
	for (String id : mids) {
	    msg = msgs.get (id);
	    write ("MSG\t" + id + "\t" + msg.anchor.toString () + "\t" + msg.size + "\t" + msg.ttlval + "\t" + (int) msg.r + "\t" + (int) msg.a + "\t" +
		   (msg.copies > 0 ? 1 : 0) + "\t" + msg.repl + "\t" + msg.abort + "\t" + msg.copies + "\tLT\t" + msg.lifetime / (double) msg.ttlval + "\t" +
		   msg.copy_count_time.toString ("CC", "\t", 1));
	}
	super.done();
    }
}

