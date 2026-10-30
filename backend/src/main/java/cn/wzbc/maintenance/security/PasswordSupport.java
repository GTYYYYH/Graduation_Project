package cn.wzbc.maintenance.security;

import org.springframework.stereotype.Component;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

@Component
public class PasswordSupport {
    private final SecureRandom random=new SecureRandom();
    private final Pbkdf2PasswordEncoder encoder=Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    public String randomValidPassword() {
        byte[] bytes=new byte[18]; random.nextBytes(bytes);
        return "A1"+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public String hash(String raw) { return encoder.encode(raw); }
    public byte[] operationDigest(String content,String operation) {
        var spec=new javax.crypto.spec.PBEKeySpec(content.toCharArray(),operation.getBytes(java.nio.charset.StandardCharsets.US_ASCII),310000,256);
        try {return javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
        catch(java.security.GeneralSecurityException failure){throw new IllegalStateException("操作摘要生成失败");}
        finally{spec.clearPassword();}
    }
    public boolean matches(String raw, String hash) { return raw!=null && hash!=null && encoder.matches(raw,hash); }
}
