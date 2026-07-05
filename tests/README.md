# 测试目录

本目录存放集成测试、端到端（E2E）测试及性能测试脚本。

## 目录结构

```
tests/
├── e2e/         # 端到端测试（可使用 Playwright / Cypress）
├── integration/ # 集成测试
├── performance/ # 性能测试（JMeter 脚本等）
└── README.md
```

## 测试策略

- **单元测试**：位于 `backend/src/test/`，用 JUnit5 + Mockito，覆盖率 > 70%
- **集成测试**：位于本目录 `tests/integration/`
- **E2E 测试**：位于本目录 `tests/e2e/`
- **性能测试**：JMeter 模拟 30 并发
- **安全测试**：手动测试越权、XSS、文件上传漏洞

> 单元测试不放在这里，放在对应模块的 `src/test` 目录下。
