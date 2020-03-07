package common;

import java.util.Map;

/**
 * 储存一个键值对， 注意不要滥用，推荐只用来存有对应关系的两个值
 * @param <K>
 * @param <V>
 */
public class Pair<K, V> {

    private K left = null;
    private V right = null;

    public Pair(K left, V right) {
        this.left = left;
        this.right = right;
    }

    public K getLeft() {
        return left;
    }

    public V getRight() {
        return right;
    }

}
