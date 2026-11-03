CREATE TABLE IF NOT EXISTS users (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50)  UNIQUE NOT NULL,
    password VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT      NOT NULL,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS requests (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    type              VARCHAR(30)   NOT NULL,
    title             VARCHAR(200),
    description       VARCHAR(1000),
    status            VARCHAR(20)   NOT NULL,
    current_step_order INT,
    created_by        BIGINT        NOT NULL,
    created_at        TIMESTAMP     NOT NULL,
    version           BIGINT        DEFAULT 0 NOT NULL,
    CONSTRAINT fk_requests_user FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS approval_steps (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_type VARCHAR(30) NOT NULL,
    step_order   INT         NOT NULL,
    role         VARCHAR(30) NOT NULL,
    CONSTRAINT uq_approval_steps UNIQUE (request_type, step_order)
);

CREATE INDEX IF NOT EXISTS idx_approval_steps_request_type ON approval_steps(request_type);

CREATE TABLE IF NOT EXISTS approval_history (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT      NOT NULL,
    step_order INT,
    action     VARCHAR(30) NOT NULL,
    action_by  BIGINT      NOT NULL,
    action_at  TIMESTAMP   NOT NULL,
    comments   VARCHAR(500),
    CONSTRAINT fk_history_request FOREIGN KEY (request_id) REFERENCES requests(id),
    CONSTRAINT fk_history_user    FOREIGN KEY (action_by)  REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_approval_history_request_id ON approval_history(request_id);
