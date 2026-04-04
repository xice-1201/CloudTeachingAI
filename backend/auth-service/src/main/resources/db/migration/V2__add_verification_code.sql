-- 验证码表
CREATE TABLE verification_code (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(100) NOT NULL,
    code VARCHAR(10) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX idx_verification_code_email ON verification_code(email);
CREATE INDEX idx_verification_code_expires_at ON verification_code(expires_at);

-- 评论
COMMENT ON TABLE verification_code IS '邮箱验证码表';
COMMENT ON COLUMN verification_code.email IS '邮箱地址';
COMMENT ON COLUMN verification_code.code IS '验证码';
COMMENT ON COLUMN verification_code.expires_at IS '过期时间';
COMMENT ON COLUMN verification_code.used IS '是否已使用';
COMMENT ON COLUMN verification_code.created_at IS '创建时间';
