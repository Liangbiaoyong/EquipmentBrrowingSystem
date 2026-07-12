-- ============================================================
-- V5: 分类描述表 — 目的分类 & 成果类型的元数据说明
-- ============================================================
USE `device_borrow`;

-- 1. 分类描述表
CREATE TABLE IF NOT EXISTS `category_description` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category_type` varchar(20) NOT NULL COMMENT '分类类型: PURPOSE=目的分类 / OUTCOME=成果类型',
  `category_name` varchar(100) NOT NULL COMMENT '分类名称',
  `description` text COMMENT '分类说明描述',
  `sort` int DEFAULT '0' COMMENT '排序',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_type` (`category_type`),
  KEY `idx_name` (`category_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类描述表';

-- 2. 初始化目的分类描述（8个大类）
INSERT INTO `category_description` (`category_type`, `category_name`, `description`, `sort`) VALUES
('PURPOSE', '教学与培养', '用于课程教学、毕业设计/论文、课程设计/大作业、实习实训等人才培养活动', 1),
('PURPOSE', '科研与项目', '用于纵向/横向科研项目、校级科研启动基金、研究生学位论文研究等科研活动', 2),
('PURPOSE', '学科竞赛与创新', '用于学生竞赛、教师指导竞赛、大学生创新创业项目等创新实践活动', 3),
('PURPOSE', '学术交流与合作', '用于学术会议/展览、联合教学工作坊、访问学者/博士后研究等交流活动', 4),
('PURPOSE', '社会服务与文化传承', '用于科普活动/开放日、校企合作基地建设、古建筑测绘/乡村振兴等社会服务', 5),
('PURPOSE', '行政与公共服务', '用于学院行政管理活动、日常办公事务等行政用途', 6),
('PURPOSE', '个人发展与兴趣', '用于学生自主学习、专业技能训练等个人能力提升活动', 7),
('PURPOSE', '其他', '不属于以上分类的其他用途，可在子分类中自行填写具体类别', 8);

-- 3. 初始化成果类型描述（17种）
INSERT INTO `category_description` (`category_type`, `category_name`, `description`, `sort`) VALUES
('OUTCOME', '学术论文', '以借用设备为重要工具完成的学术期刊/会议论文', 1),
('OUTCOME', '专利', '基于设备使用产生的发明/实用新型/外观设计专利', 2),
('OUTCOME', '软著', '利用设备开发的计算机软件著作权', 3),
('OUTCOME', '科研项目结题', '设备支撑的科研项目完成结题验收', 4),
('OUTCOME', '科研获奖', '与设备使用相关的科研成果获得省部级/厅局级奖励', 5),
('OUTCOME', '竞赛获奖', '使用设备参加学科竞赛并获得奖项', 6),
('OUTCOME', '学术著作', '借助设备完成的学术专著/教材/译著等', 7),
('OUTCOME', '研究报告', '基于设备使用产出的调查研究/实验分析/技术报告', 8),
('OUTCOME', '标准制定', '利用设备参与制定的行业标准/地方标准/团体标准', 9),
('OUTCOME', '新产品工艺', '通过设备研发的新产品/新工艺/新材料/新设计', 10),
('OUTCOME', '教学成果奖', '设备支撑的教学改革/课程建设成果获教学成果奖', 11),
('OUTCOME', '毕设论文', '使用设备完成的毕业设计/学位论文', 12),
('OUTCOME', '实体模型', '借助设备制作的建筑/规划/设计类实体模型或样品', 13),
('OUTCOME', '大创结题', '大学生创新创业训练计划项目结题成果', 14),
('OUTCOME', '媒体报道', '与设备使用相关的正面社会媒体报道/宣传', 15),
('OUTCOME', '人才培养', '利用设备培养学生获得的能力提升/荣誉/证书/升学', 16),
('OUTCOME', '其他', '其他类型的使用成果，请在标题和详情中自行描述', 17);
