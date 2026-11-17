---
name: git-workflow
description: Quy ước nhánh, commit và PR cho dự án HRM. Dùng khi commit, tạo nhánh, hoặc chuẩn bị PR.
---

# Git

- Nhánh: `feature/<task-id>-<mo-ta>`, `fix/<task-id>-<mo-ta>`. Không commit thẳng vào `main`.
- Commit theo Conventional Commits: `feat(attendance): tính giờ làm ca đêm (T3.4)`; `fix(auth): ...`; `test(...)`, `docs(...)`, `refactor(...)`, `chore(...)`.
- Mỗi commit biên dịch và test được. Mỗi task = 1 PR nhỏ (dưới ~400 dòng thay đổi nếu có thể).
- Mô tả PR gồm: mục tiêu, thay đổi chính, migration (nếu có), cách kiểm thử, ảnh hưởng phân quyền/bảo mật, task ID.
- KHÔNG commit: `.env`, secret, file build (`target/`), dữ liệu cá nhân thật.
- Trước khi mở PR: `./mvnw clean verify` xanh và `docs/PROGRESS.md` đã cập nhật.
