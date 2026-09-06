INSERT INTO app.app_user (id, username, password_hash, role, enabled)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'demo_user', '$2y$10$PZqVYdYFlgc9xDd9Dn1Pn.WRpb380Ez7zuOvgwrHcMsVnTVLMYTRm', 'USER', true),
    ('00000000-0000-0000-0000-000000000002', 'demo_admin', '$2y$10$PibfJPnw3zSeO5.RddY.guJmalsANLCn8TjylunEfbZM4RIfgxtEa', 'ADMIN', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO app.service_item (id, name, category, district, description, price, enabled)
VALUES
    ('10000000-0000-0000-0000-000000000001', '上门助洁', 'CLEANING', '武侯区', '虚构演示服务：基础居家清洁。', 80.00, true),
    ('10000000-0000-0000-0000-000000000002', '助餐配送', 'MEAL_DELIVERY', '锦江区', '虚构演示服务：适老餐食配送。', 30.00, true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO app.service_slot (id, service_id, start_at, end_at, total_capacity, remaining_capacity)
VALUES
    ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     (CURRENT_DATE + 1 + TIME '09:00') AT TIME ZONE 'Asia/Shanghai',
     (CURRENT_DATE + 1 + TIME '10:00') AT TIME ZONE 'Asia/Shanghai', 10, 10),
    ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001',
     (CURRENT_DATE + 1 + TIME '14:00') AT TIME ZONE 'Asia/Shanghai',
     (CURRENT_DATE + 1 + TIME '15:00') AT TIME ZONE 'Asia/Shanghai', 2, 2),
    ('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000002',
     (CURRENT_DATE + 2 + TIME '11:00') AT TIME ZONE 'Asia/Shanghai',
     (CURRENT_DATE + 2 + TIME '12:00') AT TIME ZONE 'Asia/Shanghai', 5, 5)
ON CONFLICT (id) DO NOTHING;
