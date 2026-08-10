package org.itheima.config;

import org.itheima.websocket.WebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket配置类
 * 配置WebSocket处理器和端点
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private WebSocketHandler webSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册原生WebSocket端点
        registry.addHandler(webSocketHandler, "/api/ws")
                .setAllowedOrigins("*"); // 允许所有来源的WebSocket连接
        
        // 同时注册SockJS端点，提供浏览器兼容性
        registry.addHandler(webSocketHandler, "/api/ws")
                .setAllowedOrigins("*")
                .withSockJS();
        
        // 添加日志
        System.out.println("已注册WebSocket处理器，端点: /api/ws (原生WebSocket和SockJS)");
    }
}