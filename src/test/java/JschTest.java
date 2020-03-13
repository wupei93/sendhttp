import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import common.SshUtils;
import org.junit.Test;

import java.io.*;

public class JschTest {
    @Test
    public void testJsch1(){
        Session session = null;
        BufferedReader reader = null;
        try {
            session = SshUtils.connect("10.243.20.15");
            reader = SshUtils.execCmd(session, "hostname -i");
            reader.lines().forEach(System.out::println);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if(session != null){
                session.disconnect();
            }
            if(reader != null){
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Test
    public void testJsch() throws JSchException, IOException {
        JSch jsch=new JSch();
        Session session = jsch.getSession("admin", "10.243.20.15");
        session.setConfig("StrictHostKeyChecking", "no");
        session.setTimeout(500);
        session.setPort(22);
        session.setPassword("ChangeMe");
        session.connect();
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand("hostname -i\nhh");
        channel.connect();
        OutputStream out = channel.getOutputStream();
        InputStream in = channel.getInputStream();
        InputStream extIn = channel.getExtInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        System.out.println("in: " + reader.readLine());
        BufferedReader extReader = new BufferedReader(new InputStreamReader(extIn));
        System.out.println("extIn: " + extReader.readLine());
        in.close();
        extIn.close();
        out.close();

    }
}
