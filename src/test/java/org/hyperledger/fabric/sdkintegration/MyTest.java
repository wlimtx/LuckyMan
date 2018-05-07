package org.hyperledger.fabric.sdkintegration;

import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.*;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric.sdk.testutils.TestConfig;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@SuppressWarnings("Duplicates")
public class MyTest {
    private static final TestConfig testConfig = TestConfig.getConfig();
    private static final String TEST_ADMIN_NAME = "admin";
    private static final String TESTUSER_1_NAME = "user1";
    private static final String TEST_FIXTURES_PATH = "src/test/fixture";

    private static final String CHAIN_CODE_NAME = "lottery_go";
    private static final String CHAIN_CODE_PATH = "github.com/example_cc";
    private static final String CHAIN_CODE_VERSION = "1";

    private static final String FOO_CHANNEL_NAME = "foo";
    private static final String BAR_CHANNEL_NAME = "bar";
    private static Collection<SampleOrg> testSampleOrgs;

    public static void main(String[] args) throws Exception {
        testSampleOrgs = testConfig.getIntegrationTestsSampleOrgs();
//        File sampleStoreFile = new File(System.getProperty("java.io.tmpdir") + "/HFCSampletest.properties");
        File sampleStoreFile = new File(TEST_FIXTURES_PATH + "/sdkintegration/db/HFCSampletest.properties");
        if (!sampleStoreFile.exists()) {
            sampleStoreFile.createNewFile();
        }
        HFClient client = HFClient.createNewInstance();
        client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        // get users for all orgs

        final SampleStore sampleStore = new SampleStore(sampleStoreFile);

        for (SampleOrg sampleOrg : testSampleOrgs) {
            sampleOrg.setCAClient(HFCAClient.createNewInstance(sampleOrg.getCALocation(), sampleOrg.getCAProperties()));
            HFCAClient ca = sampleOrg.getCAClient();
            final String orgName = sampleOrg.getName();
            final String mspid = sampleOrg.getMSPID();
            ca.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
            SampleUser admin = sampleStore.getMember(TEST_ADMIN_NAME, orgName);
            if (!admin.isEnrolled()) {  //Preregistered admin only needs to be enrolled with Fabric caClient.
                admin.setEnrollment(ca.enroll(admin.getName(), "adminpw"));
                admin.setMspId(mspid);
            }

            sampleOrg.setAdmin(admin); // The admin of this org --

            SampleUser user = sampleStore.getMember(TESTUSER_1_NAME, sampleOrg.getName());
            if (!user.isRegistered()) {  // users need to be registered AND enrolled
                RegistrationRequest rr = new RegistrationRequest(user.getName(), "org1.department1");
                user.setEnrollmentSecret(ca.register(rr, admin));
            }
            if (!user.isEnrolled()) {
                user.setEnrollment(ca.enroll(user.getName(), user.getEnrollmentSecret()));
                user.setMspId(mspid);
            }
            sampleOrg.addUser(user); //Remember user belongs to this Org

            final String sampleOrgName = sampleOrg.getName();
            final String sampleOrgDomainName = sampleOrg.getDomainName();

            // src/test/fixture/sdkintegration/e2e-2Orgs/channel/crypto-config/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp/keystore/

            SampleUser peerOrgAdmin = sampleStore.getMember(sampleOrgName + "Admin", sampleOrgName, sampleOrg.getMSPID(),
                    Util.findFileSk(Paths.get(testConfig.getTestChannelPath(), "crypto-config/peerOrganizations/",
                            sampleOrgDomainName, format("/users/Admin@%s/msp/keystore", sampleOrgDomainName)).toFile()),
                    Paths.get(testConfig.getTestChannelPath(), "crypto-config/peerOrganizations/", sampleOrgDomainName,
                            format("/users/Admin@%s/msp/signcerts/Admin@%s-cert.pem", sampleOrgDomainName, sampleOrgDomainName)).toFile());
            sampleOrg.setPeerAdmin(peerOrgAdmin); //A special user that can create channels, join peers and install chaincode

            System.out.println(sampleOrg);
        }
        SampleOrg org1 = testConfig.getIntegrationTestsSampleOrg("peerOrg1");
        SampleOrg org2 = testConfig.getIntegrationTestsSampleOrg("peerOrg2");
        Channel channel;
        if (false) {
            channel = constructChannel(FOO_CHANNEL_NAME, client, org1);
            joinChannel(client, channel, org1, "peer0.org1.example.com");
            joinChannel(client, channel, org1, "peer1.org1.example.com");
//            joinChannel(client, channel, org2, "peer0.org2.example.com");
//            joinChannel(client, channel, org2, "peer1.org2.example.com");
            return;
        }
        client.setUserContext(org1.getUser(TESTUSER_1_NAME));//对于已经存在的应用通道，普通的用户也可以进行访问
        channel = client.newChannel(FOO_CHANNEL_NAME);
        for (Orderer orderer : getOrdererList(client, org1)) channel.addOrderer(orderer);
        org1.addPeer(client.newPeer("peer0.org1.example.com", "grpc://localhost:7051"));
        org1.addPeer(client.newPeer("peer1.org1.example.com", "grpc://localhost:7056"));
        org2.addPeer(client.newPeer("peer0.org2.example.com", "grpc://localhost:8051"));
        org2.addPeer(client.newPeer("peer1.org2.example.com", "grpc://localhost:8056"));
        ArrayList<SampleOrg> orgs = new ArrayList<>();
        orgs.add(org1);
        orgs.add(org2);
        for (String eventHubName : org1.getEventHubNames()) {

            final Properties eventHubProperties = testConfig.getEventHubProperties(eventHubName);

            eventHubProperties.put("grpc.NettyChannelBuilderOption.keepAliveTime", new Object[]{5L, TimeUnit.MINUTES});
            eventHubProperties.put("grpc.NettyChannelBuilderOption.keepAliveTimeout", new Object[]{8L, TimeUnit.SECONDS});

            client.setUserContext(org1.getPeerAdmin());
            EventHub eventHub = client.newEventHub(eventHubName, org1.getEventHubLocation(eventHubName),
                    eventHubProperties);
            channel.addEventHub(eventHub);
        }


        client.setUserContext(org1.getUser(TESTUSER_1_NAME));
//
        channel.initialize();


        if (false) {
//            installChaincode(client, channel, org1, 0, 1);
            installChaincode(client, channel, org1, 0, 1);//暂时这两个动作要一起执行
            //背书节点背书
            Collection<ProposalResponse> proposalResponses = instantiateChaincode(client, channel, org1, 0, 1);//1,-,0,0,
            channel.sendTransaction(proposalResponses, channel.getOrderers()).thenApply(transactionEvent -> {
                assertTrue(transactionEvent.isValid()); // must be valid to be here.
                out("Finished instantiate transaction with transaction id %s", transactionEvent.getTransactionID());
                return null;
            });
            return;
        }

        for (Peer peer : org1.getPeers()) channel.addPeer(peer);
//        for (Peer peer : org2.getPeers()) channel.addPeer(peer);
        queryChaincode(client, channel, org1, 0, 1, "fa67ad29516c1caa360ab946d3c02ec683ef2569c65e091da57178f502fea0c1");
        queryChaincode(client, channel, org1, 0, 1, "6d65c8f0555e270bd562ab84235c965de942390b1c99b25a9646095b81c78021");
        queryChaincode(client, channel, org1, 0, 1, "5c2dc969509e8021597c1eb37b0b3534b26972abe5721cf3c45f0fd86ac6133c");
        queryChaincode(client, channel, org1, 0, 1, "e762186ee2d974e2849d6058ec4a35ec04cae6ef794a6a9197dc0d4a3a74fa0d");

//        if (true)return;
        client.setUserContext(org1.getPeerAdmin());

        ArrayList<Peer> peers = new ArrayList<>();
        peers.add(org1.getPeers().iterator().next());
//        peers.add(org2.getPeers().iterator().next());
        Collection<ProposalResponse> proposalResponses = invokeChaincode(client, channel, peers, new String[]{
"lottery","a"
//                "bet", "1", "10", "yFNYF+PmuC66cNDqhyhGNRpXkaAIAR3fqmwFDq2LmE4=", "q5JTjDpHSVFbNpkE/k6Kg/4zrZJYb8jwiaZyViBHCdKUG9QH4tMXFf9XeeEQQKgEYvzYPyuMUx67qYie1b7JafLwd0B0sxfkC9PDymdA3lxFjUYBrQNEfo3/BE71DDYdC5/u2+0E4vULvI4OZAdNOcDk2ma08+hzbUw3BShEiuVOGAXLf+IMKKWSgzFl+IJsZN6WnUeHLHVtYyNGx3L0yqDPYWRlPIAHRewM07GRok5Eo5RHoe2ycgUAilbqZRAM7QwVg5+Z9f9SzpmfhFuXmY5hBvkgbBnFUyRdUmzNTZfGyyGDRrdWer7c0lbl7w78hCMASGrpIPAIFfwwklp88w==", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1hxwCC12Xui3/JxfPA2pUQ4ev8JAfDZkWpQ6lEGUJLtINKYOWRpUZ3OeLs0RVGfkhzf9s68PLGXPU6ZX5eVaR+NDrKi0Y8/0sT98o3nz/ccDqeEgJzN7CXmCEiDtOnlFEJOVfPW25NOU+EU+LRzJBcWobSgUC48zTfzZcCkekxrE61hbvOi5MeqCti9waucsxBYQegj+78JSixOkuuOmiT3K6izwI3ZjWpioxsOuxCJF+QxmJXEGWF0egHSt5HwSGRr7TnauTmHdgm8/KlrolyoQ/yPoFe1pwEzg9xo3atZVNx/kNszyJ3cJSwjsvTXVJ/hdFB639S1eWAldjePg7QIDAQAB"

//                "bet", "5", "5", "3TTb/iHa9h2QfmNJD9PEn28SGz+wGE5t6Gxe26oj8/s=", "mUPiuj91fcGFcgq69C8Fn/Py0BVFNYdUeCC+7ZRTTmCPQI1MMEuVvYBSMixPM5OQygooph4uNFJOgarqMgkaF9Sx06sfKWpN1SDBGgBUO9eUDA0wi+5ovWuZ271yDCEQmQrDcNc5zFFLssL8xMsp8CKoJQy2R2ijlXfMANAuclBtgSPmcLZli/Zddk56pYzXBEVcFrahC3O/XTaE9/NQja4L9JfAfzDXdj0dgKgg2NSaoirzgmcxuUhMtXquhC+DoVx7v2LvSa8GKr07tFrxN+jUnfywfrtbvDl3OxmRMx4WHs94nnfT0lIfHnrtg9s4BqXF3n3Z5jkXcQ8apc+ePA==", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxqZK5PmwmJJP1gLt7kKryINxwFt0HdkOuSBNuk4t+dveWKq1+NeAKTz+2KN7w9MndrsVRY5C0lKtn30iIPHvKjf5hrgaWQMF+T56XG10IZZjt1yjBRCV9uUAD5Zlsd9xEZFRDzzNuNRpacfo1e3YITEkAJsMmHAmYf6jz/vKgbWQN3/0k5+KpxTD6avcqLP5un5riXhE3j3Thd6vOQhPh/ocLu7030PCBa1pS0thKsBPgmI3MtHpA7jE7TBBE/slFwaZuhmDbG9f5vCeGcJkahfLj58ifok1SH/u6tMiqiQNxLaYVeAZ+zcX29Z748P3Usjp2S1TcLBPsbMQkva9/QIDAQAB"


//                "bet", "14", "20", "nZkbF2pj16moe54KbGZF70KiwOcuWs/ogIQedWtWkcc=", "boBToYiUNsJCHsCGbG5U1mrU26VQ2+WNZ4+vqN5hAvY+q71vOzr0bk+KfddWa9lo/rSoBpCwbYfP+WRLbnADZ12900GZnISwmk32NxmhXEY6M43TObGd3YWXaqDvptXG9slx0K7b2TZpszRyShpM+z2eLMDu7klw4QCfSGc1v9vn9Lazf1N5hyB3SdccotbHQNeI/sQR/98pRaYtjeMzMGVdov207UTMg7PvIixeAmQChOf5PbYQKShKC+Lm6N0VeIC895JrqIsadtf7+qnUr3cXJAP6iTbg1C3fCQBmab2F5dqxjmYbtkzNJ86IWFskFz9XhimM+qcDblFsOwfI/Q==", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA26QugKB980Yh4KGkNFx5fjohk2ED9Y/zk15CB2dlYcmrSu7uIgAf/Orb8TyrpnG4RazZvu9p6q6c6tB7HBepSsG/RvbdHN2F9G+5YZX1wdLODVNTkevs1KElTCOQpBGf74q0hEgVb7yn2SCYHcFPGDoSPE6+MzE/OYxUxJ6BYD7TkJ59uYEa1eozphJ3U049RoXP55VyXTOrEnLcxbmmQedLz5B2gaZ9cuvW2CfeafulnmwrpXcc3Yh8g4TT0Oicmr85URPG/IFzGNAT9OARyPwqmimoZuPeLJ7JDVb6AvrhCisIdNzj84WhhTF3KRw0pRKvACtBi+L/DXXfLj4O0wIDAQAB"

//                "bet", "20", "40", "88uX5HkN05htW9Ftirn9gOpGG6o/2V6c1BAYFGjfLLA=", "naCc1qUiHrzlJY5nXXmMpcTDItQ2vKECH9EXtK4xfLjD08casggbw6Q7e59kmSmK3Vot1jQYocC3HEZRiwpqGGWz6g1N46dl87D9+ZdWU/IYxPZ/an2d0znT+pvC8w5R7uv1xhzGtrauW0ta+/FOg0iG184X9ju916o0yQI7SI22V8JQswuwOetAExKToJEW8ZC2ecwbrslGlRLz94Vxas76VLxjrHOBGyfQPWcLHTypCBOIvEtjdIdE6+yOS+ssPHK/VwwCx9rxglPSrjQdRHR0CMipPwlebPUv4DfueWcdJV5k0dT8mzuffjxTD1z7ziiRVVF0bbvzRQEaQ5X/qw==", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwLftxdynlgBERcWhufQ8kxCYztfcjcvWNioFLcCSo4AgBfowEDAXN9IUvUqNtFAHuyxhvB+bU4N6K4KhbkZeDBjbeTm5DiaW8klI5WAi4/51byl3dxPPL1MmjLwnGZMnC/A8002l4hDV4BVhvFeIIyjUKtcF+X0krH3h+5Hv9a1CX7K8mebhivbNFksM1KAZauZ0qJf9TRDC1JY9M+braC17hb52tBGWtnkQ9KDo78Svx6VFZgwl3itDj7E6dnfevajwoPJtmcCQEiA+ym8Nq+Uxv8Wvdq72vMEuXiWtBR6oo6rqaJG2n8mSdEVE/NVs0Qv0Cl/i7UzOTlJQR58dowIDAQAB"
        });

        client.setUserContext(org1.getPeerAdmin());
        String transactionID = channel.sendTransaction(proposalResponses).get(testConfig.getTransactionWaitTime(), TimeUnit.SECONDS).getTransactionID();
        out("Finished invoke transaction with transaction id %s", transactionID);
        //;
        queryChaincode(client, channel, org1, 0, 1, "fa67ad29516c1caa360ab946d3c02ec683ef2569c65e091da57178f502fea0c1");
        queryChaincode(client, channel, org1, 0, 1, "6d65c8f0555e270bd562ab84235c965de942390b1c99b25a9646095b81c78021");
        queryChaincode(client, channel, org1, 0, 1, "5c2dc969509e8021597c1eb37b0b3534b26972abe5721cf3c45f0fd86ac6133c");
        queryChaincode(client, channel, org1, 0, 1, "e762186ee2d974e2849d6058ec4a35ec04cae6ef794a6a9197dc0d4a3a74fa0d");
    }

    static void queryChaincode(HFClient client, Channel channel, SampleOrg org, int start, int limit, String argsMan) throws InvalidArgumentException {
        // Send Query Proposal to all peers
        //
        try {
            out("Now query chaincode for the value of " + argsMan + ".");
            QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
            queryByChaincodeRequest.setArgs(new String[]{"query", argsMan});
            queryByChaincodeRequest.setFcn("invoke");
            queryByChaincodeRequest.setChaincodeID(ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME)
                    .setVersion(CHAIN_CODE_VERSION)
                    .setPath(CHAIN_CODE_PATH).build());

            Map<String, byte[]> tm2 = new HashMap<>();
            tm2.put("HyperLedgerFabric", "QueryByChaincodeRequest:JavaSDK".getBytes(UTF_8));
            tm2.put("method", "QueryByChaincodeRequest".getBytes(UTF_8));
            queryByChaincodeRequest.setTransientMap(tm2);
            Collection<Peer> peersFromOrg = new ArrayList<>();
            org.getPeers().stream().skip(start).limit(limit).forEach(peersFromOrg::add);
            Collection<ProposalResponse> queryProposals = channel.queryByChaincode(queryByChaincodeRequest, peersFromOrg);
            for (ProposalResponse proposalResponse : queryProposals) {
                if (!proposalResponse.isVerified() || proposalResponse.getStatus() != ProposalResponse.Status.SUCCESS) {
                    fail("Failed query proposal from peer " + proposalResponse.getPeer().getName() + " status: " + proposalResponse.getStatus() +
                            ". Messages: " + proposalResponse.getMessage()
                            + ". Was verified : " + proposalResponse.isVerified());
                } else {
                    String payload = proposalResponse.getProposalResponse().getResponse().getPayload().toStringUtf8();
                    out("Query payload of b from peer %s returned %s", proposalResponse.getPeer().getName(), payload);
                }
            }

        } catch (Exception e) {
            out("Caught exception while running query");
            e.printStackTrace();
            fail("Failed during chaincode query with error : " + e.getMessage());
        }
    }

    static Collection<ProposalResponse> invokeChaincode(HFClient client, Channel channel, Collection<Peer> peers,String[] args) {
        try {

            Collection<ProposalResponse> successful = new LinkedList<>();
            Collection<ProposalResponse> failed = new LinkedList<>();
            /// Send transaction proposal to all peers
            TransactionProposalRequest transactionProposalRequest = client.newTransactionProposalRequest();
            transactionProposalRequest.setChaincodeID(ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME)
                    .setVersion(CHAIN_CODE_VERSION)
                    .setPath(CHAIN_CODE_PATH).build());
            transactionProposalRequest.setFcn("invoke");
            transactionProposalRequest.setProposalWaitTime(testConfig.getProposalWaitTime());
            //这里有一个bug，同一份交易提案可以反复提交, 因为用同样的内容相同的签名
            //bet asset nonce random signature public
            transactionProposalRequest.setArgs(args);

            Map<String, byte[]> tm2 = new HashMap<>();
            tm2.put("HyperLedgerFabric", "TransactionProposalRequest:JavaSDK".getBytes(UTF_8));
            tm2.put("method", "TransactionProposalRequest".getBytes(UTF_8));
            tm2.put("result", ":)".getBytes(UTF_8));  /// This should be returned see chaincode.
            transactionProposalRequest.setTransientMap(tm2);

            out("sending transactionProposal to all peers with arguments: move(a,b,100)");


            Collection<ProposalResponse> transactionPropResp = channel.sendTransactionProposal(transactionProposalRequest, peers);
            for (ProposalResponse response : transactionPropResp) {
                if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                    out("Successful transaction proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
                    successful.add(response);
                } else {
                    failed.add(response);
                }
            }

            // Check that all the proposals are consistent with each other. We should have only one set
            // where all the proposals above are consistent.
            Collection<Set<ProposalResponse>> proposalConsistencySets = SDKUtils.getProposalConsistencySets(transactionPropResp);
            if (proposalConsistencySets.size() != 1) {
                fail(format("Expected only one set of consistent proposal responses but got %d", proposalConsistencySets.size()));
            }

            out("Received %d transaction proposal responses. Successful+verified: %d . Failed: %d",
                    transactionPropResp.size(), successful.size(), failed.size());
            if (failed.size() > 0) {
                ProposalResponse firstTransactionProposalResponse = failed.iterator().next();
                fail("Not enough endorsers for invoke(move a,b,100):" + failed.size() + " endorser error: " +
                        firstTransactionProposalResponse.getMessage() +
                        ". Was verified: " + firstTransactionProposalResponse.isVerified());
            }
            out("Successfully received transaction proposal responses.");

            ProposalResponse resp = transactionPropResp.iterator().next();
            byte[] x = resp.getChaincodeActionResponsePayload(); // This is the data returned by the chaincode.
            String resultAsString = null;
            if (x != null) {
                resultAsString = new String(x, "UTF-8");
            }
            assertEquals(":)", resultAsString);

            assertEquals(200, resp.getChaincodeActionResponseStatus()); //Chaincode's status.

            TxReadWriteSetInfo readWriteSetInfo = resp.getChaincodeActionResponseReadWriteSetInfo();
            //See blockwalker below how to transverse this
            assertNotNull(readWriteSetInfo);
            Assert.assertTrue(readWriteSetInfo.getNsRwsetCount() > 0);

            ChaincodeID cid = resp.getChaincodeID();
            assertNotNull(cid);
            assertEquals(CHAIN_CODE_PATH, cid.getPath());
            assertEquals(CHAIN_CODE_NAME, cid.getName());
            assertEquals(CHAIN_CODE_VERSION, cid.getVersion());


            return successful;
        } catch (Exception e) {
            out("Caught an exception while invoking chaincode");
            e.printStackTrace();
            fail("Failed invoking chaincode with error : " + e.getMessage());
        }


//        Assert.assertTrue(transactionEvent.isValid()); // must be valid to be here.
//        out("Finished transaction with transaction id %s", transactionEvent.getTransactionID());
//        testTxID = transactionEvent.getTransactionID(); // used in the channel queries later

        return null;
    }

    @Deprecated
    static Collection<ProposalResponse> invokeChaincode(HFClient client, Channel channel, SampleOrg org, int start, int limit) {

        try {

//            client.setUserContext(org.getPeerAdmin());
            Collection<ProposalResponse> successful = new LinkedList<>();
            Collection<ProposalResponse> failed = new LinkedList<>();
            /// Send transaction proposal to all peers
            TransactionProposalRequest transactionProposalRequest = client.newTransactionProposalRequest();
            transactionProposalRequest.setChaincodeID(ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME)
                    .setVersion(CHAIN_CODE_VERSION)
                    .setPath(CHAIN_CODE_PATH).build());
            transactionProposalRequest.setFcn("invoke");
            transactionProposalRequest.setProposalWaitTime(testConfig.getProposalWaitTime());
            transactionProposalRequest.setArgs(new String[]{"move", "a", "b", "100"});

            Map<String, byte[]> tm2 = new HashMap<>();
            tm2.put("HyperLedgerFabric", "TransactionProposalRequest:JavaSDK".getBytes(UTF_8));
            tm2.put("method", "TransactionProposalRequest".getBytes(UTF_8));
            tm2.put("result", ":)".getBytes(UTF_8));  /// This should be returned see chaincode.
            transactionProposalRequest.setTransientMap(tm2);

            out("sending transactionProposal to all peers with arguments: move(a,b,100)");

            Collection<Peer> peersFromOrg = new ArrayList<>();
            org.getPeers().stream().skip(start).limit(limit).forEach(peersFromOrg::add);

            Collection<ProposalResponse> transactionPropResp = channel.sendTransactionProposal(transactionProposalRequest, peersFromOrg);
            for (ProposalResponse response : transactionPropResp) {
                if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                    out("Successful transaction proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
                    successful.add(response);
                } else {
                    failed.add(response);
                }
            }

            // Check that all the proposals are consistent with each other. We should have only one set
            // where all the proposals above are consistent.
            Collection<Set<ProposalResponse>> proposalConsistencySets = SDKUtils.getProposalConsistencySets(transactionPropResp);
            if (proposalConsistencySets.size() != 1) {
                fail(format("Expected only one set of consistent proposal responses but got %d", proposalConsistencySets.size()));
            }

            out("Received %d transaction proposal responses. Successful+verified: %d . Failed: %d",
                    transactionPropResp.size(), successful.size(), failed.size());
            if (failed.size() > 0) {
                ProposalResponse firstTransactionProposalResponse = failed.iterator().next();
                fail("Not enough endorsers for invoke(move a,b,100):" + failed.size() + " endorser error: " +
                        firstTransactionProposalResponse.getMessage() +
                        ". Was verified: " + firstTransactionProposalResponse.isVerified());
            }
            out("Successfully received transaction proposal responses.");

            ProposalResponse resp = transactionPropResp.iterator().next();
            byte[] x = resp.getChaincodeActionResponsePayload(); // This is the data returned by the chaincode.
            String resultAsString = null;
            if (x != null) {
                resultAsString = new String(x, "UTF-8");
            }
            assertEquals(":)", resultAsString);

            assertEquals(200, resp.getChaincodeActionResponseStatus()); //Chaincode's status.

            TxReadWriteSetInfo readWriteSetInfo = resp.getChaincodeActionResponseReadWriteSetInfo();
            //See blockwalker below how to transverse this
            assertNotNull(readWriteSetInfo);
            Assert.assertTrue(readWriteSetInfo.getNsRwsetCount() > 0);

            ChaincodeID cid = resp.getChaincodeID();
            assertNotNull(cid);
            assertEquals(CHAIN_CODE_PATH, cid.getPath());
            assertEquals(CHAIN_CODE_NAME, cid.getName());
            assertEquals(CHAIN_CODE_VERSION, cid.getVersion());


            return successful;
        } catch (Exception e) {
            out("Caught an exception while invoking chaincode");
            e.printStackTrace();
            fail("Failed invoking chaincode with error : " + e.getMessage());
        }


//        Assert.assertTrue(transactionEvent.isValid()); // must be valid to be here.
//        out("Finished transaction with transaction id %s", transactionEvent.getTransactionID());
//        testTxID = transactionEvent.getTransactionID(); // used in the channel queries later

        return null;
    }

    static Collection<ProposalResponse> instantiateChaincode(HFClient client, Channel channel, SampleOrg org, int start, int limit) throws InvalidArgumentException, IOException, ChaincodeEndorsementPolicyParseException, ProposalException {
        channel.setTransactionWaitTime(testConfig.getTransactionWaitTime());
        channel.setDeployWaitTime(testConfig.getDeployWaitTime());
        //// Instantiate chaincode.
        final ChaincodeID chaincodeID;
        Collection<ProposalResponse> responses;
        Collection<ProposalResponse> successful = new LinkedList<>();
        Collection<ProposalResponse> failed = new LinkedList<>();
        chaincodeID = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME)
                .setVersion(CHAIN_CODE_VERSION)
                .setPath(CHAIN_CODE_PATH).build();
        InstantiateProposalRequest instantiateProposalRequest = client.newInstantiationProposalRequest();
        instantiateProposalRequest.setProposalWaitTime(testConfig.getProposalWaitTime());
        instantiateProposalRequest.setChaincodeID(chaincodeID);
        instantiateProposalRequest.setFcn("init");
        instantiateProposalRequest.setArgs(new String[]{
//                "FlashMan", "IronMan", "SpriderMan", "SuperMan",
                "fa67ad29516c1caa360ab946d3c02ec683ef2569c65e091da57178f502fea0c1",
                "6d65c8f0555e270bd562ab84235c965de942390b1c99b25a9646095b81c78021",
                "5c2dc969509e8021597c1eb37b0b3534b26972abe5721cf3c45f0fd86ac6133c",
                "e762186ee2d974e2849d6058ec4a35ec04cae6ef794a6a9197dc0d4a3a74fa0d",
                "100", "100",
                "100", "100",
        });

        Map<String, byte[]> tm = new HashMap<>();
        tm.put("HyperLedgerFabric", "InstantiateProposalRequest:JavaSDK".getBytes(UTF_8));
        tm.put("method", "InstantiateProposalRequest".getBytes(UTF_8));
        instantiateProposalRequest.setTransientMap(tm);

            /*
              policy OR(Org1MSP.member, Org2MSP.member) meaning 1 signature from someone in either Org1 or Org2
              See README.md Chaincode endorsement policies section for more details.
            */
        client.setUserContext(org.getPeerAdmin());
        ChaincodeEndorsementPolicy chaincodeEndorsementPolicy = new ChaincodeEndorsementPolicy();
        chaincodeEndorsementPolicy.fromYamlFile(new File(TEST_FIXTURES_PATH + "/sdkintegration/chaincodeendorsementpolicy.yaml"));
        instantiateProposalRequest.setChaincodeEndorsementPolicy(chaincodeEndorsementPolicy);


        org.getPeers().forEach(peer -> {
            try {
                channel.addPeer(peer);
            } catch (InvalidArgumentException e) {
                e.printStackTrace();
            }
        });
        Collection<Peer> peers = new ArrayList<>();
        channel.getPeers().stream().skip(start).limit(limit).forEach(peers::add);//限制本组织的服务器节点添加个数

        responses = channel.sendInstantiationProposal(instantiateProposalRequest, peers);

        for (ProposalResponse response : responses) {
            if (response.isVerified() && response.getStatus() == ProposalResponse.Status.SUCCESS) {
                successful.add(response);
                out("Succesful instantiate proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
            } else {
                failed.add(response);
            }
        }
        out("Received %d instantiate proposal responses. Successful+verified: %d . Failed: %d", responses.size(), successful.size(), failed.size());
        if (failed.size() > 0) {
            ProposalResponse first = failed.iterator().next();
            fail("Not enough endorsers for instantiate :" + successful.size() + "endorser failed with " + first.getMessage() + ". Was verified:" + first.isVerified());
        }

        ///////////////
        /// Send instantiate transaction to orderer
        return successful;//返回成功的提案响应
    }

    static void installChaincode(HFClient client, Channel channel, SampleOrg org, int start, int limit) {
        try {
            out("Running channel %s", channel.getName());
            channel.setTransactionWaitTime(testConfig.getTransactionWaitTime());
            channel.setDeployWaitTime(testConfig.getDeployWaitTime());

            final ChaincodeID chaincodeID;
            Collection<ProposalResponse> responses;
            Collection<ProposalResponse> successful = new LinkedList<>();
            Collection<ProposalResponse> failed = new LinkedList<>();

            chaincodeID = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME)
                    .setVersion(CHAIN_CODE_VERSION)
                    .setPath(CHAIN_CODE_PATH).build();

            // Install Proposal Request
            client.setUserContext(org.getPeerAdmin());

            out("Creating install proposal");

            client.setUserContext(org.getPeerAdmin());//必须要组织的管理员才能安装链码
            InstallProposalRequest installProposalRequest = client.newInstallProposalRequest();
            installProposalRequest.setChaincodeID(chaincodeID);
            ////For GO language and serving just a single user, chaincodeSource is mostly likely the users GOPATH
            installProposalRequest.setChaincodeSourceLocation(new File(TEST_FIXTURES_PATH + "/sdkintegration/gocc/lottery"));
            installProposalRequest.setChaincodeVersion(CHAIN_CODE_VERSION);
            out("Sending install proposal");

            // only a client from the same org as the peer can issue an install request
            int numInstallProposal = 0;

            Collection<Peer> peersFromOrg = new ArrayList<>();
            org.getPeers().stream().skip(start).limit(limit).forEach(peersFromOrg::add);
            numInstallProposal = limit;
            responses = client.sendInstallProposal(installProposalRequest, peersFromOrg);

            for (ProposalResponse response : responses) {
                if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                    out("Successful install proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
                    successful.add(response);
                } else {
                    failed.add(response);
                }
            }

            //判断所有的节点是否对于安装提案达成一致
            SDKUtils.getProposalConsistencySets(responses);
            //   }
            out("Received %d install proposal responses. Successful+verified: %d . Failed: %d", numInstallProposal, successful.size(), failed.size());

            if (failed.size() > 0) {
                ProposalResponse first = failed.iterator().next();
                fail("Not enough endorsers for install :" + successful.size() + ".  " + first.getMessage());
            }

        } catch (Exception e) {
            out("Caught an exception running channel %s", channel.getName());
            e.printStackTrace();
            fail("Test failed with error : " + e.getMessage());
        }
    }

    static void joinChannel(HFClient client, Channel channel, SampleOrg org, String peerName) throws InvalidArgumentException, ProposalException {
        client.setUserContext(org.getPeerAdmin());//加入应用通道必须要管理员授权，这个管理员是orderer组织系统链上描述的
        String peerLocation = org.getPeerLocation(peerName);
        Properties peerProperties = testConfig.getPeerProperties(peerName); //test properties for peer.. if any.
        if (peerProperties == null) {
            peerProperties = new Properties();
        }
        //Example of setting specific options on grpc's NettyChannelBuilder
        peerProperties.put("grpc.NettyChannelBuilderOption.maxInboundMessageSize", 9000000);

        Peer peer = client.newPeer(peerName, peerLocation, peerProperties);
        channel.joinPeer(peer);
        out("Peer %s joined channel %s", peerName, channel.getName());


    }

    static Channel constructChannel(String name, HFClient client, SampleOrg sampleOrg) throws Exception {
        ////////////////////////////
        //Construct the channel
        //

        out("Constructing channel %s", name);

        //Only peer Admin org
        client.setUserContext(sampleOrg.getPeerAdmin());


        //Just pick the first orderer in the list to create the channel.
        Orderer anOrderer = getOrdererList(client, sampleOrg).iterator().next();

        ChannelConfiguration channelConfiguration = new ChannelConfiguration(new File(TEST_FIXTURES_PATH + "/sdkintegration/e2e-2Orgs/channel/" + name + ".tx"));

        //Create channel that has only one signer that is this orgs peer admin. If channel creation policy needed more signature they would need to be added too.
        Channel newChannel = client.newChannel(name, anOrderer, channelConfiguration, client.getChannelConfigurationSignature(channelConfiguration, sampleOrg.getPeerAdmin()));

        out("Created channel %s", name);



        return newChannel;

    }

    public static Collection<Orderer> getOrdererList(HFClient client, SampleOrg org) throws InvalidArgumentException {
        Collection<Orderer> orderers = new LinkedList<>();

        for (String orderName : org.getOrdererNames()) {

            Properties ordererProperties = testConfig.getOrdererProperties(orderName);

            //example of setting keepAlive to avoid timeouts on inactive http2 connections.
            // Under 5 minutes would require changes to server side to accept faster ping rates.
            ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveTime", new Object[]{5L, TimeUnit.MINUTES});
            ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveTimeout", new Object[]{8L, TimeUnit.SECONDS});

            orderers.add(client.newOrderer(orderName, org.getOrdererLocation(orderName),
                    ordererProperties));
        }
        return orderers;
    }
    static void out(String format, Object... args) {

        System.err.flush();
        System.out.flush();

        System.out.println(format(format, args));
        System.err.flush();
        System.out.flush();

    }
}
