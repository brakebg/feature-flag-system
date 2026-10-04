-- Spec 4.3: demo data, dev profile only (spring.flyway.locations in application-dev.yml).
-- Seed rows use `system` as the creator (spec 4.1).

INSERT INTO flag_group (id, key, name, description, created_at, created_by, updated_at, updated_by, version)
VALUES ('0191f0c2-0000-7000-8000-000000000001', 'orders', 'Orders', NULL, now(), 'system', now(), 'system', 0);

INSERT INTO feature_flag (id, group_id, key, description, enabled, created_at, created_by, updated_at, updated_by, version)
VALUES ('0191f0c3-0000-7000-8000-000000000001', '0191f0c2-0000-7000-8000-000000000001', 'new-checkout', NULL, true,  now(), 'system', now(), 'system', 0),
       ('0191f0c3-0000-7000-8000-000000000002', '0191f0c2-0000-7000-8000-000000000001', 'split-payments', NULL, false, now(), 'system', now(), 'system', 0);
