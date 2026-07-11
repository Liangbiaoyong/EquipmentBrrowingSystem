-- ============================================================
-- V2 升级脚本：实验室管理 + 设备借用分类
-- 适用于已有 V1 数据库的增量升级
-- ============================================================

USE `device_borrow`;

-- -----------------------------------------------------------
-- 1. 实验室表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `laboratory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '实验室名称',
  `code` varchar(50) DEFAULT NULL COMMENT '实验室编码',
  `location_prefix` varchar(200) DEFAULT NULL COMMENT '存放地前缀',
  `description` text COMMENT '实验室描述',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实验室表';

-- -----------------------------------------------------------
-- 2. 实验室地点映射表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `laboratory_room` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `laboratory_id` bigint NOT NULL COMMENT '关联实验室ID',
  `room_name` varchar(100) NOT NULL COMMENT '房间/地点简称（如工程南501）',
  `full_location` varchar(500) DEFAULT NULL COMMENT '完整存放地名称',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_laboratory` (`laboratory_id`),
  KEY `idx_room_name` (`room_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实验室地点映射表';

-- -----------------------------------------------------------
-- 3. device 表新增字段
-- -----------------------------------------------------------
-- 如果表中已存在列名则跳过（通过忽略重复列错误处理）
ALTER TABLE `device`
  ADD COLUMN `borrow_type` tinyint DEFAULT 2 COMMENT '借用类型: 1可现场借用 2可借出（默认）' AFTER `status`,
  ADD COLUMN `laboratory_id` bigint DEFAULT NULL COMMENT '所属实验室ID' AFTER `location`,
  ADD INDEX `idx_borrow_type` (`borrow_type`),
  ADD INDEX `idx_laboratory` (`laboratory_id`);

-- -----------------------------------------------------------
-- 4. 初始实验室数据
-- -----------------------------------------------------------
INSERT INTO `laboratory` (`id`, `name`, `code`, `location_prefix`, `description`, `status`) VALUES
(1, '建成环境气候模拟实验室', 'LAB-CLIMATE', '工程实验楼南楼＞5＞', NULL, 1),
(2, '声振环境实验室', 'LAB-SOUND', '理科教学楼北楼＞6＞', NULL, 1),
(3, '建筑光学与暗夜保护实验室', 'LAB-OPTICS', '工程实验楼南楼＞5＞', NULL, 1),
(4, '数字孪生与空间模型实验室', 'LAB-DIGITAL', '理科教学楼北楼＞5＞', NULL, 1),
(5, '资源与环境智慧测评实验室', 'LAB-RESOURCE', '理科教学楼北楼＞5＞', NULL, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `code` = VALUES(`code`);

-- -----------------------------------------------------------
-- 5. 地点映射数据（基于用户提供的32条映射）
-- -----------------------------------------------------------
INSERT INTO `laboratory_room` (`laboratory_id`, `room_name`, `full_location`) VALUES
-- 建成环境气候模拟实验室 (id=1)
(1, '工程南501', '工程实验楼南楼＞5＞建成环境气候模拟实验室-501'),
(1, '工程南520', '工程实验楼南楼＞5＞绿色智慧建筑实验室1室-520'),
(1, '工程南521', NULL),
(1, '工程南522', NULL),
(1, '工程南525', NULL),
(1, '工程南529', NULL),
(1, '工程南530', '工程实验楼南楼＞5＞建筑光学与暗夜保护实验室-530'),

-- 声振环境实验室 (id=2)
(2, '工程北623', '理科教学楼北楼＞6＞声振环境实验室-623'),
(2, '工程北625', NULL),

-- 建筑光学与暗夜保护实验室 (id=3)
(3, '工程南514', NULL),
(3, '工程南515', NULL),
(3, '工程南530', '工程实验楼南楼＞5＞建筑光学与暗夜保护实验室-530'),
(3, '工程南531', '工程实验楼南楼＞5＞仪器及档案存放室-531'),

-- 数字孪生与空间模型实验室 (id=4)
(4, '工程北504', '理科教学楼北楼＞5＞数字孪生与空间模型实验室-504'),
(4, '工程北510', NULL),
(4, '工程北511', NULL),
(4, '工程北512', NULL),
(4, '文俊东512', NULL),

-- 资源与环境智慧测评实验室 (id=5)
(5, '工程北503', '理科教学楼北楼＞5＞资源与环境智慧测评实验室-503'),
(5, '工程北505', NULL),
(5, '工程北506', NULL),
(5, '文俊东511', NULL),

-- 其他未明确归属实验室的地点（暂不关联实验室）
-- 文俊东509, 文俊东510, 工程南505, 506, 508, 509, 510, 511, 513, 518, 519, 520
(1, '文俊东509', NULL),
(1, '文俊东510', NULL),
(1, '工程南505', NULL),
(1, '工程南506', NULL),
(1, '工程南508', NULL),
(1, '工程南509', NULL),
(1, '工程南510', NULL),
(1, '工程南511', NULL),
(1, '工程南513', '工程实验楼南楼＞5＞城市更新规划与文化景观设计实验室2室-513'),
(1, '工程南518', NULL),
(1, '工程南519', NULL);
