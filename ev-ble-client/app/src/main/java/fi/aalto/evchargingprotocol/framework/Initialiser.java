package fi.aalto.evchargingprotocol.framework;

import android.content.Context;
import android.system.ErrnoException;
import android.util.Log;

import com.goterl.lazycode.lazysodium.LazySodiumAndroid;
import com.goterl.lazycode.lazysodium.SodiumAndroid;

import java.io.IOException;

public class Initialiser {

    public static LazySodiumAndroid androidSodium;

    private Initialiser() {}

    public static void init(Context context) throws IOException, ErrnoException {
        IndyInitialiser.initialise(context, true);
        Log.i(Initialiser.class.toString(), "Indy library initialised!");

        // From https://docs.lazycode.co/lazysodium/usage/getting-started#how-lazysodium-names-its-functions
        Initialiser.androidSodium = new LazySodiumAndroid(new SodiumAndroid());
        Log.i(Initialiser.class.toString(), "NaCl Java binding configured!");
    }
}
