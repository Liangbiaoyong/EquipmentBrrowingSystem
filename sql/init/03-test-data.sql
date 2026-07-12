-- ============================================================
-- 测试数据生成脚本
-- 生成最近一个月（每天5~50条）随机借用记录
-- 使用: mysql -uroot -p --default-character-set=utf8mb4 device_borrow < 03-test-data.sql
-- ============================================================

USE `device_borrow`;

-- 如果已存在则删除存储过程
DROP PROCEDURE IF EXISTS `generate_test_data`;

DELIMITER $$

CREATE PROCEDURE `generate_test_data`()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE dev_id BIGINT;
    DECLARE dev_name VARCHAR(200);
    DECLARE dev_location VARCHAR(500);
    DECLARE cat_id BIGINT;

    -- 游标：所有可借用的设备
    DECLARE device_cursor CURSOR FOR
        SELECT d.id, d.name, COALESCE(d.location, ''),
               COALESCE(d.category_id, 10)
        FROM device d
        WHERE d.borrow_status = 1 AND d.device_status = 1
        ORDER BY RAND();
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    -- 用户ID: student01(id=3), teacher01(id=4), labadmin(id=2)
    -- 管理员 admin(id=1)

    -- 清理旧数据
    DELETE FROM approval_log WHERE borrow_id > 0;
    DELETE FROM borrow_outcome;
    DELETE FROM borrow_record WHERE id > 0;
    ALTER TABLE borrow_record AUTO_INCREMENT = 1;
    ALTER TABLE approval_log AUTO_INCREMENT = 1;

    -- 关闭唯一检查提升性能
    SET SESSION unique_checks = 0;
    SET SESSION foreign_key_checks = 0;

    -- 遍历每一天（从2026-06-12到2026-07-12）
    SET @day = '2026-06-12';
    WHILE @day <= '2026-07-12' DO
        -- 每天随机 5~50 条
        SET @daily_count = FLOOR(5 + RAND() * 46);

        SET @counter = 0;
        WHILE @counter < @daily_count DO
            -- 随机选取一个设备（游标方式）
            SET done = FALSE;
            OPEN device_cursor;
            FETCH device_cursor INTO dev_id, dev_name, dev_location, cat_id;

            IF NOT done THEN
                -- 随机用户（student01=3 或 teacher01=4，偏好学生 70%）
                SET @uid = IF(RAND() < 0.7, 3, 4);

                -- 随机时间段
                SET @start_hour = 8 + FLOOR(RAND() * 10);
                SET @duration_days = 1 + FLOOR(RAND() * 7);
                SET @start_time = CONCAT(@day, ' ', LPAD(@start_hour, 2, '0'), ':00:00');
                SET @end_time = DATE_ADD(@start_time, INTERVAL @duration_days DAY);

                -- 随机状态（概率分布：PENDING_APPROVAL:10%, APPROVED:15%, REJECTED:8%, BORROWING:25%, RETURNED:35%, OVERDUE:5%, CANCELLED:2%）
                SET @status_roll = RAND();
                SET @status = CASE
                    WHEN @status_roll < 0.10 THEN 'PENDING_APPROVAL'
                    WHEN @status_roll < 0.25 THEN 'APPROVED'
                    WHEN @status_roll < 0.33 THEN 'REJECTED'
                    WHEN @status_roll < 0.58 THEN 'BORROWING'
                    WHEN @status_roll < 0.93 THEN 'RETURNED'
                    WHEN @status_roll < 0.98 THEN 'OVERDUE'
                    ELSE 'CANCELLED'
                END;

                -- 随机目的分类（V4）
                SET @purpose_category = CASE FLOOR(RAND() * 8)
                    WHEN 0 THEN '教学与培养' WHEN 1 THEN '科研与项目'
                    WHEN 2 THEN '学科竞赛与创新' WHEN 3 THEN '学术交流与合作'
                    WHEN 4 THEN '社会服务与文化传承' WHEN 5 THEN '行政与公共服务'
                    WHEN 6 THEN '个人发展与兴趣' ELSE '其他'
                END;
                SET @purpose = CASE @purpose_category
                    WHEN '教学与培养' THEN ELT(1 + FLOOR(RAND() * 4), '课程教学', '毕业设计', '课程设计', '实习实训')
                    WHEN '科研与项目' THEN ELT(1 + FLOOR(RAND() * 3), '科研项目', '研究生论文', '实验室自研')
                    WHEN '学科竞赛与创新' THEN ELT(1 + FLOOR(RAND() * 3), '学生竞赛', '教师指导', '大创项目')
                    WHEN '学术交流与合作' THEN ELT(1 + FLOOR(RAND() * 3), '学术会议', '联合工作坊', '访问研究')
                    WHEN '社会服务与文化传承' THEN '社会服务'
                    WHEN '行政与公共服务' THEN '行政办公'
                    WHEN '个人发展与兴趣' THEN '自主学习'
                    ELSE '其他用途'
                END;

                -- 插入借用记录
                INSERT INTO `borrow_record` (
                    `user_id`, `device_id`, `start_time`, `end_time`, `status`,
                    `reason`, `purpose`, `purpose_category`, `purpose_subcategory`,
                    `approve_flow_def`, `current_step`, `real_return_time`,
                    `overdue_days`, `damage_report`, `outcome`, `outcome_recorded_by`,
                    `outcome_recorded_time`, `create_time`, `update_time`
                ) VALUES (
                    @uid, dev_id, @start_time, @end_time, @status,
                    ELT(1 + FLOOR(RAND() * 5), '实验测试', '课程需要', '科研使用', '竞赛准备', '日常办公'),
                    @purpose, @purpose_category, @purpose,
                    '{"steps":[{"step":1,"name":"初审"},{"step":2,"name":"终审"}],"totalSteps":2}',
                    CASE
                        WHEN @status IN ('PENDING_APPROVAL') THEN 1
                        WHEN @status IN ('CANCELLED') THEN 1
                        WHEN @status IN ('APPROVED','BORROWING','RETURNED','OVERDUE','REJECTED') THEN 2
                        ELSE 1
                    END,
                    CASE
                        WHEN @status IN ('RETURNED') THEN DATE_ADD(@end_time, INTERVAL FLOOR(RAND() * 3) DAY)
                        WHEN @status IN ('OVERDUE') THEN DATE_ADD(@end_time, INTERVAL 3 + FLOOR(RAND() * 14) DAY)
                        ELSE NULL
                    END,
                    CASE WHEN @status = 'OVERDUE' THEN FLOOR(3 + RAND() * 20) ELSE 0 END,
                    CASE WHEN @status = 'RETURNED' AND RAND() < 0.1 THEN '外壳轻微磨损' ELSE NULL END,
                    CASE WHEN @status = 'RETURNED' AND RAND() < 0.6
                        THEN ELT(1 + FLOOR(RAND() * 6),
                            '完成课程实验报告', '发表学术论文一篇', '获竞赛三等奖',
                            '完成毕业设计', '完成科研项目结题', '完成大创项目报告')
                        ELSE NULL
                    END,
                    CASE WHEN @status = 'RETURNED' AND RAND() < 0.6 THEN @uid ELSE NULL END,
                    CASE WHEN @status = 'RETURNED' AND RAND() < 0.6
                        THEN DATE_ADD(@end_time, INTERVAL FLOOR(RAND() * 7) DAY) ELSE NULL END,
                    DATE_SUB(@start_time, INTERVAL FLOOR(RAND() * 3) DAY),
                    NOW()
                );

                SET @last_id = LAST_INSERT_ID();

                -- 创建审批记录
                -- 初审：teacher01 (id=4)
                INSERT INTO `approval_log` (`borrow_id`, `step`, `approver_id`, `result`, `operate_time`)
                VALUES (@last_id, 1, 4,
                    CASE
                        WHEN @status IN ('PENDING_APPROVAL','CANCELLED') THEN 'PENDING'
                        WHEN @status = 'REJECTED' THEN 'REJECTED'
                        ELSE 'APPROVED'
                    END,
                    DATE_ADD(@start_time, INTERVAL -1 DAY));

                -- 终审：labadmin (id=2)
                IF @status NOT IN ('PENDING_APPROVAL', 'CANCELLED', 'REJECTED') THEN
                    INSERT INTO `approval_log` (`borrow_id`, `step`, `approver_id`, `result`, `operate_time`)
                    VALUES (@last_id, 2, 2, 'APPROVED', DATE_ADD(@start_time, INTERVAL -1 DAY));
                END IF;

                -- 成果记录（对 RETURNED 且有 outcome 的）
                IF @status = 'RETURNED' AND RAND() < 0.4 THEN
                    SET @outcome_type = CASE FLOOR(RAND() * 6)
                        WHEN 0 THEN '学术论文' WHEN 1 THEN '竞赛获奖'
                        WHEN 2 THEN '研究报告' WHEN 3 THEN '毕设论文'
                        WHEN 4 THEN '人才培养' ELSE '其他'
                    END;
                    INSERT INTO `borrow_outcome` (`borrow_id`, `device_id`, `outcome_type`, `title`, `recorded_by`)
                    VALUES (@last_id, dev_id, @outcome_type,
                        CONCAT(@outcome_type, ' - ', dev_name, ' - ', @purpose), @uid);
                END IF;
            END IF;

            CLOSE device_cursor;
            SET @counter = @counter + 1;
        END WHILE;

        SET @day = DATE_ADD(@day, INTERVAL 1 DAY);
    END WHILE;

    -- 恢复检查
    SET SESSION unique_checks = 1;
    SET SESSION foreign_key_checks = 1;

    -- 统计
    SELECT COUNT(*) AS total_borrow_records FROM borrow_record;
    SELECT status, COUNT(*) AS count FROM borrow_record GROUP BY status ORDER BY count DESC;
    SELECT DATE(create_time) AS day, COUNT(*) AS count FROM borrow_record
        GROUP BY DATE(create_time) ORDER BY day;
END$$

DELIMITER ;

-- 执行生成
CALL generate_test_data();

-- 清理（可选：保留存储过程以备再次调用）
-- DROP PROCEDURE IF EXISTS `generate_test_data`;

-- 更新设备借还状态（将状态为BORROWING和OVERDUE的设备标记为借用中/逾期）
UPDATE device d
JOIN borrow_record b ON b.device_id = d.id AND b.status IN ('BORROWING','OVERDUE')
SET d.borrow_status = CASE WHEN b.status = 'OVERDUE' THEN 4 ELSE 2 END
WHERE d.borrow_status = 1;

-- 更新可借数量（修正已借出的设备）
UPDATE device d
SET d.available_qty = d.total_qty - (
    SELECT COUNT(*) FROM borrow_record b
    WHERE b.device_id = d.id AND b.status IN ('BORROWING', 'OVERDUE')
)
WHERE d.total_qty > 0;
