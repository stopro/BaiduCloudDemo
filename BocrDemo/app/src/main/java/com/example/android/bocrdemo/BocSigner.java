package com.example.android.bocrdemo;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.DefaultHttpRequestFactory;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.BitSet;
import java.util.Date;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by richard on 11/12/16.
 */


public class BocSigner {

    private static final String DEFAULT_ENCODING = "UTF-8";

    private static BitSet URI_UNRESERVED_CHARACTERS = new BitSet();
    private static String[] PERCENT_ENCODED_STRINGS = new String[256];

    private static final String BceAuthVersion = "bce-auth-v1";
    private static final String AccessKeyId = "";  // TODO
    private static final String SecretAccessKey = "";  // TODO
    private static final String BocrHost = "word.bj.baidubce.com";
    private static final String BocrURL = "/api/v1/ocr/general";

    /**
     * Regex which matches any of the sequences that we need to fix up after URLEncoder.encode().
     */
    // private static final Pattern ENCODED_CHARACTERS_PATTERN;
    static {
        /*
         * StringBuilder pattern = new StringBuilder();
         *
         * pattern .append(Pattern.quote("+")) .append("|") .append(Pattern.quote("*")) .append("|")
         * .append(Pattern.quote("%7E")) .append("|") .append(Pattern.quote("%2F"));
         *
         * ENCODED_CHARACTERS_PATTERN = Pattern.compile(pattern.toString());
         */
        for (int i = 'a'; i <= 'z'; i++) {
            URI_UNRESERVED_CHARACTERS.set(i);
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            URI_UNRESERVED_CHARACTERS.set(i);
        }
        for (int i = '0'; i <= '9'; i++) {
            URI_UNRESERVED_CHARACTERS.set(i);
        }
        URI_UNRESERVED_CHARACTERS.set('-');
        URI_UNRESERVED_CHARACTERS.set('.');
        URI_UNRESERVED_CHARACTERS.set('_');
        URI_UNRESERVED_CHARACTERS.set('~');

        for (int i = 0; i < PERCENT_ENCODED_STRINGS.length; ++i) {
            PERCENT_ENCODED_STRINGS[i] = String.format("%%%02X", i);
        }
    }

    public static void signPost(HttpPost post, Date timestamp) {
        try {
            String timestampString =  DateFormatUtils.formatUTC(timestamp, "yyyy-MM-dd'T'HH:mm:ss'Z'");

            DefaultHttpRequestFactory factory = new DefaultHttpRequestFactory();
            post.addHeader("Host", BocrHost);
            post.addHeader("x-bce-date", timestampString);
            post.addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.98 Safari/537.36");

            // sign
            final int expirationPeriodInSeconds = 1800; // 30 minutes.
            String authString = BceAuthVersion + "/" + AccessKeyId + "/" + post.getFirstHeader("x-bce-date").getValue() + "/"
                    + expirationPeriodInSeconds;
            String signingKey = sha256Hex(SecretAccessKey, authString);

            String canonicalURI = BocrURL;
            // TODO: hardcode.
            String signedHeaders = "host;x-bce-date";
            String canonicalHeader = "host:" + normalize(post.getFirstHeader("Host").getValue().trim()) + "\n"
                    + "x-bce-date:"+ normalize(post.getFirstHeader("x-bce-date").getValue().trim());
            String canonicalRequest = "POST" + "\n" + canonicalURI + "\n" + "\n" + canonicalHeader;

            String signature = sha256Hex(signingKey, canonicalRequest);
            String authorizationHeader = authString + "/" + signedHeaders + "/" + signature;
            post.addHeader("Authorization", authorizationHeader);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String sha256Hex(String secretAccessKey, String authString) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretAccessKey.getBytes("UTF8"), "HmacSHA256"));
            return new String(Hex.encodeHex(mac.doFinal(authString.getBytes("UTF8"))));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String normalize(String value) {
        try {
            StringBuilder builder = new StringBuilder();
            for (byte b : value.getBytes(DEFAULT_ENCODING)) {
                if (URI_UNRESERVED_CHARACTERS.get(b & 0xFF)) {
                    builder.append((char) b);
                } else {
                    builder.append(PERCENT_ENCODED_STRINGS[b & 0xFF]);
                }
            }
            return builder.toString();
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

}
