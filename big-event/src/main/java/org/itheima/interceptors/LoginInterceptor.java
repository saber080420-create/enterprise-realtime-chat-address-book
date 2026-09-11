package org.itheima.interceptors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.itheima.utils.JwtUtil;
import org.itheima.service.UserActivityService;
import org.itheima.utils.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import java.util.Map;

@Component
public class LoginInterceptor implements AsyncHandlerInterceptor {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private UserActivityService userActivityService;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        令牌验证
        String token= request.getHeader("Authorization");
        //        验证toke
        try {
            //从redis中获取相同的token
            ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
            String redisToken = operations.get(token);
            if (redisToken==null){
                //token已经失效或被顶下线
                throw new RuntimeException();
            }
            Map<String,Object> claims= JwtUtil.parseToken(token);
            //把业务数据存放到ThreadLocal中
            ThreadLocalUtil.set(claims);
            // 刷新在线心跳：每次鉴权通过即更新最后活跃时间，统一5分钟在线口径
            try {
                Object idObj = claims.get("id");
                if (idObj instanceof Integer) {
                    userActivityService.updateOnlineStatus((Integer) idObj, true);
                } else if (idObj != null) {
                    int uid = Integer.parseInt(String.valueOf(idObj));
                    userActivityService.updateOnlineStatus(uid, true);
                }
            } catch (Exception ignore) {}
            //放行
            return true;
        } catch (Exception e) {
//            http响应状态码为401
            response.setStatus(401);
//            不放行
            return false;
        }
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
        ThreadLocalUtil.remove();
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //清空ThreadLocal中的数据
        ThreadLocalUtil.remove();
    }
}
