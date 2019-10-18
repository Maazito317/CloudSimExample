import org.cloudbus.cloudsim.*;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.logging.*;
import com.typesafe.config.*;

public class Client {
    private final static Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    Config conf = ConfigFactory.load("application.conf");
    private int clientId;

    public Client(int clientId){ this.clientId = clientId;}

    public int getClientId() {return clientId;}

    public List<Cloudlet> getCloudlets(int brokerId, int cloudlets, int idShift, String utilType){
        return createCloudlet(brokerId,cloudlets, idShift, utilType);
    }

    /**This function creates a list of cloudlets to be passed to the broker in CloudSimDemo
     *
     * @param brokerId: ID of the broker that the cloudlet is registered to
     * @param cloudlets: Number of cloudlets to be generated
     * @param idShift: id of the cloudlets
     * @param utilType: Utilization type ofr CPU, RAM, and BW
     * @return
     */
    private List<Cloudlet> createCloudlet(int brokerId,int cloudlets, int idShift, String utilType){
        LinkedList<Cloudlet> cloudletList = new LinkedList<Cloudlet>();
        long length = conf.getInt("Client-app.length");
        long fileSize = conf.getInt("Client-app.fileSize");
        long outputSize = conf.getInt("Client-app.outputSize");
        int pesNumber = conf.getInt("Client-app.pesNumber");
        UtilizationModel utilizationModel;
        switch(utilType) {
            case "Full":
                utilizationModel = new UtilizationModelFull();
                break;
            case "Null":
                utilizationModel = new UtilizationModelNull();
                break;
            case "Stochastic":
                utilizationModel = new UtilizationModelStochastic();
                break;
            default:
                logger.log(Level.WARNING,"Could not match Utilization Model name. Defaulting to Full Utilization Model");
                utilizationModel = new UtilizationModelFull();
        }

        Cloudlet[] cloudlet = new Cloudlet[cloudlets];
        IntStream.range(0, cloudlets).map(i -> {
            int f = (int) ((Math.random() * 40) + 1);
            cloudlet[i] = new Cloudlet(idShift + i, length*f,
                    pesNumber, fileSize, outputSize,
                    utilizationModel, utilizationModel, utilizationModel);
            cloudlet[i].setUserId(brokerId);
            cloudletList.add(cloudlet[i]);
            return 0;
        });
        return cloudletList;
    }
}
