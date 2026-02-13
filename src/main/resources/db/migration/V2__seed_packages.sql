INSERT INTO payment_packages (id, code, title, credits, amount_minor, currency, is_active)
VALUES
    ('7ba9ca0e-3e5c-42df-9830-cc3dfe810f52', 'CREDITS_20', '20 credits', 20, 499000, 'KZT', TRUE),
    ('4b66f8f8-a56e-4a42-9f42-a6ff8f7faf5d', 'CREDITS_50', '50 credits', 50, 1099000, 'KZT', TRUE),
    ('073f3352-a2a1-41cf-8884-abd89e6d1d8c', 'CREDITS_120', '120 credits', 120, 2399000, 'KZT', TRUE)
ON CONFLICT (code) DO NOTHING;
