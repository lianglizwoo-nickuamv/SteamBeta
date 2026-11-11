package com.example.steambeta.backend.config;

import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Reject owner/admin runtime identities, including dangerous inherited memberships. */
@Component
public class DatabaseIdentityGuard implements ApplicationRunner {
    private final DataSource dataSource;
    public DatabaseIdentityGuard(DataSource dataSource) { this.dataSource = dataSource; }
    @Override public void run(ApplicationArguments args) throws Exception {
        try (Connection c = dataSource.getConnection(); var s = c.createStatement();
             var r = s.executeQuery("""
                 select pg_has_role(current_user, 'replica_app', 'MEMBER')
                    and not pg_has_role(current_user, 'replica_academic', 'MEMBER')
                    and not exists (
                        select 1 from pg_roles r where
                        pg_has_role(current_user, r.oid, 'MEMBER') and (r.rolsuper or r.rolbypassrls))
                    and not exists (
                        select 1 from pg_namespace n where n.nspname in ('replica', 'replica_meta')
                        and pg_has_role(current_user, n.nspowner, 'MEMBER'))
                    and not exists (
                        select 1 from pg_class t join pg_namespace n on n.oid=t.relnamespace
                        where n.nspname='replica' and pg_has_role(current_user, t.relowner, 'MEMBER'))
                    and not pg_has_role(current_user,
                        (select datdba from pg_database where datname=current_database()), 'MEMBER')
                 """)) {
            if (!r.next() || !r.getBoolean(1)) {
                throw new IllegalStateException("Unsafe runtime DB identity: require limited replica_app membership only");
            }
        }
    }
}
