package org.hyperledger.fabric.sdkintegration.admin;

import org.apache.commons.codec.binary.Hex;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdkintegration.client.MyClient;
import org.hyperledger.fabric.sdkintegration.client.Pem;
import org.hyperledger.fabric.sdkintegration.client.ui.InfiniteProgressPanel;
import org.hyperledger.fabric.sdkintegration.client.util.Toast;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Scanner;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("Duplicates")
public class Organization extends JFrame {


    File keyMaterial = null;
    Organization this0 = this;
    int H = 380;
    int W = 720;
    //    JTextField encryptedLuckBytesTxt = new JTextField(42);
    JTextField luckBytesTxt = new JTextField(60);
    String base64LuckBytes;
    //    String base64EncryptedLuckBytes;
    String base64PublicKey = null;
    String base64PrivateKey = null;
    {

        setSize(W, H);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
    }
    JLabel balance = new JLabel("0 LBC");
    JTextField pubsha = new JTextField(40);
    JTextField receiver = new JTextField(48);
    JTextField receiveMoney = new JTextField(12);
    JPanel balancePanel = new JPanel() {
        {
            setBorder(new TitledBorder("余额") {
                {
                    setTitleFont(new Font("Times New Roman", Font.ITALIC, 14));
                    setTitleColor(Color.BLUE);
                }
            });
            add(balance);
        }
    };
    MyClient myClient = new MyClient();
    String[] columnName = {"账号", "资产", "筹码", "幸运号码", "状态", "熵源"};
    TableColumn column;

    JTable table = new JTable(new Object[][]{}, columnName);
    JScrollPane scrollPane = new JScrollPane(table);

    {
        table.setPreferredScrollableViewportSize(new Dimension(W-40, 200));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

        balancePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    queryBalance();
                }
            }
        });
    }
    {




        pubsha.setFont(pubsha.getFont().deriveFont(14.0f));

        setJMenuBar(new JMenuBar() {
            {
                add(new JMenu("高级") {
                    {
                        add(new JMenuItem("偏好设置...") {
                            {
                                addActionListener(e -> new JDialog(this0, true) {
                                    JDialog this2 = this;
                                    JTextField mspid = new JTextField(40);
                                    JTextField key = new JTextField(40);
                                    JTextField certs = new JTextField(40);
                                    String tips = "不存在";
                                    File keyMaterial = this0.keyMaterial;
                                    String base64PublicKey = null;
                                    String base64PrivateKey = null;

                                    {

                                        key.setText(tips);
                                        certs.setText(tips);
                                        key.setEditable(false);
                                        certs.setEditable(false);
                                        mspid.setEditable(false);
                                        setSize(W-100, H / 2+40);
                                        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                                        setLocationRelativeTo(this0);
                                        setLayout(new FlowLayout());

                                        add(new JPanel() {
                                            {
                                                setLayout(new BorderLayout());
                                                setBorder(new TitledBorder("身份信息") {
                                                    {
                                                        setTitleFont(new Font("Times New Roman", Font.ITALIC, 14));
                                                        setTitleColor(Color.BLUE);
                                                    }
                                                });
                                                add(new JPanel() {
                                                    {
                                                        add(new JLabel("      昵称: "), BorderLayout.WEST);
                                                        add(mspid, BorderLayout.CENTER);
                                                    }
                                                }, BorderLayout.NORTH);
                                                add(new JPanel() {
                                                    {
                                                        add(new JLabel("公钥位置: "), BorderLayout.WEST);
                                                        add(certs, BorderLayout.CENTER);
                                                    }
                                                }, BorderLayout.CENTER);

                                                add(new JPanel() {
                                                    {
                                                        add(new JLabel("私钥位置: "), BorderLayout.WEST);
                                                        add(key, BorderLayout.CENTER);
                                                    }
                                                }, BorderLayout.SOUTH);
                                            }
                                        });

                                        setJMenuBar(new JMenuBar() {
                                            {
                                                add(new JMenu("导入...") {
                                                    {
                                                        add("2048 RSA 密钥材料").addActionListener(l -> {
                                                            JFileChooser chooser = new JFileChooser(new File("/Users/liumingxing/IdeaProjects/sdkjava/fabric-sdk-java/mymsp/OrgMSP"));
                                                            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                                                            int confirm = chooser.showDialog(this2, "confirm");
                                                            if (confirm == 0) {
                                                                keyMaterial = chooser.getCurrentDirectory();
                                                                doConfirm();

                                                            }
                                                        });
                                                    }
                                                });
                                            }


                                        });
                                        add(new JPanel() {
                                            {
                                                setLayout(new BorderLayout());
                                                add(new JButton("应用") {
                                                    {
                                                        addActionListener(e1 -> {
                                                            this0.base64PublicKey = base64PublicKey;
                                                            this0.base64PrivateKey = base64PrivateKey;
                                                            this0.keyMaterial = keyMaterial;
                                                            if (base64PublicKey != null)
                                                                pubsha.setText(Hex.encodeHexString(Pem.sha256(Pem.base64ToDecode(base64PublicKey))));
                                                            if (keyMaterial != null)
                                                                this0.setTitle("current key path: " + keyMaterial.getAbsolutePath());
                                                            luckBytesTxt.setText("");
                                                            this2.setVisible(false);
                                                            this2.dispose();
                                                            queryBalance();
                                                            updateTable();
                                                        });


                                                    }
                                                });
                                                add(new JButton("取消") {
                                                    {
                                                        addActionListener(e1 -> {
                                                            this2.setVisible(false);
                                                            this2.dispose();
                                                        });

                                                    }
                                                }, BorderLayout.EAST);
                                                doConfirm();
                                            }
                                        });
                                    }

                                    private void doConfirm() {
                                        if (keyMaterial == null) return;
                                        mspid.setText(keyMaterial.getAbsolutePath().replaceFirst(".*?([^/]++)$", "$1"));

                                        File keyFile = new File(keyMaterial, "key.pem");
                                        File pukFile = new File(keyMaterial, "pub.pem");
                                        if (pukFile.exists()) {
                                            certs.setText(pukFile.getAbsolutePath());
                                            try {
                                                base64PublicKey = Pem.loadPemBase64(pukFile);
                                                System.out.println(Hex.encodeHexString(Pem.sha256(Pem.base64ToDecode(base64PublicKey))));
                                            } catch (Exception e1) {
                                                e1.printStackTrace();
                                            }
                                        } else {
                                            certs.setText(tips);
                                        }
                                        if (keyFile.exists()) {
                                            key.setText(keyFile.getAbsolutePath());
                                            try {
                                                base64PrivateKey = Pem.loadPemBase64(keyFile);
                                            } catch (Exception e1) {
                                                e1.printStackTrace();
                                            }
                                        } else {
                                            key.setText(tips);
                                        }
                                    }
                                }.setVisible(true));
                            }
                        });
                        add(new JMenuItem("转账"){
                            {
                                addActionListener(l-> new JDialog(this0, true) {
                                    JDialog this2 = this;
                                    {
                                        setSize(W + 100, H / 3 + 10);
                                        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                                        setLocationRelativeTo(this0);
                                        setLayout(new FlowLayout());
                                        add(new JPanel(){
                                            {
                                                add(new JPanel(){
                                                    {
                                                        setBorder(new TitledBorder("收款人账号") {
                                                            {
                                                                setTitleFont(new Font("Times New Roman", Font.ITALIC, 14));
                                                                setTitleColor(Color.BLUE);
                                                            }
                                                        });

                                                        add(receiver);
                                                    }
                                                });
                                                add(new JPanel(){
                                                    {
                                                        setBorder(new TitledBorder("转账金额") {
                                                            {
                                                                setTitleFont(new Font("Times New Roman", Font.ITALIC, 14));
                                                                setTitleColor(Color.BLUE);
                                                            }
                                                        });

                                                        add(receiveMoney);

                                                    }
                                                });
                                            }
                                        },BorderLayout.NORTH);


                                        add(new JPanel() {
                                            {
                                                setLayout(new BorderLayout());
                                                JButton transfer = new JButton("确认转账");
                                                transfer. addActionListener(e1 -> {
                                                    if (base64PublicKey != null) {
                                                        String receiverAddress = receiver.getText().trim();
                                                        if (receiverAddress.length() == 0) {
                                                            Toast.showToast(receiver, "请提供收款人账号");
                                                            return;
                                                        }
                                                        String base64ReceiverAddress;
                                                        try {
                                                            base64ReceiverAddress = Base64.getEncoder()
                                                                    .encodeToString(org.bouncycastle.util.encoders.Hex.decode(receiverAddress));
                                                        } catch (Throwable throwable) {
                                                            Toast.showToast(receiver, "错误的收款账号");
                                                            return;
                                                        }
                                                        String asset = receiveMoney.getText().trim();
                                                        if (asset.length() == 0) {
                                                            Toast.showToast(receiveMoney, "请输入转账金额");
                                                            return;
                                                        }
                                                        try {
                                                            Double.parseDouble(asset);
                                                        } catch (Throwable e) {
                                                            Toast.showToast(receiveMoney, "转账金额只能是整数或者小数");
                                                            return;
                                                        }

                                                        String message = asset + base64ReceiverAddress;
                                                        String base64Signature;
                                                        try {

                                                            base64Signature = Base64.getEncoder().encodeToString(Pem.sign(message.getBytes(), Pem.loadPrivateKey(Pem.base64ToDecode(base64PrivateKey))));
                                                        } catch (Throwable e) {
                                                            e.printStackTrace();
                                                            Toast.showToast(receiver, e.getMessage());
                                                            return;
                                                        }
                                                        String cmd = "transfer " + asset + " " + base64ReceiverAddress + " " + base64Signature + " " + base64PublicKey;
                                                        new Thread(() -> {
                                                            InfiniteProgressPanel glasspane = new InfiniteProgressPanel();
                                                            this2.setGlassPane(glasspane);
                                                            glasspane.setSize(this2.getSize());
                                                            glasspane.start();//开始动画加载效果
                                                            try {

                                                                Thread.sleep(100);
                                                                String[] split = cmd.split("\\s++");
                                                                System.out.println(Arrays.toString(split));
                                                                String transactionID = myClient.invoke(split);
                                                                System.out.println("transactionID: " + transactionID);
                                                                Toast.showToast(transfer, "转账成功:" + transactionID);
                                                                queryBalance();
                                                                updateTable();
                                                            } catch (Throwable e3) {
                                                                e3.printStackTrace();
                                                                Toast.showToast(transfer, "转账失败:" + e3.getMessage());
                                                            }
                                                            glasspane.interrupt();
                                                        }).start();

                                                    } else {
                                                        Toast.showToast(pubsha, "请提供您的账号");
                                                    }
                                                });
                                                add(transfer);
                                                add(new JButton("关闭") {
                                                    {
                                                        addActionListener(e1 -> {
                                                            this2.setVisible(false);
                                                            this2.dispose();
                                                        });

                                                    }
                                                }, BorderLayout.EAST);
                                            }
                                        });
                                    }

                                }.setVisible(true));

                            }
                        });
                        add(new JMenuItem("提供熵源"){
                            {
                                addActionListener(l -> {

                                    new JDialog(this0, true) {
                                        JDialog this2 = this;
                                        JTextField base64EncryptedEntropyText = new JTextField(40);
                                        String base64EncryptedEntropy = null;
                                        String base64Entropy = null;
                                        private void selectEntropy(String entropy) {
                                            base64Entropy = entropy;
                                            base64EncryptedEntropy = Base64.getEncoder().encodeToString(Pem.nTimesOfSha256(3, Pem.base64ToDecode(entropy)));
                                            base64EncryptedEntropyText.setText(base64EncryptedEntropy);

                                        }

                                        private void saveEntropy(String base64Entropy, String base64EncryptedEntropy) throws IOException {
                                            if (keyMaterial != null && verify(base64Entropy, base64EncryptedEntropy)) {
//                                                File file = new File(keyMaterial, base64EncryptedEntropy.replaceAll("/", "-") + ".luck");
                                                File file = new File(keyMaterial, "entropy.luck");
                                                if (!file.exists()) file.createNewFile();
                                                PrintStream io = new PrintStream(file);
                                                io.println(base64Entropy);
                                                Toast.showToast(base64EncryptedEntropyText,"熵源保存成功");
                                            }
                                        }
                                        {
                                            setSize(W + 100, H / 3 - 10);
                                            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                                            setLocationRelativeTo(this0);
                                            setLayout(new FlowLayout());

                                            add(new JPanel() {
                                                {
                                                    setBorder(new TitledBorder("熵源SHA2-256") {
                                                        {
                                                            setTitleFont(new Font("Times New Roman", Font.ITALIC, 14));
                                                            setTitleColor(Color.BLUE);
                                                        }
                                                    });

                                                    add(base64EncryptedEntropyText);
                                                    add(new JButton("随机生成熵源"){
                                                        {
                                                            addActionListener(l-> selectEntropy(getRandomArg()));
                                                        }
                                                    });
                                                    File file = new File(keyMaterial, "entropy.luck");
                                                    if (file.exists()) {
                                                        JButton decrypt = new JButton("提交熵源");
                                                        decrypt.addActionListener(l -> {
                                                            if (base64PublicKey == null) {
                                                                Toast.showToast(pubsha, "请提供您的账号");
                                                                return;
                                                            }
                                                            Scanner scanner = null;
                                                            try {
                                                                scanner = new Scanner(file);
                                                            } catch (FileNotFoundException e) {
                                                                e.printStackTrace();
                                                                Toast.showToast(decrypt,e.getMessage());
                                                            }
                                                            String base64Entropy = scanner.nextLine();
                                                            new Thread(()->{
                                                                InfiniteProgressPanel glasspane = new InfiniteProgressPanel();
                                                                this2.setGlassPane(glasspane);
                                                                glasspane.setSize(this2.getSize());
                                                                glasspane.start();//开始动画加载效果
                                                                try {
                                                                    String transactionID = myClient.invoke("decrypt", base64PublicKey, base64Entropy);
                                                                    file.delete();
                                                                    Toast.showToast(decrypt, "熵源提交成功:" + transactionID);
                                                                } catch (Throwable e) {
                                                                    e.printStackTrace();
                                                                    Toast.showToast(decrypt, e.getMessage());
                                                                }
                                                                glasspane.interrupt();
                                                            }).start();

                                                        });
                                                        add(decrypt);
                                                    } else {
                                                        JButton encrypt = new JButton("提交SHA2熵源");
                                                        encrypt.addActionListener(l -> {
                                                            if (base64PublicKey == null) {
                                                                Toast.showToast(pubsha, "请提供您的账号");
                                                                return;
                                                            }
                                                            if (base64EncryptedEntropy == null) {
                                                                Toast.showToast(base64EncryptedEntropyText, "请提供您的熵源");
                                                                return;
                                                            }
                                                            new Thread(() -> {
                                                                InfiniteProgressPanel glasspane = new InfiniteProgressPanel();
                                                                this2.setGlassPane(glasspane);
                                                                glasspane.setSize(this2.getSize());
                                                                glasspane.start();//开始动画加载效果
                                                                try {
                                                                    String transactionID = myClient.invoke("encrypt", base64PublicKey, base64EncryptedEntropy);
                                                                    saveEntropy(base64Entropy, base64EncryptedEntropy);
                                                                    Toast.showToast(encrypt, "SHA2熵源提交成功:" + transactionID);
                                                                } catch (Throwable e) {
                                                                    e.printStackTrace();
                                                                    Toast.showToast(encrypt, e.getMessage());
                                                                }
                                                                glasspane.interrupt();
                                                            }).start();
                                                        });
                                                        add(encrypt);
                                                    }

                                                }
                                            });
                                        }
                                    }.setVisible(true);
                                });
                            }
                        });

                    }
                });
            }
        });
        setLayout(new FlowLayout());
        add(new JPanel(){
            {
                setLayout(new BorderLayout());

                add(balancePanel, BorderLayout.WEST);
                add(new JPanel() {
                    {
                        setBorder(new TitledBorder("账号") {
                            {
                                setTitleFont(new Font("Times New Roman", Font.ITALIC, 14));
                                setTitleColor(Color.BLUE);
                            }
                        });
                        add(pubsha);
                    }
                });
            }
        });







        add(scrollPane);
        scrollPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    System.out.println(e);
                    updateTable();

                }
            }
        });
        JRadioButton selectTime = new JRadioButton("选号时间");
        JRadioButton submit = new JRadioButton("提交熵源时间");
        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(selectTime);
        buttonGroup.add(submit);
        add(selectTime);
        add(submit);
        JButton lottery = new JButton("开奖");
        lottery.addActionListener(e -> {
            InfiniteProgressPanel glasspane = new InfiniteProgressPanel();
            this0.setGlassPane(glasspane);
            glasspane.setSize(this0.getSize());
            glasspane.start();//开始动画加载效果

            new Thread(()->{
                try {
                    String transactionID = myClient.invoke("lottery");
                    Toast.showToast(lottery, "开奖成功"+transactionID);
                    updateTable();
                    queryBalance();
                } catch (Throwable e1) {
                    e1.printStackTrace();
                    Toast.showToast(lottery,e1.getMessage());
                }


                glasspane.interrupt();
            }).start();
        });
        add(lottery);


    }

    private void updateTable() {
        InfiniteProgressPanel glasspane = new InfiniteProgressPanel();
        this0.setGlassPane(glasspane);
        glasspane.setSize(this0.getSize());
        glasspane.start();//开始动画加载效果
        new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            Vector<Vector<String>> data = new Vector<>();

            try {
                Matcher matcher = Pattern.compile("key:\\s*+\"([^\"]++)\"\\s*+value:\\s*+\"([^\"]++)\"")
                        .matcher(myClient.queryAll());
                Matcher matcherValue = Pattern.compile("(?<=[{,])\\w++:([^,}]++)").matcher("");
                while (matcher.find()) {
                    Vector<String> row = new Vector<>();
                    row.add(matcher.group(1));
                    matcherValue.reset(matcher.group(2));
                    int i = 0;
                    while (matcherValue.find()) {
                        if (++i == 3&&!matcherValue.group(1).equals("null")) {
                            row.add(new BigInteger(Base64.getDecoder().decode(matcherValue.group(1))).toString(10));
                        } else {
                            row.add(matcherValue.group(1));
                        }

                    }
                    data.add(row);
                }
                System.out.println(data);
                table.setModel(new DefaultTableModel(data, new Vector<>(Arrays.asList(columnName))){
                    @Override
                    public boolean isCellEditable(int row, int column) {
                        return column == 0 && row > 0;
                    }
                });
                table.getColumnModel().getColumn(0).setPreferredWidth(300);
            } catch (InvalidArgumentException e1) {
                Toast.showToast(scrollPane, e1.getMessage());
                e1.printStackTrace();
            }

            glasspane.interrupt();
        }).start();
    }

    public Organization() throws Exception {
    }

    private boolean verify(String base64LuckBytes, String base64EncryptedLuckBytes) {
        return Arrays.equals(
                Pem.nTimesOfSha256(3, Base64.getDecoder().decode(base64LuckBytes))
                , Base64.getDecoder().decode(base64EncryptedLuckBytes));
    }





    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new Organization().setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

    }

    private static String getRandomArg() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    //bet asset nonce (randomArg) (sig) (puk)
    public static String signPlainToBase64(String cmd, String base64PrivateKey) throws Exception {
        String[] args = cmd.trim().split(" ");
        String asset = args[1];
        String realLuck = args[2];
        String randomArg = args[3];
        String puk = args[4];

        String message = asset + realLuck + randomArg + puk;
        System.out.println(message);
        return Base64.getEncoder().encodeToString(Pem.sign(message.getBytes()
                , Pem.loadPrivateKey(Pem.base64ToDecode(base64PrivateKey))));
    }

    public static String convertInvokeArgs(String text) {
        System.out.println(text);
//        text = "bet 30 2147483648" +
//                " uFlAEIx6YhUGnn6qvrdOQDtournousA2CWicSNJwVv8=" +
//                " MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxqZK5PmwmJJP1gLt7kKryINxwFt0HdkOuSBNuk4t+dveWKq1+NeAKTz+2KN7w9MndrsVRY5C0lKtn30iIPHvKjf5hrgaWQMF+T56XG10IZZjt1yjBRCV9uUAD5Zlsd9xEZFRDzzNuNRpacfo1e3YITEkAJsMmHAmYf6jz/vKgbWQN3/0k5+KpxTD6avcqLP5un5riXhE3j3Thd6vOQhPh/ocLu7030PCBa1pS0thKsBPgmI3MtHpA7jE7TBBE/slFwaZuhmDbG9f5vCeGcJkahfLj58ifok1SH/u6tMiqiQNxLaYVeAZ+zcX29Z748P3Usjp2S1TcLBPsbMQkva9/QIDAQAB" +
//                " gSFzMAUDaXgLYMyOArnBB8Z7mfRGtppRgmZNCfa6LThPlU2ImTSHKE6YRXPxnfPz2ZuVL0vYvGbZenI1WlbpZSFh1yheT1FCueHiRit7YXxZWKAqU7Df308pAkeVbXE5glbhBK7PXHvoD0FAXKsSjs8spvbt0B3RqFpJGE8DnpVTjug8WFNUSvsnSffzrREM2BEZrrjDGJnQWrVhkLD16lwFuwx/xGQkreWlhNC8rRRc9dZ47NOViCDtrhVEZFm8afuzO2VltFWKzVuDOzNsE+3Uw8cuXvBqSk7ApmUnZKD6O/DOb89OF0u0RzmlAFMlfLY2TYnePl1RsJN9GFi2Cg==";
        text = text.replaceAll("([^ ]++)", "\"$1\"").replaceAll(" ", ", ");
        System.out.println(text);
        return text;
    }

    private void doRebet() {
        if (keyMaterial != null) {
            File[] files = keyMaterial.listFiles();
            if (files.length == 0) {

            } else for (File f : files) {
                try {
                    if (f.getName().endsWith(".luck")) {
                        String base64 = f.getName().replaceFirst("^(.*)\\.luck$", "$1").replaceAll("-", "/");
                        Scanner scanner = new Scanner(f);
                        String base64LuckBytes = scanner.nextLine();
                        if (verify(base64LuckBytes, base64)) {
                            String cmd = "rebet " + base64PublicKey + " " + base64 + " " + base64LuckBytes;
                            break;
                        } else {
                            throw new RuntimeException("Error luck file: \'" + f + "\'");
                        }
                    }
                } catch (Throwable e) {
                    //错误的base64格式
                    e.printStackTrace();
                }
            }


        } else {
//            console.setText(console.getText() + "msp dir does not exists\n");
        }

    }

    public void queryBalance() {
        String base64Hash = base64PublicKey;
        if (base64Hash != null) {
            InfiniteProgressPanel glasspane = new InfiniteProgressPanel();
            glasspane.small();

            this0.setGlassPane(glasspane);
            glasspane.start();//开始动画加载效果
            new Thread() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(400);
                        String query = myClient.query(Hex.encodeHexString(Pem.sha256(Pem.base64ToDecode(base64Hash))));
                        System.out.println("query" + query);
                        Matcher matcher = Pattern.compile("(?:\"asset\":\\s*+)(\\d++(\\.\\d*+)?)").matcher(query);
                        matcher.find();
                        balance.setText(String.format("%-22s", matcher.group(1).replaceAll("([^.]++).++", "$1") + " LBC"));
                    } catch (Throwable e1) {
                        e1.printStackTrace();
                        balance.setText(String.format("%-22s", Math.random() + " LBC"));
                        Toast.showToast(balancePanel, e1.getMessage());
                    }
                    glasspane.interrupt();
                }
            }.start();

        } else {
            System.out.println("empty keys provider");
            Toast.showToast(pubsha, "请提供您的账号");
        }
    }
}
