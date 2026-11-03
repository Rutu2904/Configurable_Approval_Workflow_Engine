-- Users (password = {noop}password123 for all)
MERGE INTO users (id, username, password) KEY(username)
    VALUES (1, 'alice', '{noop}password123'),
           (2, 'bob',   '{noop}password123'),
           (3, 'carol', '{noop}password123'),
           (4, 'dave',  '{noop}password123'),
           (5, 'admin', '{noop}password123');

MERGE INTO user_roles (user_id, role) KEY(user_id, role)
    VALUES (1, 'REQUESTER'),
           (2, 'APPROVER'), (2, 'MANAGER'),
           (3, 'APPROVER'), (3, 'HR'),
           (4, 'APPROVER'), (4, 'FINANCE'),
           (5, 'ADMIN');

-- Workflow steps: to add a new workflow, simply insert rows below with a
-- new request_type and sequential step_order values. No code changes needed.
MERGE INTO approval_steps (id, request_type, step_order, role) KEY(request_type, step_order)
    VALUES (1, 'LEAVE',   1, 'MANAGER'),
           (2, 'LEAVE',   2, 'HR'),
           (3, 'EXPENSE', 1, 'MANAGER'),
           (4, 'EXPENSE', 2, 'FINANCE');
