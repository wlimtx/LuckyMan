package org.hyperledger.fabric.sdkintegration.admin;

import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.ChaincodeEndorsementPolicyParseException;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric.sdk.testutils.TestConfig;
import org.hyperledger.fabric.sdkintegration.SampleOrg;
import org.hyperledger.fabric.sdkintegration.SampleStore;
import org.hyperledger.fabric.sdkintegration.SampleUser;

import org.hyperledger.fabric.sdkintegration.client.util.Toast;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;
import org.hyperledger.fabric_ca.sdk.exception.EnrollmentException;
import org.hyperledger.fabric_ca.sdk.exception.InvalidArgumentException;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.fail;

public class Admin {
    private static final TestConfig testConfig = TestConfig.getConfig();
    private static final String TEST_ADMIN_NAME = "admin";
    private static final String TESTUSER_1_NAME = "user1";
    private static final String TEST_FIXTURES_PATH = "src/test/fixture";
    private static final String ChainCodeNavtivePath = "/sdkintegration/gocc/luckBytes_demo_merge";
    private static final String CHAIN_CODE_NAME = "lottery_go";
    private static final String CHAIN_CODE_PATH = "github.com/example_cc";
    private static final String CHAIN_CODE_VERSION = "1";
    private static final String FOO_CHANNEL_NAME = "foo";
    private static final String BAR_CHANNEL_NAME = "bar";
    private static Collection<SampleOrg> testSampleOrgs;
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new JFrame(){
                JFrame this0 =this;
                {

                    setSize(400, 200);
                    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                    setLocationRelativeTo(null);
                    setLayout(new FlowLayout());
                    JButton comp = new JButton("创建一条新的区块链");
                    comp.addActionListener(l -> {
                        try {
                            Admin.init(comp, true);
                            Toast.showToast(comp, "应用链创建成功");
                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.showToast(comp, "应用通道创建失败");
                        }
                    });
                    add(comp);
                    JButton comp2 = new JButton("部署智能合约");
                    comp2.addActionListener(l -> {
                        try {
                            JFileChooser chooser = new JFileChooser("/Users/liumingxing/IdeaProjects/sdkjava/fabric-sdk-java/src/test/fixture" + ChainCodeNavtivePath);
                            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                            chooser.showDialog(this0,"选择");
                            Admin.init(comp2, false);
                            Toast.showToast(comp2, "合约部署成功");
                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.showToast(comp2, "应用通道创建失败");
                        }
                    });
                    add(comp2);
                }
            }.setVisible(true);
        });
    }

    public static void init(JComponent comp, boolean create) throws Exception {
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

        if (create) {
            channel = constructChannel(FOO_CHANNEL_NAME, client, org1);

            joinChannel(client, channel, org1, "peer0.org1.example.com");
            joinChannel(client, channel, org2, "peer0.org2.example.com");

//            joinChannel(client, channel, org2, "peer0.org2.example.com");
//            joinChannel(client, channel, org2, "peer1.org2.example.com");
        } else {
            client.setUserContext(org1.getUser(TESTUSER_1_NAME));//对于已经存在的应用通道，普通的用户也可以进行访问
            channel = client.newChannel(FOO_CHANNEL_NAME);
            for (Orderer orderer : getOrdererList(client, org1)) channel.addOrderer(orderer);
            org1.addPeer(client.newPeer("peer0.org1.example.com", "grpc://localhost:7051"));
//            org1.addPeer(client.newPeer("peer1.org1.example.com", "grpc://localhost:7056"));
            org2.addPeer(client.newPeer("peer0.org2.example.com", "grpc://localhost:8051"));
//            org2.addPeer(client.newPeer("peer1.org2.example.com", "grpc://localhost:8056"));
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
//            eventHubNames = new String[]{"peer0.org2.example.com"};
//            for (String eventHubName : eventHubNames) {
//                final Properties eventHubProperties = testConfig.getEventHubProperties(eventHubName);
//                eventHubProperties.put("grpc.NettyChannelBuilderOption.keepAliveTime", new Object[]{5L, TimeUnit.MINUTES});
//                eventHubProperties.put("grpc.NettyChannelBuilderOption.keepAliveTimeout", new Object[]{8L, TimeUnit.SECONDS});
//                client.setUserContext(org2.getPeerAdmin());
//                EventHub eventHub = client.newEventHub(eventHubName, org2.getEventHubLocation(eventHubName), eventHubProperties);
//                channel.addEventHub(eventHub);
//            }

            client.setUserContext(org1.getUser(TESTUSER_1_NAME));
//
            channel.initialize();


            installChaincode(client, channel, org2, 0, 1);
            installChaincode(client, channel, org1, 0, 1);//暂时这两个动作要一起执行
            //背书节点背书
            Collection<ProposalResponse> proposalResponses = instantiateChaincode(client, channel, org1, 0, 1);//1,-,0,0,
            channel.sendTransaction(proposalResponses, channel.getOrderers()).thenApply(transactionEvent -> {
                assertTrue(transactionEvent.isValid()); // must be valid to be here.
                out("Finished instantiate transaction with transaction id %s", transactionEvent.getTransactionID());
                return null;
            });
        }
    }
    static Collection<ProposalResponse> instantiateChaincode(HFClient client, Channel channel, SampleOrg org, int start, int limit) throws org.hyperledger.fabric.sdk.exception.InvalidArgumentException, IOException, ChaincodeEndorsementPolicyParseException, ProposalException {
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
//                "fa67ad29516c1caa360ab946d3c02ec683ef2569c65e091da57178f502fea0c1",
//                "6d65c8f0555e270bd562ab84235c965de942390b1c99b25a9646095b81c78021",
//                "5c2dc969509e8021597c1eb37b0b3534b26972abe5721cf3c45f0fd86ac6133c",
//                "e762186ee2d974e2849d6058ec4a35ec04cae6ef794a6a9197dc0d4a3a74fa0d",
//                "100",
//                "100",
//                "100",


                "1e4fb641ff3cf0a195c380c3c24e4ab20751af28e09076312cb004bac39d8205",
                "4d977a7f13975e89b5e345fb98d5b39ce240665c706d8f7b18c9afa46ffae2c5",
                "100000",
                "100000",
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
            } catch (org.hyperledger.fabric.sdk.exception.InvalidArgumentException e) {
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
    public static Collection<Orderer> getOrdererList(HFClient client, SampleOrg org) throws org.hyperledger.fabric.sdk.exception.InvalidArgumentException {
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

    static void joinChannel(HFClient client, Channel channel, SampleOrg org, String peerName) throws org.hyperledger.fabric.sdk.exception.InvalidArgumentException, ProposalException {
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
}
