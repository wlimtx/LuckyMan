package main

import (
	pb "github.com/hyperledger/fabric/protos/peer"
	"crypto/sha256"
	"fmt"
	"encoding/hex"
	"crypto/x509"
	"crypto/rsa"
	"crypto"
	"github.com/hyperledger/fabric/core/chaincode/shim"
	"math"
	list2 "container/list"
	"encoding/binary"
	"bytes"
	"encoding/json"
	big2 "math/big"
)

//公钥验证
func RsaSignVer(rawPuk []byte,data []byte, signature []byte) error {
	hashed := sha256.Sum256(data)
	fmt.Println("data hash: ", hex.EncodeToString(hashed[:]))

	fmt.Println("data signature: ",hex.EncodeToString(signature))
	// 解析公钥
	pubInterface, err := x509.ParsePKIXPublicKey(rawPuk)
	if err != nil {
		return err
	}
	// 类型断言
	pub := pubInterface.(*rsa.PublicKey)
	//验证签名
	return rsa.VerifyPKCS1v15(pub, crypto.SHA256, hashed[:], signature)
}


func  (t *LuckBytes)f(stub shim.ChaincodeStubInterface, K *list2.List,men map[string]*Man) (bool,pb.Response){
	var base = sha256.Sum256([]byte("Cipher"))
	I := base
	LuckBytes := I[:]
	fmt.Println("Base Bytes: ", LuckBytes)
	sumBet := 0.0
	//计算真实幸运数字
	//只有计算真实幸运数字的时候才需要保证有序
	for k:= K.Front();k!=nil;k=k.Next() {
		man := men[k.Value.(string)]
		I = sha256.Sum256(bytes.Join([][]byte{man.LuckBytes, LuckBytes},[]byte("")))
		I = sha256.Sum256(I[:])
		LuckBytes = I[:]
		fmt.Println("twice of sha256 Base Bytes: ", LuckBytes)
		if /* man.Status == 2 || */ man.Status == 3 {
			I = sha256.Sum256(bytes.Join([][]byte{man.Cipher, LuckBytes},[]byte("")))
			LuckBytes = I[:]
		}
		sumBet += man.BetAsset
	}
	sum256 := sha256.Sum256(LuckBytes)
	LuckBytes = sum256[:]
	//用用户的输入决定
	bigLuck,ok := new(big2.Int).SetString(hex.EncodeToString(LuckBytes), 16)
	if !ok {
		return false,shim.Error("Covert Big Integer Fail")
	}

	fmt.Println("big Luck ", bigLuck)

	//计算每份幸运字节与真实幸运字节的匹配程度
	sumGap := new(big2.Int)
	//记录所有的差距
	D := list2.New()

	for k:= K.Front();k!=nil;k=k.Next() {
		man := men[k.Value.(string)]
		I = sha256.Sum256(bytes.Join([][]byte{man.LuckBytes, LuckBytes},[]byte("")))
		I = sha256.Sum256(I[:])

		bigI, ok := new(big2.Int).SetString(hex.EncodeToString(I[:]), 16)
		if !ok {
			return false,shim.Error("Covert Big I Fail")
		}
		bigI.Sub(bigI, bigLuck).Abs(bigI)

		//记录每份差距
		D.PushFront(bigI)

		//累计差距
		sumGap.Add(sumGap, bigI)
	}
	V :=-1.0
	d:= D.Front()
	var luckMan *Man
	for k:= K.Front();k!=nil;k=k.Next() {
		man := men[k.Value.(string)]
		frac := new(big2.Rat).SetFrac(sumGap,d.Value.(*big2.Int))
		f, exact := frac.Float64()
		if !exact {
			fmt.Println("convert is not very exact ")
		}
		v := f * man.BetAsset/ sumBet
		d = d.Next()
		fmt.Println("v is :-----",v)
		//取最大
		if v > V {
			fmt.Println("luckMan changed---------",man)
			V = v
			luckMan = man
		}
	}
	//分配资产
	for k:= K.Front();k!=nil;k=k.Next() {
		man := men[k.Value.(string)]

		fmt.Println("before asset: ",man.Asset)

		if man == luckMan {
			man.Asset += sumBet * 0.98
			sumBet *= 0.02
			ks := []string{"1e4fb641ff3cf0a195c380c3c24e4ab20751af28e09076312cb004bac39d8205","4d977a7f13975e89b5e345fb98d5b39ce240665c706d8f7b18c9afa46ffae2c5"}

			for i := range ks {
				manBytes, err := stub.GetState(ks[i])
				if err != nil {
					return false,shim.Error("Failed to get state")
				}
				if manBytes == nil {
					return false,shim.Error("Empty account")
				}
				man:=new(Man)
				json.Unmarshal(manBytes,&man)
				man.Asset += sumBet / 2.0
				manBytes, err = json.Marshal(man)
				err = stub.PutState(ks[i], manBytes)
				if err!= nil {
					return false,shim.Error(err.Error())
				}
			}

		}
		man.Status = 0 //开始下一次彩票
		man.LuckBytes=nil
		man.Cipher=nil
		man.BetAsset = 0

		manBytes ,err := json.Marshal(man)
		if err !=nil{
			return false,shim.Error("convert object to json fail" )
		}
		err = stub.PutState(k.Value.(string), manBytes)
		if err!=nil {
			return false,shim.Error("putState error")
		}
		fmt.Println("after asset: ",man.Asset)
	}

	return true,shim.Success(nil)
}


func Float64ToByte(float float64) []byte {
	bits := math.Float64bits(float)
	bytes := make([]byte, 8)
	binary.LittleEndian.PutUint64(bytes, bits)

	return bytes
}

func ByteToFloat64(bytes []byte) float64 {
	bits := binary.LittleEndian.Uint64(bytes)

	return math.Float64frombits(bits)
}
