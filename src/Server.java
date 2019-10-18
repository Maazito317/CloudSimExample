import org.apache.commons.math3.stat.descriptive.moment.InteractionTest;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import com.typesafe.config.*;
import javax.xml.crypto.Data;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import java.util.logging.*;
import java.util.stream.IntStream;

public class Server {
    private final static Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    private int numDC;
    private int numVms;
    private int numHosts;
    private int numBrokers;

    public void Server(){ }
    Config conf = ConfigFactory.load("application.conf");
    public List<Vm> getVms(int brokerId, int numVms, int idShift, String policy){
        return createVM(brokerId, numVms,idShift, policy);
    }

    public Datacenter getDatacenter(String name, int numHosts) {return createDatacenter(name, numHosts);}

    public DatacenterBroker getDcb(String name){ return createBroker(name);}

    public void printCloudletList(List<Cloudlet> list) {
        int size = list.size();
        Cloudlet cloudlet;

        String indent = "    ";
        Log.printLine();
        Log.printLine("# ========== OUTPUT ==========");
        Log.printLine("# Cloudlet ID" + indent + "STATUS" + indent +
                "Data center ID" + indent + "VM ID" + indent + indent + "Time" + indent +
                "Start Time" + indent + "Finish Time"
                + indent + "Submission Time" + indent + "Processing Cost");

        DecimalFormat dft = new DecimalFormat("###.##");
        for (int i = 0; i < size; i++) {
            cloudlet = list.get(i);
            Log.print(indent + cloudlet.getCloudletId() + indent + indent);

            if (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS){
                Log.print("SUCCESS");

                Log.printLine( indent + indent + cloudlet.getResourceId() + indent + indent + indent + cloudlet.getVmId() +
                        indent + indent + indent + dft.format(cloudlet.getActualCPUTime()) +
                        indent + indent + dft.format(cloudlet.getExecStartTime())+ indent + indent + indent + dft.format(cloudlet.getFinishTime())
                        + indent + indent + dft.format(cloudlet.getSubmissionTime())+ indent + indent + dft.format(cloudlet.getCostPerSec()));
            }
        }

    }

    private DatacenterBroker createBroker(String name) {
        DatacenterBroker dcb = null;
        try {
            dcb = new DatacenterBroker(name);
        } catch (Exception e) {
            logger.log(Level.SEVERE,"Could not create DataCenterBroker entity");
        }
        return dcb;
    }

    /** Returns a list of VMs made on the hosts to be submitted to data center broker
     *
     * @param brokerId: Id of the broker to be submitted to
     * @param numVms: Number of VMs
     * @param idShift: Id of the Vm
     * @param policy: Time shared or space shared policy for cloudlet scheduler
     * @return
     */
    private List<Vm> createVM(int brokerId, int numVms, int idShift, String policy) {
        LinkedList<Vm> vmList = new LinkedList<Vm>();
        long size = conf.getInt("Server-app.size");
        int ram = conf.getInt("Server-app.ram");
        int mips = conf.getInt("Server-app.mips");
        long bw = conf.getInt("Server-app.bw");
        int pesNumber = conf.getInt("Server-app.pesNumber");
        String vmm = conf.getString("Server-app.vmm");
        Vm[] vm = new Vm[numVms];
        IntStream.range(0, numVms).map(i -> {
            switch(policy) {
                case "Time":
                    vm[i] = new Vm(idShift + i, brokerId, mips, pesNumber, ram, bw, size, vmm, new CloudletSchedulerTimeShared());
                case "Space":
                    vm[i] = new Vm(idShift + i, brokerId, mips, pesNumber, ram, bw, size, vmm, new CloudletSchedulerSpaceShared());
            }
            vmList.add(vm[i]);
            return 1;
        });

        return vmList;
    }

    /** Returns a list of hosts to be used by the createDatacenters function
     *
     * @param numHosts: Number of hosts needed
     * @param idShift: id of the hosts
     * @param peList: List of processing elements available to the host
     * @param policy: Time shared or Space shared policy
     * @return
     */
    private List<Host> createHosts(int numHosts, int idShift, List<Pe> peList, String policy){
        List<Host> hostList = new ArrayList<Host>();
        int ram = conf.getInt("Server-app.Hram");; //host memory (MB)
        long storage = conf.getInt("Server-app.Hstorage");; //host storage
        int bw = conf.getInt("Server-app.Hbw");;
        Host[] host = new Host[numHosts];
        IntStream.range(0, numHosts).map(i -> {
            switch(policy) {
                case "Time":
                    host[i] = new Host(idShift, new RamProvisionerSimple(ram), new BwProvisionerSimple(bw), storage, peList, new VmSchedulerTimeShared(peList));
                    break;
                case "Space":
                    host[i] = new Host(idShift, new RamProvisionerSimple(ram), new BwProvisionerSimple(bw), storage, peList, new VmSchedulerSpaceShared(peList));
                    break;
                case "OverSub":
                    host[i] = new Host(idShift, new RamProvisionerSimple(ram), new BwProvisionerSimple(bw), storage, peList, new VmSchedulerTimeSharedOverSubscription(peList));
                    break;
                }
            hostList.add(host[i]);
            return 1;
        });
        return hostList;
    }

    /** Instantiates the datacenter entity
     *
     * @param name: Name of the datacenter
     * @param numHosts: Number of hosts needed on each datacenter
     * @return
     */
    private Datacenter createDatacenter(String name, int numHosts) {
        List<Host> hostList = new ArrayList<Host>();
        List<Pe> peList = new ArrayList<Pe>();
        int mips = conf.getInt("Server-app.mips");;
        //quad core
        int num_core1 = conf.getInt("Server-app.num_core1");;
        IntStream.range(0, num_core1).map(i -> {
            peList.add(new Pe(i, new PeProvisionerSimple(mips)));
            return 1;
        });

        hostList = createHosts(numHosts,0,peList,"Space");

        String arch = conf.getString("Server-app.arch");;
        String os = conf.getString("Server-app.os");;
        String vmm = conf.getString("Server-app.vmm");;
        double time_zone = conf.getDouble("Server-app.time_zone");         // time zone this resource located
        double cost = conf.getDouble("Server-app.cost");              // the cost of using processing in this resource
        double costPerMem = conf.getDouble("Server-app.costPerMem");        // the cost of using memory in this resource
        double costPerStorage = conf.getDouble("Server-app.costPerStorage");    // the cost of using storage in this resource
        double costPerBw = conf.getDouble("Server-app.costPerBw");            // the cost of using bw in this resource
        LinkedList<Storage> storageList = new LinkedList<Storage>();    //we are not adding SAN devices by no

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(arch,os,vmm,hostList,time_zone,cost,costPerMem,costPerStorage,costPerBw);
        Datacenter datacenter = null;
        try {
            datacenter = new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList), storageList, 0);
        } catch (Exception e) {
            logger.log(Level.SEVERE,"Could not create Datacenter entity");
        }
        return datacenter;
    }

}
