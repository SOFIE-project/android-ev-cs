package fi.aalto.evchargingprotocol.framework;

import android.content.Context;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;

import androidx.annotation.RequiresApi;

import org.hyperledger.indy.sdk.LibIndy;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

class IndyInitialiser {

    private IndyInitialiser() {}

    private static final String INDY_EXTERNAL_STORAGE_ENV_KEY = "EXTERNAL_STORAGE";
    private static final String LIBINDY_LIBRARY_NAME = "indy";

    private static String LIBINDY_PATH;

    private static JSONArray INDY_POOL_CONFIG;

    static {
        try {
            INDY_POOL_CONFIG = new JSONArray()
                    .put(new JSONObject("{\"txn\": {\"metadata\": {\"from\": \"FLFwPZYTJghhYMvkF6YNMSZvzFbEV4T8Nyuzng4Z46Wj\"}, \"type\": \"0\", \"data\": {\"dest\": \"4EfAMP83sgDCdqUXh9cdkNLgjNxyRa7qfTxVEoy4HRWU\", \"data\": {\"node_port\": 9701, \"services\": [\"VALIDATOR\"], \"node_ip\": \"195.148.124.246\", \"blskey_pop\": \"QmyhR9fNcGVeDpfEiQ4cdFXpkPK6iwmvFUFAFpmj8LCACuRhaetQ3cU3XSYXoDoAnhMJnmx3xhPoqcBqS5g8ZivuwX3jfBV1TaTR4j1kbjCMz9Xrnug2jknaBihsRDJ7u8wowCk86JUgcE9tq33EbR7xHEDoQiS7HmKv8e2bLDiL2K\", \"blskey\": \"2id7os2Teh1Q48opfQdj9PrUVVnsKHpgAnCtKLU4FSxKSyuNnrkMYv6BsubTAs25aqCNfSqAUFF5tkNcUHLtCYWpt57PrKoFZ6FfJ4ED9kqz7ojxdpPm1CM3jwKvE1ENJ357rvQnuN1EStg3181G2YnQJ4WvDgADh5nM1n5hSAYrM28\", \"alias\": \"Aalto-NODE\", \"client_ip\": \"195.148.124.246\", \"client_port\": 9702}}}, \"reqSignature\": {}, \"ver\": \"1\", \"txnMetadata\": {\"seqNo\": 1, \"txnId\": \"3814fb873831aa1d888893e032653d6d0655cb98c8652149c70c3604b027ffa6\"}}"))
                    .put(new JSONObject("{\"txn\": {\"metadata\": {\"from\": \"4UhjCPEcVEuAnTfB6eyhRk43XAKQE7TEjhJDsP26Lo6D\"}, \"type\": \"0\", \"data\": {\"dest\": \"QMitU1BErPWohhUMEhqrRVD8NnUr45w34gXmmwkZiVN\", \"data\": {\"node_port\": 9701, \"services\": [\"VALIDATOR\"], \"node_ip\": \"195.251.234.25\", \"blskey_pop\": \"RETeqzksiz6dLew4FJC3b7zKriKxki8remyw3D57PPNEHyzMpr35oETKnzZDc5QsEpHvXffLPzSgJk5Uwn4384WhpXBEwuRx1eVWUMa7jqPYzecqyFN1BnUWCVyo6EQHCA5K6Rq13byAwSJGcByguEHYuSSMd5iGN4MFUpj9ASYu3u\", \"blskey\": \"4EXrgewHbExssbwu48fdiRsXnfvxey76FKZgQboUnik3FFLpxzhHR1M8fQ7RFZ1BYirBcxq44J7K7hkSiMTF9pdsJAfmRF4KNpyzGRpcEDj8fESVA6enAzaexPTqZ3Hc6W7HwPKoCPrnLGDrUjf1BGeCGBPdejdbxcDju9ywo19EPMT\", \"alias\": \"AUEB-NODE\", \"client_ip\": \"195.251.234.25\", \"client_port\": 9702}}}, \"reqSignature\": {}, \"ver\": \"1\", \"txnMetadata\": {\"seqNo\": 2, \"txnId\": \"e0380e5496e459514b8d7aaa6998d0c2701db2a939eee02e759ad5cb7b97d751\"}}"))
                    .put(new JSONObject("{\"txn\": {\"metadata\": {\"from\": \"7GUpAMN6snG3UqaVRsSS5yHZVcvMnKT77MWEvcqSMXki\"}, \"type\": \"0\", \"data\": {\"dest\": \"4kL718dknAJCpuS2nrZRSMJmfyhVCLv2GiW9NUXDUWrv\", \"data\": {\"node_port\": 9701, \"services\": [\"VALIDATOR\"], \"node_ip\": \"34.251.67.7\", \"blskey_pop\": \"QrXjuWUga3UvCEZyL7mWNnXobRBq2bxq8BTCpUFDo8mt3FimMV8TrcnyM1CcGEiQ83D9V66U9zpUfR7a5THk6LwFKcoxF1M7GXk1MDiPaRrBe29zgX8cEbk46UqEtvzKnxV83MTiJoPK2kasXjt8w7GksKH36DeYVBPfEyuGgE8Fm9\", \"blskey\": \"4U3xqQaFZ5kw4PRASwdGPfZFBZYYwu8o9L55VJu4x7mGVkq5jpxFPqETTPSV3X5L8t5siHupn37crwmchUuGXrcaAe9a3o76rHvgzbAq1sqH8xjbwsFUo5RgHswd2RkgChuwWTFsPXHnXu8tQBryEiufoW2inzztZdzJGP583TyeBL5\", \"alias\": \"LMF-NODE\", \"client_ip\": \"34.251.67.7\", \"client_port\": 9702}}}, \"reqSignature\": {}, \"ver\": \"1\", \"txnMetadata\": {\"seqNo\": 3, \"txnId\": \"a2f544df0147d4f2b05eb0ba877ac87dd5f0443986ae12aeaa556827ce9922cc\"}}"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    static void initialise(Context context, boolean force) throws IOException, ErrnoException {
        IndyInitialiser.initialiseDirectories(context);
    }

    private static void initialiseDirectories(Context c) throws IOException, ErrnoException {
        File externalFilesDir = c.getExternalFilesDir(null);
        String externalFilesDirPath = externalFilesDir.getAbsolutePath();
        IndyInitialiser.LIBINDY_PATH = externalFilesDirPath;

        Os.setenv(IndyInitialiser.INDY_EXTERNAL_STORAGE_ENV_KEY, externalFilesDirPath, true);

        System.loadLibrary(IndyInitialiser.LIBINDY_LIBRARY_NAME);
        IndyInitialiser.writePoolConfigFile();
        LibIndy.init();
    }

    private static void writePoolConfigFile() throws IOException {
        File poolsFolder = new File(IndyInitialiser.getPoolsPath());
        if (poolsFolder.exists()) {
            return;
        }
        poolsFolder.mkdirs();
        String formattedFileSyntax = IndyInitialiser.formatConfigJSONForFileWrite(IndyInitialiser.INDY_POOL_CONFIG);

        String poolConfigFilePath = IndyInitialiser.getPoolConfigPath();
        FileWriter writer = null;
        try {
            writer = new FileWriter(poolConfigFilePath);
            writer.write(formattedFileSyntax);
        } catch (IOException e) {
            throw e;
        } finally {
            if (writer != null) {
                writer.flush();
                writer.close();
            }
        }
    }

    private static String formatConfigJSONForFileWrite(JSONArray nodesConfiguration) {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < nodesConfiguration.length(); i++) {
            try {
                JSONObject nodeConfig = nodesConfiguration.getJSONObject(i);
                builder.append(nodeConfig.toString());
                builder.append("\n");
            } catch (JSONException ignored) {
            }
        }

        return builder.toString();
    }

    static String getPoolsPath() {
        return IndyInitialiser.LIBINDY_PATH + "/pools";
    }

    static String getPoolConfigPath() {
        return IndyInitialiser.getPoolsPath() + "/pool_transactions_genesis";
    }

    static String getWalletsPath() {
        return IndyInitialiser.LIBINDY_PATH + "/wallets";
    }
}
