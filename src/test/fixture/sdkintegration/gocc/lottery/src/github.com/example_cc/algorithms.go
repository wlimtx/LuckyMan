package main

import (
	"crypto/sha256"
	"fmt"
	"encoding/hex"
	"crypto/x509"
	"crypto/rsa"
	"crypto"
	"encoding/json"
	"github.com/hyperledger/fabric/core/chaincode/shim"
	"strconv"
	"math"
	list2 "container/list"
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


func  (t *SimpleChaincode)f(stub shim.ChaincodeStubInterface, men map[string]*Man, bets map[string]*list2.List) bool{
	var sum =sha256.Sum256([]byte("Lucky-Man"))
	//计算真实幸运数字
	for address := range bets {
		for e := bets[address].Front(); e != nil; e=e.Next() {
			//fmt.Println(e)
			//big.
			bytes := sum[:]
			sum = sha256.Sum256([]byte(strconv.FormatInt(e.Value.(*Bet).Luck,10)+ string(bytes)))
			fmt.Println("sha256: ", sum)
		}
	}
	s := hex.EncodeToString(sum[:])
	fmt.Println("hex luck number: "+ s)
	n,ok := new(big2.Int).SetString(s, 16)
	if !ok {
		return ok
	}

	fmt.Println("ln: ", n)
	bounds := big2.NewInt(2147483648)
	bounds.Mul(bounds,big2.NewInt(2)).Mul(bounds, bounds).Div(bounds,big2.NewInt(2))//922 亿亿
	luck := n.Mod(n, bounds)
	fmt.Println("luck: ", luck)
	floatLuck := math.Abs(float64(luck.Int64()))
	if !ok {
		return ok
	}
	floatLuck=10
	sumBet := 0.0

	//计算奖池资产
	for address := range bets {
		fmt.Println("---------for address: ", address)
		for e := bets[address].Front(); e != nil ; e=e.Next() {
			sumBet+=e.Value.(*Bet).Asset
			fmt.Println("Bet: ",e.Value.(*Bet))
		}
	}
	fmt.Println("sum Bet: ",sumBet)
	fmt.Println("------------")
	//计算资产比例，及开奖后调整比例
	Q := 0.0
	D := make(map[string]*list2.List)
	for address := range bets {
		D[address] =list2.New()
		fmt.Println("---------for address: ", address)
		for e := bets[address].Front(); e != nil ; e=e.Next() {
			lucki := math.Abs(float64(e.Value.(*Bet).Luck))
			var L float64
			var A float64
			//幸运数字影响比例
			if floatLuck <= lucki {
				L = floatLuck/ lucki
			}else{
				L = lucki / floatLuck
			}
			//资产占比例
			A=  e.Value.(*Bet).Asset / sumBet

			fmt.Println("before: ",A)
			D[address].PushBack(A*L)
			Q+=A*L
		}
	}
	//总比例
	fmt.Println("Q: ", Q)
	for address := range bets {
		fmt.Println("---------for address: ", address)
		manBytes, err := stub.GetState(address)
		if err !=nil{
			fmt.Println("empty account", address)
			return false
		}
		man := new(Man)
		json.Unmarshal(manBytes, &man)
		fmt.Println("before asset: ",man.Asset)
		for e := D[address].Front(); e != nil ; e=e.Next() {
			var A float64
			A = e.Value.(float64)/Q
			fmt.Println("after: ",A)
			fmt.Println("win: ", A*sumBet)
			//资产增加
			man.Asset+=A*sumBet
		}
		man.Status = 0 //开始下一次彩票
		manBytes ,err = json.Marshal(man)
		if err !=nil{
			fmt.Println("empty account", address)
			return false
		}
		err = stub.PutState(address, manBytes)
		if err!=nil {
			fmt.Println("putState error")
			return false
		}
		fmt.Println("after asset: ",man.Asset)
	}
	return true
}