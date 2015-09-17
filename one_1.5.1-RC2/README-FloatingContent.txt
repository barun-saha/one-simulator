Floating Content Simulation code in the ONE Simulator
=====================================================

This file briefly summarizes the additions to the ONE simulator for
running FloatingContent simulations.  For details about the Floating
Content concept itself and the related publications, please refer to
http://www.floating-content.net/

The usual "No warranty" applies.

We try to be somewhat complete here.  If something is missing or not
quite clear, please look into the respective files or the source code!


The following files are specific to FloatingContent:

Configuration files:
--------------------

* hcs_floating.txt

  This file replaces default_settings.txt for running Floating
  Content simulations with the Helsinki City Scenario mobility
  model.

  IMPORTANT: You MUST NOT have another default_settings.txt file
  	     in the directory from which you are running the
	     simulator since the settings are conflicting.
 
* floating_app.txt

  This file contains application-specific settings to control the
  generation of messages using FloatingContent.

* scen_file.<pid>.<run>

  The simulation script (see below) will generate some settings
  dynamically that will complement and/or override some of the
  parameters defined in the above two files.  These "scenario"
  file names include a process id and a run id to ensure they
  won't conflict.  They will be removed after a simulation run
  has completed.  If they stay around, this is an indication that the
  simulation was aborted so that the shell script did not run through
  the end.

* core-def

  To restrict the creation of Floating Content messages to a certain
  part of the simulation area, this file is used.  See the script
  file below.


Script file:
-----------

* sim_float_parallel_complex.sh

  This script automates exploring different simulator and
  configuration settings.  A number of command line parameters
  control the simulation settings (and some parameters need to
  be set explicitly in the for loops of the shell script).
  The script iterates through all the permutations using a 
  specified number of processes.  Whenever one simulation 
  process completes, the next of the permutations is picked
  from the list.  The number of processes is controlled by the
  "proc" parameter.

  Note: this script was designed for use with Floating Content
  because of the huge parameter space to explore, but it could
  of course be used for other types of simulations, too.

  Parameters:

  proc <#processes>	  Indicates how many parallel instances
       			  of the simulator to run.  Should be limited
			  to the number of processor cores and be
			  further constrained by the available
			  physical memory (to avoid paging).  Make
			  sure to adapt the virtual memory size in
			  one.sh to your memory needs.

			  Default: 8

  runs <#seeds>		  How many simulation runs per scenario
       			  configuration.  Make sure your seed
			  settings in the diverse configuration
			  files offer at least as many seed 
			  values as the number of your runs.

			  Default: 10

  radio <range>		  Indicates the radio range for the
  			  wireless interfaces in meters.

			  Default: 50

  rate <bitrate>	  The rate of the wireless interfaces
       			  measured in bytes/s.

       			  Default: 250k

  buffer <buffer size>	  The buffer size for the nodes.

  	 	 	  Default: 50M

  nodes "<n1> [<n2> ...]" How many nodes in the simulation.
  	      	    	  If multiple numbers are given in
			  quotes, then the script will iterate
			  through all those numbers.

			  For historic reasons, this indicates the
			  number of nodes in each of the three node
			  groups in the Helsinki city scenario, so that
			  a value of 40 will yield 3*40 = 120 nodes,
			  40 of which being cars and 80 pedestrians.
			  Moreover, for every 40 nodes, 2 trams will
			  be created.

			  You can adjust this as you like, but make
			  sure you know what you are doing and update
			  and validate that the generate configuration
			  files do look like you want them to.

			  Default: "40 80 160"

  load "<l1> [<l2> ...]"  Mean time between the generation of two
       	     	   	  messages. Again, if multiple numbers are
       	     	   	  given, the script will iterate through all
       	     	   	  of them.

			  Default: "3600 1800 900"

  r <r_min> <r_max> 	  The minimum and maximum replication range for
    	    		  Floating Content messages in meters.  The
    	    		  shell variable r_gran (= 100m) is not
    	    		  controlled via command line parameters at
    	    		  this point.

			  The granularity defines the step size in
			  which values are chosen between min and
			  max.  This holds for r, a, ttl, and size.

			  Default: 200 500

  a <a_min> <a_max> 	  The minimum and maximum availability range for
    	    		  Floating Content messages in meters.  The
    	    		  shell variable a_gran (= 100m) is not
    	    		  controlled via command line parameters at
    	    		  this point.

			  If a_min and a_max are set to zero, r = a
			  will be chosen in the simulations.

			  Default: 500 2000

  ttl <ttl_min> <ttl_max> The minimum and maximum TTL for
    	    		  Floating Content messages in seconds.  The
    	    		  shell variable ttl_gran (= 1800s) is not
    	    		  controlled via command line parameters at
    	    		  this point.

			  Default: 1800 10800

  size <s_min> <s_max>    The minimum and maximum size for
    	    		  Floating Content messages in bytes.  The
    	    		  shell variable size_gran (= 100000) is not
    	    		  controlled via command line parameters at
    	    		  this point.

			  Default: 100000 1000000

  immediate		  Enables immediate deletion of messages once
  			  a node leaves the anchor zone.  Otherwise,
			  the message will only be deleted upon the 
			  next encounter outside the anchor zone.
			  Including this keyword as parameter will
			  enabled this.

			  Default: disabled

  core			  Causes reading the file "core-def" to 
  			  restrict the area in which messages are
			  generated.  Including this keyword as
  			  parameter will enabled this.

			  Default: disabled

  error <delay> <min,max> Enables position errors.  <delay> indicates
  			  in seconds in which intervals the position
			  is updated ("the GPS source is read").
  			  <min,max> determine the GPS location error
			  in meters, e.g., 0,10.

			  Default: disabled

  ratio	<fraction>	  Indicates the fraction of nodes that have
  			  location information.  Those will be
			  chosen randomly out of all the nodes.

			  Default: 1.0

All these parameters are reflected in the report file names, which
makes them lengthy but encompassing.

When forking simulator instances, a directory ".trackdir.<pid>" is
created by the script in which files will be created that contain
the standard output of each ONE simulator instances (numbered by the
run).  Those can be inspected to monitor simulation progress.

Upon successful completion, all these files and directories will be
removed again.


Source code:
------------

* applications/FloatingApplication.java

  This file contains a Floating Content application that just sends
  messages according to a set of configurable parameters.

  Note that the Floating Content API is not yet available in the ONE
  simulator.

* routing/FloatingContentRouter.java

  This file implements the replication and prioritization logic
  of Floating Content.

* report/FloatingContentSummaryReport.java

  This reports summarizes the results of a Floating Content
  simulation.

  This report generates four different types of output lines with
  TAB-separated fields.  Their type is indicated in the first field:
  + MEM provides information on memory consumption (just for tuning)
  + SUM	provides a summary over all message
  + TAB	provides a summary per message class according to size,
    	ttl, a, and r.
  + MSG	provides the stats for each message

  Type	Field   Semantics
  ------------------------------------------------------------
  MEM	  2	total memory
  	  3	free memory
	  4	used memory

  SUM	  2-5	"0" (for consistency with other reports)
  	  6	total # of generated messages that would expire
	  	before the end of the simulation time

  	  7	fraction of messages floating for their entire TTL
	  8	mean # replications per message
	  9	mean # aborted messages
	 10	mean # message copies at TTL expiry
	 11	"LT" (just a tag to assist in parsing/checking)
	 12	max value relative to which the following percentiles
	 	value were measured; "1.0" for the SUM and TAB lines
	 13+i	fraction of messages that disappeared ("sunk")
	 	during the i-th percentile of its lifetime;
		101 values in total; for i==101, this is the fraction
		of messages that floated till their TTL expired
	114	"CC" (just a tag to assist in parsing/checking)
	115	1.0
	116+j	mean #copies of message that existed at the end of
		the j-th percentile of a message's lifetime

  TAB	  2	message size
  	  3	message ttl
	  4	replication range r
	  5	availability range a
	  6	# messages of this class

	7--227	similar as for SUM above, just specific per class

  MSG	  2	unique message id
  	  3	(x,y) coordinates where the message was created
	  4	message size
	  5	message ttl
	  6	replication range r
	  7	availability range a
	  8	"1" if the message floated until it expired,
		"0" otherwise
	  9	# replications for this message
	 10	# aborted message transfers
	 11	# copies at the time TTL was reached
	 12	"LT"
	 13	fraction of TTL reached when this message disappeared
	 14	"CC"
	 15	message TTL (reference for the copy count that
  	  	follows)
	 16+j	#copies of message that existed at the end of
		the j-th percentile of a message's lifetime
	

* report/FloatingMessageReport.java 

  This reports logs all individual events for all messages
  (irrespective of when they expire so the log needs post-processing).
  The basic idea is repeating fairly complete information about each
  message for each reported event so that post-processing can be 
  stateless to a large extent.

  Field 1 is the simulation timestamp.
  FIeld 2 indicates the type of event; the following ones are defined
  with their respective following fields:

  Event	  Field	Semantics
  -------------------------------------------------------------------
  CREATE    3	Message originator (node id)
  	    4	Message id
	    5	Message creation time
	    6	Location where the message was created: (x,y)
	    	(this *may* be the anchor point, but does not have to,
		 the creator only needs to be within the anchor zone)
	    7	Anchor point: (x,y)
	    8	r=<replication range>
	    9   a=<availability range>
	   10	expiry time (in simulator clock)
	   11	size
	   12	ttl
	   13	(dependencies)
		This is part of the NOM 2012 API paper for references
		to prior messages; the code for generating them is not
		yet part of the distribution.

  REPLICATE
	  3--11	as above
	   12	forwarding node id
	   13	forwarding node location: (x,y)
	   14	receiving node id
	   15	receiving node location: (x,y)
	   16	ttl
	   17	(dependencies)
	   18	[residual ttl]

  DELETE
	  3--11	as above
	   12	deleting node id
	   13	location where the message is deleted
	   14	ttl
	   15	(dependencies)
	   16	[residual ttl]  
  
  ABORT
	  3--18	as for REPLICATE

  START
	  3--18 as for REPLICATE

  (Some of these structures grew historically and later additions were
  made in a way not to invalidate our existing post-processing
  scripts.  Some additions were made for validation/debugging.)

  BEWARE: These files can get really huge!


* report/FloatingAppReporter.java

  This report is only used to monitor nodes entering and leaving an
  anchor zone for validating our analytical model.

  BEWARE: These files can get really huge!
