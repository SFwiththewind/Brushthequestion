package untils;

import io.jsonwebtoken.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Date;
@Slf4j

@Component
public class JwtUtils {
    private byte[] secretKeyBytes; // 存储字节形式密钥
    private Long expiration;

    public JwtUtils(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.expiration}") Long expiration
    ) {
        this.secretKeyBytes = Base64.getDecoder().decode(secretKey);
        this.expiration = expiration;
    }

    public String generateToken(String openid) {

        return Jwts.builder()
                .setSubject(openid)
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(SignatureAlgorithm.HS256, secretKeyBytes)
                .compact();
    }

    public String parseToken(String tokenHeader) throws MalformedJwtException {
        log.info("原始Token头: {}", tokenHeader);

        if (tokenHeader != null) {
            // 精确匹配Bearer前缀，处理前后空格
            String prefix = "Bearer ";
            if (tokenHeader.startsWith(prefix)) {
                String extractedToken = tokenHeader.substring(prefix.length()).trim();
                log.info("提取后的Token: [{}]", extractedToken);
                return parseJwt(extractedToken);
            } else {
                log.error("非法Token格式，缺少Bearer前缀");
                throw new MalformedJwtException("Authorization header must start with 'Bearer '");
            }
        } else {
            log.error("空Token头");
            throw new MalformedJwtException("Missing authorization header");
        }
    }

    private String parseJwt(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(secretKeyBytes) // 使用同一字节密钥
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (Exception e) {
            log.error("JWT解析失败 | 原因: {}", e.getMessage());
            throw new MalformedJwtException("Invalid JWT", e);
        }
    }
}