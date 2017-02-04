package com.wizzardo.agent;

import com.wizzardo.tools.io.IOTools;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.channels.SocketChannel;
import java.util.*;

/**
 * Created by wizzardo on 04/02/17.
 */
public class NetworkTools {

    public static String formatMacAddress(byte[] mac) {
        StringBuilder sb = new StringBuilder(17);
        for (byte b : mac) {
            if (sb.length() != 0)
                sb.append(":");
            String str = Integer.toHexString(b & 0xFF);
            if (str.length() == 1)
                sb.append('0');
            sb.append(str);
        }
        return sb.toString();
    }

    public static NetworkInterface getNetworkInterface(String host, int port) throws IOException {
        FileInputStream in = new FileInputStream("/proc/net/dev");
        String stats = new String(IOTools.bytes(in));
        IOTools.close(in);

//        System.out.println("stats: " + stats);
        String[] split = stats.split("\n");
        List<Map.Entry<String, Long>> l = new ArrayList<>(split.length - 2);
        for (int i = 2; i < split.length; i++) {
            String[] data = split[i].trim().split("[: ]+");
            l.add(new AbstractMap.SimpleEntry<>(data[0], Long.parseLong(data[1])));
        }
        l.sort(Collections.reverseOrder(Comparator.comparing(Map.Entry::getValue)));


        for (Map.Entry<String, Long> e : l) {
            NetworkInterface i = NetworkInterface.getByName(e.getKey());
            if (i.isLoopback())
                continue;
            if (!i.isUp())
                continue;

            Enumeration<InetAddress> addresses = i.getInetAddresses();
            for (InetAddress address : Collections.list(addresses)) {
                try {
                    if (address instanceof Inet6Address)
                        continue;

                    if (!address.isReachable(3000))
                        continue;

                    SocketChannel socket = SocketChannel.open();
                    socket.socket().setSoTimeout(3000);
                    socket.connect(new InetSocketAddress(host, port));
                    socket.close();

                    return i;
                } catch (IOException ignored) {
                }
            }
        }
        return null;
    }
}
