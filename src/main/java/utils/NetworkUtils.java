package utils;

import java.io.IOException;
import java.net.InetAddress;

public class NetworkUtils {

    /**
     * Checks if the network is offline by attempting to reach a well-known host.
     *
     * @return true if the network is offline, false if online.
     */
    public static boolean isOffline() {
        try {
            // Try to resolve and reach a reliable host (e.g., www.google.com).
            InetAddress address = InetAddress.getByName("www.google.com");
            // Try to reach within 2000 milliseconds (2 seconds)
            if (address.isReachable(2000)) {
                return false; // Online if reachable
            } else {
                return true; // Offline if not reachable
            }
        } catch (IOException e) {
            // If there's an exception, assume we are offline.
            return true;
        }
    }
}