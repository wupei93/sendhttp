package common;

public class Urls {

    /**
     * 与http://`hostname -i`:9101/index 首页中的dump all key 作用相同
     * @param dtKey
     * @param ip
     * @param params
     * @return
     */
    public static String queryInAllTable(DtKey dtKey, String ip, Pair... params){
        String level;
        switch (dtKey.getDtType()){
            case CT:
            case BR:
                level = String.valueOf(1);
                break;
            default:
                level = String.valueOf(0);
                break;
        }
        return getBaseUrlBuilder(ip)
                .appendPath(UrlConstant.DIAGNOSTIC)
                .appendPath(dtKey.getDtType().toString())
                .appendPath(level)
                .appendPath(UrlConstant.DumpAllKeys)
                .appendPath(dtKey.toString())
                .appendParams(params)
                .build();
    }

    private static UrlBuilder getBaseUrlBuilder(String ip){
        return UrlBuilder.getBuilder(UrlConstant.HTTP + ip + UrlConstant.PORT);
    }
}
