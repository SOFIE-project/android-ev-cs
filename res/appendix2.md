# Verifiable Credentials and Verifiable Presentations

This section presents the structure of the verifiable credentials and presentations used in the protocol. All the credentials and the presentations conform to the [W3C Verifiable Credentials Data Model standard, version 1.0 (latest)](https://www.w3.org/TR/vc-data-model/).

## Verifiable Credentials

### EV charging credential

This credential is used by the EVUs to prove authorization to utilize the charging service. The credential is issued by the ER to the EV. The issuer DID is a public Indy DID, while the EV DID is an ephemeral Peer DID. An example of such a credential is given below.

```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1",
    "https://www.w3.org/2020/credentials/ev-info/v1"
  ],
  "id": "https://www.w3.org/2020/credentials/ev-info",
  "type": [
    "VerifiableCredential",
    "EVChargingCredential"
  ],
  "credentialSubject": {
    "id": "EV.did@EV:CS"
  },
  "issuer": "ER.did",
  "issuanceDate": "2020-12-31T00:00:00Z",
  "expirationDate": "2020-12-31T23:59:59Z",
  "proof": {
    "type": "JcsEd25519Signature2020",
    "created": "2020-12-31T00:00:00Z",
    "proofPurpose": "assertionMethod",
    "verificationMethod": "ER.did#key1",
    "signatureValue": "eyJhbGciOiJ...IsImI"
  }
}
```

> Example of the charging credential used by an EV. The DIDs of the EV and the ER must be replaced with actual values.

### CS district info credentail

This credential is used by the CS in the ephemeral DID design to provide to the EV proof of being located in a given energy district. The credential is issued by the CSO to the CS. Similar to the EV charging credential, the CSO is identified by a public Indy DID, while the CS by a Peer DID. An example of such a credential is the following:

```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1",
    "https://www.w3.org/2020/credentials/cs-did-info/v1"
  ],
  "id": "https://www.w3.org/2020/credentials/cs-did-info",
  "type": [
    "VerifiableCredential",
    "CSInfoCredential"
  ],
  "credentialSubject": {
    "id": "CS.did@EV:CS",
    "district": "1"
  },
  "issuer": "CSO.did",
  "issuanceDate": "2018-03-12T07:10:31Z",
  "expirationDate": "2024-12-31T23:59:59Z",
  "proof": {
    "type": "JcsEd25519Signature2020",
    "created": "2018-03-12T07:10:31Z",
    "proofPurpose": "assertionMethod",
    "verificationMethod": "CSO.did#key1",
    "signatureValue": "JG7JcHzDi...DzrBar"
  }
}
```

> Example of the district info credential structure used by a CS in the ephemeral DID design. The DIDs of the CS and the CSO must be replaced with actual values.

### CS ring info credential

This credential is used by the CS in the ring signature design to provide proof of the CS being part of a specific ring --- and hence, located in a given energy district --- by proving that its DID is included in the ring. The ring contains all the CS DIDs that are part of the ring, along with the energy district information. An example of such a credential is the following:

```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1",
    "https://www.w3.org/2020/credentials/cs-ring-info/v1"
  ],
  "id": "https://www.w3.org/2020/credentials/cs-ring-info",
  "type": [
    "VerifiableCredential",
    "CSRingCredential"
  ],
  "credentialSubject": {
    "ids": [
        "CS1.did@EV:CS",
        "CS2.did@EV:CS",
        .
        .
        .
        "CS49.did@EV:CS",
        "CS50.did@EV:CS"
    ],
    "district": "1"
    }
  },
  "issuer": "CSO.did",
  "issuanceDate": "2018-03-12T07:10:31Z",
  "expirationDate": "2024-12-31T23:59:59Z",
  "proof": {
    "type": "JcsEd25519Signature2020",
    "created": "2018-03-12T07:10:31Z",
    "proofPurpose": "assertionMethod",
    "verificationMethod": "CSO.did#key1",
    "signatureValue": "JG7JcHzDi...DzrBar"
  }
}
```

> Example of the district info credential structure used by a CS in the ring signature design. The DIDs of all the CS and the CSO must be replaced with actual values.

## Payment Commitments

The commitment is an application of the [PayWord scheme](https://people.csail.mit.edu/rivest/pubs/RS96a.pdf), adapted to the business domain of the electric vehicle charging scenario.

### EV payment commitment with ephemeral DIDs

In the ephemeral DID solution, the commitment contains information about the CS, along with the information about the hash chain (root value `w0`, chain length `n`, hashing algorithm used `alg`, monetary value of each hash chain step `p`), and the timestamp `D` of the commitment in standard ISO format. The commitment is not directly signed by the EV, but it is included in the (signed) verifiable presentation that the EV generates and sends to the CS. An example of such a commitment is given below.

```json
{
  "cs-did": "CS.did@EV:CS",
  "w0": 527436582692,
  "alg": "SHA-256",
  "n": 50,
  "D": "2020-06-29T14:44:20Z",
  "p": 0.20
}
```

> Example of a payment commitment sent by the EV to the CS in the ephemeral DID design. The DID of the CS must be replaced with the actual value.

### EV payment commitment with ring signatures

The commitment in the ring signature design contains the same information as in the case of ephemeral DIDs, with the only difference that the CS ring signature `cs-signature` replaces the CS DID `cs-did`. An example of such commitment is the following:

```json
{
  "cs-signature": {
    "ev-did": "EV.did@EV:CS",
      "proof": [
        {
          "type": "Ed25519RingSignature",
          "created": "2020-12-27T17:22:41Z",
          "proofPurpose": "assertionMethod",
          "nonce": 1597155761,
          "verificationMethod": "CS.credential.id",
          "signatureValue": "edqw331Si...gFWd3kLas"
        }
      ]
  },
  "w0": 527436582692,
  "alg": "SHA-256",
  "n": 50,
  "p": 0.20
}
```

> Example of a payment commitment sent by the EV to the CS in the ring signature design. The DIDs of the CS and the EV must be replaced with actual values.

## Verifiable Presentations

### CS presentation with ephemeral DIDs

This message contains the base64-encoded CS *district credential*. Additionally, it contains the verifiable presentation that includes the DID of the EV in the charging transaction `ev-did`. The proof `nonce` corresponds to the UNIX epoch timestamp of the CS at the time of the presentation creation. An example of such a presentation is given below.

```json
{
  "response": {
    "@type": "https://didcomm.org/didexchange/1.0/response",
    "@id": "uri:uuid:32F7436E-CFE2-4176-B158-7C1B4F16312C",
    "~thread": {
      "thid": "urn:uuid:D23CAC23-7AD9-4FEF-A46D-1A543C1BD36F"
    },
    "connection": {
      "did": "CS.did@EV:CS"
    }
  },
  "verifiableCredential": "<CS_Credential_Base64_Encoded>",
  "presentation": {
    "@context": [
      "https://www.w3.org/2018/credentials/v1",
      "https://www.w3.org/2018/credentials/examples/v1",
    ],
    "id": "urn:uuid:18E15106-E6DC-4EB5-8DEB-BFBFAC1C7A7A",
    "type": [
      "VerifiablePresentation"
    ],
    "ev-did": "EV.did@EV:CS",
    "proof": [
      {
        "type": "JcsEd25519Signature2020",
        "created": "2020-08-11T17:22:41Z",
        "proofPurpose": "assertionMethod",
        "nonce": 1597155761,
        "verificationMethod": "CS.did@EV:CS",
        "signatureValue": "edqw331Si...gFWd3kLas"
      }
    ]
  }
}
```

> Example of an 'Exchange Response' message sent by the CS to the EV in the ephemeral DID design. The DIDs of the CS and the EV must be replaced with actual values.

### CS presentation with ring signatures

In the case of ring signatures, the structure of the message does not change except for the linked proof, which is not a `JcsEd25519Signature2020` but an `Ed25519RingSignature`: this is not an existing signature suite, but rather a variation of the `JcsEd25519Signature2020` that uses a ring signature scheme. An example of such a presentation is provided below.

```json
{
  .
  .
  .
  "presentation": {
    "@context": [
      "https://www.w3.org/2018/credentials/v1",
      "https://www.w3.org/2018/credentials/examples/v1"
    ],
    "id": "urn:uuid:18E15106-E6DC-4EB5-8DEB-BFBFAC1C7A7A",
    "type": [
      "VerifiablePresentation"
    ],
    "ev-did": "EV.did@EV:CS",
    "proof": [
      {
        "type": "Ed25519RingSignature",
        "created": "2020-08-11T17:22:41Z",
        "proofPurpose": "assertionMethod",
        "nonce": 1597155761,
        "verificationMethod": "CS.credential.id",
        "signatureValue": "edqw331Si...gFWd3kLas"
      }
    ]
  }
}
```

> Example of an 'Exchange Response' message sent by the CS to the EV in the ring signature design. The DID of the EV must be replaced with the actual value.

### EV presentation

The presentation includes the verifiable presentation sent by the EV to the CS, which also contains information about the payment commitment `commitment` that the EV has previously generated, base64-encoded. The payment commitment will be signed as part of the presentation, along with a `nonce` that represents the UNIX epoch timestamp of the EV at the time the presentation is generated. Following is an example of such a message.

```json
{
  "complete": {
    "@type": "https://didcomm.org/didexchange/1.0/complete",
    "@id": "uri:uuid:D8408EA8-6687-4C30-8EDC-D074E28C657B",
    "~thread": {
      "thid": "urn:uuid:D23CAC23-7AD9-4FEF-A46D-1A543C1BD36F",
      "pthid": "urn:uuid:D23CAC23-7AD9-4FEF-A46D-1A543C1BD36F"
    }
  },
  "verifiableCredential": "<EV_Credential_Base64_Encoded>",
  "presentation": {
    "@context": [
      "https://www.w3.org/2018/credentials/v1",
      "https://www.w3.org/2018/credentials/examples/v1",
    ],
    "id": "urn:uuid:13CB2439-CA8F-46FB-95B8-3F6F0642B9B8",
    "type": [
      "VerifiablePresentation",
    ],
    "commitment": "<Payment_Commitment_Base64_Encoded>",
    "proof": [
      {
        "type": "JcsEd25519Signature2020",
        "created": "2020-12-31T09:20:12Z",
        "proofPurpose": "assertionMethod",
        "nonce": 1597155761,
        "verificationMethod": "EV.did@EV:CS",
        "signatureValue": "eyJ0eXAi...gFWFOEjXk"
      }
    ]
  }
}
```

> Example of an 'Exchange Complete' message sent by the EV to the CS. The DID of the EV must be replaced with the actual value.