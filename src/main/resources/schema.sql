-- Users table
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL
);

-- User roles table
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Requests table
CREATE TABLE requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(20) NOT NULL,
    current_step_order INT,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (created_by) REFERENCES users(id)
);

-- Approval steps define the workflow for each request type
CREATE TABLE approval_steps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_type VARCHAR(50) NOT NULL,
    step_order INT NOT NULL,
    role VARCHAR(50) NOT NULL,
    UNIQUE (request_type, step_order)
);

-- Approval history is append-only
CREATE TABLE approval_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    step_order INT,
    action VARCHAR(20) NOT NULL,
    action_by BIGINT NOT NULL,
    action_at TIMESTAMP NOT NULL,
    comments VARCHAR(500),
    FOREIGN KEY (request_id) REFERENCES requests(id),
    FOREIGN KEY (action_by) REFERENCES users(id)
);

