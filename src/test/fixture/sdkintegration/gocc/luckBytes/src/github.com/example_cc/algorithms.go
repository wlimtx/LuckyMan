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


func  (t *LuckBytes)f(stub shim.ChaincodeStubInterface, keys *list2.List,men map[string]*Man, bets map[string]*list2.List) (bool,pb.Response){

	var base =sha256.Sum256([]byte("Lucky-Bytes"))
	I:= base
	LuckBytes := I[:]
	fmt.Println("Base Bytes: ", LuckBytes)
	//计算真实幸运数字
	//只有计算真实幸运数字的时候才需要保证有序
	for k:=keys.Front();k!=nil;k=k.Next() {
		for e := bets[k.Value.(string)].Front(); e != nil; e=e.Next() {
			I = sha256.Sum256(bytes.Join([][]byte{e.Value.(*Bet).LuckBytes, LuckBytes},[]byte("")))
			LuckBytes = I[:]
			I = sha256.Sum256(LuckBytes)
			LuckBytes = I[:]
			fmt.Println("twice of sha256 Base Bytes: ", LuckBytes)
		}
	}
	sum224 := sha256.Sum224(LuckBytes)
	LuckBytes =sum224[:]
	bigLuck,ok := new(big2.Int).SetString(hex.EncodeToString(LuckBytes), 16)
	if !ok {
		return false,shim.Error("Covert Big Integer Fail")
	}

	fmt.Println("big Luck ", bigLuck)
	sumBet := 0.0

	//计算奖池资产
	for address := range bets {
		for e := bets[address].Front(); e != nil ; e=e.Next() {
			sumBet+=e.Value.(*Bet).Asset
		}
	}
	fmt.Println("sum Bet: ",sumBet)
	//计算每份幸运字节与真实幸运字节的匹配程度
	sumGap := new(big2.Int)
	D := make(map[string]*list2.List)
	for address := range bets {
		D[address] =list2.New()
		fmt.Println("---------for address: ", address)
		for e := bets[address].Front(); e != nil ; e=e.Next() {

			LuckBytes = base[:]
			I = sha256.Sum256(bytes.Join([][]byte{e.Value.(*Bet).LuckBytes, LuckBytes},[]byte("")))
			sum224 =sha256.Sum224(I[:])
			LuckBytes = sum224[:]
			lucki,ok := new(big2.Int).SetString(hex.EncodeToString(LuckBytes), 16)
			if !ok {
				return false,shim.Error("Covert Big Integer Fail")
			}
			fmt.Println("luck(i): ", lucki)
			lucki.Sub(lucki, bigLuck).Abs(lucki)//差距

			sumGap.Add(sumGap, lucki)//累计所有的差距


			D[address].PushBack(lucki)
		}
	}


	K := make(map[string]*list2.List)
	v:= 0.0
	//计算调整后的资产比例，及总比例
	for address := range bets {
		K[address] =list2.New()
		e2:=bets[address].Front()
		for e := D[address].Front(); e != nil ; e=e.Next() {
			luckFrac := new(big2.Rat).SetFrac(e.Value.(*big2.Int), sumGap)//差距/总差距
			fmt.Println("each luck scale: ",luckFrac)
			res, exact := luckFrac.Float64()
			if exact{
				//return false,shim.Error("convert luck rat frac to float64 fail")
				fmt.Println("convert is not very exact ")
			}
			fmt.Println("luck value",res)
			f := res *  e2.Value.(*Bet).Asset/ sumBet	//计算每份的资产占比
			v += f
			K[address].PushBack(f)
			e2=e2.Next()

		}
	}
	//分配资产
	for address := range bets {
		for e := K[address].Front(); e != nil ; e=e.Next() {
			//资产增加
			men[address].Asset += e.Value.(float64)/ v * sumBet
		}
		men[address].Status = 0 //开始下一次彩票
		manBytes ,err := json.Marshal(men[address])
		if err !=nil{
			return false,shim.Error("convert object to json fail" )
		}
		err = stub.PutState(address, manBytes)
		if err!=nil {
			return false,shim.Error("putState error")
		}
		fmt.Println("after asset: ",men[address].Asset)
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
