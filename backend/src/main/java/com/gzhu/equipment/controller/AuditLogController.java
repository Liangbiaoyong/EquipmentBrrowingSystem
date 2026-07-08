package com.gzhu.equipment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gzhu.equipment.common.R;
import com.gzhu.equipment.entity.SysLog;
import com.gzhu.equipment.mapper.SysLogMapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController @RequestMapping("/admin/logs") @RequiredArgsConstructor @Api(tags = "操作日志审计")
public class AuditLogController {
    private final SysLogMapper sysLogMapper;

    @GetMapping
    @ApiOperation("操作日志列表") @PreAuthorize("hasAuthority('admin:log')")
    public R<IPage<SysLog>> list(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)String username,@RequestParam(required=false)Integer status){
        LambdaQueryWrapper<SysLog> w=new LambdaQueryWrapper<>();
        if(username!=null&&!username.isEmpty())w.like(SysLog::getUsername,username);
        if(status!=null)w.eq(SysLog::getStatus,status);
        w.orderByDesc(SysLog::getCreateTime);
        return R.ok(sysLogMapper.selectPage(new Page<>(page,size),w));
    }

    @GetMapping("/export")
    @ApiOperation("导出操作日志CSV") @PreAuthorize("hasAuthority('admin:log')")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required=false)String username,@RequestParam(required=false)Integer status) throws Exception {
        LambdaQueryWrapper<SysLog> w=new LambdaQueryWrapper<>();
        if(username!=null&&!username.isEmpty())w.like(SysLog::getUsername,username);
        if(status!=null)w.eq(SysLog::getStatus,status);
        w.orderByDesc(SysLog::getCreateTime).last("LIMIT 5000");
        List<SysLog> logs=sysLogMapper.selectList(w);
        ByteArrayOutputStream bos=new ByteArrayOutputStream();
        bos.write(0xEF);bos.write(0xBB);bos.write(0xBF);
        OutputStreamWriter osw=new OutputStreamWriter(bos,StandardCharsets.UTF_8);
        osw.write("用户名,操作,方法,IP,耗时(ms),状态,时间\n");
        DateTimeFormatter dtf=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for(SysLog l:logs){
            osw.write(String.format("%s,%s,%s,%s,%d,%s,%s\n",
                l.getUsername()!=null?l.getUsername():"",l.getOperation()!=null?l.getOperation():"",
                l.getMethod()!=null?l.getMethod():"",l.getIp()!=null?l.getIp():"",
                l.getDuration()!=null?l.getDuration():0,l.getStatus()!=null&&l.getStatus()==1?"成功":"失败",
                l.getCreateTime()!=null?l.getCreateTime().format(dtf):""));
        }
        osw.flush();osw.close();
        HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        h.set(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=logs_export_"+System.currentTimeMillis()+".csv");
        return ResponseEntity.ok().headers(h).body(bos.toByteArray());
    }
}
