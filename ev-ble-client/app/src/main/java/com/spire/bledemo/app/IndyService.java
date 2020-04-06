package com.spire.bledemo.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
//import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.View;

//import org.hyperledger.indy.sdk.IndyException;
//import org.hyperledger.indy.sdk.anoncreds.Anoncreds;
//import org.hyperledger.indy.sdk.anoncreds.AnoncredsResults;
//import org.hyperledger.indy.sdk.anoncreds.CredentialsSearchForProofReq;
//import org.hyperledger.indy.sdk.did.Did;
//import org.hyperledger.indy.sdk.did.DidJSONParameters;
//import org.hyperledger.indy.sdk.did.DidResults;
//import org.hyperledger.indy.sdk.ledger.Ledger;
//import org.hyperledger.indy.sdk.pool.Pool;
//import org.hyperledger.indy.sdk.wallet.Wallet;
//import indy_utils.IndyUtils;



import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;


public class IndyService {
    Context mContext;

    IndyService(Context context) {
        mContext = context;
    }

    public String createEVdid() {
        //  gatt, tx, timing
//        create EV did
//        create proof request
//        sign with ev did
//        encrypt with cs did

        String message = "did:5e6KbhMAsjt9YuUggfCTXc{\"prover_did\":\"2J9gVaxVq9sZtm6TfuWsZL\",\"cred_def_id\":\"78D2cjm9e8Fp663qZkH287:3:CL:78D2cjm9e8Fp663qZkH287:2:CSO-Info-Credential-Schema:1.0:CSO-Info-Credential-Definition\",\"blinded_ms\":{\"u\":\"7051628447641906269660413424679699604085284254916793314553260029679475880574882603579418828403439207347991143342434024126777113987401934441349559930550972775276547245305229998350534158572877694011987148223578331546087133803678520067775002155569366012545416754641083810491358257993543147499835241182146513171320291645964928119895055952244071038135138115510276459309777517980843814972182622070261030945840421532141964745411100732421013622651122501752487200642260164178933295491370412714440747411326791643680171393932624269467578558856936273752860270078812267335307329247293132448024210453434484471431359746714700363146\",\"ur\":null,\"hidden_attributes\":[\"master_secret\"],\"committed_attributes\":{}},\"blinded_ms_correctness_proof\":{\"c\":\"57305588521977403673157249754658648905404168628738022583345346230776859346080\",\"v_dash_cap\":\"1420663190052084429508313494836373894992430295778070681812926445260662868501101258467600866046813385936575311211110687102745873760404641951788162613966401491166159808956850646978315738350489012190571349477571231818092414719495849642605881992379646399704117177158669460974658383961373805356361746752070163838887780392981385687854296392430588543073503909443336246949817321226795319681335688249490836088424327871549630618863578072174937071901910898047582295700799627163327065957267806872106532705535560620584400082505485190066686281050346219759523976472527594315486331373239117392197630755661069560183627865368763817650980659322456891204018452683775950779762809796793684546519541065401694413040816414624954633042559967323\",\"m_caps\":{\"master_secret\":\"27791070757020635025126800232775518918630876733827965651738962216581839791279281811422502294960541276749109604173635828616108273720573313455904442970987456055453499178514791515567\"},\"r_caps\":{}},\"nonce\":\"565033035383495486896767\"}";
        return message;
    }

    public void parseCsDid1(String data) {
       // new IndyInitialisationTask().execute();
    }

    public void parseCSdid2AndCSOownershipProof(String payload) {
//        unmarshall did, proof and proof request ---- gson?
//        save did
//        verify proof
//        generate proof for proof request(maybe proof request need not be sent over air?)
//        sign proof
//        encrypt proof
//        send proof
    }

    public String createProof() {
        return "Proof";
    }

    public void sendchargeStartMessage() {

    }

    public void releaseMicroCharge() {

    }


/*
//    @SuppressLint("StaticFieldLeak")
//    final class IndyInitialisationTask extends AsyncTask<Void, Void, Void> {
//        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
//        @Override
//        protected Void doInBackground(Void... voids) {
//            initialiseTestObjects();
//            return null;                // null must be returned, as in https://www.quora.com/Why-does-doInBackground-in-the-AsyncTask-class-need-to-return-null-even-though-it%E2%80%99s-returning-type-is-set-to-void/answer/Vishal-Ratna
//        }
//    }
//
//    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
//    private void initialiseTestObjects() {
//        try {
//            Log.i(this.getClass().toString(), "Initialising Indy context...");
//            IndyUtils.initialiseIndyDirectory(mContext);
//
//            JSONObject evWalletConfig = new JSONObject().put("id", "evWallet");
//            JSONObject csoWalletConfig = new JSONObject().put("id", "csoWallet");
//            JSONObject csWalletConfig = new JSONObject().put("id", "csWallet");
//            JSONObject csoStewardWalletConfig = new JSONObject().put("id", "csoStewardWallet");
//            JSONObject testWalletCredentials = new JSONObject().put("key", "password");
//
//            String csoStewardDIDSeed = "F37DeEe0ba861dFdca5bBF466DAcaB11";                                  //The DID generated from this seed has already been added (externally) to the pool as a STEWARD.
//
//            try {
//                Log.i(this.getClass().toString(), "Creating EV, CSO, CS and CSO steward wallets...");
//                Wallet.createWallet(evWalletConfig.toString(), testWalletCredentials.toString()).get();
//                Wallet.createWallet(csoWalletConfig.toString(), testWalletCredentials.toString()).get();
//                Wallet.createWallet(csWalletConfig.toString(), testWalletCredentials.toString()).get();
//                Wallet.createWallet(csoStewardWalletConfig.toString(), testWalletCredentials.toString()).get();
//            } catch (InterruptedException | ExecutionException | IndyException ignored) {
//            } finally {
//                Log.i(this.getClass().toString(), "Opening EV wallet...");
//                Wallet evWallet = Wallet.openWallet(evWalletConfig.toString(), testWalletCredentials.toString()).get();
//                Log.i(this.getClass().toString(), "Opening CSO wallet...");
//                Wallet csoWallet = Wallet.openWallet(csoWalletConfig.toString(), testWalletCredentials.toString()).get();
//                Log.i(this.getClass().toString(), "Opening CS wallet...");
//                Wallet csWallet = Wallet.openWallet(csWalletConfig.toString(), testWalletCredentials.toString()).get();
//                Log.i(this.getClass().toString(), "Opening CSO steward wallet...");
//                Wallet csoStewardWallet = Wallet.openWallet(csoStewardWalletConfig.toString(), testWalletCredentials.toString()).get();
//
//                String poolName = "SOFIE";
//                String poolConfigPath = IndyUtils.getPoolConfigPath();
//                JSONObject poolCreationConfig = new JSONObject().put("genesis_txn", poolConfigPath);
//                try {
//                    Log.i(this.getClass().toString(), "Creating test pool configuration...");
//                    Pool.createPoolLedgerConfig(poolName, poolCreationConfig.toString()).get();
//                    Log.i(this.getClass().toString(), "Test pool configuration created.");
//                } catch (InterruptedException | ExecutionException | IndyException ignored) {
//                } finally {
//                    Log.i(this.getClass().toString(), "Opening test pool...");
//                    Pool.setProtocolVersion(2).get();
//                    Pool testPool = Pool.openPoolLedger(poolName, null).get();
//                    Log.i(this.getClass().toString(), "Test pool opened.");
//
//                    Log.i(this.getClass().toString(), "Creating EV DID...");
//                    DidResults.CreateAndStoreMyDidResult evDID = Did.createAndStoreMyDid(evWallet, new JSONObject().toString()).get();         //A new DID is generated each time, as of today.
//                    Log.i(this.getClass().toString(), "EV DID created.");
//
//                    Log.i(this.getClass().toString(), "Creating CSO DID...");
//                    DidResults.CreateAndStoreMyDidResult csoDID = Did.createAndStoreMyDid(csoWallet, new JSONObject().toString()).get();         //A new DID is generated each time, as of today.
//                    Log.i(this.getClass().toString(), String.format("CSO DID created: %s", csoDID.getDid()));
//
//                    Log.i(this.getClass().toString(), "Creating CS DID...");
//                    DidResults.CreateAndStoreMyDidResult csDID = Did.createAndStoreMyDid(csWallet, new JSONObject().toString()).get();         //A new DID is generated each time, as of today.
//                    Log.i(this.getClass().toString(), "CS DID created.");
//
//                    Log.i(this.getClass().toString(), "Calculating CSO steward DID...");
//                    DidJSONParameters.CreateAndStoreMyDidJSONParameter csoStewardDIDInfo = new DidJSONParameters.CreateAndStoreMyDidJSONParameter(null, csoStewardDIDSeed, null, null);
//                    DidResults.CreateAndStoreMyDidResult csoStewardDID = Did.createAndStoreMyDid(csoStewardWallet, csoStewardDIDInfo.toJson()).get();
//                    Log.i(this.getClass().toString(), String.format("CSO steward DID calculated: %s - %s", csoStewardDID.getDid(), csoStewardDID.getVerkey()));
//
//                    Log.i(this.getClass().toString(), "Registering CSO identity on the ledger...");
//                    JSONObject csoRegistrationNymRequest = new JSONObject(Ledger.buildNymRequest(csoStewardDID.getDid(), csoDID.getDid(), csoDID.getVerkey(), "CSO", "ENDORSER").get());
//                    JSONObject requestResult = new JSONObject(Ledger.signAndSubmitRequest(testPool, csoStewardWallet, csoStewardDID.getDid(), csoRegistrationNymRequest.toString()).get());
//                    if (!requestResult.getString("op").equals("REPLY")) {
//                        Log.e(this.getClass().toString(), "Writing to ledger failed.");
//                        return;
//                    }
//                    Log.i(this.getClass().toString(), String.format("CSO identity registered on the ledger with receipt: %s", requestResult.toString()));
//
//
//                    String csoInfoCredentialSchemaName = "CSO-Info-Credential-Schema";
//                    String csoInfoCredentialSchemaVersion = "1.0";
//                    JSONArray csoInfoCredentialSchemaAttributes = new JSONArray(new String[]{"CSO"});
//                    Log.i(this.getClass().toString(), "Creating credential schema for CS-CSO info and writing to the ledger...");
//                    AnoncredsResults.IssuerCreateSchemaResult csoInfoCredentialSchema = Anoncreds.issuerCreateSchema(csoDID.getDid(), csoInfoCredentialSchemaName, csoInfoCredentialSchemaVersion, csoInfoCredentialSchemaAttributes.toString()).get();
//                    Log.i(this.getClass().toString(), String.format("Credential schema for CS-CSO info created: %s", csoInfoCredentialSchema.getSchemaJson()));
//                    JSONObject csoInfoCredentialsSchemaNymRequest = new JSONObject(Ledger.buildSchemaRequest(csoDID.getDid(), csoInfoCredentialSchema.getSchemaJson()).get());
//                    requestResult = new JSONObject(Ledger.signAndSubmitRequest(testPool, csoWallet, csoDID.getDid(), csoInfoCredentialsSchemaNymRequest.toString()).get());
//                    Log.i(this.getClass().toString(), String.format("Credential schema written to the ledger with receipt: %s", requestResult.toString()));
//                    if (!requestResult.getString("op").equals("REPLY")) {
//                        Log.e(this.getClass().toString(), "Writing to ledger failed.");
//                        return;
//                    }
//
//                    JSONObject csoInfoCredentialSchemaNymGetRequest = new JSONObject(Ledger.buildGetSchemaRequest(csoDID.getDid(), csoInfoCredentialSchema.getSchemaId()).get());
//                    requestResult = new JSONObject(Ledger.submitRequest(testPool, csoInfoCredentialSchemaNymGetRequest.toString()).get());
//                    JSONObject csoInfoCredentialSchemaFromLedger = new JSONObject(Ledger.parseGetSchemaResponse(requestResult.toString()).get().getObjectJson());
//
//                    String csoInfoCredentialDefinitionName = "CSO-Info-Credential-Definition";
//                    Log.i(this.getClass().toString(), "Creating credential definition for CS-CSO info and writing to the ledger...");
//                    AnoncredsResults.IssuerCreateAndStoreCredentialDefResult csoInfoCredentialDefinition = Anoncreds.issuerCreateAndStoreCredentialDef(csoWallet, csoDID.getDid(), csoInfoCredentialSchemaFromLedger.toString(), csoInfoCredentialDefinitionName, null, null).get();
//                    Log.i(this.getClass().toString(), String.format("Credential definition for CS-CSO info created: %s", csoInfoCredentialDefinition.getCredDefJson()));
//                    JSONObject csoInfoCredentialDefinitionNymRequest = new JSONObject(Ledger.buildCredDefRequest(csoDID.getDid(), csoInfoCredentialDefinition.getCredDefJson()).get());
//                    requestResult = new JSONObject(Ledger.signAndSubmitRequest(testPool, csoWallet, csoDID.getDid(), csoInfoCredentialDefinitionNymRequest.toString()).get());
//                    if (!requestResult.getString("op").equals("REPLY")) {
//                        Log.e(this.getClass().toString(), "Writing to ledger failed.");
//                        return;
//                    }
//                    Log.i(this.getClass().toString(), String.format("Credential definition written to the ledger with receipt: %s", requestResult.toString()));
//
//
//                    Log.i(this.getClass().toString(), "Creating credential offer for CS-CSO info...");
//                    JSONObject csoInfoCredentialOffer = new JSONObject(Anoncreds.issuerCreateCredentialOffer(csoWallet, csoInfoCredentialDefinition.getCredDefId()).get());
//                    Log.i(this.getClass().toString(), String.format("Credential offer for CS-CSO info created: %s", csoInfoCredentialOffer.toString()));
//
//                    Log.i(this.getClass().toString(), "Creating master secret for CS wallet...");
//                    final String csMasterSecretID = "csMasterSecret";
//                    try {
//                        Anoncreds.proverCreateMasterSecret(csWallet, csMasterSecretID).get();
//                    } catch (InterruptedException | ExecutionException | IndyException ignored) {
//                    } finally {
//                        Log.i(this.getClass().toString(), String.format("Master secret for CS wallet created: %s", csMasterSecretID));
//                        Log.i(this.getClass().toString(), "Creating credential request for CS-CSO info...");
//                        AnoncredsResults.ProverCreateCredentialRequestResult csoInfoCredentialRequest = Anoncreds.proverCreateCredentialReq(csWallet, csDID.getDid(), csoInfoCredentialOffer.toString(), csoInfoCredentialDefinition.getCredDefJson(), csMasterSecretID).get();
//                        Log.i(this.getClass().toString(), String.format("Credential request for CS-CSO info created: %s", csoInfoCredentialRequest.getCredentialRequestJson()));
//
//                        JSONObject csoInfoCredentialCSOAttribute = new JSONObject().put("raw", csoDID.getDid()).put("encoded", String.format("%d", csoDID.getDid().hashCode()));
//
//                        JSONObject csoInfoCredentialContent = new JSONObject().put("CSO", csoInfoCredentialCSOAttribute);
//                        Log.i(this.getClass().toString(), "Creating credential for CS-CSO info...");
//                        AnoncredsResults.IssuerCreateCredentialResult csoInfoCredential = Anoncreds.issuerCreateCredential(csoWallet, csoInfoCredentialOffer.toString(), csoInfoCredentialRequest.getCredentialRequestJson(), csoInfoCredentialContent.toString(), null, -1).get();
//                        Log.i(this.getClass().toString(), String.format("Credential for CS-CSO info created: %s", csoInfoCredential.getCredentialJson()));
//
//                        Log.i(this.getClass().toString(), "Saving credential for CS-CSO info into CS wallet...");
//                        Anoncreds.proverStoreCredential(csWallet, null, csoInfoCredentialRequest.getCredentialRequestMetadataJson(), csoInfoCredential.getCredentialJson(), csoInfoCredentialDefinition.getCredDefJson(), csoInfoCredential.getRevocRegDeltaJson()).get();
//                        Log.i(this.getClass().toString(), "Credential for CS-CSO info saved into CS wallet");
//
//                        Log.i(this.getClass().toString(), "Creating CSO Ownership proof request...");
//                        JSONObject csoOwnershipProofRequest = new JSONObject()
//                                .put("name", "CSO Ownership Proof Request")
//                                .put("version", "1.0")
//                                .put("nonce", Anoncreds.generateNonce().get())
//                                .put("requested_predicates", new JSONObject()
//                                        .put("cso_id_min", new JSONObject()
//                                                .put("name", "CSO")
//                                                .put("p_type", ">=")
//                                                .put("p_value", csoDID.getDid().hashCode())
//                                                .put("restrictions", new JSONArray()
//                                                        .put(new JSONObject()
//                                                                .put("cred_def_id", csoInfoCredentialDefinition.getCredDefId())
//                                                                .put("issuer_did", csoDID.getDid())
//                                                        )
//                                                )
//                                        )
//                                        .put("cso_id_max", new JSONObject()
//                                                .put("name", "CSO")
//                                                .put("p_type", "<=")
//                                                .put("p_value", csoDID.getDid().hashCode())
//                                                .put("restrictions", new JSONArray()
//                                                        .put(new JSONObject()
//                                                                .put("cred_def_id", csoInfoCredentialDefinition.getCredDefId())
//                                                                .put("issuer_did", csoDID.getDid())
//                                                        )
//                                                )
//                                        )
//                                );
//                        Log.i(this.getClass().toString(), String.format("CSO Ownership proof request created: %s", csoOwnershipProofRequest));
//
//                        Log.i(this.getClass().toString(), "Selecting credentials for CSO Ownership proof request...");
//                        CredentialsSearchForProofReq credentialsSearch = CredentialsSearchForProofReq.open(csWallet, csoOwnershipProofRequest.toString(), null).get();
//                        JSONObject credentialForCSOMin = new JSONObject(new JSONArray(credentialsSearch.fetchNextCredentials("cso_id_min", 1).get()).getJSONObject(0).getString("cred_info"));
//                        JSONObject credentialForCSOMax = new JSONObject(new JSONArray(credentialsSearch.fetchNextCredentials("cso_id_max", 1).get()).getJSONObject(0).getString("cred_info"));
//                        credentialsSearch.close();
//                        JSONObject proofCredentials = new JSONObject()
//                                .put("self_attested_attributes", new JSONObject())
//                                .put("requested_attributes", new JSONObject())
//                                .put("requested_predicates", new JSONObject()
//                                        .put("cso_id_min", new JSONObject()
//                                                .put("cred_id", credentialForCSOMin.getString("referent"))
//                                        )
//                                        .put("cso_id_max", new JSONObject()
//                                                .put("cred_id", credentialForCSOMax.getString("referent"))
//                                        )
//                                );
//                        Log.i(this.getClass().toString(), String.format("Credentials for CSO Ownership proof request selected: %s", proofCredentials));
//
//                        Log.i(this.getClass().toString(), "Creating proof for CSO Ownership proof request...");
//                        JSONObject csoInfoProof = new JSONObject(Anoncreds.proverCreateProof(csWallet, csoOwnershipProofRequest.toString(), proofCredentials.toString(), csMasterSecretID, new JSONObject().put(csoInfoCredentialSchema.getSchemaId(), new JSONObject(csoInfoCredentialSchema.getSchemaJson())).toString(), new JSONObject().put(csoInfoCredentialDefinition.getCredDefId(), new JSONObject(csoInfoCredentialDefinition.getCredDefJson())).toString(), new JSONObject().toString()).get());
//                        Log.i(this.getClass().toString(), String.format("Proof for CSO Ownership proof request created: %s", csoInfoProof));
//
//                        Log.i(this.getClass().toString(), "Verifying proof presented by the CS");
//                        boolean proofResult = Anoncreds.verifierVerifyProof(csoOwnershipProofRequest.toString(), csoInfoProof.toString(), new JSONObject().put(csoInfoCredentialSchema.getSchemaId(), new JSONObject(csoInfoCredentialSchema.getSchemaJson())).toString(), new JSONObject().put(csoInfoCredentialDefinition.getCredDefId(), new JSONObject(csoInfoCredentialDefinition.getCredDefJson())).toString(), new JSONObject().toString(), new JSONObject().toString()).get();
//                        Log.i(this.getClass().toString(), String.format("Proof presented by the CS verified with result: %b", proofResult));
//
//                        Log.i(this.getClass().toString(), "Closing test pool...");
//                        testPool.close();
//                        Log.i(this.getClass().toString(), "Test pool closed.");
//                        Log.i(this.getClass().toString(), "Closing EV wallet...");
//                        evWallet.close();
//                        Log.i(this.getClass().toString(), "EV wallet closed.");
//                        Log.i(this.getClass().toString(), "Closing CSO wallet...");
//                        csoWallet.close();
//                        Log.i(this.getClass().toString(), "CSO wallet closed.");
//                        Log.i(this.getClass().toString(), "Closing CS wallet...");
//                        csWallet.close();
//                        Log.i(this.getClass().toString(), "CS wallet closed.");
//                        Log.i(this.getClass().toString(), "Closing CSO steward wallet...");
//                        csoStewardWallet.close();
//                        Log.i(this.getClass().toString(), "CSO steward wallet closed.");
//                    }
//                }
//            }
//        } catch (Exception e) {
//            Log.e(this.getClass().toString(), e.getLocalizedMessage());
//            Log.e(this.getClass().toString(), e.getMessage());
//            Log.e(this.getClass().toString(), e.getCause().toString());
//            e.printStackTrace();
//        }
//    }
*/

}

