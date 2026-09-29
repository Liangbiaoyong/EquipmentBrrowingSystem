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
        // 落到 /error 的请求（404/400 等）几乎全部来自公网扫描器：量大（实测每小时数百条）、
        // 以约 10 秒一波的节奏出现、来源 IP 是反向代理而非真实用户，且不是应用故障。
        // 仍然记录，但换一个独立的操作类型「外部扫描」并计为成功，
        // 以免混进正常业务的失败记录里把操作日志刷屏。
        boolean externalScan = joinPoint.getTarget() != null
                && "BasicErrorController".equals(joinPoint.getTarget().getClass().getSimpleName());
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
                sysLog.setOperation(externalScan ? "外部扫描" : joinPoint.getSignature().getName());
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
