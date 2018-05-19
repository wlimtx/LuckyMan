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
	list2 "container/list"

	bytes2 "bytes"
)

type LuckBytes struct {
}

type Man struct
{
	 Asset  float64  `json:"asset"`
	 Status int8 	 `json:"status"`  //三种状态，0 未参与赌博，1 有赌注， 2 提交幸运数字
}

type Bet struct {
	LuckBytes []byte   `json:"luck"`  //幸运数字
	Asset     float64 `json:"asset"` //赌注
}

func main() {
	err := shim.Start(new(LuckBytes))
	if err != nil {
		fmt.Printf("Error starting Simple chaincode: %s", err)
	}

}


func (t *LuckBytes) Init(stub shim.ChaincodeStubInterface) pb.Response {

	fmt.Println("########### lottery Init ###########")
	_, args := stub.GetFunctionAndParameters()
	var err error

	genesisCout := 4
	if len(args) != 8 {
		return shim.Error("Incorrect number of arguments. Expecting "+strconv.Itoa(genesisCout<<1))
	}

	var value [4]float64
	for i := 0; i < genesisCout; i++ {
		value[i], err = strconv.ParseFloat(args[genesisCout+i], 64)
		if err != nil || value[i] <= 0{
			return shim.Error("Expecting positive value for asset holding")
		}
	}

	for i := 0; i < genesisCout; i++ {
		fmt.Printf("The asset of \"%s\" is %f",args[i], value[i])
	}

	//公钥hash与资产建立映射
	for i := 0; i < genesisCout; i++ {
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

func (t *LuckBytes) Invoke(stub shim.ChaincodeStubInterface) pb.Response {
	fmt.Println("########### example_cc Invoke ###########")
	function, args := stub.GetFunctionAndParameters()

	if function != "invoke" {
		return shim.Error("Unknown function call")
	}

	if len(args) < 1 {
		return shim.Error("Incorrect number of arguments. Expecting at least 1")
	}

	if args[0] == "query" {
		// queries an entity state
		return t.query(stub, args)
	}

	if args[0] == "bet" {
		// Deletes an entity from its state
		return t.bet(stub, args)
	}

	if args[0] == "rebet" {
		return t.rebet(stub, args)
	}

	if args[0] == "lottery" {
		// Deletes an entity from its state
		return t.lottery(stub, args)
	}

	return shim.Error("Unknown action, check the first argument, must be one of 'query', or 'bet', 'unlock', 'lottery'")
}

func (t *LuckBytes) lottery(stub shim.ChaincodeStubInterface, args []string) pb.Response {
	var err error
	I, err := stub.GetStateByRange("", "")
	if err!= nil {
		return shim.Error(err.Error())
	}
	var men =make(map[string]*Man)
	var bets = make(map[string]*list2.List)
	keySet := list2.New()
	for ;I.HasNext(); {
		kv, err := I.Next()
		if err != nil {
			return shim.Error(err.Error())
		}
		man := new(Man)
		json.Unmarshal(kv.Value, &man)
		if man.Status != 2 {
			return shim.Error("A prize must be made after all bets are made")
		}
		//按照数据库的读取顺序
		keySet.PushFront(kv.Key)//主要用于保证访问顺序，map的顺序是随机的
		men[kv.Key] = man
		CI, err := stub.GetStateByPartialCompositeKey("address-base64Luck", []string{kv.Key})
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
			bet.LuckBytes, err= base64.StdEncoding.DecodeString(keys[1])
			if err!= nil {
				return shim.Error(err.Error())
			}
			bet.Asset = ByteToFloat64(next.Value)
			//一个账号可能压了多份赌注
			bets[kv.Key].PushBack(bet)
			//保存起来等会删掉
			err = stub.DelState(next.Key)
			if err!= nil{
				return shim.Error(err.Error())
			}
			fmt.Println(next.Key, "---address-base64LuckBytes---:" ,bet.Asset)
		}
		CI.Close()

	}
	I.Close()
	fmt.Println("men: ",men)
	fmt.Println("bets: ",bets)
	b, response := t.f(stub,keySet, men, bets)
	if !b {
		return response
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

func (t *LuckBytes) rebet(stub shim.ChaincodeStubInterface, args []string) pb.Response {
	//args = rebet base64Puk base64LuckHash base64RealLuck
	var base64Puk, base64LuckHash, base64RealLuck string


	base64Puk = args[1]
	base64LuckHash = args[2]
	base64RealLuck = args[3]
	//base64格式的公钥
	rawPuk, err := base64.StdEncoding.DecodeString(base64Puk)
	if err!= nil {
		return shim.Error("bad public key, expect base64 format")
	}
	realLuck, err := base64.StdEncoding.DecodeString(base64RealLuck)
	if err!= nil {
		return shim.Error("bad luck bit, expect base64 format")
	}
	//检查该公钥账号是否存在
	sum256 := sha256.Sum256(rawPuk)
	hexPukHash := hex.EncodeToString(sum256[:])
	fmt.Println("hexPukHash: "+ hexPukHash)
	fmt.Println("1")
	b, response := t.isAllManCommit(stub)
	fmt.Println("2")
	//fmt.Println("hexPukHash: "+ hexPukHash)
	if !b {
		return response
	}

	// Get the state from the ledger
	manBytes, err := stub.GetState(hexPukHash)
	fmt.Println("3")
	if err != nil {
		return shim.Error("Failed to get state"+ hexPukHash)
	}
	if manBytes == nil {
		return shim.Error("Empty account")
	}
	man:=new(Man)
	json.Unmarshal(manBytes,&man)
	if man.Status== 0 {
		return shim.Error("This man hasn't submitted")
	}
	if man.Status== 2 {
		return shim.Error("This man has unlocked")
	}
	//要求该nonce为luck 的 hash 的base64
	luckHash, err := base64.StdEncoding.DecodeString(base64LuckHash)
	if err != nil {
		return shim.Error("bad base64LuckHash, expect base64 format")
	}
	oldKey, err := stub.CreateCompositeKey("address-base64Luck", []string{hexPukHash, base64LuckHash})
	fmt.Println("4")
	if err!= nil {
		return shim.Error("create key fail "+err.Error())
	}
	fmt.Println("5")
	assetBytes, err := stub.GetState(oldKey)
	fmt.Println("6")
	if err != nil {
		return shim.Error("Failed to get state")
	}
	fmt.Println("7")
	asset := ByteToFloat64(assetBytes)
	//thrice of hash
	sum256 = sha256.Sum256(realLuck)
	sum256 = sha256.Sum256(sum256[:])
	sum256 = sha256.Sum256(sum256[:])
	if bytes2.Compare(luckHash,sum256[:])!=0{
		return shim.Error("Dummy LuckBytes Value")
	}
	newKey, err := stub.CreateCompositeKey("address-base64Luck", []string{hexPukHash, base64RealLuck})

	err = stub.DelState(oldKey)
	fmt.Println("delete state successful")
	if err != nil {
		return shim.Error("Failed to get state")
	}
	err = stub.PutState(newKey, Float64ToByte(asset))
	if err != nil {
		return shim.Error(err.Error())
	}
	man.Status=2
	manBytes, err = json.Marshal(man)
	err = stub.PutState(hexPukHash, manBytes)
	if err!= nil {
		return shim.Error(err.Error())
	}
	if transientMap, err := stub.GetTransient(); err == nil {
		if transientData, ok := transientMap["result"]; ok {
			return shim.Success(transientData)
		}
	}
	return shim.Success(nil)
}
func (t *LuckBytes) isAllManCommit(stub shim.ChaincodeStubInterface) (bool, pb.Response) {
	var err error
	I, err := stub.GetStateByRange("", "")
	if err!= nil {
		return false,shim.Error(err.Error())
	}
	for ;I.HasNext();  {
		I.Next()
		kv, err := I.Next()
		if err != nil {
			return false,shim.Error(err.Error())
		}
		man := Man{}
		json.Unmarshal(kv.Value, &man)
		//必须所有的人都进行了提交
		if man.Status == 0 {
			return false,shim.Error("The second submission must be submitted after all the first submissions")
		}
	}
	return true,shim.Success(nil)

}


//第一次提交
func (t *LuckBytes) bet(stub shim.ChaincodeStubInterface, args []string) pb.Response {
	var base64LuckHash, base64Random, base64Sig, base64Puk string
	var asset float64
	var err error
	//args := [...]string{"bet", "asset" , "base64LuckHash","base64Random","base64Sig","base64Puk"}

	if len(args) != 6 {
		return shim.Error("Incorrect number of arguments. Expecting 6, function followed by 2 names and 1 value")
	}

	//资产必须是正数
	asset, err = strconv.ParseFloat(args[1],64)
	if err != nil || asset <= 0 {
		return shim.Error("Expecting positive value for asset holding")
	}

	base64LuckHash = args[2]
	base64Random = args[3]
	base64Sig = args[4]

	base64Puk = args[5]



	//要求该nonce为luck 的 hash 的base64
	luckHash, err := base64.StdEncoding.DecodeString(base64LuckHash)
	if err != nil {
		return shim.Error("bad base64LuckHash, expect base64 format")
	}


	fmt.Println("base64 signature: ", base64Sig)

	//base64格式的签名
	rawSig, err := base64.StdEncoding.DecodeString(base64Sig)
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
	hexPukHash := hex.EncodeToString(sum256[:])
	fmt.Println("hexPukHash: "+ hexPukHash)

	// Get the state from the ledger
	manBytes, err := stub.GetState(hexPukHash)
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
	message := ([]byte)(args[1]+ base64LuckHash + base64Random + base64Puk)
	fmt.Println(message)

	if RsaSignVer(rawPuk, message,rawSig) != nil {
		return shim.Error("dummy signature")//验证签名失败
	}


	//打印幸运hash
	fmt.Println("luckHash value: ", hex.EncodeToString(luckHash))

	compositeKey, err := stub.CreateCompositeKey("address-base64Luck", []string{hexPukHash, base64LuckHash})
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
	err = stub.PutState(hexPukHash, manBytes)
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


func (t *LuckBytes) query(stub shim.ChaincodeStubInterface, args []string) pb.Response {
	if len(args) == 2 {
		manBytes, err := stub.GetState(args[1])
		if err != nil {
			return shim.Error(err.Error())
		}
		if manBytes == nil {
			return shim.Error("{\"Error\":\"Nil manBytes for " + args[1] + "\"}")
		}
		fmt.Printf("Query Response: The manBytes of \"%s\" is %s", args[1], manBytes)
		return shim.Success(manBytes)
	}

	if len(args) == 1 {
		var buffer bytes2.Buffer
		//query all account
		I, err := stub.GetStateByRange("", "")
		if err != nil {
			return shim.Error("{\"Error\":\"Failed to get state for " + args[1] + "\"}")
		}
		for I.HasNext()  {
			kv, err := I.Next()
			if err != nil {
				return shim.Error(err.Error())
			}
			buffer.WriteString(kv.String()+ ",")
		}
		return shim.Success(buffer.Bytes())
	}

	return shim.Error("un support query way")
}
