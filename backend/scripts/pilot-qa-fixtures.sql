-- Local synthetic fixtures, run explicitly as migrator. Not a Flyway migration.
-- Additive and repeatable; never remove existing user content or memberships.
BEGIN;
DO $$ BEGIN
 IF (SELECT count(*) FROM replica.access_account WHERE login_identifier IN
 ('25010333','25010323','82921019','qa.pilot.outsider','qa.pilot.second.member') AND enabled) <> 5 THEN
 RAISE EXCEPTION 'Prepare all five enabled test accounts first'; END IF;
 IF EXISTS (SELECT 1 FROM replica.community_membership m JOIN replica.access_account a ON a.id=m.account_id
 WHERE a.login_identifier='qa.pilot.outsider' AND m.active) THEN
 RAISE EXCEPTION 'QA outsider already has memberships; inspect without deleting'; END IF;
END $$;
INSERT INTO replica.community(slug,name,description,rules) VALUES
 ('qa-pilot-a','[QA PILOTO] Comunidad A','Solo datos sinteticos para revision visual.','Solo cuentas de prueba; no publicar datos personales.'),
 ('qa-pilot-b','[QA PILOTO] Comunidad B','Prueba de aislamiento de moderacion.','Solo cuentas de prueba; no publicar datos personales.')
ON CONFLICT(slug) DO NOTHING;
INSERT INTO replica.community_membership(community_id,account_id,role)
 SELECT c.id,a.id,CASE WHEN c.slug='qa-pilot-a' AND a.login_identifier='82921019' THEN 'MODERATOR' ELSE 'MEMBER' END
 FROM replica.community c CROSS JOIN replica.access_account a
 WHERE (c.slug='qa-pilot-a' AND a.login_identifier IN ('25010333','25010323','82921019'))
 OR (c.slug='qa-pilot-b' AND a.login_identifier IN ('qa.pilot.second.member','82921019'))
ON CONFLICT(community_id,account_id) DO NOTHING;
-- Same moderator is a regular member in B, allowing a direct role-boundary check.
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM replica.community_membership m JOIN replica.community c ON c.id=m.community_id
 JOIN replica.access_account a ON a.id=m.account_id
 WHERE c.slug='qa-pilot-b' AND a.login_identifier='82921019' AND m.role<>'MEMBER') THEN
 RAISE EXCEPTION 'Unexpected role in B; inspect without downgrading existing roles'; END IF;
END $$;
INSERT INTO replica.community_post(community_id,author_account_id,request_key,title,body)
 SELECT c.id,a.id,('00000000-0000-4000-8000-' || lpad(i::text,12,'0'))::uuid,
 '[QA PILOTO] Pregunta ' || lpad(i::text,2,'0'), 'Texto sintetico para paginacion, comentarios y reportes. Caso ' || i
 FROM replica.community c CROSS JOIN replica.access_account a CROSS JOIN generate_series(1,12) i
 WHERE c.slug='qa-pilot-a' AND a.login_identifier='25010333'
ON CONFLICT ON CONSTRAINT community_post_request_once DO NOTHING;
INSERT INTO replica.community_post(community_id,author_account_id,request_key,title,body)
 SELECT c.id,a.id,'00000000-0000-4000-8000-000000000101'::uuid,
 '[QA PILOTO] Contenido B','Prueba de rechazo de moderacion desde la comunidad A.'
 FROM replica.community c CROSS JOIN replica.access_account a
 WHERE c.slug='qa-pilot-b' AND a.login_identifier='qa.pilot.second.member'
ON CONFLICT ON CONSTRAINT community_post_request_once DO NOTHING;
COMMIT;
