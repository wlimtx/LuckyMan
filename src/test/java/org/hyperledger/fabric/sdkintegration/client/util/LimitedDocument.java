package org.hyperledger.fabric.sdkintegration.client.util;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;

public class LimitedDocument extends PlainDocument {
  
    private static final long serialVersionUID = 1L;  
    private int maxLength = -1;// 允许的最大长度  
    private String allowCharAsString = null;// 允许的字符串格式（0123456789）  
  
    public LimitedDocument() {  
        super();  
    }  
  
    public LimitedDocument(int maxLength) {  
        super();  
        this.maxLength = maxLength;  
    }  
  
    public void insertString(int offset, String str, AttributeSet attrSet) throws BadLocationException {
        if (str == null) {  
            return;  
        }  
        if (allowCharAsString != null && str.length() == 1) {  
            if (allowCharAsString.indexOf(str) == -1) {  
                return;// 不是所要求的字符格式，就直接返回，不进行下面的添加  
            }  
        }  
        char[] charVal = str.toCharArray();  
        String strOldValue = getText(0, getLength());  
        char[] tmp = strOldValue.toCharArray();  
        if (maxLength != -1 && (tmp.length + charVal.length > maxLength)) {  
            Toolkit.getDefaultToolkit().beep();// 发出一个警告声
            return;// 长度大于指定的长度maxLength，也直接返回，不进行下面的添加  
        }  
        super.insertString(offset, str, attrSet);  
    }  
  
    public void setAllowChar(String str) {  
        allowCharAsString = str;  
    }  
}  