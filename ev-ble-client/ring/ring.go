package ring

import (
	"strings"
	//	"time"

	//	"fmt"

	"go.dedis.ch/kyber"
	"go.dedis.ch/kyber/group/edwards25519"

	"github.com/btcsuite/btcutil/base58"
	"go.dedis.ch/kyber/sign/anon"
)

//Sign : Ring Signature Function
func Sign(M, skSeed []byte, pbKeyList string) []byte {

	//	start := time.Now()

	pbKeySet := strings.Split(pbKeyList, "|")
	n := len(pbKeySet)

	suite := edwards25519.NewBlakeSHA256Ed25519()

	// Create an anonymity set of "public keys" from given base58 encoded verkeys
	X := make([]kyber.Point, n, n)
	for i := range X {
		X[i] = suite.Point()                                  // get basic point
		e := X[i].UnmarshalBinary(base58.Decode(pbKeySet[i])) // set to public key point
		if e != nil {
			panic("invalid public key")
		}
	}

	x, _, _ := suite.NewKeyAndSeedWithInput(skSeed) // create a private key x

	//	pk, _ := suite.Point().Mul(x, nil).MarshalBinary()
	//	fmt.Printf("Private key of signer: %s\n\n", base58.Encode(sk))
	//	fmt.Printf("Public key of signer: %s\n\n", base58.Encode(pk))

	mine := findIndexOf(suite.Point().Mul(x, nil), X)
	if mine == -1 {
		panic("signer not in set")
	}

	//	X[mine] = suite.Point().Mul(x, nil)             // corresponding public key X

	//	fmt.Printf("Public keys: %s\n\n", X)

	// Generate the signature
	//	fmt.Printf("Private key of signer: %s\n\n", x)

	sig := anon.Sign(suite, M, anon.Set(X), nil, mine, x)

	//fmt.Print("Signature:\n" + hex.Dump(sig))

	// Verify the signature against the correct message
	//	elapsed := time.Since(start)
	//	fmt.Sprint("Signing time")
	//	fmt.Sprint(elapsed)

	return sig
}

func findIndexOf(element kyber.Point, X []kyber.Point) int {
	for k, v := range X {
		if element.Equal(v) {
			return k
		}
	}
	return -1 //not found.
}

// Verify : Verifies the ring signature
func Verify(M, sig []byte, pbKeyList string) bool {

	//	start := time.Now()

	suite := edwards25519.NewBlakeSHA256Ed25519()

	pbKeySet := strings.Split(pbKeyList, "|")
	n := len(pbKeySet)

	// Create an anonymity set of "public keys" from given base58 encoded verkeys
	X := make([]kyber.Point, n)
	for i := range X {
		X[i] = suite.Point()
		e := X[i].UnmarshalBinary(base58.Decode(pbKeySet[i]))
		if e != nil {
			panic("invalid public key")
		}
	}
	//	fmt.Printf("Public keys: %s\n\n", X)

	tag, _ := anon.Verify(suite, M, anon.Set(X), nil, sig)

	if tag == nil || len(tag) != 0 {
		panic("Verify returned wrong tag")
	}

	//	fmt.Print("\n\nSignature has been verified\n")

	//	elapsed := time.Since(start)
	//	fmt.Sprint("Verification time")
	//	fmt.Sprint(elapsed)

	return true
}