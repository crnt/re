package org.mdpnp.devices;


import com.rti.dds.domain.DomainParticipant;
import com.rti.dds.publication.Publisher;
import com.rti.dds.publication.PublisherQos;
import com.rti.dds.subscription.Subscriber;
import com.rti.dds.subscription.SubscriberQos;
import ice.DeviceIdentity;
import ice.MDSConnectivity;
import org.mdpnp.rtiapi.data.EventLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ManagedResource(description="Partition Assignment Controller")
public class PartitionAssignmentController implements MDSHandler.Objective.MDSListener {

    private static final Logger log = LoggerFactory.getLogger(PartitionAssignmentController.class);

    private long lastPartitionFileTime = 0L;

    @ManagedAttribute(description="Last time partition file was checked.")
    public long getLastPartitionFileTime() {
        return lastPartitionFileTime;
    }

    @ManagedOperation(description="Check for partition file.")
    public void checkForPartitionFile() {
        File f = new File("device.partition");
        checkForPartitionFile(f);
    }

    void checkForPartitionFile(File f) {

        if (f == null || !f.exists()) {
            // File once existed
            if (lastPartitionFileTime != 0L) {
                setPartition(new String[0]);
                lastPartitionFileTime = 0L;
            } else {
                // No file and it never existed
            }
        } else if (f.canRead() && f.lastModified() > lastPartitionFileTime) {
            try {
                List<String> partition = readPartitionFile(f);
                setPartition(partition.toArray(new String[0]));
            } catch (FileNotFoundException e) {
                log.error("Reading partition info", e);
            } catch (IOException e) {
                log.error("Reading partition info", e);
            }

            lastPartitionFileTime = f.lastModified();
        }
    }

    private List<String> readPartitionFile(File f) throws IOException {
        List<String> partition = new ArrayList<>();
        FileReader fr = new FileReader(f);
        BufferedReader br = new BufferedReader(fr);
        String line = null;
        while (null != (line = br.readLine())) {
            if (!line.startsWith("#"))
                partition.add(line.trim());
        }
        br.close();
        return partition;
    }

    @ManagedAttribute(description="DDS partitions for this device")
    public String[] getPartition() {
        PublisherQos pQos = new PublisherQos();
        publisher.get_qos(pQos);
        String[] partition = new String[pQos.partition.name.size()];
        for (int i = 0; i < partition.length; i++) {
            partition[i] = (String) pQos.partition.name.get(i);
        }
        return partition;
    }

    @ManagedAttribute(description="DDS partitions for this device")
    public void addPartition(String partition) {
        List<String> currentPartition = new ArrayList<String>(Arrays.asList(getPartition()));
        currentPartition.add(partition);
        setPartition(currentPartition.toArray(new String[0]));
    }

    public void removePartition(String partition) {
        List<String> currentPartition = new ArrayList<String>(Arrays.asList(getPartition()));
        currentPartition.remove(partition);
        setPartition(currentPartition.toArray(new String[0]));
    }

    @ManagedAttribute(description="DDS partitions for this device")
    public void setPartition(String[] partition) {
        PublisherQos pQos = new PublisherQos();
        SubscriberQos sQos = new SubscriberQos();
        publisher.get_qos(pQos);
        subscriber.get_qos(sQos);

        if (null == partition) {
            partition = new String[0];
        }

        boolean same = partition.length == pQos.partition.name.size();

        if (same) {
            for (int i = 0; i < partition.length; i++) {
                if (!partition[i].equals(pQos.partition.name.get(i))) {
                    same = false;
                    break;
                }
            }
        }

        if (!same) {
            String       asString = toString(partition);
            List<String> asList   = Arrays.asList(partition);

            log.info("Changing partition to >" + asString + "<");
            pQos.partition.name.clear();
            sQos.partition.name.clear();
            pQos.partition.name.addAll(asList);
            sQos.partition.name.addAll(asList);
            publisher.set_qos(pQos);
            subscriber.set_qos(sQos);

            final MDSConnectivity state = new MDSConnectivity();
            state.partition = asString;
            state.unique_device_identifier=deviceIdentity.unique_device_identifier;

            connectivityAdapter.publish(state);

        } else {
            log.info("Not changing to same partition " + Arrays.toString(partition));
        }
    }

    /**
     * @param partition
     * @return comma-separated list of partitions.
     */
    static String toString(String [] partition) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < partition.length; i++) {
            if(b.length()!=0)
                b.append(",");
            b.append(partition[i]);
        }
        return b.toString();
    }

    MDSHandler getConnectivityAdapter() {
        return connectivityAdapter;
    }

    private final MDSHandler connectivityAdapter;
    private final DeviceIdentity deviceIdentity;
    private final Publisher publisher;
    private final Subscriber subscriber;

    public void start() {
        connectivityAdapter.start();
        connectivityAdapter.addConnectivityListener(this);
    }

    public void shutdown() {
        connectivityAdapter.removeConnectivityListener(this);
        connectivityAdapter.shutdown();
    }


    public PartitionAssignmentController(DeviceIdentity d, DomainParticipant dp, EventLoop e, Publisher p, Subscriber s) {
        deviceIdentity = d;
        publisher = p;
        subscriber = s;
        connectivityAdapter = new MDSHandler(e, dp);

    }

    /**
     * default ctor only useful to tests of disconnected functionality
     */
    PartitionAssignmentController(DeviceIdentity d) {
        deviceIdentity = d;
        publisher = null;
        subscriber = null;
        connectivityAdapter = new MDSHandler.NoOp();
    }

    @Override
    public void handleDataSampleEvent(MDSHandler.Objective.MDSEvent evt) {
        ice.MDSConnectivityObjective  o = (ice.MDSConnectivityObjective )evt.getSource();
        if(deviceIdentity.unique_device_identifier.equals(o.unique_device_identifier)) {
            String p[] = {o.partition};
            setPartition(p);
        }
    }
}
