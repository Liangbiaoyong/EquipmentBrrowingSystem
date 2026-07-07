package com.gzhu.equipment.aspect;

import com.gzhu.equipment.entity.SysLog;
import com.gzhu.equipment.mapper.SysLogMapper;
import com.gzhu.equipment.security.JwtUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

/**
 * 操作日志审计AOP — 自动记录所有 @RequestMapping 方法的调用
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final SysLogMapper sysLogMapper;

    @Around("@annotation(org.springframework.web.bind.annotation.RequestMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    public Object auditLog(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        String method = joinPoint.getSignature().toShortString();
        String params = truncate(Arrays.toString(joinPoint.getArgs()), 500);

        Object result;
        int status = 1;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            status = 0;
            throw t;
        } finally {
            long duration = System.currentTimeMillis() - start;
            try {
                SysLog sysLog = new SysLog();
                var auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getPrincipal() instanceof JwtUserPrincipal p) {
                    sysLog.setUserId(p.getUserId());
                    sysLog.setUsername(p.getUsername());
                }
                sysLog.setOperation(joinPoint.getSignature().getName());
                sysLog.setMethod(method);
                sysLog.setParams(params);

                ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attrs != null) {
                    HttpServletRequest req = attrs.getRequest();
                    sysLog.setIp(req.getRemoteAddr());
                }

                sysLog.setDuration(duration);
                sysLog.setStatus(status);
                sysLogMapper.insert(sysLog);
            } catch (Exception e) {
                log.warn("审计日志写入失败: {}", e.getMessage());
            }
        }
        return result;
    }

    private String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
