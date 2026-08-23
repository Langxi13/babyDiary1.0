SET @fixture_account_id = (
  SELECT account_id FROM account WHERE username=@fixture_username AND deleted_at IS NULL LIMIT 1
);
SET @fixture_space_id = (
  SELECT space_id FROM diary_space WHERE personal_owner_id=@fixture_account_id AND deleted_at IS NULL LIMIT 1
);

CREATE TEMPORARY TABLE fixture_ones (n int PRIMARY KEY);
INSERT INTO fixture_ones VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9);
CREATE TEMPORARY TABLE fixture_tens LIKE fixture_ones;
CREATE TEMPORARY TABLE fixture_hundreds LIKE fixture_ones;
CREATE TEMPORARY TABLE fixture_thousands LIKE fixture_ones;
CREATE TEMPORARY TABLE fixture_ten_thousands LIKE fixture_ones;
INSERT INTO fixture_tens SELECT n FROM fixture_ones;
INSERT INTO fixture_hundreds SELECT n FROM fixture_ones;
INSERT INTO fixture_thousands SELECT n FROM fixture_ones;
INSERT INTO fixture_ten_thousands SELECT n FROM fixture_ones;
CREATE TEMPORARY TABLE fixture_numbers (n int PRIMARY KEY);
INSERT INTO fixture_numbers
SELECT ones.n + tens.n*10 + hundreds.n*100 + thousands.n*1000 + ten_thousands.n*10000 + 1
FROM fixture_ones ones
CROSS JOIN fixture_tens tens
CROSS JOIN fixture_hundreds hundreds
CROSS JOIN fixture_thousands thousands
CROSS JOIN fixture_ten_thousands ten_thousands
WHERE ones.n + tens.n*10 + hundreds.n*100 + thousands.n*1000 + ten_thousands.n*10000 < 20000;

INSERT INTO tag(public_id,space_id,name,color,created_by)
SELECT UUID_TO_BIN(CONCAT('30000000-0000-4000-8000-',LPAD(HEX(n),12,'0'))),
       @fixture_space_id,CONCAT('性能标签 ',n),
       ELT(n,'#B14F64','#3A7D6B','#D89068','#5B6FA8','#8A6D9B','#447C8B','#9C6A4C','#587A52','#A25353','#6A7280'),
       @fixture_account_id
FROM fixture_numbers WHERE n<=10;

INSERT INTO diary(public_id,space_id,author_id,title,diary_date,content_html,content_text,
                  mood_key,visibility,locked,version,created_at,updated_at)
SELECT UUID_TO_BIN(CONCAT('10000000-0000-4000-8000-',LPAD(HEX(n),12,'0'))),
       @fixture_space_id,@fixture_account_id,CONCAT('合成性能日记 ',n),
       DATE_ADD('2016-09-01',INTERVAL MOD(n-1,3652) DAY),
       CONCAT('<p>第 ',n,' 篇确定性合成日记，用于性能验证，不包含真实用户数据。</p>'),
       CONCAT('第 ',n,' 篇确定性合成日记，用于性能验证，不包含真实用户数据。'),
       ELT(MOD(n-1,5)+1,'HAPPY','CALM','EXCITED','GRATEFUL','TIRED'),
       IF(MOD(n,4)=0,'PRIVATE','SHARED'),MOD(n,20)=0,1,
       DATE_ADD('2016-09-01',INTERVAL MOD(n-1,3652) DAY),
       DATE_ADD('2016-09-01',INTERVAL MOD(n-1,3652) DAY)
FROM fixture_numbers WHERE n<=10000;

INSERT INTO diary_tag(space_id,diary_id,tag_id)
SELECT @fixture_space_id,d.diary_id,t.tag_id
FROM fixture_numbers f
JOIN diary d ON d.public_id=UUID_TO_BIN(CONCAT('10000000-0000-4000-8000-',LPAD(HEX(f.n),12,'0')))
JOIN tag t ON t.public_id=UUID_TO_BIN(CONCAT('30000000-0000-4000-8000-',LPAD(HEX(MOD(f.n-1,10)+1),12,'0')))
WHERE f.n<=10000;

INSERT INTO media_asset(public_id,space_id,owner_id,media_type,original_filename,caption,taken_at,
                        access_scope,library_visible,status,derivative_version,created_at,updated_at)
SELECT UUID_TO_BIN(CONCAT('20000000-0000-4000-8000-',LPAD(HEX(n),12,'0'))),
       @fixture_space_id,@fixture_account_id,'IMAGE',CONCAT('synthetic-',n,'.png'),
       CONCAT('合成图片 ',n),DATE_ADD('2016-09-01',INTERVAL MOD(CEIL(n/2)-1,3652) DAY),
       IF(MOD(n,3)=0,'SPACE','LINKED'),true,'READY',1,
       DATE_ADD('2016-09-01',INTERVAL MOD(CEIL(n/2)-1,3652) DAY),
       DATE_ADD('2016-09-01',INTERVAL MOD(CEIL(n/2)-1,3652) DAY)
FROM fixture_numbers;

INSERT INTO media_variant(asset_id,variant_type,profile,storage_provider,storage_key,content_type,
                          size_bytes,width,height,status,created_at,updated_at)
SELECT a.asset_id,v.variant_type,v.profile,'LOCAL',
       CONCAT('performance/',LOWER(v.profile),'-',f.n),'image/png',68,v.width,v.height,'READY',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
FROM fixture_numbers f
JOIN media_asset a ON a.public_id=UUID_TO_BIN(CONCAT('20000000-0000-4000-8000-',LPAD(HEX(f.n),12,'0')))
CROSS JOIN (
  SELECT 'ORIGINAL' AS variant_type,'source' AS profile,1 AS width,1 AS height
  UNION ALL SELECT 'THUMBNAIL','compact',1,1
  UNION ALL SELECT 'PREVIEW','screen',1,1
) v;

INSERT INTO diary_media(space_id,diary_id,asset_id,position)
SELECT @fixture_space_id,d.diary_id,a.asset_id,MOD(f.n-1,2)
FROM fixture_numbers f
JOIN diary d ON d.public_id=UUID_TO_BIN(CONCAT('10000000-0000-4000-8000-',LPAD(HEX(CEIL(f.n/2)),12,'0')))
JOIN media_asset a ON a.public_id=UUID_TO_BIN(CONCAT('20000000-0000-4000-8000-',LPAD(HEX(f.n),12,'0')));

INSERT INTO album_group(public_id,space_id,name,sort_order,created_by)
VALUES(UUID_TO_BIN('40000000-0000-4000-8000-000000000001'),@fixture_space_id,'性能相册组',0,@fixture_account_id);

INSERT INTO album(public_id,space_id,group_id,created_by,name,description,type,sort_order)
SELECT UUID_TO_BIN(CONCAT('50000000-0000-4000-8000-',LPAD(HEX(n),12,'0'))),
       @fixture_space_id,g.group_id,@fixture_account_id,CONCAT('性能相册 ',n),'确定性合成相册','CUSTOM',n
FROM fixture_numbers f
JOIN album_group g ON g.public_id=UUID_TO_BIN('40000000-0000-4000-8000-000000000001')
WHERE f.n<=10;

INSERT INTO album_media(space_id,album_id,asset_id,position)
SELECT @fixture_space_id,al.album_id,a.asset_id,FLOOR((f.n-1)/10)
FROM fixture_numbers f
JOIN album al ON al.public_id=UUID_TO_BIN(CONCAT('50000000-0000-4000-8000-',LPAD(HEX(MOD(f.n-1,10)+1),12,'0')))
JOIN media_asset a ON a.public_id=UUID_TO_BIN(CONCAT('20000000-0000-4000-8000-',LPAD(HEX(f.n),12,'0')));

INSERT INTO favorite_media(space_id,account_id,asset_id,created_at)
SELECT @fixture_space_id,@fixture_account_id,a.asset_id,UTC_TIMESTAMP(6)
FROM fixture_numbers f
JOIN media_asset a ON a.public_id=UUID_TO_BIN(CONCAT('20000000-0000-4000-8000-',LPAD(HEX(f.n),12,'0')))
WHERE MOD(f.n,10)=0;

INSERT INTO diary_draft(public_id,space_id,owner_id,draft_key,payload,created_at,updated_at)
SELECT UUID_TO_BIN(CONCAT('70000000-0000-4000-8000-',LPAD(HEX(n),12,'0'))),
       @fixture_space_id,@fixture_account_id,CONCAT('create-performance-',n),
       JSON_OBJECT('title',CONCAT('性能草稿 ',n)),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
FROM fixture_numbers WHERE n<=3;

INSERT INTO anniversary(public_id,space_id,created_by,title,anniversary_date,sort_order)
SELECT UUID_TO_BIN(CONCAT('60000000-0000-4000-8000-',LPAD(HEX(n),12,'0'))),
       @fixture_space_id,@fixture_account_id,CONCAT('性能纪念日 ',n),DATE_ADD('2020-01-01',INTERVAL n MONTH),n
FROM fixture_numbers WHERE n<=3;

UPDATE space_storage_usage SET used_bytes=20000*68*3,updated_at=UTC_TIMESTAMP(6)
WHERE space_id=@fixture_space_id;

SELECT @fixture_space_id AS fixture_space_id,
       (SELECT COUNT(*) FROM diary WHERE space_id=@fixture_space_id AND deleted_at IS NULL) AS diaries,
       (SELECT COUNT(*) FROM media_asset WHERE space_id=@fixture_space_id AND deleted_at IS NULL) AS media_assets;
