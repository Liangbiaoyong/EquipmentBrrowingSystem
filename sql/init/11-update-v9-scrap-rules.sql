-- ============================================================
-- V9: 报废规则表 + 初始数据
-- ============================================================
USE `device_borrow`;

CREATE TABLE IF NOT EXISTS `scrap_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `gb_keyword` varchar(200) NOT NULL COMMENT '国标分类名/关键词（支持包含匹配）',
  `min_years` int NOT NULL DEFAULT '6' COMMENT '最低使用年限',
  `priority` int DEFAULT '100' COMMENT '优先级（越小越优先）',
  `remark` varchar(500) DEFAULT NULL COMMENT '说明',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_keyword` (`gb_keyword`(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报废规则表';

-- 初始化报废规则（按国标分类关键词 → 最低使用年限）
INSERT INTO `scrap_rule` (`gb_keyword`, `min_years`, `priority`, `remark`) VALUES
('计算机设备', 6, 10, '计算机及外设'),
('办公设备', 6, 10, '办公用设备'),
('车辆', 8, 10, '机动车辆'),
('图书档案设备', 5, 10, '图书档案类'),
('机械设备', 10, 10, '机械加工设备'),
('电气设备', 5, 10, '电气电力设备'),
('雷达', 10, 20, '雷达导航类'),
('无线电', 10, 20, '无线电设备'),
('卫星导航', 10, 20, '卫星导航设备'),
('通信设备', 5, 15, '通信传输设备'),
('广播', 5, 15, '广播电视设备'),
('电视', 5, 15, '电视设备'),
('电影设备', 5, 15, '电影放映设备'),
('仪器仪表', 5, 15, '仪器仪表及测量'),
('电子和通信测量', 5, 15, '电子通信测量设备'),
('计量标准', 5, 15, '计量标准器具'),
('量具', 5, 15, '量具衡器'),
('衡器', 5, 15, '衡器'),
('家具', 15, 10, '家具及装具');
