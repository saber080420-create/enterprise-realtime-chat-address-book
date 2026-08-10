package org.itheima;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtTest {
@Test
    public void tetsGen(){
    Map<String,Object> claims = new HashMap<>();
    claims.put("id",1);
    claims.put("username","张三");
//        生成jwt代码
   String token= JWT.create()
            .withClaim("user",claims)//添加载荷
            .withExpiresAt(new Date(System.currentTimeMillis()+1000*60*60*12))//添加过期时间
            .sign(Algorithm.HMAC256("itheima"));//指定算法，配置密钥


    System.out.println(token);
    }
    @Test
    public void testParse(){
//        定义字符串，模拟用户传递过来的token
        String token ="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +//头部，记录令牌类型和签名算法等
                ".eyJ1c2VyIjp7ImlkIjoxLCJ1c2VybmFtZSI6IuW8oOS4iSJ9LCJleHAiOjE3NTIyODE0MzR9" +//载荷，携带自定义信，因为这里仅是用base64编码组成的，不是加密，所以不能放私密信息，很容易就能被翻译
                ".la475fkyCoeM8NvvM24ul381Lxyn59n7tWZWMuKI0vg";//签名，对头部和载荷进行加密计算得来
                                                                //头部和载荷都是用base64编码展现，只有签名是用加密的方式展现
        JWTVerifier jwtVerifier=JWT.require(Algorithm.HMAC256("itheima")).build();

        DecodedJWT decodedJWT=jwtVerifier.verify(token);//验证token，后生成一个解析后的JWT对象
        Map<String, Claim> claims = decodedJWT.getClaims();
        System.out.println(claims.get("user"));
//        如果篡改了头部和载荷部分的数据，那么验证失败
//        如果篡改密钥也失败
//        token过期了也失败
    }
}
