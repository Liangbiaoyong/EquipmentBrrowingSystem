-- Fix: update all test account passwords to match admin123
-- Uses the same working BCrypt hash as admin
UPDATE sys_user SET password = '$2b$10$AgBkzhU4VePwdNIX9qcCRuM//tvoQKV6bh/mHV9P3FmiQ7Zkmztni'
WHERE username IN ('labadmin', 'student01', 'teacher01');
