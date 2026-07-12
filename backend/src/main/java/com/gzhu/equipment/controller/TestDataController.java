package com.gzhu.equipment.controller;

import com.gzhu.equipment.common.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/admin/test-data")
@RequiredArgsConstructor
@Api(tags = "测试数据管理")
public class TestDataController {

    private final JdbcTemplate db;
    private static final Random RND = new Random();
    private static final String TEST_PWD = "$2a$10$WMR7yG3.4Tavv912DxxynevSB7laOXLZ.mkvkV2HxHQuClpB5OVgi";

    @PostMapping("/generate")
    @ApiOperation("一键生成测试数据")
    @PreAuthorize("hasAuthority('admin:user')")
    public R<Map<String,Object>> generate() {
        Map<String,Object> r = new LinkedHashMap<>();
        try {
            int teachers = createUsers("testTeacher", 50, 1, "建筑学院");
            int students = createUsers("testStudent", 100, 0, "建筑学院");
            int borrows = genBorrows();
            r.put("teachers", teachers); r.put("students", students); r.put("borrows", borrows);
            log.info("测试数据生成: teachers={} students={} borrows={}", teachers, students, borrows);
            return R.ok(r);
        } catch (Exception e) {
            log.error("测试数据生成失败", e);
            return R.fail("生成失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/cleanup")
    @ApiOperation("一键清除测试数据")
    @PreAuthorize("hasAuthority('admin:user')")
    public R<Map<String,Object>> cleanup() {
        Map<String,Object> r = new LinkedHashMap<>();
        int total=0;
        // 分步执行，避免MySQL子查询限制
        try { total+=db.update("DELETE o FROM overdue_record o INNER JOIN borrow_record b ON o.borrow_id=b.id INNER JOIN sys_user u ON b.user_id=u.id WHERE u.username LIKE 'testTeacher%' OR u.username LIKE 'testStudent%'"); }catch(Exception e){log.warn("cleanup overdue: {}",e.getMessage());}
        try { total+=db.update("DELETE oc FROM borrow_outcome oc INNER JOIN borrow_record b ON oc.borrow_id=b.id INNER JOIN sys_user u ON b.user_id=u.id WHERE u.username LIKE 'testTeacher%' OR u.username LIKE 'testStudent%'"); }catch(Exception e){log.warn("cleanup outcome: {}",e.getMessage());}
        try { total+=db.update("DELETE a FROM approval_log a INNER JOIN borrow_record b ON a.borrow_id=b.id INNER JOIN sys_user u ON b.user_id=u.id WHERE u.username LIKE 'testTeacher%' OR u.username LIKE 'testStudent%'"); }catch(Exception e){log.warn("cleanup approval: {}",e.getMessage());}
        try { total+=db.update("DELETE b FROM borrow_record b INNER JOIN sys_user u ON b.user_id=u.id WHERE u.username LIKE 'testTeacher%' OR u.username LIKE 'testStudent%'"); }catch(Exception e){log.warn("cleanup borrow: {}",e.getMessage());}
        int users=0; try{users=db.update("DELETE FROM sys_user WHERE username LIKE 'testTeacher%' OR username LIKE 'testStudent%'");}catch(Exception e){log.warn("cleanup users: {}",e.getMessage());}
        int repairs=0; try{repairs=db.update("DELETE FROM repair_record WHERE fault_description LIKE '%测试%'");}catch(Exception e){log.warn("cleanup repairs: {}",e.getMessage());}
        total+=users+repairs; r.put("deleted",total); r.put("users",users); r.put("repairs",repairs);
        log.warn("测试数据已清除: total={} users={} repairs={}", total, users, repairs);
        return R.ok(r);
    }

    private int createUsers(String prefix, int count, int type, String dept) {
        int created = 0;
        for (int i = 1; i <= count; i++) {
            String name = String.format("%s%03d", prefix, i);
            Long exists = db.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username = ?", Long.class, name);
            if (exists != null && exists > 0) continue;
            db.update("INSERT INTO sys_user (username,real_name,user_type,department,password,auth_source,status) VALUES (?,?,?,?,?,'L',1)",
                    name, name, type, dept, TEST_PWD);
            created++;
        }
        return created;
    }

    private int genBorrows() {
        List<Long> devIds = db.queryForList("SELECT id FROM device WHERE device_status IN (1,2,3) LIMIT 200", Long.class);
        List<Long> userIds = db.queryForList("SELECT id FROM sys_user WHERE username LIKE 'testTeacher%' OR username LIKE 'testStudent%'", Long.class);
        List<Long> admins = db.queryForList("SELECT id FROM sys_user WHERE user_type IN (2,3) LIMIT 5", Long.class);
        if (devIds.isEmpty() || userIds.isEmpty()) return 0;

        LocalDate today = LocalDate.now();
        int total = 0;
        List<String> cats = Arrays.asList("教学与培养","科研与项目","学科竞赛与创新","个人发展与兴趣");
        List<String> subs = Arrays.asList("课堂教学","毕业设计","项目研究","竞赛训练","课程作业","实习实训","自主学习");

        for (int d = 30; d >= 0; d--) {
            int count = 5 + RND.nextInt(96);
            LocalDate day = today.minusDays(d);
            for (int i = 0; i < count; i++) {
                Long uid = userIds.get(RND.nextInt(userIds.size()));
                Long did = devIds.get(RND.nextInt(devIds.size()));
                String purpose = subs.get(RND.nextInt(subs.size())) + "-测试" + RND.nextInt(1000);
                String pcat = cats.get(RND.nextInt(cats.size()));

                LocalDateTime start = day.atTime(8 + RND.nextInt(10), RND.nextInt(60));
                LocalDateTime end = start.plusDays(1 + RND.nextInt(7));

                double roll = RND.nextDouble();
                String status; LocalDateTime realRet = null; int overdue = 0;

                if (roll < 0.10) status = "PENDING_APPROVAL";
                else if (roll < 0.20) status = "REJECTED";
                else if (roll < 0.25) status = "CANCELLED";
                else if (roll < 0.65) { status = "RETURNED"; realRet = end.minusHours(RND.nextInt(24)); }
                else if (roll < 0.85) status = "BORROWING";
                else { status = "OVERDUE"; overdue = 1 + RND.nextInt(30); end = today.minusDays(overdue).atTime(18, 0); }

                db.update("INSERT INTO borrow_record (user_id,device_id,start_time,end_time,status,reason,purpose,purpose_category,purpose_subcategory,current_step,real_return_time,overdue_days,create_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        uid, did, start, end, status, "测试", purpose, pcat, subs.get(RND.nextInt(subs.size())),
                        "PENDING_APPROVAL".equals(status) ? 1 : 2, realRet, overdue, day.atStartOfDay());
                Long bid = db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                total++;

                // 审批记录
                if (!Arrays.asList("PENDING_APPROVAL","CANCELLED").contains(status)) {
                    Long a1 = admins.get(RND.nextInt(admins.size()));
                    String res1 = "REJECTED".equals(status) ? "REJECTED" : "APPROVED";
                    db.update("INSERT INTO approval_log (borrow_id,step,approver_id,result,comment,operate_time) VALUES (?,1,?,?,?,?)",
                            bid, a1, res1, "APPROVED".equals(res1) ? "通过" : "驳回", start.plusHours(1));
                    if ("APPROVED".equals(res1)) {
                        Long a2 = admins.get(RND.nextInt(admins.size()));
                        db.update("INSERT INTO approval_log (borrow_id,step,approver_id,result,comment,operate_time) VALUES (?,2,?,?,?,?)",
                                bid, a2, "APPROVED", "终审通过", start.plusHours(3));
                    }
                }
                // 10% 概率生成维修记录
                if ("RETURNED".equals(status) && RND.nextDouble() < 0.10) {
                    String fault = Arrays.asList("屏幕损坏","无法开机","按键失灵","接口松动","电池老化").get(RND.nextInt(5));
                    db.update("INSERT INTO repair_record (device_id,borrow_id,fault_description,status,repair_comment,create_time) VALUES (?,?,?,?,?,?)",
                            did, bid, fault + "-测试", RND.nextBoolean() ? "FIXED" : "PENDING", "测试维修", day.plusDays(1).atStartOfDay());
                }
            }
        }
        return total;
    }
}
