package fi.aalto.indy_utils;

import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.pool.Pool;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;

public final class PoolUtils {

    private static final String POOL_NAME = "SOFIE";
    private static final String POOL_CONFIG_PATH = IndyUtils.getPoolConfigPath();
    private static JSONObject POOL_CONFIG;

    static {
        try {
            POOL_CONFIG = new JSONObject().put("genesis_txn", PoolUtils.POOL_CONFIG_PATH);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private PoolUtils() {}

    public static void createSOFIEPoolConfig() throws IndyException {
        try {
            Pool.createPoolLedgerConfig(PoolUtils.POOL_NAME, PoolUtils.POOL_CONFIG.toString()).get();
            Pool.setProtocolVersion(2).get();
        } catch ( InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException ignored) {}         //Config file already exists
    }

    public static Pool connectToSOFIEPool() throws IndyException {
        Pool pool = null;
        try {
            pool = Pool.openPoolLedger(PoolUtils.POOL_NAME, PoolUtils.POOL_CONFIG.toString()).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return pool;
    }
}
