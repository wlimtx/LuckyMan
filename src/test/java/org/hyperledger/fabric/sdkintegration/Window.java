package org.hyperledger.fabric.sdkintegration;

import org.apache.commons.codec.binary.Hex;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.*;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class Window extends JFrame {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new JFrame() {
                File keyMaterial = null;
                JFrame this0 = this;
                int height = 800;
                int width = 1000;

                String base64PublicKey = null;
                String base64PrivateKey = null;
                String tips = "not exists";
                {

                    setSize(width, height);
                    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                    setLocationRelativeTo(null);
                }

                {

                    JTextField mspid = new JTextField(70);
                    JTextField key = new JTextField(70);
                    JTextField certs = new JTextField(70);
                    key.setText(tips);
                    certs.setText(tips);
                    key.setEditable(false);
                    certs.setEditable(false);
                    mspid.setEditable(false);
                    JTextField pubsha = new JTextField(50);
                    pubsha.setFont(pubsha.getFont().deriveFont(18.0f));
//                    JTextField keysha = new JTextField(50);

                    setJMenuBar(new JMenuBar() {
                        {
                            add(new JMenu("Import") {
                                {
                                    add(new JMenuItem("2048 RSA Key Material") {
                                        {
                                            addActionListener(e -> {
                                                JFileChooser chooser = new JFileChooser(new File("/Users/liumingxing/IdeaProjects/sdkjava/fabric-sdk-java/mymsp"));
                                                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                                                int confirm = chooser.showDialog(this0, "confirm");
                                                if (confirm == 0) {
                                                    keyMaterial = chooser.getCurrentDirectory();
                                                    System.out.println(keyMaterial.getAbsolutePath());
                                                    this0.setTitle("current key path: " + keyMaterial.getAbsolutePath());
                                                    mspid.setText(keyMaterial.getAbsolutePath().replaceFirst(".*?([^/]++)$", "$1"));
                                                    File keyFile = new File(keyMaterial, "key.pem");
                                                    File pukFile = new File(keyMaterial, "pub.pem");
                                                    System.out.println(keyFile);

                                                    if (pukFile.exists()) {
                                                        certs.setText(pukFile.getAbsolutePath());
                                                        try {
                                                            base64PublicKey = Pem.loadPemBase64(pukFile);
                                                            System.out.println(Hex.encodeHexString(Pem.sha256(Pem.base64ToDecode(base64PublicKey))));
                                                            System.out.println(base64PublicKey);
                                                        } catch (Exception e1) {
                                                            e1.printStackTrace();
                                                        }
                                                        pubsha.setText(Hex.encodeHexString(Pem.sha256(Pem.base64ToDecode(base64PublicKey))));
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
                                            });
                                        }


                                    });
                                }
                            });
                        }
                    });
                    setLayout(new FlowLayout());

                    add(new JPanel() {
                        {
                            setBorder(new TitledBorder("Lucky Man Coin Address"){
                                {
                                    setTitleFont(new Font("Times New Roman",Font.ITALIC,20));
                                    setTitleColor(Color.BLUE);
                                }
                            });
                            add(pubsha);
                        }
                    });
                    add(new JPanel() {
                        {
                            setLayout(new BorderLayout());
                            setBorder(new TitledBorder("Membership Service Provider") {
                                {
                                    setTitleFont(new Font("Times New Roman", Font.ITALIC, 18));
                                }
                            });
                            add(new JPanel() {
                                {
                                    add(new JLabel("             Identify: "), BorderLayout.WEST);
                                    add(mspid, BorderLayout.CENTER);
                                }
                            }, BorderLayout.NORTH);
                            add(new JPanel() {
                                {
                                    add(new JLabel("public key path: "), BorderLayout.WEST);
                                    add(certs, BorderLayout.CENTER);
                                }
                            }, BorderLayout.CENTER);

                            add(new JPanel() {
                                {
                                    add(new JLabel("private key path: "), BorderLayout.WEST);
                                    add(key, BorderLayout.CENTER);
                                }
                            }, BorderLayout.SOUTH);
                        }
                    });
                    JTextPane comp = new JTextPane();
                    comp.addKeyListener(new KeyAdapter() {
                        @Override
                        public void keyReleased(KeyEvent e) {
                            if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isActionKey()) {
                                try {

                                    String text = (comp.getText() + " " + getRandomArg()).replace("\n","");
                                    String SIG = signPlainToBase64(text + " " + base64PublicKey, base64PrivateKey);

                                    System.out.println("signature: " + SIG);
                                    System.out.println("private key: " + base64PrivateKey);
                                    System.out.println("public key: " + base64PublicKey);
                                    comp.setText(text + " " + SIG + " " + base64PublicKey);
                                    convertInvokeArgs(comp.getText());
                                } catch (Exception e1) {
                                    e1.printStackTrace();
                                }
                            }
                        }
                    });
                    comp.setPreferredSize(new Dimension(width - 16, 200));
//                    comp.
                    add(new JPanel(){
                        {
                            setBorder(new TitledBorder("Command") {
                                {
                                    setTitleFont(new Font("Times New Roman", Font.ITALIC, 18));
                                }
                            });
                            setLayout(new BorderLayout());
                            add(new JScrollPane(comp));
                        }
                    });

                }


            }.setVisible(true);
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
        String nonce = args[2];
        String randomArg = args[3];
        String puk = args[4];

        String message = asset + nonce + randomArg + puk;
        System.out.println(message);
        return Base64.getEncoder().encodeToString(Pem.sign(message.getBytes()
                , Pem.loadPrivateKey(Pem.base64ToDecode(base64PrivateKey))));
    }

    public static String convertInvokeArgs(String text) {
//        text = "bet 30 2147483648" +
//                " uFlAEIx6YhUGnn6qvrdOQDtournousA2CWicSNJwVv8=" +
//                " MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxqZK5PmwmJJP1gLt7kKryINxwFt0HdkOuSBNuk4t+dveWKq1+NeAKTz+2KN7w9MndrsVRY5C0lKtn30iIPHvKjf5hrgaWQMF+T56XG10IZZjt1yjBRCV9uUAD5Zlsd9xEZFRDzzNuNRpacfo1e3YITEkAJsMmHAmYf6jz/vKgbWQN3/0k5+KpxTD6avcqLP5un5riXhE3j3Thd6vOQhPh/ocLu7030PCBa1pS0thKsBPgmI3MtHpA7jE7TBBE/slFwaZuhmDbG9f5vCeGcJkahfLj58ifok1SH/u6tMiqiQNxLaYVeAZ+zcX29Z748P3Usjp2S1TcLBPsbMQkva9/QIDAQAB" +
//                " gSFzMAUDaXgLYMyOArnBB8Z7mfRGtppRgmZNCfa6LThPlU2ImTSHKE6YRXPxnfPz2ZuVL0vYvGbZenI1WlbpZSFh1yheT1FCueHiRit7YXxZWKAqU7Df308pAkeVbXE5glbhBK7PXHvoD0FAXKsSjs8spvbt0B3RqFpJGE8DnpVTjug8WFNUSvsnSffzrREM2BEZrrjDGJnQWrVhkLD16lwFuwx/xGQkreWlhNC8rRRc9dZ47NOViCDtrhVEZFm8afuzO2VltFWKzVuDOzNsE+3Uw8cuXvBqSk7ApmUnZKD6O/DOb89OF0u0RzmlAFMlfLY2TYnePl1RsJN9GFi2Cg==";
        text = text.replaceAll("([^ ]++)", "\"$1\"").replaceAll(" ", ", ");
        System.out.println(text);
        return text;
    }
}
