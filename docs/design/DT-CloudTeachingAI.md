# CloudTeachingAI 数字孪生设计文档（DT）

**文档版本**：v1.1
**创建日期**：2026-03-21
**最后更新**：2026-03-21
**关联文档**：SDD-CloudTeachingAI.md v2.5

> v1.1 修订：关联文档升级至 SDD v2.5；在第 3.1 节补充 `gateway.access` Topic 的孪生用途；新增 `twin.cdc.ability` CDC Topic（第 3.2 节），监听 `learn-db.ABILITY_MAP` 表；在 `StudentLearningTwin` 模型中补充 `last_login_at` 字段；在第 4.2 节补充 `login.event` 路由规则；修正 `interaction_stats` 数据来源（改为 Kafka 事件驱动，不直接访问 chat-db）；补充 `TeachingEffectivenessTwin.mentoring` 数据来源（新增 `twin.cdc.mentor` CDC Topic）；明确 `twin-notify` 与 `twin-query-service` 职责分离（WebSocket 端点归属 `twin-notify`）；明确冷启动快照调用使用 `X-Internal-Token` 绕过 Gateway 角色过滤；补充 Redis 复用现有集群的说明；明确孪生层 API 权限边界（ADMIN 全量访问，TEACHER 访问学生/课程/教学效果孪生）；修正 `snapshot_loader.py` 注释（Python 使用 httpx，非 Feign）。

---

## 1. 数字孪生架构设计

### 1.1 四层架构总览

```mermaid
graph TB
    subgraph PEL["物理实体层 Physical Entity Layer"]
        direction LR
        PE1["业务微服务\n8个Java服务"]
        PE2["AI智能体服务\n5个Python服务"]
        PE3["基础设施\nKafka / Redis / PostgreSQL / MinIO"]
        PE4["K8s集群\nPod / Node / HPA"]
        PE5["用户行为\n学生 / 教师 / 管理员"]
    end

    subgraph DSL["数据同步层 Data Sync Layer"]
        direction LR
        DSL1["Kafka Topic 镜像\n现有14个Topic直接复用"]
        DSL2["Micrometer指标流\nPrometheus Scrape"]
        DSL3["CDC变更捕获\nDebezium → Kafka"]
        DSL4["新增采集Topic\n6个孪生专用Topic"]
    end

    subgraph DML["数字模型层 Digital Model Layer"]
        direction LR
        DM1["学生学习行为孪生\nStudentLearningTwin"]
        DM2["课程内容孪生\nCourseContentTwin"]
        DM3["系统运行状态孪生\nSystemRuntimeTwin"]
        DM4["教学效果孪生\nTeachingEffectivenessTwin"]
    end

    subgraph SL["服务层 Service Layer"]
        direction LR
        SL1["孪生查询服务\ntwin-query-service"]
        SL2["预测分析服务\ntwin-prediction-service"]
        SL3["仿真推演服务\ntwin-simulation-service"]
        SL4["孪生状态推送\ntwin-notify（WebSocket）"]
    end

    subgraph APP["应用层 Application Layer"]
        direction LR
        APP1["实时监控大屏\n管理员视角"]
        APP2["学习预警面板\n教师视角"]
        APP3["个人孪生画像\n学生视角"]
    end

    PEL -->|"原始事件 / 指标 / 变更"| DSL
    DSL -->|"标准化数据流"| DML
    DML -->|"孪生状态 / 模型输出"| SL
    SL -->|"查询 / 推送 / 仿真结果"| APP
    SL -.->|"反馈指令（可选）"| PEL
```

### 1.2 与现有系统的关系定位

数字孪生层作为**只读观测层**叠加在现有系统之上，不修改任何现有服务的业务逻辑。

| 原则 | 说明 |
|------|------|
| 零侵入 | 现有13个Kafka Topic直接复用，不修改生产者代码 |
| 只读订阅 | 孪生层只消费数据，不向业务服务写入 |
| 独立部署 | 孪生服务作为新的K8s Namespace `twin` 独立运行 |
| 最终一致 | 孪生模型状态与物理实体保持最终一致，允许秒级延迟 |

---

## 2. 核心孪生实体建模

### 2.1 学生学习行为孪生（StudentLearningTwin）

**物理映射**：learn-service + assign-service + chat-agent 中的单个学生

**状态属性定义**：

```
StudentLearningTwin {
    student_id: bigint                    // 来源：user-db.USER.id
    twin_version: timestamp               // 最后同步时间

    activity_state: enum {
        ACTIVE,          // 近7天有学习行为
        IDLE,            // 7-14天无行为
        DORMANT,         // 14天以上无行为
        AT_RISK          // 活跃度骤降（较前2周下降>50%）
    }

    last_login_at: timestamp              // 来源：login.event（auth-service 发布）；
                                          // 用于 AT_RISK 检测的登录时间维度

    ability_snapshot: [{
        knowledge_point_id: int
        mastery: enum { NONE, PARTIAL, MASTERED }
        score: float
        last_updated: timestamp
    }]                                    // 来源：learn-db.ABILITY_MAP

    progress_stats: {
        total_resources: int
        completed_resources: int
        completion_rate: float
        total_watch_seconds: int
        avg_daily_seconds: float          // 近7天日均学习时长
    }                                     // 来源：learn-db.LEARNING_PROGRESS

    assignment_performance: {
        submitted_count: int
        avg_ai_score: float
        avg_final_score: float
        pending_count: int
        overdue_count: int
    }                                     // 来源：assign-db.SUBMISSION

    interaction_stats: {
        chat_session_count_7d: int
        avg_tokens_per_session: float
        last_chat_at: timestamp
    }                                     // 来源：user.behavior 事件（chat-agent 发布）；
                                          // 不直接访问 chat-db（零侵入原则）

    path_execution: {
        current_path_generated_at: timestamp
        path_items_total: int
        path_items_completed: int
        path_adherence_rate: float        // 路线遵循率
    }                                     // 来源：learn-db.LEARNING_PATH

    predictions: {
        dropout_risk_score: float         // 流失风险分 0-1
        mastery_forecast_30d: [{
            knowledge_point_id: int
            predicted_mastery: enum
        }]
        recommended_intervention: enum {
            NONE, NOTIFY_TEACHER, TRIGGER_NAV_REFRESH, SEND_ENCOURAGEMENT
        }
    }
}
```

**状态转换规则**：

```mermaid
stateDiagram-v2
    [*] --> ACTIVE : 首次登录 / 学习行为发生
    ACTIVE --> IDLE : 连续7天无 user.behavior 事件
    IDLE --> ACTIVE : 新的学习行为发生
    IDLE --> DORMANT : 再连续7天无行为（累计14天）
    DORMANT --> ACTIVE : 重新登录并产生行为
    ACTIVE --> AT_RISK : 本周行为量 < 上上周50%（滑动窗口检测）
    AT_RISK --> ACTIVE : 行为量恢复正常
    AT_RISK --> IDLE : 继续无行为7天
```

---

### 2.2 课程内容孪生（CourseContentTwin）

**物理映射**：course-service + tag-agent + vector-db 中的单门课程

**状态属性定义**：

```
CourseContentTwin {
    course_id: bigint
    twin_version: timestamp

    structure: {
        chapter_count: int
        resource_count: int
        resource_type_distribution: { VIDEO: int, DOCUMENT: int, SLIDE: int }
        total_duration_seconds: int
    }                                     // 来源：course-db.CHAPTER / RESOURCE

    knowledge_coverage: [{
        knowledge_point_id: int
        knowledge_point_name: varchar
        resource_count: int
        avg_confidence: float
        confirmed_count: int
        unconfirmed_count: int
    }]                                    // 来源：course-db.RESOURCE_KNOWLEDGE

    content_quality: {
        tagging_completeness: float       // 已标注资源占比
        teacher_confirmation_rate: float
        title_only_tag_rate: float        // 降级标注（TITLE_ONLY）占比
        compliance_flag_count: int
        compliance_flag_pending: int
    }                                     // 来源：analysis-db.COMPLIANCE_FLAG

    engagement_stats: {
        enrolled_student_count: int
        active_learner_count_7d: int
        avg_completion_rate: float
        most_accessed_resource_id: bigint
        least_accessed_resource_id: bigint
        avg_watch_rate: float
    }                                     // 来源：learn-db.LEARNING_PROGRESS 聚合

    assignment_stats: {
        assignment_count: int
        avg_class_score: float
        score_distribution: [{ range: string, count: int }]
        ai_grading_success_rate: float
    }                                     // 来源：assign-db.SUBMISSION 聚合

    lifecycle_state: enum {
        DRAFT,           // 草稿，无学生
        ACTIVE,          // 正常运行中
        LOW_ENGAGEMENT,  // 活跃学习者 < 20%
        CONTENT_ISSUE,   // 合规预警未处置
        ARCHIVED
    }
}
```

---

### 2.3 系统运行状态孪生（SystemRuntimeTwin）

**物理映射**：整个K8s集群 + 所有微服务 + 基础设施

**状态属性定义**：

```
SystemRuntimeTwin {
    twin_version: timestamp
    overall_health: enum { HEALTHY, DEGRADED, CRITICAL }

    services: [{
        service_name: string
        namespace: enum { biz, ai, infra }
        replicas_ready: int
        replicas_desired: int
        cpu_usage_pct: float
        memory_usage_pct: float
        p99_latency_ms: float
        error_rate_pct: float
        hpa_triggered: boolean
        health: enum { HEALTHY, DEGRADED, DOWN }
    }]                                    // 来源：Prometheus Micrometer 指标

    kafka_state: [{
        topic: string
        consumer_group: string
        lag: bigint
        lag_seconds: float
        producer_rate: float
        consumer_rate: float
    }]                                    // 来源：Kafka JMX / Kafka UI API

    databases: [{
        db_name: string
        primary_healthy: boolean
        replica_lag_seconds: float
        active_connections: int
        slow_query_count_1h: int
        disk_usage_pct: float
    }]                                    // 来源：PostgreSQL pg_stat_* + Prometheus

    redis_state: {
        cluster_ok: boolean
        used_memory_pct: float
        hit_rate: float
        evicted_keys_1h: int
        connected_clients: int
    }

    ai_task_state: [{
        agent: string
        queue_depth: int
        avg_processing_time_ms: float
        claude_api_error_rate: float
        fallback_triggered_count_1h: int
    }]

    active_alerts: [{
        alert_name: string
        severity: enum { P1, P2 }
        triggered_at: timestamp
        service: string
    }]
}
```

---

### 2.4 教学效果孪生（TeachingEffectivenessTwin）

**物理映射**：teacher_id 维度的教学行为聚合

**状态属性定义**：

```
TeachingEffectivenessTwin {
    teacher_id: bigint
    twin_version: timestamp

    course_portfolio: [{
        course_id: bigint
        title: varchar
        enrolled_count: int
        avg_completion_rate: float
        avg_score: float
        content_quality_score: float
    }]

    graph_building: {
        total_resources: int
        tagged_resources: int
        confirmed_tags: int
        pending_confirmation: int
        knowledge_points_covered: int
        graph_density: float              // 资源/知识点比
    }

    assignment_management: {
        total_assignments: int
        avg_submission_rate: float
        avg_class_score: float
        manual_review_rate: float
        avg_review_turnaround_hours: float
    }

    mentoring: {
        active_mentee_count: int
        at_risk_mentee_count: int         // 处于AT_RISK状态的学生数；聚合自 StudentLearningTwin
        guidance_frequency_30d: float     // 来源：twin.cdc.mentor（监听 user-db.MENTOR_GUIDANCE）
        last_guidance_at: timestamp       // 来源：twin.cdc.mentor（监听 user-db.MENTOR_GUIDANCE）
    }

    effectiveness_score: {
        content_quality: float            // 0-100
        student_engagement: float
        learning_outcome: float
        responsiveness: float
        composite_score: float
    }
}
```

---

## 3. 数据采集点规划

### 3.1 现有 Kafka Topic 复用（零改造）

| 现有 Topic | 孪生用途 | 目标孪生实体 |
|-----------|---------|------------|
| `user.behavior` | 更新活跃度、进度统计、chat 交互统计 | StudentLearningTwin |
| `ability.updated` | 触发能力快照同步、流失风险重算 | StudentLearningTwin |
| `learning-path.generated` | 更新路线执行状态 | StudentLearningTwin |
| `submission.created` | 更新作业待批改队列深度 | StudentLearningTwin, TeachingEffectivenessTwin |
| `submission.graded` | 更新作业得分统计 | StudentLearningTwin, CourseContentTwin |
| `submission.reviewed` | 更新人工复核率指标 | TeachingEffectivenessTwin |
| `resource.uploaded` | 触发课程结构快照更新 | CourseContentTwin |
| `resource.tagged` | 更新知识图谱覆盖状态 | CourseContentTwin |
| `login.event` | 更新 `last_login_at`、活跃度状态、AT_RISK检测 | StudentLearningTwin |
| `notification.send` | 统计通知频率（间接反映系统事件密度） | SystemRuntimeTwin |
| `gateway.access` | 统计 API 访问频率、错误率趋势（来自 API Gateway 访问日志） | SystemRuntimeTwin |

### 3.2 新增孪生专用采集 Topic

现有 Topic 覆盖了业务事件，以下维度存在采集盲区，需新增：

**`twin.metrics.service`**

```
生产者：各微服务 Micrometer（通过 Prometheus Remote Write 桥接）
消费者：SystemRuntimeTwin 同步器
采集内容：service_name, instance_id, cpu_usage_pct, memory_usage_pct,
          http_request_count, http_error_count, p99_latency_ms, hpa_current_replicas
采集频率：30秒一次
```

**`twin.metrics.kafka`**

```
生产者：Kafka Exporter（独立采集进程，读取 Kafka JMX）
消费者：SystemRuntimeTwin 同步器
采集内容：topic, consumer_group, lag, producer_rate, consumer_rate
采集频率：15秒一次
```

**`twin.cdc.learn`**

```
生产者：Debezium CDC（监听 learn-db.LEARNING_PROGRESS 表）
消费者：StudentLearningTwin 同步器
用途：实时捕获学习进度变化，补充 user.behavior 事件中缺失的进度细节
```

**`twin.cdc.assign`**

```
生产者：Debezium CDC（监听 assign-db.SUBMISSION 表）
消费者：StudentLearningTwin / CourseContentTwin 同步器
用途：实时追踪作业批改状态机转换，补充 submission.graded 之外的中间状态
```

**`twin.cdc.ability`**

```
生产者：Debezium CDC（监听 learn-db.ABILITY_MAP 表）
消费者：StudentLearningTwin 同步器
用途：实时捕获能力图谱变化；ability.updated 事件有 30 分钟防抖，CDC 补充防抖窗口内
      的细粒度掌握度变更，确保 ability_snapshot 的实时性
```

**`twin.cdc.mentor`**

```
生产者：Debezium CDC（监听 user-db.MENTOR_GUIDANCE 表）
消费者：TeachingEffectivenessTwin 同步器
用途：实时更新 mentoring.guidance_frequency_30d 和 mentoring.last_guidance_at；
      user-db 中导师指导记录无对应 Kafka 事件，CDC 为唯一实时采集手段
```

### 3.3 冷启动全量快照拉取

孪生服务初始化时，通过 httpx 直接调用现有业务服务内部 API 进行全量快照拉取（Python 服务使用 httpx，非 Java Feign），后续通过 Kafka 增量更新。

所有冷启动调用均携带 `X-Internal-Token` 请求头（从 K8s Secret 注入），绕过 API Gateway 的角色过滤器，直接访问业务服务内部端点，不经过 Gateway 路由。

| 采集目标 | 调用服务 | 端点 | 用途 |
|---------|---------|------|------|
| 学生能力图谱全量 | learn-service | `GET /api/v1/students/{id}/ability-map` | StudentLearningTwin 冷启动 |
| 课程结构全量 | course-service | `GET /api/v1/courses/{id}/chapters` | CourseContentTwin 冷启动 |
| 作业提交统计 | assign-service | `GET /api/v1/assignments/{id}/submissions` | 历史数据回填 |
| 分析报告 | analysis-agent | `GET /api/v1/analysis/reports` | TeachingEffectivenessTwin 初始化（需 `X-Internal-Token`，该端点 Gateway 侧限制 ADMIN 角色） |
| 导师关系全量 | user-service | `GET /api/v1/mentor-relations` | TeachingEffectivenessTwin.mentoring 冷启动 |

---

## 4. 数据流向设计

### 4.1 整体数据流

```mermaid
flowchart LR
    subgraph Physical["物理系统（现有）"]
        direction TB
        BizSvc["业务微服务\nauth/user/course/learn/assign/notify/media"]
        AISvc["AI智能体\ntag/nav/grade/chat/analysis"]
        Infra["基础设施\nPostgreSQL × 9 / Redis / MinIO / K8s"]
    end

    subgraph Sync["数据同步层（新增）"]
        direction TB
        KafkaMirror["Kafka Topic 镜像消费者\n复用现有14个Topic\n独立Consumer Group: twin-sync"]
        Debezium["Debezium CDC\n监听 learn-db / assign-db / user-db"]
        MetricsExporter["Metrics Exporter\nPrometheus → Kafka桥接"]
        SnapshotLoader["快照加载器\n冷启动时 httpx 全量拉取\n携带 X-Internal-Token"]
    end

    subgraph TwinStore["孪生状态存储（新增）"]
        direction TB
        TwinDB[("twin-db\nPostgreSQL\n孪生实体状态持久化")]
        TwinCache[("twin-cache\nRedis（复用现有集群\nkey前缀 twin:*）\n热点孪生状态缓存")]
        TwinTS[("twin-timeseries\nTimescaleDB\n时序指标存储")]
    end

    subgraph TwinSvc["孪生服务层（新增）"]
        direction TB
        SyncWorker["TwinSyncWorker\n状态同步处理器"]
        QuerySvc["twin-query-service\nREST API 查询"]
        PredictSvc["twin-prediction-service\n预测分析引擎"]
        SimSvc["twin-simulation-service\n仿真推演引擎"]
        TwinNotify["twin-notify\nWebSocket推送孪生状态变更"]
    end

    Physical -->|"Kafka Events（现有Topic）"| KafkaMirror
    Physical -->|"DB变更流"| Debezium
    Infra -->|"Prometheus指标"| MetricsExporter
    Physical -->|"REST API（冷启动）"| SnapshotLoader

    KafkaMirror --> SyncWorker
    Debezium --> SyncWorker
    MetricsExporter --> SyncWorker
    SnapshotLoader --> SyncWorker

    SyncWorker --> TwinDB
    SyncWorker --> TwinCache
    SyncWorker --> TwinTS

    TwinDB & TwinCache --> QuerySvc
    TwinDB & TwinTS --> PredictSvc
    TwinDB --> SimSvc
    QuerySvc & PredictSvc --> TwinNotify
```

### 4.2 TwinSyncWorker 事件路由规则

| 事件 Topic | 触发动作 | 目标孪生实体 |
|-----------|---------|------------|
| `login.event` | `last_login_at` 更新 + `activity_state` 重算 | StudentLearningTwin |
| `user.behavior` | `progress_stats` 增量更新、`interaction_stats` 统计（chat 行为） | StudentLearningTwin |
| `ability.updated` | `ability_snapshot` 全量刷新 + `dropout_risk_score` 重算 | StudentLearningTwin |
| `learning-path.generated` | `path_execution` 更新 | StudentLearningTwin |
| `submission.created` | `assignment_performance.pending_count` +1 | StudentLearningTwin, TeachingEffectivenessTwin |
| `submission.graded` | `assignment_performance` 更新 + `avg_class_score` 重算 | StudentLearningTwin, CourseContentTwin, TeachingEffectivenessTwin |
| `submission.reviewed` | `manual_review_rate` 更新 | TeachingEffectivenessTwin |
| `resource.tagged` | `knowledge_coverage` 更新 + `content_quality` 重算 | CourseContentTwin |
| `gateway.access` | API 访问频率、错误率趋势统计 | SystemRuntimeTwin |
| `twin.metrics.service` | `services[*]` 更新 | SystemRuntimeTwin |
| `twin.metrics.kafka` | `kafka_state[*]` 更新 | SystemRuntimeTwin |
| `twin.cdc.learn` | `progress_stats` 细粒度更新 | StudentLearningTwin |
| `twin.cdc.assign` | 状态机同步 | StudentLearningTwin, CourseContentTwin |
| `twin.cdc.ability` | `ability_snapshot` 细粒度更新（防抖窗口内的掌握度变更） | StudentLearningTwin |
| `twin.cdc.mentor` | `mentoring.guidance_frequency_30d` / `last_guidance_at` 更新 | TeachingEffectivenessTwin |

---

## 5. 与现有系统的集成方案

### 5.1 集成架构（最小侵入性）

```mermaid
graph TB
    subgraph Existing["现有系统（零修改）"]
        GW["API Gateway\n:8080"]
        Kafka["Kafka Cluster\n现有14个Topic"]
        Services["业务微服务 + AI智能体"]
        DBs["各服务 PostgreSQL"]
        Nacos["Nacos\n服务注册中心"]
    end

    subgraph TwinNS["K8s Namespace: twin（新增）"]
        TwinGW["twin-gateway\n:8200"]
        SyncWorker["twin-sync-worker\nConsumer Group: twin-sync"]
        CDC["debezium-connector\nKafka Connect 部署"]
        MetricsBridge["prometheus-kafka-bridge"]
        QueryAPI["twin-query-service\n:8201"]
        PredictAPI["twin-prediction-service\n:8202"]
        SimAPI["twin-simulation-service\n:8203"]
        TwinNotify["twin-notify\n:8204\nWebSocket推送"]
        TwinDB[("twin-db\nPostgreSQL")]
        TwinCache[("twin-cache\nRedis（复用现有集群\nkey前缀 twin:*）")]
        TwinTS[("twin-timeseries\nTimescaleDB")]
    end

    Kafka -->|"独立Consumer Group\ntwin-sync"| SyncWorker
    DBs -->|"Debezium CDC\nKafka Connect\nlearn-db / assign-db / user-db"| CDC
    Services -->|"Prometheus Scrape"| MetricsBridge
    Nacos -->|"服务注册"| TwinNS

    CDC --> SyncWorker
    MetricsBridge --> SyncWorker
    SyncWorker --> TwinDB & TwinCache & TwinTS

    TwinDB & TwinCache --> QueryAPI
    TwinDB & TwinTS --> PredictAPI
    TwinDB --> SimAPI
    QueryAPI & PredictAPI --> TwinNotify

    GW -->|"新增路由\n/api/v1/twin/**\n/ws/twin/**"| TwinGW
    TwinGW --> QueryAPI & PredictAPI & SimAPI & TwinNotify
```

### 5.2 对现有系统的唯一改动

只需在 API Gateway 路由配置中追加两条规则：

```yaml
- id: twin-query-service
  uri: lb://twin-query-service
  predicates: [Path=/api/v1/twin/**]
  filters: [JwtAuthFilter, RoleFilter=ADMIN|TEACHER, StripPrefix=0]

- id: twin-notify-ws
  uri: lb:ws://twin-notify
  predicates: [Path=/ws/twin/**]
  filters: [JwtQueryParamAuthFilter, RoleFilter=ADMIN|TEACHER, StripPrefix=0]
```

**权限边界说明**：

| 角色 | 可访问端点 | 说明 |
|------|-----------|------|
| ADMIN | 全部孪生 API | 含系统运行状态孪生、所有学生/课程/教师孪生 |
| TEACHER | `/twin/students/{id}`、`/twin/courses/{id}`、`/twin/teachers/{id}`、预测与仿真端点 | 仅限自己课程的学生和课程；`/twin/system` 不对教师开放 |

各孪生服务在业务层进一步校验资源归属（如教师只能查询自己课程的学生孪生），不依赖 Gateway 单层过滤。

Debezium 通过 Kafka Connect 独立部署，以 PostgreSQL 逻辑复制槽（Logical Replication Slot）方式读取 WAL 日志，不需要修改任何现有服务代码。新增监听 `user-db.MENTOR_GUIDANCE` 表，需在 user-db 上开启逻辑复制（`wal_level=logical`），与 learn-db / assign-db 的配置方式一致。

### 5.3 孪生服务 API 端点

**twin-query-service**（REST 查询，仅提供 HTTP 端点）

```
GET  /api/v1/twin/students/{id}                  # 学生孪生画像（TEACHER/ADMIN）
GET  /api/v1/twin/students/{id}/risk             # 流失风险详情（TEACHER/ADMIN）
GET  /api/v1/twin/courses/{id}                   # 课程孪生状态（TEACHER/ADMIN）
GET  /api/v1/twin/teachers/{id}                  # 教学效果孪生（TEACHER 本人/ADMIN）
GET  /api/v1/twin/system                         # 系统运行状态孪生（ADMIN 专属）
GET  /api/v1/twin/system/alerts                  # 当前活跃告警（ADMIN 专属）
```

**twin-notify**（WebSocket 推送，独立承载长连接）

```
WS   /ws/twin/updates                            # 孪生状态变更实时推送（TEACHER/ADMIN）
```

twin-notify 订阅 twin-query-service 和 twin-prediction-service 的状态变更通知（通过内部 Redis Pub/Sub），向已连接的客户端推送增量更新，不提供 REST 查询能力。

**twin-prediction-service**

```
GET  /api/v1/twin/students/{id}/forecast         # 30天掌握度预测（TEACHER/ADMIN）
GET  /api/v1/twin/courses/{id}/engagement-trend  # 课程参与度趋势预测（TEACHER/ADMIN）
POST /api/v1/twin/simulation/learning-path       # 仿真：调整路线后的效果预测（TEACHER/ADMIN）
```

---

## 6. 孪生应用场景

### 6.1 预测性分析

**场景 A：学生流失风险预测**

```mermaid
flowchart LR
    A["StudentLearningTwin\n状态变更"] --> B["dropout_risk_score 重算\n特征：\n- activity_state\n- avg_daily_seconds趋势\n- overdue_count\n- path_adherence_rate\n- chat_session_count_7d"]
    B --> C{risk_score > 0.7?}
    C -->|是| D["recommended_intervention\n= NOTIFY_TEACHER"]
    C -->|否| E["recommended_intervention\n= NONE"]
    D --> F["twin-notify 推送\n教师预警面板"]
    D --> G["触发 notification.send\n→ 现有notify-service\n发送站内通知给教师"]
```

**场景 B：知识点掌握度预测**

基于 `ability_snapshot` 历史时序数据（存储在 TwinTS），对每个知识点拟合学习曲线，预测30天后的掌握状态，输出到 `StudentLearningTwin.predictions.mastery_forecast_30d`，供 nav-agent 在路线生成时参考。

**场景 C：课程内容质量预警**

当 `CourseContentTwin.content_quality.title_only_tag_rate > 0.3`（超过30%资源为降级标注）时，自动生成内容质量预警，推送给对应教师。

---

### 6.2 实时监控

**系统运行监控大屏**（管理员视角，扩展现有 `/admin/stats`）：

| 面板区域 | 数据来源 | 刷新方式 |
|---------|---------|---------|
| 服务健康矩阵（13个服务 × 副本数/CPU/内存/错误率） | SystemRuntimeTwin.services | WebSocket推送，≤5s |
| Kafka消费延迟趋势 | SystemRuntimeTwin.kafka_state | WebSocket推送，≤5s |
| 实时在线学习人数 / AI任务队列深度 | StudentLearningTwin聚合 | WebSocket推送，≤5s |
| AT_RISK学生数 / 合规预警未处置数 / P1告警 | StudentLearningTwin + CourseContentTwin | WebSocket推送，≤5s |

**教师学生预警面板**（扩展现有 `/teacher/students`）：

| 预警类型 | 触发条件 | 展示信息 |
|---------|---------|---------|
| 流失风险 | dropout_risk_score > 0.7 | 学生姓名、风险分、最后活跃时间、建议干预方式 |
| 作业逾期 | overdue_count > 0 | 逾期作业数、最近逾期作业名称 |
| 能力停滞 | 连续14天 ability_snapshot 无变化 | 停滞知识点列表 |
| 路线偏离 | path_adherence_rate < 0.3 | 推荐路线与实际学习路径的偏差 |

---

### 6.3 仿真推演

**场景：调整学习路线后的效果仿真**

```mermaid
sequenceDiagram
    actor Teacher as 教师
    participant SimAPI as twin-simulation-service
    participant TwinDB as twin-db
    participant PredictSvc as twin-prediction-service

    Teacher->>SimAPI: POST /twin/simulation/learning-path\n{student_id, proposed_path_items}
    SimAPI->>TwinDB: 读取 StudentLearningTwin 当前状态
    SimAPI->>SimAPI: 克隆孪生状态（沙箱模式，不修改原状态）
    SimAPI->>SimAPI: 模拟执行 proposed_path_items\n（基于历史完成率和掌握度提升规律）
    SimAPI->>PredictSvc: 预测调整后30天掌握度
    PredictSvc-->>SimAPI: 预测结果
    SimAPI-->>Teacher: 返回对比报告\n{current_forecast, proposed_forecast, delta}
```

仿真结果仅作为辅助参考，不自动修改物理系统中的学习路线。

---

## 7. 孪生数据库设计

### 7.1 twin-db 核心表结构

```mermaid
erDiagram
    STUDENT_TWIN {
        bigint student_id PK
        enum activity_state
        jsonb ability_snapshot
        jsonb progress_stats
        jsonb assignment_performance
        jsonb interaction_stats
        jsonb path_execution
        jsonb predictions
        timestamp twin_version
    }
    COURSE_TWIN {
        bigint course_id PK
        jsonb structure
        jsonb knowledge_coverage
        jsonb content_quality
        jsonb engagement_stats
        jsonb assignment_stats
        enum lifecycle_state
        timestamp twin_version
    }
    TEACHER_TWIN {
        bigint teacher_id PK
        jsonb course_portfolio
        jsonb graph_building
        jsonb assignment_management
        jsonb mentoring
        jsonb effectiveness_score
        timestamp twin_version
    }
    SYSTEM_TWIN {
        int id PK
        enum overall_health
        jsonb services
        jsonb kafka_state
        jsonb databases
        jsonb redis_state
        jsonb ai_task_state
        jsonb active_alerts
        timestamp twin_version
    }
```

### 7.2 twin-timeseries 时序表结构（TimescaleDB）

```sql
-- 学生活跃度时序
CREATE TABLE student_activity_ts (
    time        TIMESTAMPTZ NOT NULL,
    student_id  BIGINT NOT NULL,
    metric      VARCHAR(64),  -- 'daily_seconds', 'completion_rate', 'dropout_risk_score'
    value       FLOAT
);
SELECT create_hypertable('student_activity_ts', 'time');

-- 系统指标时序
CREATE TABLE system_metrics_ts (
    time         TIMESTAMPTZ NOT NULL,
    service_name VARCHAR(64),
    metric       VARCHAR(64),  -- 'cpu_pct', 'p99_latency_ms', 'error_rate_pct'
    value        FLOAT
);
SELECT create_hypertable('system_metrics_ts', 'time');
```

---

## 8. 部署规划

### 8.1 新增 K8s 资源清单

```yaml
# Namespace: twin
services:
  twin-sync-worker:        # Kafka消费 + CDC处理，端口 8300
  twin-query-service:      # REST查询API，端口 8201
  twin-prediction-service: # 预测分析，端口 8202
  twin-simulation-service: # 仿真推演，端口 8203
  twin-notify:             # WebSocket推送，端口 8204
  debezium-connect:        # Kafka Connect + Debezium，端口 8083
  prometheus-kafka-bridge: # 指标桥接，端口 8084

databases:
  twin-db:                 # PostgreSQL，端口 5432
  twin-timeseries:         # TimescaleDB，端口 5433
  # twin-cache 复用现有 Redis Cluster（infra namespace），不单独部署
  # 孪生层缓存 key 统一使用 twin:* 前缀与业务层隔离
  # 若孪生缓存量级增大（预估超过 Redis 总内存 20%），再评估独立部署
```

### 8.2 HPA 配置

| 服务 | 初始副本 | 最大副本 | 触发条件 |
|------|---------|---------|---------|
| twin-sync-worker | 2 | 6 | Kafka `twin.*` Topic 消费延迟 > 30s |
| twin-query-service | 2 | 4 | CPU > 70% |
| twin-prediction-service | 1 | 3 | CPU > 70% |
| twin-simulation-service | 1 | 2 | 按需手动扩容 |
| twin-notify | 2 | 4 | 连接数 > 200/副本 |

---

## 9. 孪生服务代码结构

### 9.1 twin-sync-worker（Python）

```
twin-sync-worker/
├── main.py                         # 启动入口，初始化消费者和快照加载器
├── consumer/
│   ├── kafka_consumer.py           # aiokafka 消费现有14个Topic + 6个孪生Topic
│   └── event_router.py             # 事件路由：Topic → 对应孪生实体更新器
├── updater/
│   ├── student_twin_updater.py     # StudentLearningTwin 状态更新逻辑
│   ├── course_twin_updater.py      # CourseContentTwin 状态更新逻辑
│   ├── teacher_twin_updater.py     # TeachingEffectivenessTwin 状态更新逻辑
│   └── system_twin_updater.py      # SystemRuntimeTwin 状态更新逻辑
├── snapshot/
│   └── snapshot_loader.py          # 冷启动全量快照拉取（httpx 调用现有业务服务内部 API，
│                                   #   携带 X-Internal-Token，不经过 API Gateway）
├── db/
│   ├── twin_db.py                  # twin-db asyncpg 连接池
│   ├── twin_cache.py               # Redis 连接（复用现有 Redis Cluster，key 前缀 twin:*）
│   └── twin_ts.py                  # twin-timeseries asyncpg 连接池
├── config.py
├── requirements.txt
└── Dockerfile
```

### 9.2 twin-query-service（Python FastAPI）

```
twin-query-service/
├── main.py
├── router/
│   ├── student_router.py           # GET /twin/students/{id}，含资源归属校验
│   ├── course_router.py            # GET /twin/courses/{id}
│   ├── teacher_router.py           # GET /twin/teachers/{id}
│   └── system_router.py            # GET /twin/system（ADMIN 专属）
├── db/
│   └── twin_db.py
├── config.py
└── Dockerfile
```

### 9.3 twin-notify（Python FastAPI + WebSocket）

```
twin-notify/
├── main.py
├── websocket/
│   ├── twin_ws_handler.py          # WS /ws/twin/updates 连接管理
│   └── ws_session_registry.py      # 本实例 WebSocket 连接注册表
├── subscriber/
│   └── redis_subscriber.py         # 订阅 Redis Pub/Sub twin:notify:{user_id}，
│                                   #   接收 twin-query-service / twin-prediction-service
│                                   #   的状态变更通知并推送给已连接客户端
├── config.py
└── Dockerfile
```

### 9.4 twin-prediction-service（Python FastAPI）

```
twin-prediction-service/
├── main.py
├── router/
│   ├── forecast_router.py          # GET /twin/students/{id}/forecast
│   └── simulation_router.py        # POST /twin/simulation/learning-path
├── model/
│   ├── dropout_risk_model.py       # 流失风险评分模型（特征工程 + 规则/ML）
│   ├── mastery_forecast_model.py   # 掌握度预测（学习曲线拟合）
│   └── simulation_engine.py        # 路线调整效果仿真引擎
├── db/
│   ├── twin_db.py
│   └── twin_ts.py
├── config.py
└── Dockerfile
```

---

## 10. Nacos 配置规划

```
# 孪生服务公共配置
twin-common-config.yaml
  - kafka.bootstrap-servers: kafka:9092
  - kafka.consumer-group: twin-sync
  - twin-db.url: postgresql://twin-db:5432/twin
  - twin-cache.nodes: redis-0:6379,...   # 复用现有 Redis Cluster，key 前缀 twin:*
  - twin-timeseries.url: postgresql://twin-timeseries:5433/twin_ts

# 各孪生服务独立配置
twin-sync-worker-prod.yaml
  - snapshot.batch-size: 100
  - snapshot.concurrency: 4

twin-prediction-service-prod.yaml
  - dropout-risk.threshold: 0.7
  - forecast.horizon-days: 30

twin-query-service-prod.yaml
  - cache.ttl-seconds: 30

twin-notify-prod.yaml
  - websocket.max-connections-per-replica: 200
  - redis.pubsub.channel-prefix: twin:notify:
```

---

## 11. 关键设计决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 孪生状态存储格式 | jsonb（PostgreSQL） | 各孪生实体结构差异大，jsonb 灵活且支持索引查询 |
| 时序存储 | TimescaleDB | 基于 PostgreSQL 扩展，运维成本低，支持时序压缩和连续聚合 |
| CDC 工具 | Debezium + Kafka Connect | 与现有 Kafka 基础设施复用，零侵入业务服务 |
| 孪生层与业务层隔离 | 独立 K8s Namespace + 独立数据库 | 孪生层故障不影响业务系统，资源独立管控 |
| 预测模型 | 规则引擎 + 简单统计模型 | 初期数据量有限，避免过度工程化；后期可替换为 ML 模型 |
| 仿真模式 | 沙箱克隆（只读） | 仿真不修改物理系统状态，保证数据安全 |
| 孪生层缓存 | 复用现有 Redis Cluster（key 前缀 twin:*） | 避免额外运维成本；孪生缓存量级预估较小，独立部署收益不足以覆盖成本；量级增大后可迁移 |
| WebSocket 推送 | twin-notify 独立服务 | 与 twin-query-service 职责分离；长连接管理与 REST 查询独立扩缩容，避免相互影响 |
| 冷启动鉴权 | X-Internal-Token 直连业务服务内部端点 | 绕过 Gateway 角色过滤器，与现有服务间 Feign 调用鉴权机制一致 |
| interaction_stats 数据来源 | user.behavior 事件（Kafka） | 零侵入原则，不直接访问 chat-db；chat-agent 在会话结束时通过 user.behavior 事件上报交互统计 |

---

*物理系统设计详见 SDD-CloudTeachingAI.md v2.5*

