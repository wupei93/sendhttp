package common;

import java.io.IOException;
import java.nio.channels.FileChannel;

public class FileUtils {

    public static void transferAll(FileChannel src, FileChannel dist){
        try {
            long remaining = src.size();
            while(remaining > 0){
                remaining -= dist.transferFrom(src, dist.size(), remaining);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
