import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import com.typesafe.config.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;
import java.util.*;
import java.util.logging.*;

public class CloudSimDemo {
    private final static Logger logger = Logger.getLogger("SIMULATOR");

    private final static Config conf = ConfigFactory.load("application.conf");
    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        logger.log(Level.INFO, "Start time: "+ startTime);
        int numUser = conf.getInt("CloudSim-Demo.numUser");
        Calendar cal = Calendar.getInstance();
        boolean trace_flag = false;

        //Initialize all the entities (CIS, CloudSimShutdown etc)
        CloudSim.init(numUser, cal, trace_flag);

        //Initiate a server instance followed by creation of 3 datacenters with 3 hosts each
        Server server = new Server();
        Datacenter dc0 = server.getDatacenter("Datacenter0", 3);
        Datacenter dc1 = server.getDatacenter("Datacenter1", 3);
        Datacenter dc2 = server.getDatacenter("Datacenter2", 3);

        //Create an instance of data center broker
        DatacenterBroker dcb = server.getDcb("broker0");
        int brokerId = dcb.getId();

        //Create client instances and create cloudlet lists
        Client client0 = new Client(0);
        List<Cloudlet> cloudletList0 = new ArrayList<Cloudlet>();
        int numCloudlets0 = conf.getInt("CloudSim-Demo.numCloudlets0");
        cloudletList0 = client0.getCloudlets(dcb.getId(), numCloudlets0, 0, "Full");

        Client client1 = new Client(1);
        List<Cloudlet> cloudletList1 = new ArrayList<Cloudlet>();
        int numCloudlets1 = conf.getInt("CloudSim-Demo.numCloudlets1");
        cloudletList1 = client0.getCloudlets(brokerId, numCloudlets1, numCloudlets0, "Full");

        List<Cloudlet> finalCloudletList = new ArrayList<Cloudlet>();
        finalCloudletList.addAll(cloudletList0);
        Collections.shuffle(finalCloudletList,new Random(3));
        finalCloudletList.addAll(cloudletList1);

        //create list of VMs
        List<Vm> vmList = new ArrayList<Vm>();
        int numVms = conf.getInt("CloudSim-Demo.numVms");
        vmList = server.getVms(brokerId, numVms, 0,"Space");

        //Pass cloudlet list and vm list to data center broker
        dcb.submitCloudletList(finalCloudletList);
        dcb.submitVmList(vmList);

        //Pause the simulation to dynamically add more cloudlets and vms
        Runnable monitor = new Runnable() {
            @Override
            public void run() {
                CloudSim.pauseSimulation(200);
                while (true) {
                    if (CloudSim.isPaused()) {
                        break;
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                logger.log(Level.INFO, CloudSim.clock() + ": The simulation is paused for 5 sec");

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                Client client2 = new Client(2);
                //Create VMs and Cloudlets and send them to broker
                List<Vm> vmListDynamic = new ArrayList<Vm>();
                int numVmsD = conf.getInt("CloudSim-Demo.numVmD");
                vmListDynamic = server.getVms(brokerId,numVmsD, numVms,"Space"); //creating 5 vms
                List<Cloudlet> cloudletListDynamic = new ArrayList<Cloudlet>();
                int numCloudletsD = conf.getInt("CloudSim-Demo.numCloudletsD");
                cloudletListDynamic = client2.getCloudlets(brokerId, numCloudletsD, numCloudlets0+numCloudlets1,"Full"); // creating 10 cloudlets
                dcb.submitVmList(vmListDynamic);
                dcb.submitCloudletList(cloudletListDynamic);

                CloudSim.resumeSimulation();
            }
        };

        new Thread(monitor).start();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //Start the simulation and get results
        CloudSim.startSimulation();

        CloudSim.stopSimulation();

        //Collect costs and execution times
        var executionTime = finalCloudletList.stream()
                .map(e -> e.getFinishTime() - e.getExecStartTime())
                .reduce(0.0, (x, y) -> x + y);
        var avgExecutionTime = executionTime / finalCloudletList.size();
        logger.log(Level.INFO,"Average Cloudlet Execution Times"+avgExecutionTime);

        var cpuCost = finalCloudletList.stream()
                .map(e -> e.getActualCPUTime() * e.getCostPerSec())
                .reduce(0.0, (x, y) -> x + y);
        var avgCpuCost = cpuCost / finalCloudletList.size();
        System.out.println(avgCpuCost);
        logger.log(Level.INFO,"Average CPU Cost"+avgCpuCost);

        var bwCost = finalCloudletList.stream()
                .map(e -> e.getCloudletFileSize() * 0.1)
                .reduce(0.0, (x, y) -> x + y);
        var avgBwCost = bwCost / finalCloudletList.size();
        logger.log(Level.INFO,"Average BW Cost"+avgBwCost);

        var totalCost = avgBwCost + avgCpuCost;
        logger.log(Level.INFO,"Average Total Cost"+totalCost);

        logger.log(Level.INFO,"Simulation finished!");
        long endTime = System.currentTimeMillis();
        double totalTimeTaken = (endTime - startTime)/1000.0;
        logger.log(Level.INFO,"The total time taken for the execution: " + totalTimeTaken + " s.");
    }
}
