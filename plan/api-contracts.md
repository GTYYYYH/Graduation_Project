# API、前端类型与模块衔接契约 v1

日期：2026-10-12，Asia/Shanghai；T002/B01契约，B02增量接入。依据当前requirements、architecture、tasks §2.2；详细字段与表见[data-dictionary.md](data-dictionary.md)。认证/CSRF、注册、目录及资格审核已实现并内部验证，等待B02用户验收；其余接口仍为后续批次契约。

## 1. 公共协议与类型

2026-10-15用户直接修订（优先于旧计划的首次派单设备必选条款）：正常首次派单只选择合格维修人员，客户端不发送equipmentId。接口保留equipmentId可选字段兼容既有明确关联/UNKNOWN操作；显式传入时仍校验原设备规则。无关联工单的equipment明确返回null，原申请位置/原文/图片与权限照常保留。重复关闭仍要求具体台账和原因，接单/维修/验收仍属B05。

- API前缀`/api`；JSON UTF-8。Id是正Long范围的十进制字符串；数量/total为非负安全整数Number，不能对所有Long做全局字符串序列化。
- body中`?`可省略，`| null`可显式null；没有这两种标记的字段必填且非null。未知JSON字段返回400，不接受客户端角色、操作者、时间、状态、快照或兼容模式。
- Query可选字段省略表示不过滤，不把字符串`null`解释成空值。page默认1、pageSize默认20且1—100；非法值400。所有枚举为数据字典唯一编码。
- 普通结果200，首次创建201；成功code=OK，data按下表；空结果用空数组/0或null，不伪造记录。图片成功为二进制，错误仍统一JSON。
- 表格中的“登录”指启用的有效会话；“合格TECHNICIAN”指启用+APPROVED。Service始终再校验归属、状态、资格、version/round。

```typescript
type Id = string;
type Instant = string; // ISO8601 UTC，毫秒和Z
type UUID = string; // 规范小写UUID v4
type Role = 'REPORTER' | 'TECHNICIAN' | 'ADMIN';
type ReviewStatus = 'PENDING' | 'REJECTED' | 'APPROVED';
type RepairRequestStatus = 'PENDING_REVIEW' | 'NEEDS_SUPPLEMENT' | 'APPROVED' | 'WITHDRAWN' | 'DUPLICATE_CLOSED';
type WorkOrderStatus = 'PENDING_ACCEPT' | 'REASSIGN_PENDING' | 'IN_PROGRESS' | 'PENDING_ACCEPTANCE' | 'COMPLETED';
type LocationMode = 'STRUCTURED' | 'LEGACY_TEXT';
type BuildingType = 'DORMITORY' | 'TEACHING' | 'CANTEEN' | 'GYM';
type LocationType = 'ROOM' | 'AREA';
type LocationUse = 'DORM_ROOM' | 'DORM_LOBBY' | 'DORM_DUTY' | 'CLASSROOM' | 'OFFICE' | 'TEACHING_LOBBY' | 'RESTROOM_MALE' | 'RESTROOM_FEMALE' | 'CANTEEN_AREA' | 'GYM_COURT' | 'GYM_GALLERY';
type FacilityType = 'BUNK_DESK' | 'TOILET' | 'SHOWER' | 'BASIN' | 'AIR_CONDITIONER' | 'AIR_CONDITIONER_GROUP' | 'LIGHT_GROUP' | 'SOCKET_GROUP' | 'CLASS_DESK_CHAIR_GROUP' | 'COMPUTER' | 'PROJECTOR' | 'OFFICE_DESK_CHAIR_GROUP' | 'STANDING_AC_GROUP' | 'BASKETBALL_HOOP_GROUP';
type AttachmentPurpose = 'REQUEST_FAULT' | 'REQUEST_SUPPLEMENT' | 'REPAIR_VOUCHER';
type AttachmentState = 'TEMPORARY' | 'BOUND' | 'DELETING' | 'DELETED';
type AssignmentOutcome = 'PENDING' | 'ACCEPTED' | 'REFUSED' | 'REPLACED';
type LocationCategory = 'FAULT_PROCESSING' | 'PENDING_ACCEPTANCE' | 'NEEDS_SUPPLEMENT' | 'PENDING_REVIEW' | 'NONE';
interface FieldError { field: string; code: string; message: string; }
interface ApiResponse<T> { code: string; message: string; data: T | null; requestId: string; errors?: FieldError[]; }
interface PageResult<T> { records: T[]; total: number; page: number; pageSize: number; }
interface PageQuery { page?: number; pageSize?: number; keyword?: string; }
interface NewMutation { operationId: UUID; }
interface MutationMeta extends NewMutation { version?: number; round?: number; }
interface VersionedMutation extends NewMutation { version: number; }
interface RoundMutation extends VersionedMutation { round: number; }
interface AccountReferenceVo { id: Id; name: string; }
interface LocationReferenceVo { id: Id; code: string; name: string; floorId: Id; }
interface EquipmentReferenceVo { id: Id; equipmentNo: string; name: string; locationId: Id | null; }
type ResourceType = 'ACCOUNT' | 'REGISTRATION_REVIEW' | 'EQUIPMENT' | 'REPAIR_REQUEST' | 'WORK_ORDER' | 'REPAIR_PROGRESS' | 'REPAIR_ROUND';
interface MutationReceiptVo {
  operationId: UUID; resourceType: ResourceType; resourceId: Id;
  resultVersion: number | null; resultRound: number | null; committedAt: Instant;
  relatedResourceType: ResourceType | null; relatedResourceId: Id | null;
}
```

Id/UUID/日期/长度的运行时校验不由TypeScript别名替代。排序字段使用服务端白名单；不允许直接传SQL字段或任意sort表达式。requestId由后端生成并附于成功/失败结果；不回传SQL、堆栈、磁盘路径或敏感输入。

## 2. 认证、本人账号、审核与人员

### 2.1 DTO与VO

```typescript
type AccountAction = 'EDIT_PROFILE' | 'CHANGE_PASSWORD' | 'RESUBMIT_REVIEW' | 'REVIEW_APPROVE' | 'REVIEW_REJECT' | 'ENABLE_ACCOUNT' | 'DISABLE_ACCOUNT' | 'RESET_PASSWORD';
interface AccountCapabilities { canBrowseCampus: boolean; canCreateRequest: boolean; canWorkOnTasks: boolean; canManage: boolean; }
interface CurrentUserVo {
  id: Id; loginName: string; name: string; phone: string; role: Role;
  enabled: boolean; reviewStatus: ReviewStatus | null; version: number;
  capabilities: AccountCapabilities;
}
interface AuthSessionVo { user: CurrentUserVo; expiresAt: Instant; }
interface CsrfVo { token: string; headerName: 'X-CSRF-TOKEN'; }
interface RegisterDto { role: 'REPORTER' | 'TECHNICIAN'; loginName: string; name: string; phone: string; password: string; confirmPassword: string; }
interface RegistrationVo { id: Id; loginName: string; role: 'REPORTER' | 'TECHNICIAN'; reviewStatus: ReviewStatus | null; }
interface LoginDto { loginName: string; password: string; }
interface ReviewSubmissionVo {
  id: Id; attempt: number; nameSnapshot: string; phoneSnapshot: string;
  submittedAt: Instant; status: ReviewStatus; version: number;
  reviewedBy: AccountReferenceVo | null; reviewedAt: Instant | null; reason: string | null;
}
interface AccountProfileVo extends CurrentUserVo { allowedActions: AccountAction[]; }
interface TechnicianReviewVo { account: AccountProfileVo; current: ReviewSubmissionVo; history: ReviewSubmissionVo[]; allowedActions: AccountAction[]; queriedAt: Instant; }
interface ResubmitReviewDto extends VersionedMutation { attempt: number; reviewVersion: number; name: string; phone: string; }
interface ReviewDecisionDto extends VersionedMutation { attempt: number; reviewVersion: number; passed: boolean; reason: string | null; }
interface UpdateProfileDto extends VersionedMutation { name: string; phone: string; }
interface ChangePasswordDto extends VersionedMutation { currentPassword: string; newPassword: string; confirmNewPassword: string; }
interface AccountStatusDto extends VersionedMutation {}
interface ResetPasswordDto extends VersionedMutation {}
type PasswordResetResultVo =
  | { kind: 'NEW_SECRET'; receipt: MutationReceiptVo; account: AccountReferenceVo; loginName: string; newPassword: string; }
  | { kind: 'ALREADY_COMMITTED'; receipt: MutationReceiptVo; account: AccountReferenceVo; loginName: string; newPassword: null; };
type PersonnelCategory = 'ALL' | 'REPORTER_UNFINISHED' | 'REPORTER_COMPLETED' | 'TECHNICIAN_UNFINISHED' | 'TECHNICIAN_COMPLETED';
interface PersonnelQuery extends PageQuery { category?: PersonnelCategory; role?: Role; reviewStatus?: ReviewStatus; enabled?: boolean; }
type RoleBusinessCounts =
  | { role: 'REPORTER'; pendingRequests: number; unfinishedOrders: number; completedOrders: number; }
  | { role: 'TECHNICIAN'; unfinishedTasks: number; completedOrders: number; };
interface PendingReviewVo { id: Id; loginName: string; name: string; phone: string; enabled: boolean; reviewAttempt: number; submittedAt: Instant; }
interface PersonnelVo extends AccountProfileVo { reviewAttempt: number; counts: RoleBusinessCounts | null; }
interface PersonnelListVo {
  records: PersonnelVo[]; page: number; pageSize: number; listTotal: number; allPeopleTotal: number;
  categoryCounts: Record<Exclude<PersonnelCategory, 'ALL'>, number>; pendingReviewCount: number; queriedAt: Instant;
}
type PersonnelHistoryCategory = 'REQUESTS' | 'RELATED_ORDERS' | 'CURRENT_TASKS' | 'HISTORICAL_ORDERS';
type BusinessRelation = 'SUBMITTER' | 'CURRENT_ASSIGNEE' | 'ACTUAL_REPAIRER' | 'COMPLETED_BY_ME' | 'REFUSED' | 'REPLACED';
interface PersonnelHistoryQuery extends PageQuery { category: PersonnelHistoryCategory; requestStatus?: RepairRequestStatus; workOrderStatus?: WorkOrderStatus; }
interface BusinessHistoryItemVo {
  resourceType: 'REPAIR_REQUEST' | 'WORK_ORDER'; id: Id; businessNo: string;
  equipmentName: string; status: RepairRequestStatus | WorkOrderStatus; createdAt: Instant;
  relations: BusinessRelation[]; canViewDetail: boolean;
}
interface PersonnelDetailVo {
  person: PersonnelVo; availableCategories: PersonnelHistoryCategory[];
  categoryTotals: Partial<Record<PersonnelHistoryCategory, number>>;
  statusCounts: Record<string, number>; records: PageResult<BusinessHistoryItemVo>; queriedAt: Instant;
}
```

### 2.2 API与权限

| 方法/路径 | 输入 | data类型 | 权限/特定规则 |
| --- | --- | --- | --- |
| GET /api/auth/csrf | 无 | CsrfVo | 公开；no-store；只返回CSRF Token，不返回登录Token |
| POST /api/auth/register | RegisterDto | RegistrationVo | 公开但必须CSRF；仅两普通角色；成功201、不自动登录 |
| POST /api/auth/login | LoginDto | AuthSessionVo | 公开但必须CSRF；成功后设置HttpOnly Cookie |
| GET /api/auth/me | 无 | AuthSessionVo | 登录；固定到期不续期 |
| POST /api/auth/logout | 无body | null | CSRF；撤销当前会话并清Cookie；已无有效会话也清Cookie，不复活会话 |
| GET /api/account/profile | 无 | AccountProfileVo | 登录；所有角色本人 |
| PATCH /api/account/profile | UpdateProfileDto | MutationReceiptVo | 本人REPORTER/ADMIN/合格TECHNICIAN；待审只读，未通过走重提 |
| POST /api/account/password | ChangePasswordDto | MutationReceiptVo | 登录本人；校验旧密码，成功撤销全部旧会话并清Cookie |
| GET /api/account/review | 无 | TechnicianReviewVo | 登录TECHNICIAN本人；历史最新在前 |
| POST /api/account/review/resubmit | ResubmitReviewDto | MutationReceiptVo | 本人TECHNICIAN、REJECTED且启用；原账号，attempt必须当前次数 |
| GET /api/admin/accounts/{id}/registration-review | Path Id | TechnicianReviewVo | ADMIN；目标TECHNICIAN；停用也可看 |
| POST /api/admin/accounts/{id}/registration-review | Path Id + ReviewDecisionDto | MutationReceiptVo | ADMIN；目标启用+PENDING，passed=false原因必填 |
| GET /api/admin/registration-reviews | PageQuery | PageResult<PendingReviewVo> | B02最小待审入口；ADMIN；仅PENDING TECHNICIAN，keyword匹配账号/姓名/手机号；含停用账号的只读详情入口 |
| GET /api/admin/personnel | PersonnelQuery | PersonnelListVo | ADMIN；先过滤再分页；分类人数独立于组合筛选 |
| GET /api/admin/personnel/{id}/history | Path Id + PersonnelHistoryQuery | PersonnelDetailVo | ADMIN；业务分类与人员列表查询分开 |
| POST /api/admin/accounts/{id}/enable | Path Id + AccountStatusDto | MutationReceiptVo | ADMIN；恢复不自动审核/复活旧会话 |
| POST /api/admin/accounts/{id}/disable | Path Id + AccountStatusDto | MutationReceiptVo | ADMIN；不自停用，全部未完结业务阻止，返回本人有权查看的阻塞编号 |
| POST /api/admin/accounts/{id}/reset-password | Path Id + ResetPasswordDto | PasswordResetResultVo | ADMIN；仅普通账号；随机密码首次响应一次，禁止事后查询 |

Cookie：`CAMPUS_SESSION`，HttpOnly，SameSite=Lax，Path=/api；本机HTTP Secure=false，未来HTTPS必须Secure=true。后端用随机≥256位Token，只存摘要；固定8小时无续期。登录/退出轮换CSRF，客户端重新GET csrf；写操作头`X-CSRF-TOKEN`。CSRF Token仅当前身份内存，身份改变清除；Pinia从不存登录Token。认证写请求UNKNOWN时暂停业务并读取me核实，me明确401后允许重新登录；每次新登录会话隔离旧请求，即使账号相同。

B02待审列表供T014详情入口使用，后续T053合并完整人员管理；不提供尚未实现的人员业务分类统计或启停用操作。

注册/登录的密码类公开接口不使用普通业务operationId查询；失败或响应丢失不能把重复账号409当成注册成功，须按实际登录/查询流程核对。密码更改的成功回执不含新密码；成功使当前会话失效。密码重置的同标识回放仅返回ALREADY_COMMITTED+null，不能再次生成密码，也不能从操作核对API读回明文；遗失密码只能明确新标识再次重置。

## 3. 校园目录、台账

```typescript
type EquipmentAction = 'UPDATE_EQUIPMENT' | 'ENABLE_EQUIPMENT' | 'DISABLE_EQUIPMENT' | 'VIEW_EQUIPMENT_HISTORY';
interface BuildingVo { id: Id; code: string; name: string; type: BuildingType; displayOrder: number; }
interface FloorVo { id: Id; code: string; buildingId: Id; level: number; name: string; displayOrder: number; }
interface LocationVo { id: Id; code: string; floorId: Id; type: LocationType; use: LocationUse; name: string; reportable: boolean; canCreateRequest: boolean; displayOrder: number; }
interface CatalogVo { configVersion: string; buildings: BuildingVo[]; floors: FloorVo[]; locations: LocationVo[]; }
interface EquipmentQuery extends PageQuery { buildingId?: Id; floorId?: Id; locationId?: Id; enabled?: boolean; facilityType?: FacilityType; }
interface EquipmentSaveDto extends NewMutation { version?: number; name: string; locationId: Id; addressText: string; facilityType: FacilityType; reason?: string | null; }
interface EquipmentStatusDto extends VersionedMutation {}
interface EquipmentVo extends EquipmentReferenceVo {
  addressText: string; facilityType: FacilityType; enabled: boolean; version: number;
  currentWorkOrder: WorkOrderSummaryVo | null; allowedActions: EquipmentAction[];
}
interface EquipmentHistoryQuery extends PageQuery { status?: WorkOrderStatus; }
interface EquipmentDetailVo { equipment: EquipmentVo; history: PageResult<WorkOrderSummaryVo>; queriedAt: Instant; }
```

| 方法/路径 | 输入 | data类型 | 权限/特定规则 |
| --- | --- | --- | --- |
| GET /api/campus/catalog | 无 | CatalogVo | 登录启用账号；未合格维修人员可浏览；canCreateRequest仅REPORTER |
| GET /api/equipment | EquipmentQuery | PageResult<EquipmentVo> | ADMIN；无管理员新建普通账号功能混入 |
| POST /api/equipment | EquipmentSaveDto | MutationReceiptVo | ADMIN；201；version必须省略；编号后端生成，默认启用，范围内设施 |
| GET /api/equipment/{id} | Path Id + EquipmentHistoryQuery | EquipmentDetailVo | ADMIN；当前工单单独展示，历史列表分页 |
| PATCH /api/equipment/{id} | Path Id + EquipmentSaveDto | MutationReceiptVo | ADMIN；version必填；未完结工单时不能变locationId；移动原因必填，保存历史 |
| POST /api/equipment/{id}/enable | Path Id + EquipmentStatusDto | MutationReceiptVo | ADMIN；不改工单 |
| POST /api/equipment/{id}/disable | Path Id + EquipmentStatusDto | MutationReceiptVo | ADMIN；停止新业务，既有工单继续 |
| GET /api/equipment/{id}/work-orders | Path Id + EquipmentHistoryQuery | PageResult<WorkOrderSummaryVo> | ADMIN；复用同一历史权限/查询逻辑 |

新增equipmentNo和initializationKey不由前端填写；后续补录无初始key。旧只有文字设备在显式维护中选择合法locationId并保留旧地址历史，不解析原文。普通报修无需先选设备，不向表单提供强制设施清单。

## 4. 报修申请

```typescript
type RequestAction = 'SUPPLEMENT_REQUEST' | 'WITHDRAW_REQUEST' | 'RETURN_REQUEST' | 'CLOSE_DUPLICATE' | 'APPROVE_AND_ASSIGN' | 'VIEW_LINKED_ORDER';
interface CreateRepairRequestDto extends NewMutation {
  locationId: Id; equipmentName: string; equipmentCategory?: string | null;
  addressText: string; description: string; contactName: string; contactPhone: string;
  attachmentIds: Id[];
}
interface SupplementRequestDto extends VersionedMutation {
  locationId?: Id; equipmentName: string; equipmentCategory?: string | null;
  addressText: string; description: string; contactName: string; contactPhone: string;
  attachmentIds: Id[];
}
interface ReturnRequestDto extends VersionedMutation { reason: string; }
interface WithdrawRequestDto extends VersionedMutation {}
interface DuplicateCloseDto extends VersionedMutation { equipmentId: Id; reason: string; }
interface ApproveAndAssignDto extends VersionedMutation { equipmentId?: Id | null; technicianId: Id; confirmPreviouslyRefused: boolean; }
interface RepairRequestQuery extends PageQuery { status?: RepairRequestStatus; locationId?: Id; }
interface WorkOrderLinkVo { id: Id; workOrderNo: string; status: WorkOrderStatus; }
interface RepairRequestSummaryVo {
  id: Id; requestNo: string; equipmentName: string; status: RepairRequestStatus;
  locationMode: LocationMode; location: LocationReferenceVo | null; locationNameSnapshot: string | null;
  firstSubmittedAt: Instant; createdAt: Instant; version: number;
  workOrder: WorkOrderLinkVo | null; allowedActions: RequestAction[];
}
interface RepairRequestSubmissionVo {
  id: Id; submissionNo: number; equipmentName: string; equipmentCategory: string | null;
  locationMode: LocationMode; location: LocationReferenceVo | null; locationNameSnapshot: string | null;
  addressText: string; description: string; contactName: string; contactPhone: string;
  attachments: AttachmentVo[]; submittedAt: Instant;
}
interface RepairRequestEventVo {
  id: Id; type: 'SUBMITTED' | 'SUPPLEMENTED' | 'RETURNED' | 'WITHDRAWN' | 'DUPLICATE_CLOSED' | 'APPROVED_ASSIGNED';
  fromStatus: RepairRequestStatus | null; toStatus: RepairRequestStatus;
  actor: AccountReferenceVo; reason: string | null; occurredAt: Instant;
}
interface RepairRequestDetailVo extends RepairRequestSummaryVo {
  reporter: AccountReferenceVo; currentSubmission: RepairRequestSubmissionVo;
  submissions: RepairRequestSubmissionVo[]; events: RepairRequestEventVo[];
  matchedEquipment: EquipmentReferenceVo | null; queriedAt: Instant;
}
```

| 方法/路径 | 输入 | data类型 | 权限/特定规则 |
| --- | --- | --- | --- |
| POST /api/repair-requests | CreateRepairRequestDto | MutationReceiptVo | REPORTER；201；合法可报修位置，首次1—5张故障图；不建工单 |
| GET /api/repair-requests | RepairRequestQuery | PageResult<RepairRequestSummaryVo> | REPORTER本人/ADMIN；先权限过滤 |
| GET /api/admin/repair-requests | RepairRequestQuery | PageResult<RepairRequestSummaryVo> | 仅ADMIN；2026-10-14用户待审核数量提示/审核列表专用alias，复用同一列表规则，防止跨标签Cookie身份变化被误当作管理员全局计数 |
| GET /api/repair-requests/{id} | Path Id | RepairRequestDetailVo | 本人/ADMIN/合法关联TECHNICIAN；按分派材料范围投影 |
| POST /api/repair-requests/{id}/return | Path Id + ReturnRequestDto | MutationReceiptVo | ADMIN；待审核→退回待补充，原因必填 |
| POST /api/repair-requests/{id}/supplement | Path Id + SupplementRequestDto | MutationReceiptVo | 原REPORTER；待补充→待审核；不新建申请 |
| POST /api/repair-requests/{id}/withdraw | Path Id + WithdrawRequestDto | MutationReceiptVo | 原REPORTER；待审核/待补充且未生成工单 |
| POST /api/repair-requests/{id}/duplicate-close | Path Id + DuplicateCloseDto | MutationReceiptVo | ADMIN；具体台账重复依据，不能用于范围外申请 |
| POST /api/repair-requests/{id}/approve-and-assign | Path Id + ApproveAndAssignDto | MutationReceiptVo | ADMIN；申请、工单、分派与事件一次提交；回执含关联workOrderId |

补充STRUCTURED时locationId必须提供且合法；LEGACY_TEXT必须省略locationId，不因补充转为新位置模式。客户端不能创建LEGACY_TEXT。历史图片不删除；本次attachmentIds为新增0—5张，已有合法故障历史图时可为0；首次为1—5张。类别可null/省略，名称、地址、描述、联系人和电话仍必填。联系快照不写账号手机号，也不套账号手机号唯一性。

## 5. 工单、分派、维修与验收

```typescript
type OrderAction = 'ACCEPT' | 'REFUSE' | 'ASSIGN' | 'PROGRESS' | 'SUBMIT_RESULT' | 'ACCEPTANCE';
type HistoryScope = 'FULL' | 'ASSIGNMENT_ONLY';
interface WorkOrderQuery extends PageQuery { status?: WorkOrderStatus; scope?: 'CURRENT' | 'HISTORY' | 'ALL'; locationId?: Id; technicianId?: Id; }
interface AcceptWorkOrderDto extends VersionedMutation {}
interface RefuseWorkOrderDto extends VersionedMutation { reason: string; }
interface AssignWorkOrderDto extends VersionedMutation { technicianId: Id; reason: string; confirmPreviouslyRefused: boolean; }
interface ProgressDto extends RoundMutation { content: string; }
interface SubmitRepairResultDto extends RoundMutation { result: string; attachmentIds: Id[]; }
interface AcceptanceDto extends RoundMutation { passed: boolean; reason: string | null; }
interface WorkOrderSummaryVo {
  id: Id; workOrderNo: string; requestId: Id; requestNo: string; reportedEquipmentName: string; equipment: EquipmentReferenceVo | null;
  status: WorkOrderStatus; locationMode: LocationMode; location: LocationReferenceVo | null;
  locationNameSnapshot: string | null; createdAt: Instant;
  assignedTechnician: AccountReferenceVo | null; acceptedTechnician: AccountReferenceVo | null;
  version: number | null; round: number | null; isRework: boolean | null;
  allowedActions: OrderAction[]; historyScope: HistoryScope;
}
interface AssignmentVo {
  id: Id; assignmentNo: number; technician: AccountReferenceVo; assignedBy: AccountReferenceVo;
  visibleSubmissionId: Id; assignedAt: Instant; endedAt: Instant | null;
  outcome: AssignmentOutcome; reason: string | null; decisionAt: Instant | null;
}
interface RepairProgressVo { id: Id; round: number; technician: AccountReferenceVo; content: string; recordedAt: Instant; }
interface AcceptanceVo { passed: boolean; reason: string | null; reporter: AccountReferenceVo; acceptedAt: Instant; }
interface RepairRoundVo {
  id: Id; round: number; technician: AccountReferenceVo; startedAt: Instant;
  submittedAt: Instant | null; result: string | null; attachments: AttachmentVo[];
  acceptance: AcceptanceVo | null; progress: RepairProgressVo[];
}
interface WorkOrderEventVo {
  id: Id; type: 'FIRST_ASSIGNED' | 'ACCEPTED' | 'REFUSED' | 'ASSIGNED' | 'PROGRESS_ADDED' | 'RESULT_SUBMITTED' | 'ACCEPTANCE_FAILED' | 'COMPLETED';
  fromStatus: WorkOrderStatus | null; toStatus: WorkOrderStatus;
  actor: AccountReferenceVo; reason: string | null; occurredAt: Instant;
}
interface WorkOrderDetailVo extends WorkOrderSummaryVo {
  requestMaterial: RepairRequestSubmissionVo; reporter: AccountReferenceVo;
  assignments: AssignmentVo[]; rounds: RepairRoundVo[]; events: WorkOrderEventVo[];
  firstSubmittedAt: Instant | null; acceptedAt: Instant | null; completedAt: Instant | null; queriedAt: Instant;
}
```

| 方法/路径 | 输入 | data类型 | 权限/特定规则 |
| --- | --- | --- | --- |
| GET /api/work-orders | WorkOrderQuery | PageResult<WorkOrderSummaryVo> | 原REPORTER/关联TECHNICIAN/ADMIN；TECHNICIAN scope区分当前与历史；TECHNICIAN本人范围不由请求userId改变 |
| GET /api/work-orders/navigation-count | 无查询参数 | WorkOrderNavigationCountVo | 合格TECHNICIAN统计本人待接单＋处理中（含返工）；REPORTER仅本人待验收；ADMIN统计全局全部未完成工单，不接受身份／范围覆盖 |
| GET /api/work-orders/{id} | Path Id | WorkOrderDetailVo | 同范围，默认拒绝；有限历史投影，不返Entity |
| POST /api/work-orders/{id}/accept | Path Id + AcceptWorkOrderDto | MutationReceiptVo | 当前合格TECHNICIAN、待接单；负责人及首次接单时间固定，开round1 |
| POST /api/work-orders/{id}/refuse | Path Id + RefuseWorkOrderDto | MutationReceiptVo | 当前TECHNICIAN、待接单；原因必填，进入待重派 |
| POST /api/work-orders/{id}/assign | Path Id + AssignWorkOrderDto | MutationReceiptVo | ADMIN；待接单/待重派；待接单必须不同人，接单后禁止 |
| POST /api/work-orders/{id}/progress | Path Id + ProgressDto | MutationReceiptVo | 当前实际维修者、处理中、同version/round；独立进度 |
| POST /api/work-orders/{id}/submit-result | Path Id + SubmitRepairResultDto | MutationReceiptVo | 当前实际维修者、处理中；本轮结果及1—5本轮新凭证 |
| POST /api/work-orders/{id}/acceptance | Path Id + AcceptanceDto | MutationReceiptVo | 原REPORTER、待验收、同version/round；false原因必填 |

ASSIGNMENT_ONLY只返回本人分派期间可见原材料、当次分派与本人拒单/改派事件；rounds为空，后续实际维修人、轮次/version/isRework及接单/完成时间设null，allowedActions为空。列表可显示需求允许的当前工单状态和本人历史关系，但不得由此泄露后续维修材料。FULL下version非null、round≥0，接单前round=0/isRework=false。

2026-10-18用户追加导航数量红标：`WorkOrderNavigationCountVo = { userId: Id; role: 'ADMIN' | 'TECHNICIAN' | 'REPORTER'; count: number }`。在后端当前有权范围以单条COUNT统计，不以分派／进度／图片行数计数；待重派不归原拒单人员，待验收不计入维修端，管理员统计待接单／待重派／处理中（含返工）／待验收，完成后三端均不计。前端核对响应身份，数量0隐藏；进入列表不会清零。沿用可见页面30秒核对、路由／窗口恢复立即核对及已确认工单写入后重读；失败保留已知值并标记过期，初次失败保持未知。复用管理端徽标样式，不新增未读表或Web Storage状态。

接单后和返工始终同一实际负责人；通过才completedAt非null。各写请求的version与round来自最近有权读取的详情，不能使用新operationId绕过旧版本。返工新轮的旧凭证不能放入attachmentIds。验收passed=true时reason必须为null；非null一律400，不静默忽略后参与重放。ACCEPTANCE允许动作由原报修人选择通过／不通过，两分支仍共用同一持久操作类型。

2026-10-18 B05实装契约同步：allowedActions采用上述页面与后端共同消费的动作值；持久操作类型采用下节事件式值，与URL动作名不同。管理员keyword还可匹配本工单当前／曾分派维修人员的姓名或账号；普通角色不扩展此搜索范围。中文关键词与ASCII编号比较在查询中转换字符集，不回改迁移。维修任务排序为待接单→返工处理中→普通处理中→其余，管理员待接单／待重派优先。

## 6. 附件、空间提示、推荐与统计

### 6.1 附件

```typescript
interface TempAttachmentUploadDto {
  operationId: UUID; purpose: AttachmentPurpose; file: File;
  batchId?: UUID; repairRequestId?: Id; workOrderId?: Id; round?: number;
}
interface CancelTemporaryAttachmentDto { operationId: UUID; }
interface AttachmentVo {
  id: Id; originalName: string; mediaType: 'image/jpeg' | 'image/png'; byteSize: number;
  purpose: AttachmentPurpose; state: AttachmentState; uploadedAt: Instant;
  expiresAt: Instant | null; contentUrl: string;
}
interface AttachmentTarget { kind: 'REQUEST_SUBMISSION' | 'REPAIR_ROUND'; id: Id; round: number | null; }
```

| 方法/路径 | 输入 | data类型 | 权限/特定规则 |
| --- | --- | --- | --- |
| POST /api/attachments/temp | multipart TempAttachmentUploadDto | AttachmentVo | 登录且具对应业务资格；201；REQUEST_FAULT必须batchId，SUPPLEMENT必须repairRequestId，VOUCHER必须workOrderId+round |
| DELETE /api/attachments/{id}/temp | Path Id；X-Operation-Id头为CancelTemporaryAttachmentDto.operationId | null | 上传人+CSRF；仅临时或同一已取消结果；BOUND不能取消 |
| GET /api/attachments/{id}/content | Path Id | 图片二进制 | 临时上传人或正式目标当前权限；Cookie鉴权，private/no-store |

contentUrl只含同源`/api/attachments/{id}/content`，无登录Token和磁盘路径。目标用途字段互斥，不能混传。图片扩展名/MIME/内容和5,000,000字节边界由后端校验。单次上传一图；业务提交关联1—5或允许的0—5张。过期/被占用/跨人/跨业务/跨轮次不能绑定，失败保留合法临时附件供同标识重试。

### 6.2 空间汇总

```typescript
interface CampusSummaryQuery { buildingId?: Id; floorId?: Id; }
interface StageCounts { pendingAccept: number; reassignPending: number; inProgress: number; rework: number; pendingAcceptance: number; needsSupplement: number; pendingReview: number; }
interface LocationSummaryVo { locationId: Id; category: LocationCategory; counts: StageCounts; visibleTotal: number; queriedAt: Instant; }
interface CampusSummariesVo { configVersion: string; locations: LocationSummaryVo[]; queriedAt: Instant; }
interface LocationItemQuery extends PageQuery { category?: Exclude<LocationCategory, 'NONE'>; }
interface LocationItemVo {
  resourceType: 'REPAIR_REQUEST' | 'WORK_ORDER'; id: Id; businessNo: string;
  equipmentName: string; status: RepairRequestStatus | WorkOrderStatus; stage: keyof StageCounts;
  createdAt: Instant; allowedActions: (RequestAction | OrderAction)[];
}
interface LocationItemsVo { location: LocationVo; counts: StageCounts; category: LocationCategory; visibleTotal: number; items: PageResult<LocationItemVo>; queriedAt: Instant; }
```

| 方法/路径 | 输入 | data类型 | 权限/特定规则 |
| --- | --- | --- | --- |
| GET /api/campus/summaries | CampusSummaryQuery | CampusSummariesVo | 登录；先权限后聚合；无当前业务权的维修人员返回空locations；不暗示他人故障 |
| GET /api/campus/locations/{id}/items | Path Id + LocationItemQuery | LocationItemsVo | 同当前空间业务范围；空结果为0/空列表，不带历史完结单 |

汇总只返回visibleTotal>0的位置，普通空位置仍由catalog可浏览；同响应所有queriedAt一致。七计数之和为visibleTotal，inProgress与rework互斥，APPROVED申请不重复计工单；主类别按字典§7。旧文字记录不出现空间汇总，但参与普通业务、推荐及统计。

### 6.3 合格名单与推荐

```typescript
type AssignmentContextQuery =
  | { context: 'FIRST_ASSIGN'; requestId: Id; workOrderId?: never; }
  | { context: 'CHANGE_BEFORE_ACCEPT' | 'REASSIGN'; workOrderId: Id; requestId?: never; };
type AssignmentCandidateQuery = AssignmentContextQuery & PageQuery;
interface RefusalReferenceVo { assignmentId: Id; reason: string; refusedAt: Instant; }
interface EligibleTechnicianVo { id: Id; name: string; enabled: boolean; reviewStatus: 'APPROVED'; refusal: RefusalReferenceVo[]; eligible: boolean; }
interface AssignmentEligibleVo { candidates: PageResult<EligibleTechnicianVo>; eligibleTotal: number; queriedAt: Instant; }
interface AssignmentCandidateVo extends EligibleTechnicianVo {
  W: number; I: number; R: number; A: number; load: number; todayCompleted: number;
  lastAssignedAt: Instant | null; reason: string;
}
interface AssignmentCandidatesVo {
  top5: AssignmentCandidateVo[]; candidates: PageResult<AssignmentCandidateVo>;
  eligibleTotal: number; preferredTotal: number; queriedAt: Instant; businessDate: string;
}
```

| 方法/路径 | 输入 | data类型 | 权限/特定规则 |
| --- | --- | --- | --- |
| GET /api/admin/assignment-eligible | AssignmentCandidateQuery | AssignmentEligibleVo | ADMIN；T028先提供完整实时合格名单，与最终派单同资格规则 |
| GET /api/work-orders/assignment-candidates | AssignmentCandidateQuery | AssignmentCandidatesVo | ADMIN；T047在同规则上派生推荐；前5与完整名单同只读快照 |

context与目标只能为对应组合；FIRST_ASSIGN要求待审核申请，CHANGE_BEFORE_ACCEPT要求待接单，REASSIGN要求待重派。待接单排除当前人员；本工单曾拒单合格者不入优先名单，完整名单仍可选但最终提交confirmPreviouslyRefused=true。无合格者eligibleTotal=0；有合格者但top5为空不等于无人可派。排名用L→D→lastAssignedAt(null最前)→人员数值ID，keyword只筛完整名单，不篡改全体Top5的排序含义；分页不截断资格总量。

### 6.4 工作台与统计

```typescript
interface WorkbenchVo { counts: Record<string, number>; items: BusinessHistoryItemVo[]; queriedAt: Instant; }
interface StatisticsQuery { month: string; } // YYYY-MM
type StatisticsMetric = 'COMPLETED' | 'RESPONSE_DURATION' | 'REPAIR_DURATION' | 'DAILY_COMPLETED' | 'EQUIPMENT_FREQUENCY' | 'CURRENT_STATE';
interface StatisticsDetailQuery extends PageQuery { metric: StatisticsMetric; month?: string; date?: string; equipmentId?: Id; unlinkedEquipment?: true; status?: WorkOrderStatus; }
interface DailyCompletedVo { date: string; count: number; }
interface EquipmentFrequencyVo { equipment: EquipmentReferenceVo | null; count: number; }
interface StatisticsVo {
  month: string; timeZone: 'Asia/Shanghai'; completedCount: number; sampleCount: number;
  averageResponseSeconds: number | null; averageRepairSeconds: number | null;
  invalidTimeSampleCount: number; dailyCompleted: DailyCompletedVo[];
  equipmentFrequencies: EquipmentFrequencyVo[]; stateScope: 'ALL_CURRENT_STATE';
  stateCounts: Record<WorkOrderStatus, number>; queriedAt: Instant;
}
interface WorkOrderStatisticVo {
  id: Id; workOrderNo: string; reportedEquipmentName: string; equipment: EquipmentReferenceVo | null; status: WorkOrderStatus;
  firstSubmittedAt: Instant; createdAt: Instant; acceptedAt: Instant | null; completedAt: Instant | null;
  responseSeconds: number | null; repairSeconds: number | null; hasInvalidTime: boolean;
}
interface StatisticsDetailVo { metric: StatisticsMetric; month: string | null; scope: 'MONTH_COMPLETED' | 'MONTH_CREATED' | 'ALL_CURRENT_STATE'; records: PageResult<WorkOrderStatisticVo>; queriedAt: Instant; }
```

| 方法/路径 | 输入 | data类型 | 权限/特定规则 |
| --- | --- | --- | --- |
| GET /api/workbench | 无 | WorkbenchVo | 登录且有业务资格；REPORTER本人、TECHNICIAN当前任务、ADMIN全局待办，未合格者不开放维修工作台 |
| GET /api/statistics/dashboard | StatisticsQuery | StatisticsVo | ADMIN；查询时区固定上海；独立当前状态分布 |
| GET /api/statistics/details | StatisticsDetailQuery | StatisticsDetailVo | ADMIN；复用指标时间字段/权限/去重规则 |

CURRENT_STATE的业务筛选仅允许status，month/date/equipmentId/unlinkedEquipment必须省略，返回month=null与ALL_CURRENT_STATE；各指标沿用PageQuery的分页和keyword摘要搜索。其他指标month必填；DAILY_COMPLETED的date必填且属该月，其他指标不得传date。EQUIPMENT_FREQUENCY按工单created_at，必须且只能传equipmentId或unlinkedEquipment=true之一；其他月度指标不得传设备/状态条件。完成量/时长按completed_at。平均无样本为null；存在异常时间则对应平均null并报告invalidTimeSampleCount，不假装为0或偷偷删样本。

2026-10-27用户确认的V006兼容：无台账工单在设备频次中计入独立“未关联台账”分组（equipment=null），不推测设备ID、不按报修名称归并成设备；该分组用unlinkedEquipment=true下钻。明细equipment允许null并保留reportedEquipmentName，旧文字工单同样参与全部统计，不据此补绑设备或3D位置。

工作台counts按角色固定：REPORTER的pendingReview/needsSupplement/pendingAcceptance；TECHNICIAN的pendingAccept/inProgress/rework/pendingAcceptance；ADMIN的pendingReview/reassignPending。不存在的角色字段不返，items最多20项且仅作详情导航，完整列表仍分页读取。

## 7. 操作结果核对、错误code与事务衔接

```typescript
type OperationType = 'CREATE_REQUEST' | 'SUPPLEMENT_REQUEST' | 'RETURN_REQUEST' | 'WITHDRAW_REQUEST' | 'DUPLICATE_CLOSE' | 'APPROVE_ASSIGN' | 'ACCEPTED' | 'REFUSED' | 'ASSIGNED' | 'PROGRESS_ADDED' | 'RESULT_SUBMITTED' | 'ACCEPTANCE' | 'PROFILE_UPDATE' | 'PASSWORD_CHANGE' | 'PASSWORD_RESET' | 'ACCOUNT_ENABLE' | 'ACCOUNT_DISABLE' | 'REVIEW_RESUBMIT' | 'REVIEW_DECIDE' | 'EQUIPMENT_CREATE' | 'EQUIPMENT_UPDATE' | 'EQUIPMENT_ENABLE' | 'EQUIPMENT_DISABLE';
interface OperationResultQuery { operation: OperationType; }
type OperationResultVo = { kind: 'SUCCEEDED'; receipt: MutationReceiptVo; } | { kind: 'NOT_CONFIRMED'; receipt: null; };
```

| 方法/路径 | 输入 | data类型 | 权限/特定规则 |
| --- | --- | --- | --- |
| GET /api/operations/{operationId} | Path UUID + OperationResultQuery | OperationResultVo | 登录当前操作者及当前合法记录范围；无标记仅NOT_CONFIRMED；不查他人结果或秘密 |

承载表映射：CREATE_REQUEST/SUPPLEMENT_REQUEST→repair_request_submission；申请其他操作→repair_request_event；工单操作→work_order_event；账号操作→account_event；设备操作→equipment_event。无统一操作日志表，字段与结果随业务同事务。幂等作用域、摘要、锁顺序采用数据字典§5；已成功标记重放不再次改变业务，即使状态已变也在当前查看权限内返回原回执，不能把旧version/round配新标识当作合法操作。

| HTTP/code | 含义 | 前端处理 |
| --- | --- | --- |
| 400 VALIDATION_FAILED / INVALID_QUERY / INVALID_LOCATION / PASSWORD_CONFIRM_MISMATCH / CURRENT_PASSWORD_INVALID | 输入错误；需要原因的空白、Id/轮次/字段不合法等 | 留输入，errors定位字段 |
| 401 AUTH_INVALID_CREDENTIALS | 登录账号/密码错误 | 登录表单反馈，不猜事务成功 |
| 401 AUTH_REQUIRED / SESSION_EXPIRED / SESSION_REVOKED / ACCOUNT_DISABLED | 受保护业务的登录无效 | 清身份、业务缓存/草稿，提示重新登录 |
| 403 FORBIDDEN / RECORD_FORBIDDEN / TECHNICIAN_NOT_APPROVED / PROFILE_READ_ONLY / CSRF_INVALID | 权限/资格/记录/CSRF不足 | 保留全局身份；失去记录权限时清该记录内容；CSRF重新获取后由用户重试原操作 |
| 404 RESOURCE_NOT_FOUND | 允许范围内资源不存在 | 返回列表核对；不提供他人存在性细节 |
| 409 LOGIN_NAME_EXISTS / PHONE_EXISTS | 全局唯一性冲突 | 对应字段提示，停用账号仍占用 |
| 409 VERSION_CONFLICT / STATE_CONFLICT / ROUND_CONFLICT / QUALIFICATION_CHANGED | 旧版本/状态/轮次/资格 | 保留草稿，刷新允许动作，停止旧提交 |
| 409 EQUIPMENT_HAS_UNFINISHED_ORDER / LOCATION_MISMATCH / ACCOUNT_HAS_UNFINISHED_BUSINESS / PREVIOUS_REFUSAL_CONFIRMATION_REQUIRED | 跨对象规则冲突 | 仅返回当前身份有权看的冲突信息 |
| 409 OPERATION_CONTENT_MISMATCH / ATTACHMENT_EXPIRED / ATTACHMENT_ALREADY_BOUND / ATTACHMENT_CONTEXT_MISMATCH | 重用标识不同内容或附件不合法 | 原标识核对/修正输入；不重传旧凭证 |
| 400 ATTACHMENT_TYPE_INVALID / ATTACHMENT_COUNT_INVALID / OUT_OF_SCOPE_EQUIPMENT | 图片或台账范围错误 | 留草稿，明确调整办法；范围外申请本身仍由管理员退回 |
| 413 ATTACHMENT_TOO_LARGE / REQUEST_TOO_LARGE | 单图或请求体超限 | 5,000,000/+1字节边界；框架multipart整请求上限需覆盖5张总量及协议开销 |
| 500 INTERNAL_ERROR | 未预期错误 | requestId诊断，不显示内部细节；写结果按未知处理 |

所有业务HTTP成功/失败响应、目录/提示/推荐/统计及图片使用private/no-store；公开模型可按内容版本缓存。错误data默认null；若返回结构化冲突数据，只能包含当前有权记录的编号/版本/允许动作，不包含其他人的统计或秘密。

## 8. 前端、渲染与服务方法契约

### 8.1 公共请求和身份

```typescript
interface RequestOptions { signal?: AbortSignal; timeoutMs?: number; }
type ReadResult<T> =
  | { kind: 'SUCCESS'; data: T; requestId: string; }
  | { kind: 'HTTP_ERROR'; status: number; response: ApiResponse<unknown>; }
  | { kind: 'READ_FAILED'; message: string; };
type MutationResult<T> =
  | { kind: 'SUCCESS'; data: T; requestId: string; }
  | { kind: 'HTTP_ERROR'; status: number; response: ApiResponse<unknown>; }
  | { kind: 'UNKNOWN'; operationId: UUID | null; message: string; };
declare function apiRead<T>(path: string, options?: RequestOptions): Promise<ReadResult<T>>;
declare function apiMutation<T>(path: string, method: 'POST' | 'PATCH' | 'DELETE', body: unknown, options?: RequestOptions): Promise<MutationResult<T>>;
```

read默认8秒，mutation默认20秒；credentials=same-origin，写请求CSRF。401、403、409分别处理；网络断开/读取超时不清身份。写请求已发出后超时/断网/取消/5xx或无法解析合法成功结果均为UNKNOWN，不自动重试，不清草稿、不生成新operationId、不宣称事务失败。已收到可信400/401/403/404/409/413拒绝JSON可显示明确错误。成功后重新读需要更新的列表/汇总/详情/推荐。

Pinia auth只保存后端确认CurrentUserVo、恢复状态与必要的内存CSRF；登录Token永不进入Pinia/Web Storage。公共登录401只作表单反馈；受保护请求确定401才清身份。403必须通过T006的permission_error_keeps_identity；UNKNOWN必须保留原标识/内容。退出、账号切换、明确身份或记录失权清理对应业务内容和Object URL。

### 8.2 草稿、读取代次和同步

```typescript
type DraftKey =
  | { userId: Id; operation: 'CREATE_REQUEST'; locationId: Id; }
  | { userId: Id; operation: 'SUPPLEMENT_REQUEST'; requestId: Id; }
  | { userId: Id; operation: 'ADD_PROGRESS' | 'SUBMIT_RESULT' | 'ACCEPTANCE'; workOrderId: Id; round: number; };
interface AttachmentSelection { file: File; temporaryId: Id | null; uploadOperationId: UUID; }
interface ReadGeneration { identityEpoch: number; targetKey: string; generation: number; }
```

前台10秒发起读，禁止同一目标重叠/堆积；恢复前台立即读，本人确认成功立即刷新。回调三元身份/目标/代次全部匹配才应用。失败不推进lastSuccessAt；保留仍有权旧数据并标过期，第一次失败不显示空数据；失权则清内容。读取只更新只读展示/allowedActions，不覆盖输入；状态变化使操作不合法则保留草稿并停提交。

新报修按userId+locationId，补充按userId+requestId，维修/验收按userId+workOrderId+operation+round；只在同次登录内存保存。切位置不把旧输入复制到新位置；刷新/关闭/退出不恢复。未提交File留内存，明确上传/提交步骤才调用ensureTemporaryUploaded；成功只清本次草稿，UNKNOWN冻结原提交身份/轮次/内容直到核对，不影响其他草稿。

### 8.3 渲染与manifest

```typescript
type Vec3 = [number, number, number];
type CampusTarget = { kind: 'OVERVIEW' } | { kind: 'BUILDING'; buildingId: Id } | { kind: 'FLOOR'; floorId: Id } | { kind: 'LOCATION'; locationId: Id };
interface CameraAnchor { position: Vec3; target: Vec3; }
interface ManifestResource { key: string; url: string; contentHash: string; byteSize: number; }
interface ManifestLocation { locationCode: string; floorCode: string; templateKey: string; entryAnchor: CameraAnchor; interiorAnchor: CameraAnchor; doorKey: string | null; }
interface CampusManifest {
  configVersion: string; contentVersion: string; resources: ManifestResource[];
  buildings: { buildingCode: string; exteriorResourceKey: string; }[];
  locations: ManifestLocation[];
}
interface CampusRenderer {
  mount(container: HTMLElement, manifest: CampusManifest): Promise<void>;
  selectTarget(target: CampusTarget, generation: number): Promise<void>;
  enterLocation(id: Id, generation: number): Promise<void>;
  returnToFloor(): void; resetOverview(): void;
  projectAnchor(locationId: Id): { x: number; y: number; visible: boolean } | null;
  dispose(): void;
}
```

manifest通过稳定code与catalog显式映射Id，所有floor/building关系来自配置；不解析房号、mesh名/UUID或坐标。configVersion不匹配明确3D不可用且保留普通业务列表，不重置数据。资源URL限本机公开campus资源、无联系人/业务/Token/附件URL。T016先增样板，后续批次扩展几何；不是本批制作模型。

单渲染实例、最多两个内景模板缓存条目；各实例门状态/锚点独立，共享资源按引用释放。加载开始后15秒超时反馈；迟到结果只有仍为同身份/目标/最新代次才显示，否则释放。不自动切列表，不把3D动画当业务提交。热目标选择和进入各≤1秒；冷内景5秒从点击房门/进入观察起，性能实测在B12。位置选择器和普通列表提供等价键盘/窄屏入口。

### 8.4 后端/上传服务及本地命令

```typescript
interface CleanupResult { claimed: number; deleted: number; retryPending: number; }
interface MaintenanceResult {
  command: 'campus-init' | 'admin-init' | 'admin-reset' | 'correct-location-name';
  changed: boolean; resourceId: Id | null; configVersion: string | null;
  counts: { buildings: number; floors: number; locations: number; reportable: number; equipment: number; } | null;
  message: string;
}
```

| 名称 | 签名/输入输出约定 | 首次定义任务 |
| --- | --- | --- |
| ApiResponse/PageResult | 与§1同名；ID字段单独序列化，错误含requestId | T005 |
| CurrentUser | 后端Long userId、Role、ReviewStatus可空、enabled；由认证生成，不从body读取 | T005/T011 |
| PasswordSupport | randomValidPassword():String；hash(raw):String；matches(raw,hash):boolean；随机/哈希结果不写日志 | T009 |
| SessionService | revokeCurrent():void；revokeAll(userId):void；业务事务中执行，固定8小时无续期 | T011 |
| BusinessAccessPolicy | canReadAttachment(CurrentUser,AttachmentTarget):boolean；同申请/分派期间投影；未接入归属默认拒绝 | T019，T022/T025扩展 |
| AttachmentService | bindTemporary(ids,AttachmentTarget,CurrentUser):List<AttachmentVo>；调用者业务事务内锁/复核/绑定 | T019 |
| AttachmentCleanupService | cleanupExpired(now:Instant):CleanupResult{claimed,deleted,retryPending}；删失败不复活绑定 | T020 |
| ensureTemporaryUploaded | (selection:AttachmentSelection[],context:TempAttachmentUploadDto去file):Promise<Id[]>；仅明确上传步骤调用 | T021 |
| EquipmentQueryService | currentAndHistory(equipmentId,EquipmentHistoryQuery,CurrentUser):EquipmentDetailVo | T025/T026 |
| EquipmentService | lockAndValidateEquipment(equipmentId,locationId可空,CurrentUser):Equipment；调用者先锁用户后锁设备，锁内复核 | T026 |
| LocalMaintenanceCommand | name():String；execute():MaintenanceResult；显式maintenance.command选择，Web禁用，正常启动不运行 | T008 |
| MaintenanceRunner | 只执行指定LocalMaintenanceCommand；拒绝未知命令/未指明环境目标；秘密不进入普通日志 | T008 |
| campus-init | 配置版本/摘要及明确目标库→10/41/296/257/1817首次快照；稳定code/key幂等 | T008 |
| admin-init | 明确目标库→仅首次生成管理员，重复不覆盖；随机密码仅本机成功展示一次 | T009 |
| admin-reset | 本地维护者/明确目标库→保留管理员ID/历史，更新哈希并撤销全部会话；无HTTP入口 | T055 |
| correct-location-name | locationCode/newName/reason/operatorLabel/目标库→同稳定ID改名及历史；不改所属楼层/旧快照 | T056 |

模块方法的实现与真实测试留在其T任务；本文不提前创建Service空壳、维护脚本、DDL或业务代码。Java辅助VO可在所属已批准VO中定义嵌套record，避免仅为本文的每个辅助类型扩展文件白名单。秘密不放MaintenanceResult，管理员命令的一次性密码仅由成功本地流程单独展示。

## 9. 本轮静态验收与未验证项

- 完整DTO/VO、字段可空性、API权限、状态/幂等/锁顺序在本文件与数据字典对应；所有REQ-01—20有数据/交互支撑。
- 原规范存在queryAt/queriedAt两种示意写法，本文统一为queriedAt；只统一详细名字，不改变查询时点语义。
- 新增的文本上限、账号大小写规则、回执结构、路径细节和type名字是助手实施决定；成本是后续若调整需同步契约/迁移/测试，不能悄悄改成第二套状态。
- T005/T006仍须用实际BootstrapIT/client.test.ts、依赖树、后端verify、前端typecheck/build及代理请求验证；本轮没有可构建工程，不宣称这些测试已通过。
