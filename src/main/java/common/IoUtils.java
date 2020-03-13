package common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class IoUtils {

    public static byte[] readAllAndClose(InputStream in) throws IOException {
        ByteBuffer result = ByteBuffer.wrap(new byte[1024]);
        while (true) {
            if(result.remaining() == 0){
                result = ByteBuffer.wrap(Arrays.copyOf(result.array(),
                        result.capacity() * 2));
            }
            int readLength = in.read(result.array(), result.position(), result.remaining());
            if(readLength == -1){
                break;
            }
            result.position(result.position() + readLength);
        }
        return Arrays.copyOf(result.array(), result.position());
    }
}
