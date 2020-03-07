package common;

import com.jcraft.jsch.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class SshUtils {
    private final static String DEFAULT_USERNAME = "admin";
    private final static String DEFAULT_PASSWD = "ChangeMe";
    private final static int DEFAULT_POST = 22;
    private final static JSch jsch = new JSch();

    public static Session connect(String host) throws Exception {
        return connect(DEFAULT_USERNAME, DEFAULT_PASSWD, host, DEFAULT_POST);
    }

    /**配置连接
     * @param user
     * @param passwd
     * @param host
     * @param post
     * @throws Exception
     */
    public static Session connect(String user, String passwd, String host, int post) throws Exception {
        Session session = jsch.getSession(user, host, post);
        if (session == null) {
            throw new Exception("session is null");
        }
        session.setPassword(passwd);
        java.util.Properties config = new java.util.Properties();
        //第一次登陆
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        try {
            session.connect(30000);
        } catch (Exception e) {
            throw new Exception("连接远程端口无效或用户名密码错误");
        }
        return session;
    }

    /**
     * @description 执行shell命令
     * @param command shell 命令
     * @throws Exception
     */
    public static BufferedReader execCmd(Session session, String command) throws Exception {
        command += "\n";
        System.out.print("执行命令：" + command);
        BufferedReader reader = null;
        Channel channel = null;
        try {
            /** 可选
            *    session、shell、exec、x11、auth-agent@openssh.com、
             *    direct-tcpip、forwarded-tcpip、sftp、subsystem
            */
            channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);

            //channel.setInputStream(null);
            ((ChannelExec) channel).setErrStream(System.out);

            channel.connect();
            InputStream in = channel.getExtInputStream();
            reader = new BufferedReader(new InputStreamReader(in));
            reader.lines().forEach(System.out::println);
            String line = null;
            while ((line = reader.readLine())!= null){
                System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSchException e) {
            e.printStackTrace();
        } finally {
            channel.disconnect();
        }
        return reader;
    }
}
