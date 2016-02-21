/*
 * Copyright 2015, 2016 Barun Saha (http://barunsaha.me)
 *
 * Distributed under GNU GPLv3 license.
 */
package interfaces;

import core.NetworkInterface;
import core.Settings;

/**
 * A network interface that is incompatible with others but continues gracefully
 * without throwing a runtime exception.
 *
 * @author barun
 */
public class SimpleBroadcastInterfaceTypeCheck2 extends SimpleBroadcastInterface {

    public SimpleBroadcastInterfaceTypeCheck2(Settings s) {
        super(s);
    }

    public SimpleBroadcastInterfaceTypeCheck2(SimpleBroadcastInterfaceTypeCheck2 ni) {
        super(ni);
    }

    @Override
    public NetworkInterface replicate() {
        return new SimpleBroadcastInterfaceTypeCheck2(this);
    }

    /**
     * Tries to connect this host to another host. The other host must be active
     * and within range of this host for the connection to succeed.
     *
     * @param anotherInterface The interface to connect to
     */
    @Override
    public void connect(NetworkInterface anotherInterface) {
        System.out.println(getInterfaceType() + " <-> "
                + anotherInterface.getInterfaceType());

        assert super.getInterfaceType().equals(
                anotherInterface.getInterfaceType()) : "Not same interface"
                + " types";
        if (super.getInterfaceType().equals(anotherInterface.getInterfaceType())
                && super.isScanning()) {
            super.connect(anotherInterface);
        }
    }

    @Override
    public void createConnection(NetworkInterface anotherInterface) {
//        System.out.println(getInterfaceType() + " <-> " + 
//                anotherInterface.getInterfaceType());
//        boolean shouldConnect = getInterfaceType().equals(
//                anotherInterface.getInterfaceType());
//        
//        assert shouldConnect : "Not same interface" + 
//                " types";

        super.createConnection(anotherInterface);
    }
}
