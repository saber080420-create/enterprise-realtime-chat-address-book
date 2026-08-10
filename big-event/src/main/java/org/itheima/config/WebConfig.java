package org.itheima.config;

import org.itheima.interceptors.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private LoginInterceptor loginInterceptor;
    
    @Value("${file.upload.path:uploads}")
    private String uploadPath;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录接口和注册接口不拦截
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")  // 拦截所有请求
                .excludePathPatterns(
                    "/user/login",
                    "/user/register",
                    "/account/login",
                    "/account/register",
                    "/org/department/tree",
                    "/department/tree", // 排除部门树接口，允许未登录用户获取部门列表用于注册
                    "/uploads/**",
                    "/error", // 放行错误页，避免静态资源缺失时被拦截成401
                    "/api/ws/**" // 放行WebSocket握手与通信端点
                );
        // 添加日志
        System.out.println("已配置拦截器排除路径: /user/login, /user/register, /account/login, /account/register, /org/department/tree, /department/tree, /uploads/**, /error");
    }
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置静态资源映射
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath + "/");
    }
}
