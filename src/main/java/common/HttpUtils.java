package common;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpUtils {

    private static Pattern httpPattern = Pattern.compile("http://.*");
    private static Pattern aHrefPattern = Pattern.compile("(.*<a href=\")(.+)(\">.*)");

    public static boolean isHttpUrl(String url){
        if(url == null){
            return false;
        }
        return httpPattern.matcher(url).matches();
    }

    public static Optional<String> fetchHttpUrl(String rawStr){
        Matcher matcher = aHrefPattern.matcher(rawStr);
        String url = null;
        if (matcher.find()){
            url = matcher.group(2);
        }
        if(isHttpUrl(url)){
            return Optional.of(url);
        }
        return Optional.empty();
    }
}
