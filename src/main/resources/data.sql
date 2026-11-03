-- Users (all passwords are "password123" with {noop} prefix for plain text)
INSERT INTO users (id, username, password) VALUES (1, 'alice', '{noop}password123');
INSERT INTO users (id, username, password) VALUES (2, 'bob', '{noop}password123');
INSERT INTO users (id, username, password) VALUES (3, 'carol', '{noop}password123');
INSERT INTO users (id, username, password) VALUES (4, 'dave', '{noop}password123');
INSERT INTO users (id, username, password) VALUES (5, 'admin', '{noop}password123');

-- User roles
INSERT INTO user_roles (user_id, role) VALUES (1, 'REQUESTER');
INSERT INTO user_roles (user_id, role) VALUES (2, 'APPROVER');
INSERT INTO user_roles (user_id, role) VALUES (2, 'MANAGER');
INSERT INTO user_roles (user_id, role) VALUES (3, 'APPROVER');
INSERT INTO user_roles (user_id, role) VALUES (3, 'HR');
INSERT INTO user_roles (user_id, role) VALUES (4, 'APPROVER');
INSERT INTO user_roles (user_id, role) VALUES (4, 'FINANCE');
INSERT INTO user_roles (user_id, role) VALUES (5, 'ADMIN');

-- Workflow: LEAVE requires MANAGER approval then HR approval
INSERT INTO approval_steps (id, request_type, step_order, role) VALUES (1, 'LEAVE', 1, 'MANAGER');
INSERT INTO approval_steps (id, request_type, step_order, role) VALUES (2, 'LEAVE', 2, 'HR');

-- Workflow: EXPENSE requires MANAGER approval then FINANCE approval
INSERT INTO approval_steps (id, request_type, step_order, role) VALUES (3, 'EXPENSE', 1, 'MANAGER');
INSERT INTO approval_steps (id, request_type, step_order, role) VALUES (4, 'EXPENSE', 2, 'FINANCE');

-- To add a new workflow, just insert rows here. For example:
-- INSERT INTO approval_steps (request_type, step_order, role) VALUES ('PURCHASE_ORDER', 1, 'MANAGER');
-- INSERT INTO approval_steps (request_type, step_order, role) VALUES ('PURCHASE_ORDER', 2, 'FINANCE');
-- INSERT INTO approval_steps (request_type, step_order, role) VALUES ('PURCHASE_ORDER', 3, 'DIRECTOR');
-- (Make sure the DIRECTOR role is assigned to a user in user_roles)

