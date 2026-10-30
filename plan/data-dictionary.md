# 数据字典与事务约定 v1

日期：2026-10-12，Asia/Shanghai。任务：T002；所属批次B01。

依据：requirements.md v2.1、architecture.md v2.1、tasks.md §2.2和T002。本文冻结详细实现名称与字段，不修改业务范围，不表示已经执行DDL或通过MySQL测试。后续接口见[api-contracts.md](api-contracts.md)。

## 1. 公共类型、时间与存储规则

2026-10-15用户直接修订：正常首次派单不再要求设备台账，新增V006__optional_work_order_equipment.sql仅将work_order.equipment_id放宽为NULL；原外键和对非空设备的未完结唯一约束保留，已有记录不改。无关联时repair_request.matched_equipment_id及审核事件evidence_equipment_id均可空，不推断或虚构台账；位置仍固化自申请。以下旧设备锁/设备复核描述仅适用于明确传入equipmentId及重复关闭。T067未来索引迁移须使用执行时下一个未用版本，不能复用V006。

| 约定 | 精确定义 |
| --- | --- |
| 数据库 | 独立MySQL8.4.11；InnoDB；默认utf8mb4/utf8mb4_0900_as_cs；dev/demo/test库隔离 |
| Id | BIGINT正数主键，Java Long；JSON/TypeScript为十进制字符串，1—9223372036854775807；不转为JavaScript Number |
| Version | INT，0—2147483647，默认0；修改当前记录按原version条件更新并+1，溢出拒绝而不回绕 |
| Round | INT；工单接单前current_round=0，接单后轮次号从1起；本轮写操作只接受正数且等于当前轮次 |
| Instant | DATETIME(3)存UTC；Java使用UTC转换；JSON为带Z的ISO8601毫秒时间，如2026-10-12T00:00:00.000Z |
| Clock | 后端注入Clock.systemUTC()；同一事务用同一业务时点；测试可控，不依赖浏览器日期或实际等待 |
| 本地日/月 | Asia/Shanghai自然日或自然月，起点包含、终点不包含；先换算UTC，再查询时间字段 |
| 公共列 | 下表每表均有id BIGINT PK和created_at DATETIME(3) NOT NULL；可修改主表另有updated_at；列未写“可空”均NOT NULL |
| 外键 | 下面“→”均为真实FK，RESTRICT/NO ACTION；不级联删除账号、业务或历史；循环关系后加FK |
| 数据删除 | 账号/设备只启停用；业务、审核和正式附件历史保留；临时文件删除仅走附件清理协议 |
| 非空文本 | trim后不可为空；手机号和账号拒绝格式外字符；密码保留原文做校验，不trim、不正规化 |
| 排序 | 主条件相同补ID；字符串ID在后端按BIGINT排序，前端不能按字典序替代 |

登录账号采用`utf8mb4_0900_as_ci`的独立列排序规则：英文大小写视为同一账号，中文照原文保存，不做拼音或房号解析。此大小写约定、下列非既定文本上限和具体表名是T002的助手实施选择，便于一致校验，不冒称以前已逐项确认；变更时须同步迁移、查询和接口。

| 字段族 | 存储及输入上限 |
| --- | --- |
| loginName | VARCHAR(30)，2—30个Unicode码点，仅汉字/英文字母/数字/下划线；全局唯一，含停用账号 |
| phone/contactPhone | CHAR(11)，正则`^1[3-9][0-9]{9}$`；账号phone全局唯一，业务contactPhone不加唯一约束 |
| password/confirmPassword | 输入8—32个Unicode码点，至少一个ASCII字母和一个数字，允许其他特殊字符；确认完全一致；只存password_hash VARCHAR(255) |
| name/contactName/equipmentName | VARCHAR(100)，1—100个码点；姓名可重名 |
| equipmentCategory | VARCHAR(100)可空；0—100个码点，空白归null；自由文本，不作为设施种类或资格判断 |
| addressText/locationNameSnapshot | VARCHAR(500)，1—500；旧记录的已有合法原文原样保留 |
| description/progress/result | TEXT，接口1—4000个码点 |
| reason | VARCHAR(2000)，需要原因的操作为1—2000个码点 |
| code/configVersion/businessNo | ASCII VARCHAR(64)，由固定配置或服务端生成；唯一键按字节比较 |
| operationId/batchId | CHAR(36)，规范小写UUID v4；前端crypto.randomUUID生成，后端验证 |
| digest | BINARY(32)；普通内容SHA-256，含密码输入的操作使用项目外秘密Key的HMAC-SHA-256，不落明文或裸密码散列 |
| 图片 | JPG/JPEG/PNG，扩展名/MIME/实际可解码内容一致；1—5,000,000字节；每次业务提交新增至多5张 |

## 2. 唯一业务枚举

| 名称 | 编码及含义 |
| --- | --- |
| Role | REPORTER报修用户；TECHNICIAN维修人员；ADMIN预置管理员 |
| ReviewStatus | PENDING待审核；REJECTED审核未通过；APPROVED审核通过；非维修人员为null |
| RepairRequestStatus | PENDING_REVIEW待审核；NEEDS_SUPPLEMENT退回待补充；APPROVED已审核通过；WITHDRAWN已撤回；DUPLICATE_CLOSED重复关闭 |
| WorkOrderStatus | PENDING_ACCEPT待接单；REASSIGN_PENDING待重派；IN_PROGRESS处理中；PENDING_ACCEPTANCE待验收；COMPLETED已完成 |
| LocationMode | STRUCTURED有显式位置；LEGACY_TEXT旧文字记录。新请求客户端不能传此字段 |
| BuildingType | DORMITORY宿舍；TEACHING教学楼；CANTEEN食堂；GYM体育馆 |
| LocationType | ROOM房间；AREA楼层区域 |
| LocationUse | DORM_ROOM、DORM_LOBBY、DORM_DUTY、CLASSROOM、OFFICE、TEACHING_LOBBY、RESTROOM_MALE、RESTROOM_FEMALE、CANTEEN_AREA、GYM_COURT、GYM_GALLERY |
| FacilityType | BUNK_DESK、TOILET、SHOWER、BASIN、AIR_CONDITIONER、AIR_CONDITIONER_GROUP、LIGHT_GROUP、SOCKET_GROUP、CLASS_DESK_CHAIR_GROUP、COMPUTER、PROJECTOR、OFFICE_DESK_CHAIR_GROUP、STANDING_AC_GROUP、BASKETBALL_HOOP_GROUP |
| AttachmentPurpose | REQUEST_FAULT首次故障图；REQUEST_SUPPLEMENT补充图；REPAIR_VOUCHER本轮凭证 |
| AttachmentState | TEMPORARY临时可绑定；BOUND正式历史；DELETING已领取删除且不可绑定；DELETED物理删除完成 |
| AssignmentOutcome | PENDING尚未接单；ACCEPTED已接单；REFUSED已拒单；REPLACED接单前已改派。它是关系结果，不是工单状态 |
| LocationCategory | FAULT_PROCESSING、PENDING_ACCEPTANCE、NEEDS_SUPPLEMENT、PENDING_REVIEW、NONE，仅派生展示，不建状态表 |

工单IN_PROGRESS且current_round>1为本轮返工；返工不是第六种工单状态。enabled为BOOLEAN且CHECK IN(0,1)，不把审核资格或故障阶段塞进启停用字段。

设施允许集合：DORM_ROOM为BUNK_DESK/TOILET/SHOWER/BASIN/AIR_CONDITIONER/LIGHT_GROUP/SOCKET_GROUP（床桌两组，合计8项）；CLASSROOM为CLASS_DESK_CHAIR_GROUP/AIR_CONDITIONER_GROUP/LIGHT_GROUP/SOCKET_GROUP/COMPUTER/PROJECTOR（6项）；OFFICE为OFFICE_DESK_CHAIR_GROUP/AIR_CONDITIONER/LIGHT_GROUP/SOCKET_GROUP（4项）；CANTEEN_AREA和GYM_GALLERY为STANDING_AC_GROUP/LIGHT_GROUP（各2项）；GYM_COURT另含BASKETBALL_HOOP_GROUP（3项）。其余LocationUse均reportable=false且无允许台账集合；分组实物不另拆台账。

## 3. 表及字段

### 3.1 V001：账号、会话、审核

`user_account`

| 字段 | 类型/约束 |
| --- | --- |
| login_name | VARCHAR(30)，上述账号排序规则，UNIQUE uk_user_login |
| name、phone、password_hash | VARCHAR(100)、CHAR(11)、VARCHAR(255)；phone UNIQUE uk_user_phone |
| role、enabled | VARCHAR(16) Role；BOOLEAN |
| review_status | VARCHAR(16)可空；TECHNICIAN必须为ReviewStatus，其他角色必须null |
| review_attempt | INT，维修人员≥1，其他为0；与当前审核申请对应 |
| provision_key | ASCII VARCHAR(64)可空，UNIQUE；仅预置管理员使用固定admin键，防重复初始化 |
| version、updated_at | 公共Version、Instant |

`login_session`

| 字段 | 类型/约束 |
| --- | --- |
| user_id | BIGINT→user_account.id |
| token_digest | BINARY(32)，UNIQUE uk_session_digest；只存SHA-256摘要 |
| issued_at、expires_at | Instant；CHECK expires_at>issued_at；应用固定expires_at=issued_at+8小时 |
| revoked_at | Instant可空；null代表尚未撤销，不代表尚未到期 |
| revoke_reason | VARCHAR(32)可空；LOGOUT/PASSWORD_CHANGED/PASSWORD_RESET/ACCOUNT_DISABLED |

不存明文Token，不设置last_access续期字段。查询使用token_digest；撤销按user_id。Cookie名称与CSRF见接口契约。

`registration_review`

| 字段 | 类型/约束 |
| --- | --- |
| user_id、attempt | BIGINT→user_account.id；INT≥1；UNIQUE uk_review_attempt(user_id,attempt) |
| name_snapshot、phone_snapshot、submitted_at | VARCHAR(100)、CHAR(11)、Instant；提交快照不可覆盖 |
| status | VARCHAR(16) ReviewStatus；初始PENDING |
| reviewed_by、reviewed_at、reason | BIGINT→user_account.id可空；Instant可空；VARCHAR(2000)可空 |
| version | Version；PENDING无审核人/时间；REJECTED必须非空原因；已审核记录不可再次决定 |

`account_event`

| 字段 | 类型/约束 |
| --- | --- |
| actor_user_id、target_user_id | BIGINT→user_account.id；本人/管理员由后端决定 |
| event_type、occurred_at | VARCHAR(32)：PROFILE_UPDATED/PASSWORD_CHANGED/PASSWORD_RESET/ENABLED/DISABLED/REVIEW_RESUBMITTED/REVIEW_DECIDED；Instant |
| review_id | BIGINT→registration_review.id可空 |
| reason | VARCHAR(2000)可空；不保存密码、确认密码或随机密码 |
| operation_type、operation_id、content_digest | VARCHAR(32)可空、CHAR(36)可空、BINARY(32)可空；成组存在；operation_type采用接口OperationType |
| result_version、result_resource_id | INT可空、BIGINT可空；成功回执元数据 |

账号事件保存相关账号用例的重试标记和历史；不新增通用工作流/JSON审计框架。含秘密的操作用HMAC摘要；随机密码只在首次成功响应临时返回，事件不提供明文回放。

### 3.2 V002：空间、设备及初始化记录

`campus_building`

| 字段 | 类型/约束 |
| --- | --- |
| code、name、type | ASCII VARCHAR(64) UNIQUE；VARCHAR(100)；VARCHAR(16) BuildingType |
| display_order、config_version | INT≥0；ASCII VARCHAR(64) |

`campus_floor`

| 字段 | 类型/约束 |
| --- | --- |
| building_id、level | BIGINT→campus_building.id；INT≥1；UNIQUE uk_floor_level(building_id,level) |
| code、name、display_order、config_version | ASCII VARCHAR(64) UNIQUE；VARCHAR(100)；INT≥0；ASCII VARCHAR(64) |

`campus_location`

| 字段 | 类型/约束 |
| --- | --- |
| floor_id、code | BIGINT→campus_floor.id；ASCII VARCHAR(64) UNIQUE |
| type、space_use | VARCHAR(16) LocationType；VARCHAR(32) LocationUse |
| name、reportable、display_order | VARCHAR(100)；BOOLEAN；INT≥0 |
| config_version、version、updated_at | ASCII VARCHAR(64)；Version；Instant |

`equipment`

| 字段 | 类型/约束 |
| --- | --- |
| equipment_no、name | ASCII VARCHAR(64) UNIQUE；VARCHAR(100) |
| location_id、address_text | BIGINT→campus_location.id可空（仅旧台账）；VARCHAR(500) |
| facility_type、enabled | VARCHAR(32) FacilityType；BOOLEAN |
| initialization_key | ASCII VARCHAR(64)可空 UNIQUE；幂等初始台账，不覆盖后期资料 |
| version、updated_at | Version；Instant |

新台账必须有范围内reportable位置与该空间允许的FacilityType；新台账不能由客户端制造旧记录例外。门窗/讲台/公共空间无台账种类，投影仪可无mesh。历史设备允许显式维护位置，但未完结工单期间不能移动。

`equipment_location_history`

| 字段 | 类型/约束 |
| --- | --- |
| equipment_id、actor_user_id | BIGINT→equipment.id；BIGINT→user_account.id |
| old_location_id、new_location_id | BIGINT→campus_location.id均可空；不能同时null或相同 |
| old_address_text、new_address_text、reason | VARCHAR(500)、VARCHAR(500)、VARCHAR(2000) |
| changed_at、equipment_event_id | Instant；BIGINT→equipment_event.id |

`equipment_event`

| 字段 | 类型/约束 |
| --- | --- |
| equipment_id、actor_user_id | BIGINT→equipment.id；BIGINT→user_account.id |
| event_type、occurred_at | VARCHAR(32)：CREATED/UPDATED/ENABLED/DISABLED/LOCATION_CHANGED；Instant |
| operation_type、operation_id、content_digest、result_version | VARCHAR(32) OperationType、CHAR(36)、BINARY(32)、INT≥0 |

`location_name_history`

| 字段 | 类型/约束 |
| --- | --- |
| location_id、operator_label | BIGINT→campus_location.id；VARCHAR(100)，本地维护者标识，不伪造登录用户 |
| old_name、new_name、reason、changed_at | VARCHAR(100)、VARCHAR(100)、VARCHAR(2000)、Instant |

`campus_initialization`

| 字段 | 类型/约束 |
| --- | --- |
| config_version、config_digest | ASCII VARCHAR(64) UNIQUE；BINARY(32) |
| building_count、floor_count、location_count、reportable_count、equipment_count | INT≥0；首次分别10/41/296/257/1817 |
| initialized_at | Instant；记录首次初始快照，不当作运行时数量上限 |

本组共8表。空间代码来自T008显式配置，初始版本采用`campus-v1`；所有层序、标签和关联显式导入。初始化数量分项1392/402/18/5；重复初始化只核对兼容/补未导入项，不覆盖人工维护资料。

### 3.3 V003：附件

`attachment_file`

| 字段 | 类型/约束 |
| --- | --- |
| uploader_id、upload_operation_id | BIGINT→user_account.id；CHAR(36)；UNIQUE uk_upload(uploader_id,upload_operation_id) |
| purpose、state | VARCHAR(32) AttachmentPurpose；VARCHAR(16) AttachmentState |
| batch_id、target_request_id、target_work_order_id、target_round | CHAR(36)可空；BIGINT可空；BIGINT可空；INT可空，≥1 |
| original_name、storage_key、media_type | VARCHAR(255)展示名；ASCII VARCHAR(64) UNIQUE随机存储名；VARCHAR(32)：image/jpeg或image/png |
| byte_size、file_digest、uploaded_at、expires_at | BIGINT，1—5,000,000；BINARY(32)；Instant；Instant=上传成功+24小时 |
| bound_submission_id、bound_round_id、bound_at | BIGINT可空，V004加→repair_request_submission.id；BIGINT可空，V005加→repair_round.id；Instant可空 |
| deletion_claimed_at、deleted_at、delete_attempts | Instant可空、Instant可空、INT≥0；失败可重试物理删除 |

新报修图片用batch_id；补充用target_request_id；凭证用target_work_order_id+target_round。正式BOUND时bound_submission_id/bound_round_id恰有一个非null；同一附件不跨目标复用，文件位置不因绑定而移动。V003先建立目标列，V004/V005再加真实业务FK及完整目标CHECK；未接入的正式归属访问默认拒绝。

删除协议：锁住附件并复核过期/未关联→提交DELETING使其不可绑定→锁外删文件→记DELETED；失败保持DELETING重试。绑定只接受锁内TEMPORARY且未过期的本人正确用途/目标附件。不能把未登记的正在上传文件立即认作孤儿；上传临时文件/活跃上传保护与故障验证在T019/T020实现。

### 3.4 V004：申请、提交快照、事件

`repair_request`

| 字段 | 类型/约束 |
| --- | --- |
| request_no、reporter_id | ASCII VARCHAR(64) UNIQUE；BIGINT→user_account.id |
| equipment_name、equipment_category | VARCHAR(100)；VARCHAR(100)可空 |
| address_text、description、contact_name、contact_phone | VARCHAR(500)、TEXT、VARCHAR(100)、CHAR(11) |
| location_mode、location_id、location_name_snapshot | VARCHAR(16) LocationMode；BIGINT→campus_location.id可空；VARCHAR(500)可空 |
| status、first_submitted_at | VARCHAR(32) RepairRequestStatus；Instant，不因重提改写 |
| current_submission_id、matched_equipment_id | BIGINT→repair_request_submission.id可空（仅创建事务构造期间）；BIGINT→equipment.id可空 |
| version、updated_at | Version；Instant |

`repair_request_submission`

| 字段 | 类型/约束 |
| --- | --- |
| request_id、submission_no、actor_user_id | BIGINT→repair_request.id；INT≥1；BIGINT→user_account.id；UNIQUE uk_request_submission(request_id,submission_no) |
| equipment_name、equipment_category、address_text、description、contact_name、contact_phone | 同申请字段，提交时固化，不覆盖历史 |
| location_mode、location_id、location_name_snapshot、submitted_at | 同申请位置规则；Instant |
| operation_type、operation_id、content_digest、result_version | VARCHAR(32)：CREATE_REQUEST/SUPPLEMENT_REQUEST；CHAR(36)；BINARY(32)；INT≥0 |

`repair_request_event`

| 字段 | 类型/约束 |
| --- | --- |
| request_id、actor_user_id、submission_id | BIGINT→repair_request.id；BIGINT→user_account.id；BIGINT→repair_request_submission.id可空 |
| event_type、from_status、to_status、reason、occurred_at | VARCHAR(32)：SUBMITTED/SUPPLEMENTED/RETURNED/WITHDRAWN/DUPLICATE_CLOSED/APPROVED_ASSIGNED；前状态可空；后状态；VARCHAR(2000)可空；Instant |
| evidence_equipment_id | BIGINT→equipment.id可空；重复关闭保存匹配依据，不构成审核通过 |
| operation_type、operation_id、content_digest | VARCHAR(32)、CHAR(36)、BINARY(32)均可空且成组；提交已有submission标记的事件不重复保存另一标记 |
| result_resource_type、result_resource_id、result_version、result_round | VARCHAR(32)、BIGINT、INT、INT均可空；成功回执关联 |

STRUCTURED必须location_id和名称快照非null；LEGACY_TEXT必须location_id和快照null，保留address_text。模式由服务器按记录来源固定；补充旧文字申请不偷偷转为STRUCTURED。当前申请与current_submission的循环FK在创建两表后增加：先插申请（指针null），再插首提交，再回写指针，同事务完成；提交前Service必须确认指针非null。MySQL没有延迟FK，不能将构造期间的指针声明NOT NULL并伪称可插入。复合FK(request.id,current_submission_id)→submission(request_id,id)保证快照属于本申请。

### 3.5 V005：工单、分派、轮次、进度、事件

`work_order`

| 字段 | 类型/约束 |
| --- | --- |
| work_order_no、request_id、equipment_id、reporter_id | ASCII VARCHAR(64) UNIQUE；BIGINT→repair_request.id UNIQUE uk_order_request；BIGINT→equipment.id可空（V006）；BIGINT→user_account.id |
| location_mode、location_id、location_name_snapshot | 从审核时申请固化，规则同申请；不得通过设备位置给旧文字申请补绑 |
| status、version、current_round、updated_at | WorkOrderStatus、Version、INT≥0、Instant |
| current_assignment_id、assigned_technician_id、accepted_technician_id | BIGINT→work_order_assignment.id可空；BIGINT→user_account.id可空；BIGINT→user_account.id可空 |
| accepted_at、completed_at | Instant均可空；首次接单固定，只有最终验收通过才completed_at非null |
| unfinished_equipment_id | BIGINT GENERATED ALWAYS AS (CASE WHEN status<>'COMPLETED' THEN equipment_id ELSE NULL END) STORED；UNIQUE uk_order_unfinished_equipment |

REASSIGN_PENDING无当前负责人；COMPLETED保留实际维修人/最后分派关系作历史。接单前current_round=0且accepted_*为空；接单后负责人固定且current_round≥1；已完成才completed_at非null。这些同列关系通过CHECK与Service复核；跨表身份/状态关系在锁内校验，不能以CHECK假装能检查别表。

`work_order_assignment`

| 字段 | 类型/约束 |
| --- | --- |
| work_order_id、assignment_no、technician_id、assigned_by | BIGINT→work_order.id；INT≥1；BIGINT→user_account.id；BIGINT→user_account.id；UNIQUE uk_assignment_no(work_order_id,assignment_no) |
| visible_submission_id、assigned_at、ended_at | BIGINT→repair_request_submission.id，当时可见原报修材料；Instant；Instant可空 |
| outcome、reason、decision_at | AssignmentOutcome；VARCHAR(2000)可空；Instant可空 |

拒单/改派结束旧分派并记录原因；ACCEPTED关系证明实际维修归属。仅曾分派者的详情投影仅限visible_submission、当次分派/本人拒单改派和相应原图，绝不放行后续全部维修材料。

`repair_round`

| 字段 | 类型/约束 |
| --- | --- |
| work_order_id、round_no、technician_id | BIGINT→work_order.id；INT≥1；BIGINT→user_account.id；UNIQUE uk_round_no(work_order_id,round_no) |
| started_at、submitted_at、result_text | Instant；Instant可空；TEXT可空，提交时必填 |
| acceptance_passed、acceptance_reason、accepted_by、acceptance_at | BOOLEAN可空；VARCHAR(2000)可空；BIGINT→user_account.id可空；Instant可空 |

第一次接单开round1；验收失败保存当前轮验收，并在原工单开round+1，started_at等于失败验收时点；工单负责人不变。本轮提交必须有本轮新结果/新凭证，不复用旧轮附件。每轮submitted_at≥started_at，验收时点≥submitted_at；未提交/未验收的字段为null。

`repair_progress`

| 字段 | 类型/约束 |
| --- | --- |
| work_order_id、round_id、technician_id | BIGINT→work_order.id；BIGINT→repair_round.id；BIGINT→user_account.id；Service/FK组合保证轮次属于工单 |
| content、recorded_at | TEXT，1—4000；Instant |

进度独立于凭证；新增进度与对应work_order_event同事务，重试标记存事件，不生成重复进度。

`work_order_event`

| 字段 | 类型/约束 |
| --- | --- |
| work_order_id、actor_user_id、assignment_id、round_id、progress_id | BIGINT→work_order.id、user_account.id；后三者可空且分别→对应表 |
| event_type、from_status、to_status、reason、occurred_at | VARCHAR(32)：FIRST_ASSIGNED/ACCEPTED/REFUSED/ASSIGNED/PROGRESS_ADDED/RESULT_SUBMITTED/ACCEPTANCE_FAILED/COMPLETED；前状态可空；后状态；原因可空；Instant |
| operation_type、operation_id、content_digest | VARCHAR(32)、CHAR(36)、BINARY(32)，除首次派单已由申请事件标记外成组存在 |
| result_resource_id、result_version、result_round | BIGINT、INT≥0、INT≥0，成功回执 |

## 4. 索引及迁移次序

| 范围 | 必须的索引/约束 |
| --- | --- |
| 账号 | uk_user_login、uk_user_phone、uk_user_provision；role/review_status/enabled用于资格查询 |
| 会话 | token_digest唯一；user_id+revoked_at用于全会话撤销；expires_at用于清理 |
| 审核 | user_id+attempt唯一，status+submitted_at用于待审核列表 |
| 空间 | building.code、floor.code、location.code分别唯一；building_id+level唯一；floor_id+display_order |
| 设备 | equipment_no/initialization_key唯一；location_id+enabled；设备历史equipment_id+changed_at+id |
| 申请 | request_no唯一；reporter_id+status+created_at；location_id+status；提交request_id+submission_no唯一；事件request_id+occurred_at+id |
| 工单 | work_order_no、request_id、unfinished_equipment_id分别唯一；assigned_technician_id+status；reporter_id+status；location_id+status；equipment_id+created_at |
| 历史 | assignment(work_order_id,assignment_no)、round(work_order_id,round_no)唯一；submission(request_id,id)、assignment(work_order_id,id)、round(work_order_id,id)补唯一供复合FK；work_order(id,current_assignment_id)与progress(work_order_id,round_id)确保关系属于同工单；assignment(technician_id,assigned_at)、progress(round_id,recorded_at,id) |
| 附件 | uploader_id+upload_operation_id唯一；state+expires_at；bound_submission_id、bound_round_id；storage_key唯一 |
| 提交重试 | 各承载表UNIQUE(actor_user_id,operation_type,operation_id)；operation类型只指定一个承载表，避免跨表歧义 |
| 统计/推荐 | completed_at+accepted_technician_id；created_at+equipment_id；分派technician_id+assigned_at；其余按实际SQL和T067证据加索引 |

迁移按V001→V002→V003→V004→V005→V006追加；跨版本目标FK在目标表创建后追加。V001不创建全部未来业务表，V002在T007、V003在T019、V004在T022、V005在T025执行，V006落实用户2026-10-15首次派单无需台账的修订。T067只在有证据需要时使用届时下一未用迁移版本增加性能索引。已应用迁移不修改，不调用flyway clean，不使用另一套自动建表工具。

## 5. 幂等、锁顺序与事务

统一锁顺序：参与业务的`user_account`按数值ID升序→`equipment`按ID→`repair_request`→`work_order`→`repair_round`→`attachment_file`按ID升序。多个同类对象按ID升序；只锁必要子集，任何路径不逆序。

```mermaid
flowchart LR
    U[用户按ID升序] --> E[设备按ID升序]
    E --> Q[申请]
    Q --> W[工单]
    W --> N[当前轮次]
    N --> A[附件按ID升序]
    A --> V[锁内再次校验]
    V --> C[业务和事件及标记同事务提交]
```

加锁前只做非锁定读取以确定必要ID，进入事务按上述顺序锁定并重新核实映射/资格/状态；若旧读得到的ID集合变化，回滚后从头核对，不在末尾追加前序用户锁。死锁只允许整个事务有限重试同operationId，不在未知结果时生成新标识。配置名称修正为本地维护，不混入业务锁链。

| 用例 | 同一事务内必须完成 |
| --- | --- |
| 注册/重新申请 | 用户唯一性/资料/审核状态与提交快照；重提次数+1和账号事件 |
| 注册审核 | 锁管理员及目标账号→当前审核记录；账号启用且attempt/version相同→写决定与事件；不自动启用 |
| 登录/改密/停用 | 共同锁用户，登录锁内重查哈希/启用；改密、重置、停用写账号+撤销全部旧会话 |
| 新申请/补充 | 锁报修人→原申请（补充）→附件；校验位置；提交快照/当前申请/事件/附件绑定/重试标记一次提交 |
| 审核并首次派单 | 锁报修人、拟维修人员及管理员→设备→申请→新工单→附件必要子集；复核资格/位置/唯一约束；通过+工单+分派+事件一次提交 |
| 接单/拒单/改派 | 锁相关人员→设备必要子集→申请必要子集→工单→轮次；旧分派结束/新关系/状态/事件原子提交；接单开round1 |
| 进度/结果/验收 | 锁报修人/实际维修者→工单→当前轮次→附件；version/round/责任复核，事件与结果/下一轮/最终时间同提交 |
| 台账移动/停用 | 与审核共同锁设备；再次查未完结工单；移动、版本和位置历史原子提交；旧工单位置不动 |
| 账号停用 | 锁目标用户后检查全部相关未完结业务；产生新业务或分派的路径共同锁同一用户 |
| 附件绑定/删除 | 共同锁附件；TEMPORARY→BOUND与业务一起提交；TEMPORARY→DELETING提交后锁外删文件，互斥 |

提交摘要包含operation类型、资源ID、version/round和全部业务字段；按固定字段顺序、UTF-8、显式null规范化，附件ID数组按数值ID排序视为集合。相同操作者+操作+operationId且摘要相同返回原成功回执；不同内容返回409 OPERATION_CONTENT_MISMATCH。成功标记、原结果和业务记录同事务，失败不留“成功”标记。

同标识重试首先核对当前身份及原记录的当前查看权限；不能因重放成功而再次写事件，也不能把另一账号的标识结果返回。首次并发重复会由唯一索引/共同目标锁协调；冲突后重新读取已提交标记，只有摘要和权限均匹配才返回原结果。查不到标记只能表示未确认，不等于事务失败。

## 6. 状态允许表

| 申请操作 | 原状态 | 后状态 | 角色/必要条件 |
| --- | --- | --- | --- |
| 首次提交 | 无 | PENDING_REVIEW | REPORTER；合法可报修位置、1—5故障图 |
| return | PENDING_REVIEW | NEEDS_SUPPLEMENT | ADMIN；非空原因；材料/位置/范围问题 |
| supplement | NEEDS_SUPPLEMENT | PENDING_REVIEW | 原REPORTER；保留编号/首次时间/历史 |
| withdraw | PENDING_REVIEW或NEEDS_SUPPLEMENT | WITHDRAWN | 原REPORTER；无工单 |
| duplicate-close | PENDING_REVIEW | DUPLICATE_CLOSED | ADMIN；具体设备重复依据+原因 |
| approve-and-assign | PENDING_REVIEW | APPROVED | ADMIN；设备启用/位置一致/无未完结工单；合格人员；同时建待接单工单 |

| 工单操作 | 原状态 | 后状态 | 角色/必要条件 |
| --- | --- | --- | --- |
| accept | PENDING_ACCEPT | IN_PROGRESS | 当前合格TECHNICIAN；固定负责人、accepted_at和round1 |
| refuse | PENDING_ACCEPT | REASSIGN_PENDING | 当前TECHNICIAN；非空原因，结束分派 |
| assign（接单前） | PENDING_ACCEPT | PENDING_ACCEPT | ADMIN；不同合格人员+非空原因 |
| assign（重派） | REASSIGN_PENDING | PENDING_ACCEPT | ADMIN；合格人员+原因，沿用工单 |
| progress | IN_PROGRESS | IN_PROGRESS | 当前实际维修者；version/round一致；独立进度 |
| submit-result | IN_PROGRESS | PENDING_ACCEPTANCE | 当前实际维修者；本轮结果+1—5本轮新凭证 |
| acceptance(false) | PENDING_ACCEPTANCE | IN_PROGRESS | 原REPORTER；原因必填；原工单同负责人，round+1 |
| acceptance(true) | PENDING_ACCEPTANCE | COMPLETED | 原REPORTER；保存最终completed_at |

上表保留需求的七条工单状态转换；progress是状态不变的记录操作，不新增状态转换。范围外申请只能退回纠正/撤回，不以重复关闭或新增台账绕过范围。

## 7. 权限投影、派生数量与统计

- REPORTER仅本人申请及关联工单；TECHNICIAN当前任务和本人合法历史；ADMIN完整管理且不能代验收。停用会话无权读取，未合格维修人员只有几何目录/本人审核/允许账号能力。
- allowedActions来自后端当前角色+资格+归属+状态+version/round判断；前端菜单不构成授权。
- 曾拒单/被接单前改派者只投影对应分派期间材料；详情、附件、计数与列表用同一策略，不返回后续轮次或其他人员成果。
- 空间汇总先权限过滤后计数；只计STRUCTURED且未完结事项。APPROVED申请只计其工单；current_round>1的IN_PROGRESS只计返工，不同时计普通处理中。
- 每位置主类别优先FAULT_PROCESSING→PENDING_ACCEPTANCE→NEEDS_SUPPLEMENT→PENDING_REVIEW。可见总数为七阶段计数之和，无权记录不能影响颜色/总数；为0不返回暗示他人故障的信息。
- 推荐W/I/R/A互斥，L=W+2I+3R+0.5A；D按上海自然日最终验收完成的独立工单计数；L→D→lastAssignedAt（null最前）→人员BIGINT ID升序，推荐不预留/写入业务。
- 月度完成量/平均时长用completed_at；故障频次用工单created_at；全部当前状态分布不随month变化。响应=accepted_at-request.first_submitted_at；维修时长=sum(round.submitted_at-round.started_at)，含返工、不含待验收。
- 先按工单聚合轮次，再JOIN其他数据，附件/进度不能扩大数量；无样本平均null，异常时间单独标记，不按0或静默丢弃。只读聚合用REPEATABLE READ一致快照，查阅明细是新的查询时点。

## 8. 需求支撑与待验证边界

| 验收编号 | 字典/约定支撑 |
| --- | --- |
| REQ-01 | §1、2、3.1、5：账号/审核/会话/密码/撤销 |
| REQ-02 | §3.5、7：分派历史、实际维修归属、账号去重 |
| REQ-03 | §3.2：10/41/296/257/1817、初始化版本/摘要 |
| REQ-04 | §2 FacilityType、3.2：1392/402、72教学空调、投影仪无mesh |
| REQ-05 | §3.2：食堂18、体育馆5，AREA身份与设施分组 |
| REQ-06 | §1、2、3.2、3.4：显式位置与类别自由文本 |
| REQ-07 | §3.4、6：五状态、原申请重提、范围外退回 |
| REQ-08 | §3.2、3.5、5：台账/位置/资格/未完结唯一 |
| REQ-09 | §3.5、6：接单责任、本轮凭证、原工单返工 |
| REQ-10 | §7：单位置/单业务计数、主类别、返工互斥 |
| REQ-11 | §3.5、7：分派材料快照与同权限附件投影 |
| REQ-12 | 接口契约§8：读时点、身份/目标代次、草稿保护 |
| REQ-13 | §5和接口契约§8：原operationId、不确定结果、内存草稿 |
| REQ-14 | §3.2、3.4、3.5、5：旧文字/位置历史/锁定工单位置 |
| REQ-15 | §7：W/I/R/A、L/D、稳定排序 |
| REQ-16 | §5、7：一致快照、最终资格再核对、无自动派单 |
| REQ-17 | §1、3.3、3.5、7：图片/凭证/时长/去重 |
| REQ-18 | 接口契约§8：渲染生命周期、局部失败、迟到处理 |
| REQ-19 | 接口契约§8：普通列表/选择器等价入口；本字典不以mesh作授权 |
| REQ-20 | §1、3.2和接口契约§8：配置版本/证据分层；真实性能在B12 |

本轮仅静态设计与覆盖检查；表数21（V001×4、V002×8、V003×1、V004×3、V005×5）。未执行DDL、FK/CHECK/生成列/并发/迁移/数据库隔离验证；这些在对应授权T项用真实MySQL8.4完成。


## B09 维护行为补充（2026-10-25）

沿用V001—V006，无新增表/迁移。`admin-reset`仅显式非Web维护命令，检查目标数据库和预置管理员，保留ID/关联历史；哈希更新与旧会话撤销同事务，本次控制台一次性输出新密码。`correct-location-name`检查locationId/version，保留稳定ID/code，写名称历史和维护者/原因；新提交读当前名称，旧申请/工单快照不覆盖。参数与运行方式见README本地维护。普通启动不调用维护命令；维护命令执行后关闭上下文，避免调度器阻止进程退出。

账号操作回执沿用PROFILE_UPDATE、PASSWORD_CHANGE、PASSWORD_RESET、ACCOUNT_ENABLE、ACCOUNT_DISABLE；事件名称不替代操作类型。秘密不写回执/事件，普通重置明文仅首次响应，已提交重放不会再次提供明文。
