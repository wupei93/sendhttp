import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestSomewhat {
    @Test
    public void testRemove(){
        Map<String, List<String>> candidates = new HashMap<>();
        List<String> lines = new ArrayList<String>();
        lines.add("hello");
        lines.add("world");
        candidates.put("key", lines);
        System.out.println(candidates.remove("key"));
    }
}
