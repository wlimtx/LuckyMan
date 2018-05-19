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
    private static final String ChainCodeNavtivePath = "/sdkintegration/gocc/luckBytes_demo";
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
            installChaincode(client, channel, org1, 0, 2);//暂时这两个动作要一起执行
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
        queryChaincode(client, channel, org1, 0, 1);
//        if (true)return;

//        if (true)return;
        client.setUserContext(org1.getPeerAdmin());

        ArrayList<Peer> peers = new ArrayList<>();
        Iterator<Peer> I = org1.getPeers().iterator();
        peers.add(I.next());
        peers.add(I.next());
//        peers.add(org2.getPeers().iterator().next());
        Collection<ProposalResponse> proposalResponses = invokeChaincode(client, channel, peers, new String[]{
"lottery"

//                "bet", "80", "cgZjfU4VWATsteKEa0+UdqalGzBoKb2V5FR6Bqptq3c=", "xkQFcNGHd7HXgKtDmJ2xjEDeJ+JidaM9AQ9uSasfhxkkFP/Lq9vOzmZCIBIAT+EroY2D3CnVMQu2WEQmiaTKOhtJnNeS6xqHaifyehaq2I2MRLKvZUDXK7cRM6k1ksI1A1XLB/0Fz1xAnpGzFqGUl7tyqVp75tpkAJwlvnsCKyM7gQlvRPavEMVaKP18nmNd3AJV52rvKcQfC/F3C87hbF28DJGszcl5y6EQwnDd4Xg1FoM9QMCgHU3B70r41ueYoLzCRdKbTyK8qPAPIjG1mNVTdc2fGNrsl0ZrlMEP/HDqMorF4tukHe4YE1ViU0bhYMOzqlj6qbwttceP8bAQ4A==", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1hxwCC12Xui3/JxfPA2pUQ4ev8JAfDZkWpQ6lEGUJLtINKYOWRpUZ3OeLs0RVGfkhzf9s68PLGXPU6ZX5eVaR+NDrKi0Y8/0sT98o3nz/ccDqeEgJzN7CXmCEiDtOnlFEJOVfPW25NOU+EU+LRzJBcWobSgUC48zTfzZcCkekxrE61hbvOi5MeqCti9waucsxBYQegj+78JSixOkuuOmiT3K6izwI3ZjWpioxsOuxCJF+QxmJXEGWF0egHSt5HwSGRr7TnauTmHdgm8/KlrolyoQ/yPoFe1pwEzg9xo3atZVNx/kNszyJ3cJSwjsvTXVJ/hdFB639S1eWAldjePg7QIDAQAB"

//                "encrypt", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1hxwCC12Xui3/JxfPA2pUQ4ev8JAfDZkWpQ6lEGUJLtINKYOWRpUZ3OeLs0RVGfkhzf9s68PLGXPU6ZX5eVaR+NDrKi0Y8/0sT98o3nz/ccDqeEgJzN7CXmCEiDtOnlFEJOVfPW25NOU+EU+LRzJBcWobSgUC48zTfzZcCkekxrE61hbvOi5MeqCti9waucsxBYQegj+78JSixOkuuOmiT3K6izwI3ZjWpioxsOuxCJF+QxmJXEGWF0egHSt5HwSGRr7TnauTmHdgm8/KlrolyoQ/yPoFe1pwEzg9xo3atZVNx/kNszyJ3cJSwjsvTXVJ/hdFB639S1eWAldjePg7QIDAQAB", "JIiBrQv4o4bptsKGHLshcq5n/7N70nrt70VYbD7JwXc=", "BlvzzAaAV5U4O06fkQzq+Q=="

//                "bet", "90", "57DOXHh/zFnvMZ6o59YXysbE43vsoXiwT7Z20e7SgW0=", "xYWmooOmvNh6D/+YMyEI8qkTbm4KflQ9rJhzpi9KaC9O4mRCND0xFmuLxUbZLmwneBb8CTEoV+RNAO6+AbOkx0LP14h0bihKUQnoyPz+R4H1mHyzvynudY+BwgojDl1ZoKF3MhGCbKQwQZbTH6nXB6uoRpCxklx5nXcJr88P7E3unpLHaQEIsxn7FJyaKFwQAxF8v/UzBcLBX5NGrjMUKWMcDG38IP0kffXTUw8lpkAs5AnB8VTmTRM70h1hHd/MhSh/jKfzwGFv+RJf6Oux1QBDHtpqoVXZBKbF2U69EiPzl2j4t6qkwMm9MR+iDDyDBnXZgqivHLbFmcKB14l/sw==", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxqZK5PmwmJJP1gLt7kKryINxwFt0HdkOuSBNuk4t+dveWKq1+NeAKTz+2KN7w9MndrsVRY5C0lKtn30iIPHvKjf5hrgaWQMF+T56XG10IZZjt1yjBRCV9uUAD5Zlsd9xEZFRDzzNuNRpacfo1e3YITEkAJsMmHAmYf6jz/vKgbWQN3/0k5+KpxTD6avcqLP5un5riXhE3j3Thd6vOQhPh/ocLu7030PCBa1pS0thKsBPgmI3MtHpA7jE7TBBE/slFwaZuhmDbG9f5vCeGcJkahfLj58ifok1SH/u6tMiqiQNxLaYVeAZ+zcX29Z748P3Usjp2S1TcLBPsbMQkva9/QIDAQAB"

//                "bet", "40", "qCIU0VgOmbo0fjGlcRtVbePjjXX5sFT9cMx1nqNdOm0=", "sYUZljhObOf85LMmybBDgMfJ7tpE+stYE38dQa8xBHS2L2oPFtnacgHdlBeB54rzY7Uag2qzPvpQ3WQOMNfP3uUS0yaraR10EwsC9RRRI5tzd4pNTmGhneeTTkVILKrCQTWetFwShbUo6lvX/W2sDBgp45129mmDdQ4sqsk+YdaG5zDQli4+f2VtIgz3w4WDEsoHPyu9pO+E4HJ9pLA3aScYzDuExhfJmHqkiEiB3ha5tB4BDhy+icsYnDjJ54i+OEXiBjl8fl/cddyBssqOW19JXKoI/daZegpKte9F2chqHXkutgUkb3Ue2cDJ4qPM8GIQgKKEJi2tr2wfoAAEEQ==", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA26QugKB980Yh4KGkNFx5fjohk2ED9Y/zk15CB2dlYcmrSu7uIgAf/Orb8TyrpnG4RazZvu9p6q6c6tB7HBepSsG/RvbdHN2F9G+5YZX1wdLODVNTkevs1KElTCOQpBGf74q0hEgVb7yn2SCYHcFPGDoSPE6+MzE/OYxUxJ6BYD7TkJ59uYEa1eozphJ3U049RoXP55VyXTOrEnLcxbmmQedLz5B2gaZ9cuvW2CfeafulnmwrpXcc3Yh8g4TT0Oicmr85URPG/IFzGNAT9OARyPwqmimoZuPeLJ7JDVb6AvrhCisIdNzj84WhhTF3KRw0pRKvACtBi+L/DXXfLj4O0wIDAQAB"

//                "bet", "70", "HvW1VTeq/DzGSFzIzS6KaOGz/PvdJVNUt6/q1OB4L1Q=", "VeO2LKjBC3XUl4N8aBriF5bQdj6KtgD2MdKKTjHFqQapT71Ypgba0aNePKTXGjig3c9NnT0UXWfZi6uEGafr/n/UY1oUAFuM5RKImRh+rWKnxcMBfPFS2G96EYs9j4As0dWVeqrwJpnX5GnQ/BP5Dz2jvYThAl985hLSqvrHFh8kAvKJ5mBS/cyuly70ta1ddUCnY+NWa4JHFa8m2x9sFzyX0LH5cW6LZj5vlifSIs6P0U4u8bi0jnVzwtD0jHM3R0aD3L7AA1C8pgeOeHm5/8b2+FrVbNeAeVgib63gH2MC8bXThSzu2oLuV5WWKS5qIEBhH2SLrVVDLfNSyQwhpw==", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwLftxdynlgBERcWhufQ8kxCYztfcjcvWNioFLcCSo4AgBfowEDAXN9IUvUqNtFAHuyxhvB+bU4N6K4KhbkZeDBjbeTm5DiaW8klI5WAi4/51byl3dxPPL1MmjLwnGZMnC/A8002l4hDV4BVhvFeIIyjUKtcF+X0krH3h+5Hv9a1CX7K8mebhivbNFksM1KAZauZ0qJf9TRDC1JY9M+braC17hb52tBGWtnkQ9KDo78Svx6VFZgwl3itDj7E6dnfevajwoPJtmcCQEiA+ym8Nq+Uxv8Wvdq72vMEuXiWtBR6oo6rqaJG2n8mSdEVE/NVs0Qv0Cl/i7UzOTlJQR58dowIDAQAB"

        });


        client.setUserContext(org1.getPeerAdmin());
        String transactionID = channel.sendTransaction(proposalResponses).get(testConfig.getTransactionWaitTime(), TimeUnit.SECONDS).getTransactionID();
        out("Finished invoke transaction with transaction id %s", transactionID);
        queryChaincode(client, channel, org1, 0, 1);
//        queryChaincode(client, channel, org1, 0, 1, "e762186ee2d974e2849d6058ec4a35ec04cae6ef794a6a9197dc0d4a3a74fa0d");
    }

    static void queryChaincode(HFClient client, Channel channel, SampleOrg org, int start, int limit, String... queryArgs) throws InvalidArgumentException {
        // Send Query Proposal to all peers
        //
        try {
            out("Now query chaincode for the value of " + Arrays.toString(queryArgs) + ".");
            QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();

            String[] args = new String[queryArgs.length + 1];
            args[0] = "query";
            System.arraycopy(queryArgs, 0, args, 1, queryArgs.length);
            queryByChaincodeRequest.setArgs(args);
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
                    out("Query payload of b from peer %s returned %s", proposalResponse.getPeer().getName(),
                            payload.replaceAll("(\\Qnamespace:\"lottery_go\"\\E\\s*+)(key:\\s*+)(\"[^\"]++\"\\s*+)(value:\\s*+)"
                                    , "\n$1\n$2\n$3\n$4\n").replaceAll("\\\\\"", ""));
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
                "100",
                "100",
                "100",
                "100",
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

            installProposalRequest.setChaincodeSourceLocation(new File(TEST_FIXTURES_PATH + ChainCodeNavtivePath));
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
