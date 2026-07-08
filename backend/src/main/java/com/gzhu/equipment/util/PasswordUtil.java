package com.gzhu.equipment.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码Hash生成工具 — 用于生成种子数据的BCrypt哈希
 *
 * 运行方式（项目根目录）：
 *   cd backend
 *   mvn exec:java -Dexec.mainClass="com.gzhu.equipment.util.PasswordUtil" -Dexec.args="your_password"
 *
 * 输出可以直接复制到 sql/init/02-data.sql 的 password 字段。
 */
public class PasswordUtil {
    public static void main(String[] args) {
        String password = args.length > 0 ? args[0] : "admin123";
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode(password);
        System.out.println("密码: " + password);
        System.out.println("Hash: " + hash);
    }
}
