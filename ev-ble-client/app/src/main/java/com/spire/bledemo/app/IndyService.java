package com.spire.bledemo.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.hyperledger.indy.sdk.anoncreds.AnoncredsResults;
import org.hyperledger.indy.sdk.did.DidResults;
import org.hyperledger.indy.sdk.pool.Pool;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONObject;

import fi.aalto.indy_utils.CredentialDefinitionUtils;
import fi.aalto.indy_utils.CredentialSchemaUtils;
import fi.aalto.indy_utils.CredentialUtils;
import fi.aalto.indy_utils.DIDUtils;
import fi.aalto.indy_utils.IndyUtils;
import fi.aalto.indy_utils.PoolUtils;
import fi.aalto.indy_utils.ProofUtils;
import fi.aalto.indy_utils.WalletUtils;

//import android.support.annotation.RequiresApi;


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


    @SuppressLint("StaticFieldLeak")
    final class IndyInitialisationTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            initialiseTestObjects();
            return null;                // null must be returned, as in https://www.quora.com/Why-does-doInBackground-in-the-AsyncTask-class-need-to-return-null-even-though-it%E2%80%99s-returning-type-is-set-to-void/answer/Vishal-Ratna
        }
    }

    private void initialiseTestObjects() {
        try {

            // 1. Indy initialisation

            Log.i(this.getClass().toString(), "Initialising Indy context...");
            IndyUtils.initialise(mContext);

            // 2. Wallets creation

            Log.i(this.getClass().toString(), "Creating EV, CSO, CS, DSO and steward wallets...");
            WalletUtils.createEVWallet();
            WalletUtils.createCSWallet();
            WalletUtils.createCSOWallet();
            WalletUtils.createDSOWallet();
            WalletUtils.createStewardWallet();

            // 3. Wallets opening

            Log.i(this.getClass().toString(), "Opening EV wallet...");
            Wallet evWallet = WalletUtils.openEVWallet();
            Log.i(this.getClass().toString(), "Opening CSO wallet...");
            Wallet csoWallet = WalletUtils.openCSOWallet();
            Log.i(this.getClass().toString(), "Opening DSO wallet...");
            Wallet dsoWallet = WalletUtils.openDSOWallet();
            Log.i(this.getClass().toString(), "Opening CS wallet...");
            Wallet csWallet = WalletUtils.openCSWallet();
            Log.i(this.getClass().toString(), "Opening steward wallet...");
            Wallet stewardWallet = WalletUtils.openStewardWallet();

            // 4. Pool configuration + connection

            Log.i(this.getClass().toString(), "Creating test pool configuration...");
            PoolUtils.createSOFIEPoolConfig();
            Log.i(this.getClass().toString(), "Test pool configuration created.");

            Log.i(this.getClass().toString(), "Connecting to SOFIE pool...");
            Pool sofiePool = PoolUtils.connectToSOFIEPool();
            Log.i(this.getClass().toString(), "Connected to SOFIE pool.");

            // 5. DIDs creation

            Log.i(this.getClass().toString(), "Calculating EV DID...");
            DidResults.CreateAndStoreMyDidResult evDID = DIDUtils.createEVDID(evWallet);
            Log.i(this.getClass().toString(), String.format("EV DID calculated: %s - %s", evDID.getDid(), evDID.getVerkey()));

            Log.i(this.getClass().toString(), "Calculating CS DID...");
            DidResults.CreateAndStoreMyDidResult csDID = DIDUtils.createCSDID(csWallet);
            Log.i(this.getClass().toString(), String.format("CS DID calculated: %s - %s", csDID.getDid(), csDID.getVerkey()));

            Log.i(this.getClass().toString(), "Calculating steward DID...");
            DidResults.CreateAndStoreMyDidResult stewardDID = DIDUtils.createStewardDID(stewardWallet);
            Log.i(this.getClass().toString(), String.format("CSO steward DID calculated: %s - %s", stewardDID.getDid(), stewardDID.getVerkey()));

            Log.i(this.getClass().toString(), "Calculating and writing on ledger CSO DID...");
            DidResults.CreateAndStoreMyDidResult csoDID = DIDUtils.createAndWriteCSODID(csoWallet, stewardWallet, stewardDID.getDid(), sofiePool);
            Log.i(this.getClass().toString(), String.format("CSO DID calculated and written on ledger: %s - %s", csoDID.getDid(), csoDID.getVerkey()));

            Log.i(this.getClass().toString(), "Calculating and writing on ledger DSO DID...");
            DidResults.CreateAndStoreMyDidResult dsoDID = DIDUtils.createAndWriteDSODID(dsoWallet, stewardWallet, stewardDID.getDid(), sofiePool);
            Log.i(this.getClass().toString(), String.format("DSO DID calculated and written on ledger: %s - %s", dsoDID.getDid(), dsoDID.getVerkey()));

            // 6. Credential schemas creation

            Log.i(this.getClass().toString(), "Creating and writing on ledger credential schema for CS-CSO info...");
            AnoncredsResults.IssuerCreateSchemaResult csoInfoCredentialSchema = CredentialSchemaUtils.createAndWriteCSOInfoCredentialSchema(csoDID.getDid(), csoWallet, sofiePool);
            Log.i(this.getClass().toString(), String.format("Credential schema for CS-CSO info created and written on ledger."));

            JSONObject csoInfoCredentialSchemaFromLedger = CredentialSchemaUtils.readCredentialSchemaFromLedger(csoDID.getDid(), csoInfoCredentialSchema.getSchemaId(), sofiePool);
            Log.i(this.getClass().toString(), String.format("CS-CSO info credential schema fetched from ledger: %s", csoInfoCredentialSchemaFromLedger));

            Log.i(this.getClass().toString(), "Creating and writing on ledger credential schema for CS-DSO district info...");
            AnoncredsResults.IssuerCreateSchemaResult dsoDistrictCredentialSchema = CredentialSchemaUtils.createAndWriteDSODistrictCredentialSchema(dsoDID.getDid(), dsoWallet, sofiePool);
            Log.i(this.getClass().toString(), String.format("Credential schema for CS-DSO district info created and written on ledger."));

            JSONObject dsoDistrictCredentialSchemaFromLedger = CredentialSchemaUtils.readCredentialSchemaFromLedger(dsoDID.getDid(), dsoDistrictCredentialSchema.getSchemaId(), sofiePool);
            Log.i(this.getClass().toString(), String.format("CS-DSO district info credential schema fetched from ledger: %s", dsoDistrictCredentialSchemaFromLedger));

            // 7. Credential definitions creation

            Log.i(this.getClass().toString(), "Creating and writing on ledger credential definition for CS-CSO info...");
            AnoncredsResults.IssuerCreateAndStoreCredentialDefResult csoInfoCredentialDefinition = CredentialDefinitionUtils.createAndWriteCSOInfoCredentialDefinition(csoDID.getDid(), csoWallet, csoInfoCredentialSchemaFromLedger.getJSONObject("object"), sofiePool);
            Log.i(this.getClass().toString(), String.format("Credential definition for CS-CSO info created and written on ledger."));

            JSONObject csoInfoCredentialDefFromLedger = CredentialDefinitionUtils.readCredentialDefinitionFromLedger(csoDID.getDid(), csoInfoCredentialDefinition.getCredDefId(), sofiePool);
            Log.i(this.getClass().toString(), String.format("CS-CSO info credential definition fetched from ledger: %s", csoInfoCredentialDefFromLedger));

            Log.i(this.getClass().toString(), "Creating and writing on ledger credential definition for CS-DSO district info...");
            AnoncredsResults.IssuerCreateAndStoreCredentialDefResult dsoDistrictCredentialDefinition = CredentialDefinitionUtils.createAndWriteDSODistrictCredentialDefinition(dsoDID.getDid(), dsoWallet, dsoDistrictCredentialSchemaFromLedger.getJSONObject("object"), sofiePool);
            Log.i(this.getClass().toString(), String.format("Credential definition for CS-DSO district info created and written on ledger."));

            JSONObject dsoDistrictCredentialDefFromLedger = CredentialDefinitionUtils.readCredentialDefinitionFromLedger(dsoDID.getDid(), dsoDistrictCredentialDefinition.getCredDefId(), sofiePool);
            Log.i(this.getClass().toString(), String.format("CS-DSO district info credential definition fetched from ledger: %s", dsoDistrictCredentialDefFromLedger));


//                Log.i(this.getClass().toString(), "A");
//                BlobStorageWriter tailsWriter = BlobStorageWriter.openWriter("default", new JSONObject().put("base_dir", IndyUtils.getTailsFilePath(getApplicationContext())).put("uri_pattern", "").toString()).get();
//                Log.i(this.getClass().toString(), "B");
//                Anoncreds.issuerCreateAndStoreRevocReg(csoWallet, csoDID.getDid(), "CL_ACCUM", "tag", csoInfoCredentialFromLedgerResult.getObjectJson(), new JSONObject().toString(), tailsWriter).get();
//                Log.i(this.getClass().toString(), "C");

            // 8. Credential offers creation

            Log.i(this.getClass().toString(), "Creating credential offer for CS-CSO info...");
            JSONObject csoInfoCredentialOffer = CredentialUtils.createCredentialOffer(csoWallet, csoInfoCredentialDefFromLedger.getString("id"));
            Log.i(this.getClass().toString(), String.format("Credential offer for CS-CSO info created: %s", csoInfoCredentialOffer));

            Log.i(this.getClass().toString(), "Creating credential offer for CS-DSO district district info...");
            JSONObject dsoDistrictCredentialOffer = CredentialUtils.createCredentialOffer(dsoWallet, dsoDistrictCredentialDefFromLedger.getString("id"));
            Log.i(this.getClass().toString(), String.format("Credential offer for CS-DSO district info created: %s", dsoDistrictCredentialOffer));


            Log.i(this.getClass().toString(), "Creating master secret for CS wallet...");
            String csMasterSecretID = CredentialUtils.createAndSaveCSMasterSecret(csWallet);
            Log.i(this.getClass().toString(), String.format("Master secret for CS wallet created: %s", csMasterSecretID));

            // 9. Credential requests creation

            Log.i(this.getClass().toString(), "Creating credential request for CS-CSO info...");
            AnoncredsResults.ProverCreateCredentialRequestResult csoInfoCredentialRequest = CredentialUtils.createCSOInfoCredentialRequest(csWallet, csDID.getDid(), csoInfoCredentialOffer, csoInfoCredentialDefFromLedger.getJSONObject("object"), csMasterSecretID);
            Log.i(this.getClass().toString(), String.format("Credential request for CS-CSO info created: %s", csoInfoCredentialRequest.getCredentialRequestJson()));

            Log.i(this.getClass().toString(), "Creating credential request for CS-DSO district info...");
            AnoncredsResults.ProverCreateCredentialRequestResult dsoDistrictCredentialRequest = CredentialUtils.createDSODistrictCredentialRequest(csWallet, csDID.getDid(), dsoDistrictCredentialOffer, dsoDistrictCredentialDefFromLedger.getJSONObject("object"), csMasterSecretID);
            Log.i(this.getClass().toString(), String.format("Credential request for CS-DSO district info created: %s", dsoDistrictCredentialRequest.getCredentialRequestJson()));

            // 10. Credentials creation

            Log.i(this.getClass().toString(), "Creating credential for CS-CSO info...");
            AnoncredsResults.IssuerCreateCredentialResult csoInfoCredential = CredentialUtils.createCSOInfoCredential(csoWallet, csoInfoCredentialOffer, new JSONObject(csoInfoCredentialRequest.getCredentialRequestJson()), csoDID.getDid());
            Log.i(this.getClass().toString(), String.format("Credential for CS-CSO info created: %s", csoInfoCredential.getCredentialJson()));

            Log.i(this.getClass().toString(), "Saving credential for CS-CSO info into CS wallet...");
            WalletUtils.saveCredential(csWallet, new JSONObject(csoInfoCredentialRequest.getCredentialRequestMetadataJson()), new JSONObject(csoInfoCredential.getCredentialJson()), csoInfoCredentialDefFromLedger.getJSONObject("object"), csoInfoCredential.getRevocRegDeltaJson() != null ? new JSONObject(csoInfoCredential.getRevocRegDeltaJson()) : null);
            Log.i(this.getClass().toString(), "Credential for CS-CSO info saved into CS wallet");

            Log.i(this.getClass().toString(), "Creating credential for CS-DSO district info...");
            AnoncredsResults.IssuerCreateCredentialResult dsoDistrictCredential = CredentialUtils.createDSODistrictCredential(dsoWallet, dsoDistrictCredentialOffer, new JSONObject(dsoDistrictCredentialRequest.getCredentialRequestJson()));
            Log.i(this.getClass().toString(), String.format("Credential for CS-DSO district info created: %s", dsoDistrictCredential.getCredentialJson()));

            Log.i(this.getClass().toString(), "Saving credential for CS-DSO district info into CS wallet...");
            WalletUtils.saveCredential(csWallet, new JSONObject(dsoDistrictCredentialRequest.getCredentialRequestMetadataJson()), new JSONObject(dsoDistrictCredential.getCredentialJson()), dsoDistrictCredentialDefFromLedger.getJSONObject("object"), dsoDistrictCredential.getRevocRegDeltaJson() != null ? new JSONObject(dsoDistrictCredential.getRevocRegDeltaJson()) : null);
            Log.i(this.getClass().toString(), "Credential for CS-DSO district info saved into CS wallet");

            // 11. Proof requests creation

            Log.i(this.getClass().toString(), "Creating CSO Info + DSO district proof request...");
            JSONObject csoInfodsoDistrictProofRequest = ProofUtils.createCSOInfoAndDSODistrictProofRequest(csoDID.getDid(), dsoDID.getDid(), csoInfoCredentialDefFromLedger.getString("id"), dsoDistrictCredentialDefFromLedger.getString("id"));
            Log.i(this.getClass().toString(), String.format("CSO Info + DSO district proof request created: %s", csoInfodsoDistrictProofRequest));

            // 12. Proofs creation

            Log.i(this.getClass().toString(), "Selecting credentials for CSO Info + DSO district proof request...");
            JSONObject csoInfodsoDistrictProofRequestCredentials = CredentialUtils.getPredicatesForCSOInfoDSODistrictProofRequest(csWallet, csoInfodsoDistrictProofRequest);
            Log.i(this.getClass().toString(), String.format("Credentials for CSO Info + DSO district proof request selected: %s", csoInfodsoDistrictProofRequestCredentials));

            Log.i(this.getClass().toString(), "Creating proof for CSO Info + DSO district proof request...");
            JSONObject csoInfodsoDistrictProof = ProofUtils.createProofCSOInfoDSODistrictProofRequest(
                    csWallet,
                    csoInfodsoDistrictProofRequest,
                    csoInfodsoDistrictProofRequestCredentials,
                    csMasterSecretID,
                    csoInfoCredentialSchemaFromLedger.getString("id"),
                    dsoDistrictCredentialSchemaFromLedger.getString("id"),
                    csoInfoCredentialSchemaFromLedger.getJSONObject("object"),
                    dsoDistrictCredentialSchemaFromLedger.getJSONObject("object"),
                    csoInfoCredentialDefFromLedger.getString("id"),
                    dsoDistrictCredentialDefFromLedger.getString("id"),
                    csoInfoCredentialDefFromLedger.getJSONObject("object"),
                    dsoDistrictCredentialDefFromLedger.getJSONObject("object")
            );
            Log.i(this.getClass().toString(), String.format("Proof for  CSO Info + DSO district proof request created: %s", csoInfodsoDistrictProof));

            // 13. Proofs verification

            Log.i(this.getClass().toString(), "Verifying proof for CSO Info + DSO district...");
            boolean isCSOInfoDSODistrictProofValid = ProofUtils.verifyCSOInfoDSODistrictProof(
                    csoInfodsoDistrictProofRequest,
                    csoInfodsoDistrictProof,
                    csoInfoCredentialSchemaFromLedger.getString("id"),
                    dsoDistrictCredentialSchemaFromLedger.getString("id"),
                    csoInfoCredentialSchemaFromLedger.getJSONObject("object"),
                    dsoDistrictCredentialSchemaFromLedger.getJSONObject("object"),
                    csoInfoCredentialDefFromLedger.getString("id"),
                    dsoDistrictCredentialDefFromLedger.getString("id"),
                    csoInfoCredentialDefFromLedger.getJSONObject("object"),
                    dsoDistrictCredentialDefFromLedger.getJSONObject("object")
            );
            Log.i(this.getClass().toString(), String.format("Proof for CSO Info + DSO district verified with result: %b", isCSOInfoDSODistrictProofValid));

            // 14. Pool disconnection

            Log.i(this.getClass().toString(), "Closing test pool...");
            sofiePool.close();
            Log.i(this.getClass().toString(), "Test pool closed.");

            // 15. Wallets de-initialisation

            Log.i(this.getClass().toString(), "Closing EV wallet...");
            evWallet.close();
            Log.i(this.getClass().toString(), "EV wallet closed.");
            Log.i(this.getClass().toString(), "Closing CSO wallet...");
            csoWallet.close();
            Log.i(this.getClass().toString(), "CSO wallet closed.");
            Log.i(this.getClass().toString(), "Closing DSO wallet...");
            dsoWallet.close();
            Log.i(this.getClass().toString(), "DSO wallet closed.");
            Log.i(this.getClass().toString(), "Closing CS wallet...");
            csWallet.close();
            Log.i(this.getClass().toString(), "CS wallet closed.");
            Log.i(this.getClass().toString(), "Closing steward wallet...");
            stewardWallet.close();
            Log.i(this.getClass().toString(), "Steward wallet closed.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}

