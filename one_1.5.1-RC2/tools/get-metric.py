import os

reportdir = '../reports/'
coopmodes = ['coop']
traces = ['kaist', 'hcs', 'rwp'] 
metrics = ['delivery_prob']
reports = ['MessageStatsReport']


def getFileName(mode,trace,metric):
    return mode +"_"+trace +"_"+ metric+".txt"

def getTraceFiles(coopmodes,traces,metrics):
    aList = []
    for aMode in coopmodes:
        for aTrace in traces:
            for aMetric in metrics:
                aList.append(getFileName(aMode,aTrace,aMetric))
    return aList

def getMetricFromFile(metric, file):
    fullName = reportdir+file
    if os.path.exists(fullName) == 0:
        print "no file: " + fullName 
        return 0
    aFile = open(fullName,'r')
    for aLine in aFile:
        if metric in aLine:
            return aLine.split(' ')[1].strip()

#-------------------- Main Function -------------
def main():
    # the main code goes here 
    
    # get the input files
    files = getTraceFiles(coopmodes,traces,reports)
    print "files: " + str(files).strip()

    # for each file, get the metric
    for entry in files:
        aMetric =  getMetricFromFile('delivery_prob', entry)
        if aMetric != 0:
            print " metric " + aMetric

            





if __name__ == "__main__":
    main()
    
