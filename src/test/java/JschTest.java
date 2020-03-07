public class JschTest {
    public static void main(String[] args){
        /*Session session = null;
        BufferedReader reader = null;
        try {
            session = connect("10.243.20.15");
            reader = SshUtils.execCmd(session, "hostname -i");
            String line = null;
            reader.lines().forEach(System.out::println);
            while ((line = reader.readLine())!= null){
                System.out.println(line);
            }
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
        }*/
    }
}
