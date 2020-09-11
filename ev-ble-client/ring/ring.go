package ring

import (
	"strings"
	
	"crypto/cipher"
	"fmt"
	"go.dedis.ch/kyber/sign/eddsa"
	"time"

	//"encoding/hex"
	"go.dedis.ch/kyber"
	"go.dedis.ch/kyber/group/edwards25519"

	"github.com/btcsuite/btcutil/base58"
	"go.dedis.ch/kyber/sign/anon"
)

//Sign : Ring Signature Function
func Sign(M, skSeed []byte, pbKeyList string) []byte {



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

	//start := time.Now()
	sig := anon.Sign(suite, M, anon.Set(X), nil, mine, x)

	//	fmt.Print("Signature:\n" + hex.Dump(sig))
	// Verify the signature against the correct message
	//elapsed := time.Since(start)
	//fmt.Sprint("Signing time")
	//fmt.Sprint(elapsed)

	//start = time.Now()
	//anon.Verify(suite, M, anon.Set(X), nil, sig)
	//elapsed2 := time.Since(start)
	//fmt.Sprint("Signing time")
	//fmt.Sprint(elapsed2)	

	return sig // fmt.Sprintf("%s%d", "gen: " + elapsed.String() + " verification: " + elapsed2.String() + " size: ", len(sig)) 
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

func SingleSign(message string) string {

	msg := []byte(message)

	seed := []byte("0000000000000000000000000CS-0000")
	stream := ConstantStream(seed)
	suite := eddsa.NewEdDSA(stream)

	sig, _ := suite.Sign(msg)

	fmt.Print("Signature:\n")
	fmt.Print(len(sig))

	
start := time.Now()
	er := eddsa.Verify(suite.Public , msg, sig)
	if er != nil {
		panic("wrong sig")
	}
elapsed := time.Since(start)

	// Verify the signature against the correct message
	
	fmt.Print("Signing time")

	return elapsed.String()

}

type constantStream struct {
	seed []byte
}

// ConstantStream is a cipher.Stream which always returns
// the same value.
func ConstantStream(buff []byte) cipher.Stream {
	return &constantStream{buff}
}

// XORKexStream implements the cipher.Stream interface
func (cs *constantStream) XORKeyStream(dst, src []byte) {
	copy(dst, cs.seed)
}

func GenerateKeys(skSeed []byte) string {
start := time.Now()
	suite := edwards25519.NewBlakeSHA256Ed25519()

	x, _, _ := suite.NewKeyAndSeedWithInput(skSeed) // create a private key x

	pk, _ := suite.Point().Mul(x, nil).MarshalBinary()
//	fmt.Printf("Private key of signer: %s\n\n", base58.Encode(x))
	fmt.Printf("Public key of signer: %s\n\n", base58.Encode(pk))
	elapsed := time.Since(start)
	return base58.Encode(pk) + "time: " + elapsed.String()
}
