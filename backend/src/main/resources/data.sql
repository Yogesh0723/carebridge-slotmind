INSERT INTO providers (id,name,specialty,status) VALUES
('11111111-1111-1111-1111-111111111111','Dr. Sarah Chen','GENERAL','BUSY'),
('22222222-2222-2222-2222-222222222222','Dr. James Okonkwo','SPECIALIST','AVAILABLE'),
('33333333-3333-3333-3333-333333333333','Dr. Priya Sharma','PEDIATRIC','BUSY'),
('44444444-4444-4444-4444-444444444444','Dr. Rahul Verma','GENERAL','AVAILABLE'),
('55555555-5555-5555-5555-555555555555','Dr. Ananya Iyer','SPECIALIST','BUSY');
INSERT INTO slots (id,provider_id,starts_at,ends_at,status) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','11111111-1111-1111-1111-111111111111',datetime('now','+60 minutes'),datetime('now','+90 minutes'),'BOOKED'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb','11111111-1111-1111-1111-111111111111',datetime('now','+120 minutes'),datetime('now','+150 minutes'),'BOOKED'),
('cccccccc-cccc-cccc-cccc-cccccccccccc','33333333-3333-3333-3333-333333333333',datetime('now','+90 minutes'),datetime('now','+135 minutes'),'BOOKED'),
('dddddddd-dddd-dddd-dddd-dddddddddddd','22222222-2222-2222-2222-222222222222',datetime('now','+180 minutes'),datetime('now','+210 minutes'),'AVAILABLE'),
('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee','44444444-4444-4444-4444-444444444444',datetime('now','+150 minutes'),datetime('now','+180 minutes'),'AVAILABLE'),
('ffffffff-ffff-ffff-ffff-ffffffffffff','55555555-5555-5555-5555-555555555555',datetime('now','+240 minutes'),datetime('now','+300 minutes'),'BOOKED');
INSERT INTO appointments (id,patient_name,patient_contact,type,provider_id,slot_id,status) VALUES
('aaaaaaaa-1111-1111-1111-111111111111','John Martinez','555-0101','ROUTINE','11111111-1111-1111-1111-111111111111','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','CONFIRMED'),
('bbbbbbbb-2222-2222-2222-222222222222','Emily Watson','555-0102','FOLLOW_UP','11111111-1111-1111-1111-111111111111','bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb','CONFIRMED'),
('cccccccc-3333-3333-3333-333333333333','Aisha Patel','555-0103','URGENT','33333333-3333-3333-3333-333333333333','cccccccc-cccc-cccc-cccc-cccccccccccc','CONFIRMED'),
('dddddddd-4444-4444-4444-444444444444','Robert Kim','555-0104','ROUTINE','55555555-5555-5555-5555-555555555555','ffffffff-ffff-ffff-ffff-ffffffffffff','CONFIRMED');