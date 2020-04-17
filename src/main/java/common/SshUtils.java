package common;

import com.jcraft.jsch.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class SshUtils {
    private final static String DEFAULT_USERNAME = "admin";
    private final static String DEFAULT_PASSWD = "ChangeMe";
    private final static int DEFAULT_POST = 22;
    private final static JSch jsch = new JSch();
    private final static SessionPool sessionPool = new SessionPool().start();

    public static Session getConnection(String host) throws Exception {
        return sessionPool.getConnection(host);
    }

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
    public static Channel execCmd(String host, String command) throws Exception {
        Session session = getConnection(host);
        command += "\n";
        //System.out.println("执行命令：" + command);
        ChannelExec channel = null;
        try {
            /** 可选
             *    session、shell、exec、x11、auth-agent@openssh.com、
             *    direct-tcpip、forwarded-tcpip、sftp、subsystem
             */
            channel = (ChannelExec)session.openChannel("exec");
            channel.setCommand(command);
            channel.connect();
            return channel;
        } catch (JSchException e) {
            e.printStackTrace();
        }
        return null;
    }


    static class SessionPool {
        private static ConcurrentHashMap<String, CloseSessionTask> sessionMap = new ConcurrentHashMap<>();
        private DelayQueue<CloseSessionTask> closeQueue = new DelayQueue<>();
        private boolean running = false;

        public Session getConnection(String host) throws Exception {
            CloseSessionTask closeSessionTask = sessionMap.get(host);
            Session session = null;
            if(closeSessionTask != null){
                closeSessionTask.cancel();
                session = closeSessionTask.session;
            }
            if(session == null || !session.isConnected()){
                session = SshUtils.connect(host);
                System.out.println("established session:"+session+" for "+ host);
                closeSessionTask = new CloseSessionTask(host, session);
                sessionMap.put(host, closeSessionTask);
                closeQueue.add(closeSessionTask);
            }
            return session;
        }

        private SessionPool start(){
            running = true;
            new Thread(() -> {
                while(running){
                    try {
                        closeQueue.take().close();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            }).start();
            return this;
        }

        private void stop(){
            running = false;
        }

        static class CloseSessionTask implements Delayed {
            private String host;
            private Session session;
            private long expireTime;
            private volatile boolean isCancelled;

            private CloseSessionTask(String host, Session session){
                this.host = host;
                this.session = session;
                expireTime = System.currentTimeMillis() + 60000;
            }

            public void cancel() {
                isCancelled = true;
            }

            public void close() {
                if(isCancelled){
                    return;
                }
                sessionMap.remove(host);
                if(session != null){
                    session.disconnect();
                    System.out.println("closed session:"+session+" for " + host);
                }
            }

            @Override
            public long getDelay(TimeUnit unit) {
                return unit.convert(expireTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            }

            @Override
            public int compareTo(Delayed o) {
                return Long.compare(getDelay(TimeUnit.SECONDS), o.getDelay(TimeUnit.SECONDS));
            }
        }
    }

}