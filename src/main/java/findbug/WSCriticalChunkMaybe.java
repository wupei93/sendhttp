package findbug;

import com.jcraft.jsch.Channel;
import common.SshUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

public class WSCriticalChunkMaybe {
    private static ExecutorService executorService = Executors.newFixedThreadPool(10);
    private static BlockingQueue<CheckChunkAndObjectTask> checkChunkAndObjectTasks = new LinkedBlockingQueue<>();
    private static List<String> hostList = Arrays.asList("10.243.20.15","10.243.20.55","10.243.20.95");

    public static void main(String[] args) throws Exception {
        hostList.forEach(host -> {
            Channel channel = null;
            try {
                channel = SshUtils.execCmd(host,
                        "svc_log -f 'ERROR  RepoChunkScanBatchItemProcessor.java.*FAILED GC VERIFICATION on old task ' -files blobsvc-error.log* ");
                BufferedReader reader = new BufferedReader(new InputStreamReader(channel.getInputStream()));
                reader.lines().forEach( line -> {
                    int chunkIdIndex = line.indexOf("chunkId");
                    if(chunkIdIndex == -1){
                        return;
                    }
                    int objectIdIndex = line.indexOf("objectId");
                    String chunkId = line.substring(chunkIdIndex).split(" ")[0];
                    String objectId = line.substring(objectIdIndex).split(" ")[0];
                    executorService.submit(new CheckChunkAndObjectTask(chunkId, objectId));
                });
            } catch (Exception e) {
                e.printStackTrace();
            } finally{
                channel.disconnect();
            }
        });
    }

    static class CheckChunkAndObjectTask implements Callable{
        private String chunkId;
        private String objectId;

        public CheckChunkAndObjectTask(String chunkId, String objectId) {
            this.chunkId = chunkId;
            this.objectId = objectId;
        }


        @Override
        public Object call() throws Exception {
            String getPrivateIp = "`sudo ifconfig | grep \"inet addr:169\" | cut -d : -f 2|cut -d \' \' -f  1`";
            hostList.forEach(host -> {
                Channel channel = null;
                try {
                    String cmd = "curl -L \"" +
                            "http://" + getPrivateIp + ":9101/diagnostic/OB/0/DumpAllKeys/OBJECT_TABLE_KEY?showvalue=gpb&objectId=" + objectId +"\" | grep -C 1 schemaType";
                    channel = SshUtils.execCmd(host, cmd);
                    String objectUrl = new BufferedReader(new InputStreamReader(channel.getInputStream())).readLine();
                    channel.disconnect();
                    channel = SshUtils.execCmd(host, "curl -L \"" + objectUrl + "\" | grep " + chunkId);
                    new BufferedReader(new InputStreamReader(channel.getInputStream())).lines().forEach(line -> {
                        if(line.contains(chunkId)){
                            System.out.println("chunk:"+chunkId + " is still used by object:" + objectId);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                } finally{
                    if(channel != null){
                        channel.disconnect();
                    }
                }
            });
            return null;
        }

    }
}
