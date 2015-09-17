#!/bin/sh
#
# Simulating floating content for different anchor points every 200m in HCS
#
# IMPORTANT: Remember that hcs_default.txt included in the ONE distribution
# conflicts with hcs_floating.txt and thus hcs_default.txt should not be in
# the directory where you run one.sh (e.g., from this script).

# number of simulation runs
runs=10

# radio range
radio=50

# number of nodes: 40, 80, 160
node_list="160 80 40"

# load expressed as mean interval between messages per node: 3600, 1800, 900
load_list="3600 1800 900"

# anchor point (for simulations with fixed anchor)
x=1800
y=1900

# anchor zone parameters
r=200
r_max=500
r_gran=100
a=500
a_max=2000
a_gran=100

del_alg=none
repl_alg=linear
ratio=1

# number of parallel simulation runs
processes=8

# node buffer
buffer=50M

# interface rate + optional interference
rate=250k
int=

# immediate deletion when leaving the anchor zone?
immediate=

# "real-world" parameterization
loc_error=0
scan=0

size=100k
size_max=1000k
size_gran=100k

ttl=1800
ttl_max=10800
ttl_gran=1800

delay=0
delay_max=0

# restrictions for random anchor points
# default:
x_base=0
y_base=0
max_x=4500
max_y=3400

# restricted to the core area (defined in core-def)
core=

while [ $# -ge 1 ]
do
    case $1 in
	radio)
	    radio=$2
	    shift
	    shift
	    ;;
	nodes)
	    node_list="$2"
	    shift
	    shift
	    ;;
	load)
	    load_list="$2"
	    shift
	    shift
	    ;;
	r)
	    r=$2
	    r_max=$3
	    shift
	    shift
	    shift
	    ;;
	a)
	    a=$2
	    a_max=$3
	    shift
	    shift
	    shift
	    ;;
	ttl)
	    ttl=$2
	    ttl_max=$3
	    shift
	    shift
	    shift
	    ;;
	buffer)
	    buffer=$2
	    shift
	    shift
	    ;;
	size)
	    size=$2
	    size_max=$3
	    shift
	    shift
	    shift
	    ;;
	rate)
	    rate=$2
	    shift
	    shift
	    ;;
	error)
	    loc_error="$2"_"$3"
	    gps_update=$2
	    gps_error=$3
	    shift
	    shift
	    shift
	    ;;
	ratio)
	    ratio=$2
	    shift
	    shift
	    ;;
	immediate)
	    immediate=-immediate
	    shift
	    ;;
	core)
	    # this overwrites base_x, base_y, max_x, and max_y
	    core=-core
	    source core-def
	    shift
	    ;;
	proc)
	    processes=$2
	    shift
	    shift
	    ;;
	runs)
	    runs=$2
	    shift
	    shift
	    ;;
	*)
	    echo "Parameter error"
	    exit
	    ;;
    esac
done

if [ $ratio \!= 1 ]
then
	loc_error=$loc_error"_"$ratio
fi

serial=1

trackdir=.trackdir.$$
scen_base=scen_file.$$

mkdir $trackdir

for nodes in $node_list
do
  scenario_base_name=new_$radio"_"$nodes
  # for del_alg in none cosine linear exp
  for del_alg in none
  do
    # for repl_alg in none cosine linear exp
    for repl_alg in none
    do
    	for load in $load_list
    	do
	    # for repl in saf svf stf svf2 stf2 fifo rnd
	    for repl in stf2
	    do
	        scen_file=$scen_base.$serial
		date "+%Y-%m-%d %H:%M:%S" | head -c19
		echo " [$serial] Running HCS scenario for radio $radio, nodes $nodes, r=$r-$r_max a=$a-$a_max size=$size-$size_max t=$ttl-$ttl_max buffer=$buffer repl=$repl load=$load error=$loc_error delay=$delay-$delay_max scan=$scan int=$int repl_alg=$repl_alg del_alg=$del_alg"

		echo "Scenario.name=$scenario_base_name-r$r-$r_max-a$a-$a_max-t$ttl-$ttl_max-s$size-$size_max-b$buffer-d$rate-l$load-e$loc_error-D$delay-$delay_max-s$scan-$repl-R$repl_alg-E$del_alg$immediate$int$core" > $scen_file

		echo "firstinterface.transmitSpeed = $rate" >> $scen_file
		if [ a$immediate = a-immediate ]
		then
		    echo "FloatingContentRouter.deletionPolicy = immediate" >> $scen_file
		else
		    echo "FloatingContentRouter.deletionPolicy = encounter" >> $scen_file
		fi
		if [ a$gps_error \!= a ]
		then
		    echo "FloatingContentRouter.locationError = $gps_error" >> $scen_file
		    echo "FloatingContentRouter.locationSource = gps" >> $scen_file
		fi
		if [ a$gps_update \!= a ]
		then
		    echo "FloatingContentRouter.locationUpdate = $gps_update" >> $scen_file
		    echo "FloatingContentRouter.locationSource = gps" >> $scen_file
		fi
		if [ $ratio \!= 1 ]
		then
		    echo "FloatingContentRouter.locationRatio = $ratio" >> $scen_file
		fi
		echo "FloatingContentRouter.replicationPolicy = $repl" >> $scen_file
		echo "FloatingContentRouter.replicationAlgorithm = $repl_alg" >> $scen_file
		echo "FloatingContentRouter.deletionAlgorithm = $del_alg" >> $scen_file

		echo "floatingApp.anchor = $x_base,$y_base,$r,$a" >> $scen_file
		echo "floatingApp.anchorMax = $max_x,$max_y,$r_max,$a_max" >> $scen_file
		echo "floatingApp.anchorGranularity = 1,1,$r_gran,$a_gran" >> $scen_file
		echo "floatingApp.ttl = $ttl,$ttl_max,$ttl_gran" >> $scen_file
		echo "floatingApp.interval = $load" >> $scen_file
		echo "floatingApp.start = 0" >> $scen_file
		echo "firstinterface.transmitRange = $radio" >> $scen_file

		echo "Group.nrofHosts = $nodes" >> $scen_file
		trams=`expr $nodes / 20`
		echo "Group4.nrofHosts = $trams" >> $scen_file
		echo "Group5.nrofHosts = $trams" >> $scen_file
		echo "Group6.nrofHosts = $trams" >> $scen_file
		echo "Group.bufferSize = $buffer" >> $scen_file
		ttl_min=`expr $ttl_max / 60 + 1`
		ttl_min=$ttl_max
		echo "Group.msgTtl = $ttl_min" >> $scen_file
		echo "floatingApp.messageSize = $size,$size_max,$size_gran" >> $scen_file
		touch $trackdir/$serial
		( 
			./one.sh -b $runs hcs_floating.txt floating_app.txt $scen_file > $trackdir/$serial ; rm $trackdir/$serial
		) &
		serial=`expr $serial + 1`
	        while [ `ls $trackdir | wc -l` -ge $processes ]
		do
		    sleep 10
		done
	    done	# repl (prio)
	done		# load
    done		# repl_alg
  done			# del_alg
done 			# nodes

if [ `ls $trackdir | wc -l` -gt 0 ]
then
    wait
fi

rmdir .trackdir.$$
rm $scen_base.*

