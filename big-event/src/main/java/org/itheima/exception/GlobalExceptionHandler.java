package org.itheima.exception;

import org.itheima.pojo.Result;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler
    public Result handleException(Exception e){
        e.printStackTrace();
        return Result.error(StringUtils.hasLength(e.getMessage())? e.getMessage() : "操作失败");
    }

    /**
     * 处理响应写出/序列化阶段异常（如 Jackson 序列化失败）
     * 函数说明：
     * - 常见于返回对象存在循环引用、懒加载代理或类型不支持序列化等问题
     * - 若不处理，通常会以 500 的形式返回（HttpMessageNotWritableException）
     * - 这里统一转为业务失败，避免前端看到 500
     *
     * @param e HttpMessageNotWritableException 异常
     * @return 业务失败的统一响应
     */
    @ExceptionHandler(HttpMessageNotWritableException.class)
    public Result handleHttpMessageNotWritable(HttpMessageNotWritableException e) {
        e.printStackTrace();
        String msg = e.getMessage();
        if (!StringUtils.hasLength(msg)) {
            msg = "响应序列化失败";
        }
        return Result.error(msg);
    }

    /**
     * 兜底处理：捕获所有 Throwable（包括 Error）
     * 函数说明：
     * - 极端情况下（如 LinkageError、NoClassDefFoundError 或其他非 Exception 错误）
     * - 也统一包装为业务错误，避免直接 500
     *
     * @param t 任意可抛出对象
     * @return 业务失败的统一响应
     */
    @ExceptionHandler(Throwable.class)
    public Result handleThrowable(Throwable t) {
        t.printStackTrace();
        String msg = t.getMessage();
        if (!StringUtils.hasLength(msg)) {
            msg = "系统异常";
        }
        return Result.error(msg);
    }
}
