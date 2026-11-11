-- Read only. Run in the IDE connection before enabling migration mode.
SELECT current_database(), current_user, inet_server_addr(), inet_server_port(), version();
SELECT nspname, pg_get_userbyid(nspowner) AS owner
FROM pg_namespace WHERE nspname IN ('replica', 'replica_meta');
SELECT schemaname, tablename FROM pg_tables WHERE tablename = 'flyway_schema_history';
SELECT rolname, rolcanlogin, rolsuper, rolbypassrls, rolcreatedb, rolcreaterole
FROM pg_roles WHERE rolname LIKE 'replica%';
SELECT member.rolname AS login, granted.rolname AS granted_role
FROM pg_auth_members m
JOIN pg_roles member ON member.oid=m.member
JOIN pg_roles granted ON granted.oid=m.roleid
WHERE member.rolname LIKE 'replica%';
-- If history exists, inspect its qualified table separately:
-- SELECT installed_rank, version, description, success
-- FROM replica_meta.flyway_schema_history ORDER BY installed_rank;
