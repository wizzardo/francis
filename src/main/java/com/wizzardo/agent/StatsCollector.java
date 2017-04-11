package com.wizzardo.agent;

import com.wizzardo.tools.interfaces.Consumer;

import java.lang.management.ManagementFactory;
import java.util.*;

/**
 * Created by wizzardo on 11/04/17.
 */
public class StatsCollector extends Thread {
    volatile long interval;

    int tickCounter = 0;
    com.sun.management.ThreadMXBean threadMXBean;
    Map<Long, TInfo> threads = new HashMap<>(32, 1);
    Consumer<List<ThreadStats>> threadStatsHandler;

    public void setThreadStatsHandler(Consumer<List<ThreadStats>> threadStatsHandler) {
        this.threadStatsHandler = threadStatsHandler;
    }

    public static class TInfo {
        String name;
        String group;
        long id;
        long bytesAllocated;
        long cpuTime;
        long userTime;
        int tick;
        long lastRecord;
    }

    public static class ThreadStats {
        public final long id;
        public final String name;
        public final String group;
        public final long cpuTime;

        @Override
        public String toString() {
            return "ThreadStats{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    ", group='" + group + '\'' +
                    ", cpuTime=" + cpuTime +
                    ", bytesAllocated=" + bytesAllocated +
                    '}';
        }

        public final long bytesAllocated;

        public ThreadStats(long id, String name, String group, long bytesAllocated, long cpuTime) {
            this.name = name;
            this.group = group;
            this.id = id;
            this.bytesAllocated = bytesAllocated;
            this.cpuTime = cpuTime;
        }

    }

    public StatsCollector() {
        setName("JvmStatsCollector");
        setDaemon(true);
        threadMXBean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    }

    @Override
    public void run() {
        List<ThreadStats> threadsStats = new ArrayList<>();
        while (true) {
            if (threadStatsHandler != null) {
                gatherThreadStats(threadsStats);
//                System.out.println();
//                threadsStats.forEach(System.out::println);
                threadStatsHandler.consume(new ArrayList<>(threadsStats));
                threadsStats.clear();
            }

            try {
                Thread.sleep(interval);
            } catch (InterruptedException ignored) {
            }
        }
    }

    protected void gatherThreadStats(List<ThreadStats> results) {
        tickCounter++;

        long[] ids = threadMXBean.getAllThreadIds();
        long[] allocatedBytes = threadMXBean.getThreadAllocatedBytes(ids);
//        long[] threadUserTime = threadMXBean.getThreadUserTime(ids);
        long[] threadCpuTime = threadMXBean.getThreadCpuTime(ids);
        long now = System.nanoTime();

        for (int i = 0; i < ids.length; i++) {
            long id = ids[i];
            long bytesAllocated = allocatedBytes[i];
            long cpuTime = threadCpuTime[i];
//            long userTime = threadUserTime[i];
            if (cpuTime < 0) {
                threads.remove(id);
                continue;
            }

            TInfo tInfo = threads.get(id);
            if (tInfo == null) {
                tInfo = new TInfo();
                tInfo.id = id;
                threads.put(id, tInfo);
                tInfo.name = threadMXBean.getThreadInfo(tInfo.id).getThreadName();
                tInfo.group = threadGroup(id).getName();
            } else {
                ThreadStats diff = new ThreadStats(tInfo.id, tInfo.name, tInfo.group, bytesAllocated - tInfo.bytesAllocated, cpuTime - tInfo.cpuTime);
                results.add(diff);
            }

            tInfo.bytesAllocated = bytesAllocated;
            tInfo.cpuTime = cpuTime;
//            tInfo.userTime = userTime;
            tInfo.tick = tickCounter;
            tInfo.lastRecord = now;
        }

        if (tickCounter >= 30) {
            Iterator<Map.Entry<Long, TInfo>> iterator = threads.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Long, TInfo> next = iterator.next();
                if (next.getValue().tick != tickCounter)
                    iterator.remove();
            }
            tickCounter = 0;
        }
    }

    public static ThreadGroup threadGroup(long threadId) {
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        while (group.getParent() != null)
            group = group.getParent();

        Thread[] arr = new Thread[group.activeCount()];
        int l = group.enumerate(arr);
        for (int i = 0; i < l; i++) {
            if (arr[i].getId() == threadId)
                return arr[i].getThreadGroup();
        }

        return group;
    }
}
