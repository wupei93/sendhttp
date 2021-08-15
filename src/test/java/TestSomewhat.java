import org.junit.Test;

import java.io.IOException;

public class TestSomewhat {
    @Test
    public void testRemove(){
        C c = new C();
        c.setI(10);
        System.out.println(c.getI());
    }

    @Test
    public void testExcept() throws Exception{
            try{
                execute();
            } catch(IOException e){
                execute1 (e);
            }catch(Exception e){
                e.printStackTrace();
            }
    }

    private void execute () throws IOException{
        throw new IOException();
    }

    private void execute1(IOException e) throws IOException{
        throw new IOException(e);
    }


}


class P {

    public int getI() {
        return i;
    }

    int i = 1;


}

class C extends P {

    public void setI(int a) {
        this.i = a;
    }
}