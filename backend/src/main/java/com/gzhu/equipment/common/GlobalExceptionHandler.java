package com.gzhu.equipment.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(value = {IllegalArgumentException.class})
    public R<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        return R.fail(400, e.getMessage());
    }

    @ExceptionHandler(value = {RuntimeException.class})
    public R<Void> handleRuntime(RuntimeException e) {
        log.error("运行时异常: ", e);
        return R.fail(500, "服务器内部错误");
    }

    @ExceptionHandler(value = {Exception.class})
    public R<Void> handleException(Exception e) {
        log.error("系统异常: ", e);
        return R.fail(500, "系统繁忙，请稍后重试");
    }
}
