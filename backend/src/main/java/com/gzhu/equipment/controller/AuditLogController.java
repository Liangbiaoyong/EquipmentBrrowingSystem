package com.gzhu.equipment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gzhu.equipment.common.ExcelExportUtil;
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
import java.util.*;

@RestController @RequestMapping("/admin/logs") @RequiredArgsConstructor @Api(tags = "操作日志审计")
public class AuditLogController {
    private final SysLogMapper sysLogMapper;

    @GetMapping
    @ApiOperation("操作日志列表") @PreAuthorize("hasAuthority('admin:log')")
    public R<IPage<SysLog>> list(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,
                                  @RequestParam(required=false)String username,@RequestParam(required=false)Integer status){
        LambdaQueryWrapper<SysLog> w=new LambdaQueryWrapper<>();
        if(username!=null&&!username.isEmpty())w.like(SysLog::getUsername,username);
        if(status!=null)w.eq(SysLog::getStatus,status);
        w.orderByDesc(SysLog::getCreateTime);
        return R.ok(sysLogMapper.selectPage(new Page<>(page,size),w));
    }

    @GetMapping(value = "/export", produces = "application/octet-stream")
    @ApiOperation("导出操作日志（CSV/XLSX）") @PreAuthorize("hasAuthority('admin:log')")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required=false)String username,
            @RequestParam(required=false)Integer status,
            @RequestParam(defaultValue="csv")String format) throws Exception {
        LambdaQueryWrapper<SysLog> w=new LambdaQueryWrapper<>();
        if(username!=null&&!username.isEmpty())w.like(SysLog::getUsername,username);
        if(status!=null)w.eq(SysLog::getStatus,status);
        w.orderByDesc(SysLog::getCreateTime).last("LIMIT 5000");
        List<SysLog> logs=sysLogMapper.selectList(w);
        DateTimeFormatter dtf=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        if ("xlsx".equalsIgnoreCase(format)) {
            LinkedHashMap<String,String> headers=new LinkedHashMap<>();
            headers.put("username","用户名"); headers.put("operation","操作");
            headers.put("method","方法"); headers.put("ip","IP地址");
            headers.put("duration","耗时(ms)"); headers.put("status","状态");
            headers.put("createTime","时间");

            List<Map<String,Object>> rows=new ArrayList<>();
            for(SysLog l:logs){
                Map<String,Object> row=new LinkedHashMap<>();
                row.put("username",l.getUsername()!=null?l.getUsername():"");
                row.put("operation",l.getOperation()!=null?l.getOperation():"");
                row.put("method",l.getMethod()!=null?l.getMethod():"");
                row.put("ip",l.getIp()!=null?l.getIp():"");
                row.put("duration",l.getDuration()!=null?l.getDuration():0);
                row.put("status",l.getStatus()!=null&&l.getStatus()==1?"成功":"失败");
                row.put("createTime",l.getCreateTime()!=null?l.getCreateTime().format(dtf):"");
                rows.add(row);
            }
            byte[] xlsx=ExcelExportUtil.exportToXlsx(rows,headers);
            HttpHeaders h=new HttpHeaders();
            h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            h.set(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=logs_export_"+System.currentTimeMillis()+".xlsx");
            return ResponseEntity.ok().headers(h).body(xlsx);
        }

        // CSV (默认) — 正确转义
        ByteArrayOutputStream bos=new ByteArrayOutputStream();
        bos.write(0xEF);bos.write(0xBB);bos.write(0xBF);
        OutputStreamWriter osw=new OutputStreamWriter(bos,StandardCharsets.UTF_8);
        osw.write("用户名,操作,方法,IP地址,耗时(ms),状态,时间\n");
        for(SysLog l:logs){
            osw.write(escapeCsv(l.getUsername())+",");
            osw.write(escapeCsv(l.getOperation())+",");
            osw.write(escapeCsv(l.getMethod())+",");
            osw.write(escapeCsv(l.getIp())+",");
            osw.write((l.getDuration()!=null?l.getDuration():0)+",");
            osw.write((l.getStatus()!=null&&l.getStatus()==1?"成功":"失败")+",");
            osw.write(escapeCsv(l.getCreateTime()!=null?l.getCreateTime().format(dtf):""));
            osw.write("\n");
        }
        osw.flush();osw.close();
        HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        h.set(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=logs_export_"+System.currentTimeMillis()+".csv");
        return ResponseEntity.ok().headers(h).body(bos.toByteArray());
    }

    private String escapeCsv(String val) {
        if (val == null || val.isEmpty()) return "\"\"";
        if (val.contains(",") || val.contains("\"") || val.contains("\n") || val.contains("\r")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}
