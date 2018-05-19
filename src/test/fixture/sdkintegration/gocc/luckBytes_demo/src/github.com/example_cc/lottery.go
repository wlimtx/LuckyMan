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
	 Asset     float64  `json:"asset"`		//用户资产
	 BetAsset  float64  `json:"bet_asset"`  //赌注
	 LuckBytes []byte	`json:"luck_bytes"` //提交的幸运字节，明文
	 Status    int8 	`json:"status"`  	//三种状态，0 未参与，1 参与， 2 提交了随机因子hash ，3 提交随机因子
	 Cipher	   []byte   `json:"cipher"`		//用于增强最终幸运结果随机性和保密性的因子
	 //IsGenesis int		`json:""`
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

	genesisCout := 2
	if len(args) != 4 {
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
	if args[0] == "transfer" {
		// queries an entity state
		return t.transfer(stub, args)
	}
	if args[0] == "bet" {
		// Deletes an entity from its state
		return t.bet(stub, args)
	}

	if args[0] == "encrypt" {
		return t.encrypt(stub, args)
	}

	if args[0] == "decrypt" {
		return t.decrypt(stub, args)
	}

	if args[0] == "lottery" {
		return t.lottery(stub, args)
	}

	return shim.Error("Unknown action, check the first argument, must be one of 'query', or 'bet', 'encrypt', 'decrypt','lottery'")
}

func (t *LuckBytes) lottery(stub shim.ChaincodeStubInterface, args []string) pb.Response {
	var err error
	I, err := stub.GetStateByRange("", "")
	if err != nil {
		return shim.Error(err.Error())
	}
	var men = make(map[string]*Man)
	//控制man的有序
	keySet := list2.New()
	for ;I.HasNext(); {
		kv, err := I.Next()
		if err != nil {
			return shim.Error(err.Error())
		}
		man := new(Man)
		json.Unmarshal(kv.Value, &man)
		//只考虑参与者
		if man.Status!= 0 {
			//按照数据库的读取顺序
			keySet.PushFront(kv.Key)//主要用于保证访问顺序，map的顺序是随机的
			men[kv.Key] = man
		}
	}
	I.Close()
	fmt.Println("men: ",men)
	ok, response := t.f(stub,keySet, men)
	if !ok {
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

//用于加密最终的输出结果，每轮彩票的参与者都可以进行混淆
func (t *LuckBytes) encrypt(stub shim.ChaincodeStubInterface, args []string) pb.Response {
	//args = encrypt base64Puk base64Cipher
	var base64Puk, base64Cipher string
	base64Puk = args[1]
	base64Cipher = args[2]

	//base64格式的公钥
	rawPuk, err := base64.StdEncoding.DecodeString(base64Puk)
	if err!= nil {
		return shim.Error("bad public key, expect base64 format")
	}
	cipher, err := base64.StdEncoding.DecodeString(base64Cipher)
	if err!= nil {
		return shim.Error("bad plain, expect base64 format")
	}
	//检查该公钥账号是否存在
	sum256 := sha256.Sum256(rawPuk)
	hexPukHash := hex.EncodeToString(sum256[:])
	manBytes, err := stub.GetState(hexPukHash)
	if err != nil {
		return shim.Error("Failed to get state"+ hexPukHash)
	}
	if manBytes == nil {
		return shim.Error("Empty account")
	}
	man:=new(Man)
	json.Unmarshal(manBytes,&man)
	if man.Status != 1 {
		return shim.Error("only participator can encrypt the final result.")
	}
	man.Cipher = cipher
	man.Status= 2
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

//用于解密明文
func (t *LuckBytes) decrypt(stub shim.ChaincodeStubInterface, args []string) pb.Response {
	//args = decrypt base64Puk base64Plain
	var base64Puk, base64Plain string

	base64Puk = args[1]
	base64Plain = args[2]
	//base64格式的公钥
	rawPuk, err := base64.StdEncoding.DecodeString(base64Puk)
	if err!= nil {
		return shim.Error("bad public key, expect base64 format")
	}
	plain, err := base64.StdEncoding.DecodeString(base64Plain)
	if err!= nil {
		return shim.Error("bad plain, expect base64 format")
	}
	//检查该公钥账号是否存在
	sum256 := sha256.Sum256(rawPuk)
	hexPukHash := hex.EncodeToString(sum256[:])

	// Get the state from the ledger
	manBytes, err := stub.GetState(hexPukHash)
	if err != nil {
		return shim.Error("Failed to get state"+ hexPukHash)
	}
	if manBytes == nil {
		return shim.Error("Empty account")
	}
	man:=new(Man)
	json.Unmarshal(manBytes,&man)
	//只有参与投票的人才有权利去
	if man.Status!= 2 {
		return shim.Error("This man hasn't submit the cipher")
	}

	//thrice of hash
	sum256 = sha256.Sum256(plain)
	sum256 = sha256.Sum256(sum256[:])
	sum256 = sha256.Sum256(sum256[:])
	if bytes2.Compare(man.Cipher,sum256[:])!=0{
		return shim.Error("Dummy plain")
	}

	man.Status=3
	man.Cipher = plain
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
//混淆
//func (t *LuckBytes) isAllManCommit(stub shim.ChaincodeStubInterface) (bool, pb.Response) {
//	var err error
//	I, err := stub.GetStateByRange("", "")
//	if err!= nil {
//		return false,shim.Error(err.Error())
//	}
//	for ;I.HasNext();  {
//		I.Next()
//		kv, err := I.Next()
//		if err != nil {
//			return false,shim.Error(err.Error())
//		}
//		man := Man{}
//		json.Unmarshal(kv.Value, &man)
//		//必须所有的人都进行了提交
//		if man.Status == 0 {
//			return false,shim.Error("The second submission must be submitted after all the first submissions")
//		}
//	}
//	return true,shim.Success(nil)
//
//}


//转账
func (t *LuckBytes) transfer(stub shim.ChaincodeStubInterface, args []string) pb.Response {
	var base64ReceiverAddress, base64Sig, base64Puk string
	var asset float64
	var err error
	if len(args) != 5  {
		return shim.Error("Incorrect number of arguments. Expecting 5, function followed by 2 names and 4 value")
	}
	//资产必须是正数
	asset, err = strconv.ParseFloat(args[1],64)
	if err != nil || asset <= 0 {
		return shim.Error("Expecting positive value for asset holding")
	}

	base64ReceiverAddress = args[2] //收款方
	base64Sig = args[3]
	base64Puk = args[4]//转账方

	//base64格式的公钥
	receiverAddress, err := base64.StdEncoding.DecodeString(base64ReceiverAddress)
	if err!= nil {
		return shim.Error("bad public key, expect base64 format")
	}

	receiverMan :=new(Man)
	hexReceiverAddress := hex.EncodeToString(receiverAddress)
	manBytes, err := stub.GetState(hexReceiverAddress)
	if err == nil && manBytes != nil{
		json.Unmarshal(manBytes,&receiverMan)
	}else {
		receiverMan.Asset= 0
		receiverMan.BetAsset= 0
		receiverMan.Status= 0
	}




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

	if hexPukHash == hexReceiverAddress {
		return shim.Error("sender and receiver are the same one")
	}
	// Get the state from the ledger
	manBytes, err = stub.GetState(hexPukHash)
	if err != nil {
		return shim.Error("Failed to get state")
	}
	if manBytes == nil {
		return shim.Error("Empty account")
	}
	senderMan := new(Man)
	json.Unmarshal(manBytes,&senderMan)
	//余额不足
	if senderMan.Asset < asset {
		return shim.Error("not sufficient funds")
	}

	//签名内容为赌注，幸运号码，公钥
	message := ([]byte)(args[1]+ base64ReceiverAddress)
	fmt.Println(message)

	if RsaSignVer(rawPuk, message,rawSig) != nil {
		return shim.Error("dummy signature")//验证签名失败
	}

	receiverMan.Asset += asset
	senderMan.Asset -= asset


	manBytes, err = json.Marshal(senderMan)
	if err != nil{
		return shim.Error(err.Error())
	}
	//更新资产的状态
	err = stub.PutState(hexPukHash, manBytes)
	if err != nil {
		return shim.Error(err.Error())
	}

	manBytes, err = json.Marshal(receiverMan)
	if err != nil{
		return shim.Error(err.Error())
	}
	//更新资产的状态
	err = stub.PutState(hexReceiverAddress, manBytes)
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

//购买幸运号码
func (t *LuckBytes) bet(stub shim.ChaincodeStubInterface, args []string) pb.Response {
	var base64content, base64Sig, base64Puk string
	var betAsset float64
	var err error
	//args := [...]string{"bet", "betAsset", "base64content", "base64Sig", "base64Puk"}

	if len(args) != 5  {
		return shim.Error("Incorrect number of arguments. Expecting 5, function followed by 2 names and 4 value")
	}

	//资产必须是正数
	betAsset, err = strconv.ParseFloat(args[1],64)
	if err != nil || betAsset <= 0 {
		return shim.Error("Expecting positive value for betAsset holding")
	}

	base64content = args[2]//幸运号码
	base64Sig = args[3]
	base64Puk = args[4]

	//提交的幸运号码
	content, err := base64.StdEncoding.DecodeString(base64content)
	if err != nil {
		return shim.Error("bad base64content, expect base64 format")
	}
	//打印购买号码
	fmt.Println("content value: ", hex.EncodeToString(content))

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

	if betAsset > man.Asset /*||money <= 0*/{
		return shim.Error("This man do not have enough money")
	}

	//签名内容为赌注，幸运号码，公钥
	message := ([]byte)(args[1]+ base64content + base64Puk)
	fmt.Println(message)

	if RsaSignVer(rawPuk, message,rawSig) != nil {
		return shim.Error("dummy signature")//验证签名失败
	}
	man.LuckBytes = content
	man.Status = 1
	man.Asset -= betAsset
	man.BetAsset = betAsset
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
			man:=new(Man)
			man.Asset =0
			man.Status=0
			man.BetAsset=0
			manBytes, err = json.Marshal(man)
			if err!= nil {
				return shim.Error(err.Error())
			}

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
