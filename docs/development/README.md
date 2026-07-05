# 开发文档

## 文档清单

| 文件 | 说明 |
|------|------|
| [开发流程.md](开发流程.md) | 环境搭建、分阶段开发任务、迁移部署、上线清单 |
| README.md | 本索引文件 |

## 关键路径

- **环境准备**：WSL2 + Docker Engine + VSCode Remote
- **开发方式**：基础设施容器化（mysql/redis/minio），后端前端本地热更新
- **分支规范**：`main` → `dev` → `feature/xxx` 或 `fix/xxx`
- **迁移部署**：`docker compose up -d --build` 一键部署到国产 Linux

详细内容请阅读 [开发流程.md](开发流程.md)。
