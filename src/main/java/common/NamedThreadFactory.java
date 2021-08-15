package common;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.ThreadFactory;

public class NamedThreadFactory {

    public static ThreadFactory create(String prefix) {
        return new ThreadFactoryBuilder()
                .setNameFormat(prefix + "-%03d")
                .build();
    }

    public static ThreadFactory create(Class clazz){
        return create(clazz.getSimpleName());
    }
}