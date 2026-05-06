CREATE TABLE admin_audit_log (
    id BIGSERIAL PRIMARY KEY,
    actor_id BIGINT,
    actor_name VARCHAR(100),
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(80) NOT NULL,
    target_id BIGINT,
    target_name VARCHAR(255),
    detail TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_admin_audit_log_created_at ON admin_audit_log(created_at DESC);
CREATE INDEX idx_admin_audit_log_action ON admin_audit_log(action);
CREATE INDEX idx_admin_audit_log_actor_id ON admin_audit_log(actor_id);
CREATE INDEX idx_admin_audit_log_target ON admin_audit_log(target_type, target_id);
