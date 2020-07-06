package com.github.acshmily.utils;

import org.apache.commons.crypto.cipher.CryptoCipher;
import org.apache.commons.crypto.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Base64Utils;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.Properties;

/**
 * @Author: Huanghz
 * @Description: 加密/解密工具类
 * @Date:Created in 10:41 上午 2020/7/6
 * @ModifyBy:
 **/
public class EncryptUtils {
    /**
     * 加密
     * @param text 需要加密的明文
     * @param key
     * @param iv
     * @return 经过base64加密后的密文
     * @throws IOException
     * @throws InvalidAlgorithmParameterException
     * @throws InvalidKeyException
     * @throws ShortBufferException
     * @throws BadPaddingException
     * @throws IllegalBlockSizeException
     */
    public static String encrypt(String text,SecretKeySpec key,IvParameterSpec iv) throws IOException, InvalidAlgorithmParameterException, InvalidKeyException, ShortBufferException, BadPaddingException, IllegalBlockSizeException {
        Properties properties = new Properties();
        final ByteBuffer outBuffer;
        final int bufferSize = 1024;
        final int updateBytes;
        final int finalBytes;
        //Creates a CryptoCipher instance with the transformation and properties.
        try (CryptoCipher encipher = Utils.getCipherInstance(transform, properties)) {

            ByteBuffer inBuffer = ByteBuffer.allocateDirect(bufferSize);
            outBuffer = ByteBuffer.allocateDirect(bufferSize);
            inBuffer.put(getUTF8Bytes(text));

            inBuffer.flip(); // ready for the cipher to read it
            // Show the data is there
            log.debug("inBuffer={}", asString(inBuffer));

            // Initializes the cipher with ENCRYPT_MODE,key and iv.
            encipher.init(Cipher.ENCRYPT_MODE, key, iv);

            // Continues a multiple-part encryption/decryption operation for byte buffer.
            updateBytes = encipher.update(inBuffer, outBuffer);
            log.debug("updateBytes={}", updateBytes);

            // We should call do final at the end of encryption/decryption.
            finalBytes = encipher.doFinal(inBuffer, outBuffer);
            log.debug("finalBytes={}", finalBytes);
        }

        outBuffer.flip(); // ready for use as decrypt
        byte[] encoded = new byte[updateBytes + finalBytes];
        outBuffer.duplicate().get(encoded);
        String encodedString = Base64Utils.encodeToString(encoded);
        log.debug("encodedString={}", encodedString);
        return encodedString;
    }
    /**
     * 解密
     * @param encodedString 经过base64加密后的密文
     * @param key
     * @param iv
     * @return 明文
     * @throws IOException
     * @throws InvalidAlgorithmParameterException
     * @throws InvalidKeyException
     * @throws ShortBufferException
     * @throws BadPaddingException
     * @throws IllegalBlockSizeException
     */
    public static String deEncrypt(String encodedString,SecretKeySpec key,IvParameterSpec iv) throws IOException, InvalidAlgorithmParameterException, InvalidKeyException, ShortBufferException, BadPaddingException, IllegalBlockSizeException {
        Properties properties = new Properties();
        final ByteBuffer outBuffer;
        final int bufferSize = 1024;
        ByteBuffer decoded = ByteBuffer.allocateDirect(bufferSize);
        //Creates a CryptoCipher instance with the transformation and properties.
        try (CryptoCipher decipher = Utils.getCipherInstance(transform, properties)) {
            decipher.init(Cipher.DECRYPT_MODE, key, iv);
            outBuffer = ByteBuffer.allocateDirect(bufferSize);
            outBuffer.put(Base64Utils.decode(getUTF8Bytes(encodedString)));
            outBuffer.flip();
            decipher.update(outBuffer, decoded);
            decipher.doFinal(outBuffer, decoded);
            decoded.flip(); // ready for use
            log.debug("decoded={}", asString(decoded));
        }
        return asString(decoded);
    }
    /**
     * Converts String to UTF8 bytes
     *
     * @param input the input string
     * @return UTF8 bytes
     */
    public static byte[] getUTF8Bytes(String input) {
        return input.getBytes(StandardCharsets.UTF_8);
    }
    /**
     * Converts ByteBuffer to String
     *
     * @param buffer input byte buffer
     * @return the converted string
     */
    public static String asString(ByteBuffer buffer) {
        final ByteBuffer copy = buffer.duplicate();
        final byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    private static final Logger log = LoggerFactory.getLogger(EncryptUtils.class);
    private static final String transform = "AES/CBC/PKCS5Padding";

}
