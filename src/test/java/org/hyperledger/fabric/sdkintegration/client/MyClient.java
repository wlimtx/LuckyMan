package org.hyperledger.fabric.sdkintegration.client;

import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric.sdk.testutils.TestConfig;
import org.hyperledger.fabric.sdkintegration.SampleOrg;
import org.hyperledger.fabric.sdkintegration.SampleStore;
import org.hyperledger.fabric.sdkintegration.SampleUser;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;
import org.hyperledger.fabric_ca.sdk.exception.EnrollmentException;
import org.junit.Assert;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@SuppressWarnings("Duplicates")
public class MyClient {
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


    HFClient client;
    Channel channel;
    public MyClient() throws Exception {
        testSampleOrgs = testConfig.getIntegrationTestsSampleOrgs();
//        File sampleStoreFile = new File(System.getProperty("java.io.tmpdir") + "/HFCSampletest.properties");
        File sampleStoreFile = new File(TEST_FIXTURES_PATH + "/sdkintegration/db/HFCSampletest.properties");
        if (!sampleStoreFile.exists()) {
            sampleStoreFile.createNewFile();
        }

        client = HFClient.createNewInstance();
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

        client.setUserContext(org1.getUser(TESTUSER_1_NAME));//对于已经存在的应用通道，普通的用户也可以进行访问
        channel = client.newChannel(FOO_CHANNEL_NAME);
        for (Orderer orderer : getOrdererList(client, org1)) channel.addOrderer(orderer);
        org1.addPeer(client.newPeer("peer0.org1.example.com", "grpc://localhost:7051"));
//        org1.addPeer(client.newPeer("peer1.org1.example.com", "grpc://localhost:7056"));
        org2.addPeer(client.newPeer("peer0.org2.example.com", "grpc://localhost:8051"));
//        org2.addPeer(client.newPeer("peer1.org2.example.com", "grpc://localhost:8056"));
        ArrayList<SampleOrg> orgs = new ArrayList<>();
        orgs.add(org1);
        orgs.add(org2);
        String[] eventHubNames = {"peer0.org1.example.com"};
        for (String eventHubName : eventHubNames) {
            final Properties eventHubProperties = testConfig.getEventHubProperties(eventHubName);
            eventHubProperties.put("grpc.NettyChannelBuilderOption.keepAliveTime", new Object[]{5L, TimeUnit.MINUTES});
            eventHubProperties.put("grpc.NettyChannelBuilderOption.keepAliveTimeout", new Object[]{8L, TimeUnit.SECONDS});
            client.setUserContext(org1.getPeerAdmin());
            EventHub eventHub = client.newEventHub(eventHubName, org1.getEventHubLocation(eventHubName), eventHubProperties);
            channel.addEventHub(eventHub);
        }
//        eventHubNames = new String[]{"peer0.org2.example.com"};
//        for (String eventHubName : eventHubNames) {
//            final Properties eventHubProperties = testConfig.getEventHubProperties(eventHubName);
//            eventHubProperties.put("grpc.NettyChannelBuilderOption.keepAliveTime", new Object[]{5L, TimeUnit.MINUTES});
//            eventHubProperties.put("grpc.NettyChannelBuilderOption.keepAliveTimeout", new Object[]{8L, TimeUnit.SECONDS});
//            client.setUserContext(org2.getPeerAdmin());
//            EventHub eventHub = client.newEventHub(eventHubName, org2.getEventHubLocation(eventHubName), eventHubProperties);
//            channel.addEventHub(eventHub);
//        }

        client.setUserContext(org1.getUser(TESTUSER_1_NAME));
//
        channel.initialize();
        for (Peer peer : org1.getPeers()) channel.addPeer(peer);
//        for (Peer peer : org2.getPeers()) channel.addPeer(peer);
        queryChaincode(client, channel, org1, 0, 1);
    }

    public String queryAll() throws InvalidArgumentException {
        return queryChaincode(client, channel, testConfig.getIntegrationTestsSampleOrg("peerOrg1"), 0, 1);
    }

    public String query(String hexPukHash) throws InvalidArgumentException {
      return  queryChaincode(client, channel, testConfig.getIntegrationTestsSampleOrg("peerOrg1"), 0, 1, hexPukHash);
    }
    public String invoke(String... args) {
        for (int i = 0; i < 3; i++) {
            try {
                return invoke0(args);
            } catch (Throwable e) {
                System.out.println(e);
            }
        }
        throw new RuntimeException("交易失败");
    }
    public String invoke0(String... args) throws InterruptedException, ExecutionException, TimeoutException, InvalidArgumentException {
        ArrayList<Peer> peers = new ArrayList<>();
        SampleOrg org1 = testConfig.getIntegrationTestsSampleOrg("peerOrg1");
        SampleOrg org2 = testConfig.getIntegrationTestsSampleOrg("peerOrg2");
        org1.getPeers().stream().filter(peer -> peer.getName().equals("peer0.org1.example.com")).forEach(peers::add);
        org2.getPeers().stream().filter(peer -> peer.getName().equals("peer0.org2.example.com")).forEach(peers::add);
        System.out.println(peers);
        peers.forEach(peer -> {
            try {
                channel.addPeer(peer);
            } catch (InvalidArgumentException e) {
//                e.printStackTrace();
                System.out.println(e.getMessage());
            }
        });
        Collection<ProposalResponse> proposalResponses = invokeChaincode(client, channel, peers, args);
        client.setUserContext(org1.getPeerAdmin());
        return channel.sendTransaction(proposalResponses).get(testConfig.getTransactionWaitTime(), TimeUnit.SECONDS).getTransactionID();
    }


    String queryChaincode(HFClient client, Channel channel, SampleOrg org, int start, int limit, String... queryArgs) {

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
                    String s = payload.replaceAll("(\\Qnamespace:\"lottery_go\"\\E\\s*+)(key:\\s*+)(\"[^\"]++\"\\s*+)(value:\\s*+)"
                            , "\n$1\n$2\n$3\n$4\n").replaceAll("\\\\\"", "");
                    out("Query payload of b from peer %s returned %s", proposalResponse.getPeer().getName(),
                            s);
                    return s;
                }
            }

        } catch (Exception e) {
            out("Caught exception while running query");
            e.printStackTrace();
            fail("Failed during chaincode query with error : " + e.getMessage());

        }
        return "Query fail";
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

            out(Arrays.toString(args));


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
                fail("Not enough endorsers for invoke:" + failed.size() + " endorser error: " +
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
    static void out(String format, Object... args) {

        System.err.flush();
        System.out.flush();

        System.out.println(format(format, args));
        System.err.flush();
        System.out.flush();

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
}
