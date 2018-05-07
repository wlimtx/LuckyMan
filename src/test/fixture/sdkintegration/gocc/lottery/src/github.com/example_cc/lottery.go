/*
Copyright IBM Corp. 2016 All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

		 http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package main

import (
	"fmt"
	"strconv"

	"github.com/hyperledger/fabric/core/chaincode/shim"
	pb "github.com/hyperledger/fabric/protos/peer"

	"crypto/sha256"
	"encoding/hex"
	"encoding/base64"
	"encoding/json"
	"encoding/binary"
	list2 "container/list"
	"math"

)

type SimpleChaincode struct {
}

type Man struct
{
	 Asset  float64  `json:"asset"`
	 Status int8 	 `json:"status"`  //三种状态，0 未参与赌博，1 有赌注， 2 提交幸运数字
}

type Bet struct {
	Luck  int64    `json:"luck"`	//幸运数字
	Asset float64   `json:"asset"`   //赌注
}

func main() {
	err := shim.Start(new(SimpleChaincode))
	if err != nil {
		fmt.Printf("Error starting Simple chaincode: %s", err)
	}

}


func (t *SimpleChaincode) Init(stub shim.ChaincodeStubInterface) pb.Response {

	fmt.Println("########### lottery Init ###########")
	_, args := stub.GetFunctionAndParameters()
	var err error

	if len(args) != 8 {
		return shim.Error("Incorrect number of arguments. Expecting 8")
	}

	var value [4]float64
	for i := 0; i < 4; i++ {
		value[i], err = strconv.ParseFloat(args[4+i], 64)
		if err != nil || value[i] <= 0{
			return shim.Error("Expecting positive value for asset holding")
		}
	}

	for i := 0; i < 4; i++ {
		fmt.Printf("The asset of \"%s\" is %f",args[i], value[i])
	}

	//公钥hash与资产建立映射
	for i := 0; i < 4; i++ {
		man:=new(Man)
		man.Asset=value[i]
		man.Status=0
		manBytes, _ := json.Marshal(man)
		err = stub.PutState(args[i], manBytes)
		if err != nil {
			return shim.Error(err.Error())
		}
	}

	if transientMap, err := stub.GetTransient(); err == nil {
		if transientData, ok := transientMap["result"]; ok {
			return shim.Success(transientData)
		}
	}

	return shim.Success(nil)

}

// Invoke makes payment of X units from A to B
func (t *SimpleChaincode) Invoke(stub shim.ChaincodeStubInterface) pb.Response {
	fmt.Println("########### example_cc Invoke ###########")
	function, args := stub.GetFunctionAndParameters()

	if function != "invoke" {
		return shim.Error("Unknown function call")
	}

	if len(args) < 1 {
		return shim.Error("Incorrect number of arguments. Expecting at least 2")
	}

	if args[0] == "query" {
		// queries an entity state
		return t.query(stub, args)
	}
	if args[0] == "bet" {
		// Deletes an entity from its state
		return t.bet(stub, args)
	}
	if args[0] == "lottery" {
		// Deletes an entity from its state
		return t.lottery(stub, args)
	}
	return shim.Error("Unknown action, check the first argument, must be one of 'query', or 'bet'")
}

func (t *SimpleChaincode) lottery(stub shim.ChaincodeStubInterface, args []string) pb.Response {
	var err error
	I, err := stub.GetStateByRange("", "")
	if err!= nil {
		return shim.Error(err.Error())
	}
	var men =make(map[string]*Man)
	var bets = make(map[string]*list2.List)
	l := list2.New()
	for ;I.HasNext(); {
		kv, err := I.Next()
		if err != nil {
			return shim.Error(err.Error())
		}
		fmt.Println(kv.Key + ":" + string(kv.Value))
		man := new(Man)
		json.Unmarshal(kv.Value, &man)
		if man.Status == 0 {
			return shim.Error("A prize must be made after all bets are made")
		}
		men[kv.Key] = man
		CI, err := stub.GetStateByPartialCompositeKey("address-luck", []string{kv.Key})
		if err != nil {
			return shim.Error(err.Error())
		}
		bets[kv.Key]= list2.New()
		fmt.Println("man: ", man)
		//考虑每一个账号压的的赌注
		for ;CI.HasNext();  {
			next, err := CI.Next()
			if err!= nil {
				return shim.Error(err.Error())
			}
			_, keys, err := stub.SplitCompositeKey(next.Key)
			bet:=new(Bet)
			fmt.Println("keys: ", keys)
			bet.Luck, err= strconv.ParseInt(keys[1], 10, 64)
			if err!= nil {
				return shim.Error(err.Error())
			}
			bet.Asset = ByteToFloat64(next.Value)
			//一个账号可能压了多分赌注
			bets[kv.Key].PushBack(bet)
			l.PushBack(next.Key)
			//stub.DelState(next.Key)
			fmt.Println(next.Key,"---bet:" ,bet.Asset)
		}
		CI.Close()

	}
	I.Close()
	fmt.Println("men: ",men)
	fmt.Println("bets: ",bets)
	if !t.f(stub, men, bets) {
		return shim.Error("fail to run a lottery")
	}
	//删除所有的bet
	for e := l.Front(); e!=nil; e = e.Next() {
		stub.DelState(e.Value.(string))
	}


	if transientMap, err := stub.GetTransient(); err == nil {
		if transientData, ok := transientMap["result"]; ok {
			return shim.Success(transientData)
		}
	}
	return shim.Success(nil)
}

type K struct {
	Ak float64 //资产权重
	Lk float64
}


//第一次提交
func (t *SimpleChaincode) bet(stub shim.ChaincodeStubInterface, args []string) pb.Response {
	var  nonce, random, sig, base64Puk string
	var asset float64
	var err error
	//args := [...]string{"bet", "asset" , "nonce","random","sig","base64Puk"}

	if len(args) != 6 {
		return shim.Error("Incorrect number of arguments. Expecting 6, function followed by 2 names and 1 value")
	}

	//资产必须是正数
	asset, err = strconv.ParseFloat(args[1],64)
	if err != nil || asset <= 0 {
		return shim.Error("Expecting positive value for asset holding")
	}

	nonce = args[2]
	random = args[3]
	sig = args[4]

	base64Puk = args[5]
	//幸运数字为64位有符号整数
	luck, err := strconv.ParseInt(nonce, 10, 64)
	if err != nil {
		return shim.Error("nonce is not a number")
	}
	fmt.Println("base64 signature: ", sig)

	//base64格式的签名
	rawSig, err := base64.StdEncoding.DecodeString(sig)
	if err != nil {
		return shim.Error("bad signature, expect base64 format")
	}
	//base64格式的公钥
	rawPuk, err := base64.StdEncoding.DecodeString(base64Puk)
	if err!= nil {
		return shim.Error("bad public key, expect base64 format")
	}
	//检查该公钥账号是否存在
	sum256 := sha256.Sum256(rawPuk)
	pukHash := hex.EncodeToString(sum256[:])
	fmt.Println("pukHash: "+ pukHash)

	// Get the state from the ledger
	manBytes, err := stub.GetState(pukHash)
	if err != nil {
		return shim.Error("Failed to get state")
	}
	if manBytes == nil {
		return shim.Error("Empty account")
	}
	man:=new(Man)
	json.Unmarshal(manBytes,&man)
	if man.Status!= 0 {
		return shim.Error("This man has submitted.")
	}

	if asset > man.Asset/*||money <= 0*/{
		return shim.Error("This man do not have enough money")
	}

	//签名内容为赌注，幸运数字，随机数，
	message := ([]byte)(args[1]+nonce+random+ base64Puk)
	fmt.Println(message)

	if RsaSignVer(rawPuk, message,rawSig) != nil {
		return shim.Error("dummy signature")//验证签名失败
	}


	fmt.Println("luck value: ",luck)
	compositeKey, err := stub.CreateCompositeKey("address-luck", []string{pukHash, strconv.FormatInt(luck ,10)})
	if err!= nil {
		return shim.Error(err.Error())
	}

	fmt.Println("compositeKey: ",compositeKey)
	err = stub.PutState(compositeKey, Float64ToByte(asset))
	if err != nil {
		return shim.Error(err.Error())
	}
	man.Status = 1
	man.Asset -= asset
	manBytes, err = json.Marshal(man)
	if err != nil{
		return shim.Error(err.Error())
	}
	//更新资产的状态
	err = stub.PutState(pukHash, manBytes)
	if err != nil {
		return shim.Error(err.Error())
	}


	if transientMap, err := stub.GetTransient(); err == nil {
		if transientData, ok := transientMap["result"]; ok {
			return shim.Success(transientData)
		}
	}
	return shim.Success(nil)
}


func (t *SimpleChaincode) query(stub shim.ChaincodeStubInterface, args []string) pb.Response {

	var man string // Entities
	var err error

	if len(args) != 2 {
		return shim.Error("Incorrect number of arguments. Expecting address of the lottery man")
	}

	man = args[1]

	// Get the state from the ledger
	asset, err := stub.GetState(man)
	if err != nil {
		return shim.Error("{\"Error\":\"Failed to get state for " + man + "\"}")
	}

	if asset == nil {
		return shim.Error("{\"Error\":\"Nil asset for " + man + "\"}")
	}

	fmt.Printf("Query Response: The asset of \"%s\" is %d", man, asset)

	return shim.Success(([]byte)(string(asset)))
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
