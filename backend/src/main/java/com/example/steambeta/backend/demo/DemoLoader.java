package com.example.steambeta.backend.demo;

import java.sql.DriverManager;
import java.util.*;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class DemoLoader implements ApplicationRunner {
    private final Environment environment;
    private final PasswordEncoder encoder;
    public DemoLoader(Environment environment,PasswordEncoder encoder) { this.environment=environment; this.encoder=encoder; }
    @Override public void run(ApplicationArguments args) throws Exception {
        var passwords=new HashMap<String,String>();
        for(var name:List.of("alba","bruno","celia")) passwords.put(name,required("REPLICA_DEMO_"+name.toUpperCase(Locale.ROOT)+"_PASSWORD"));
        String owner=required("REPLICA_MIGRATION_USER");
        if(owner.equals(required("REPLICA_DB_USER"))) throw new IllegalStateException("Demo loading requires a separate owner identity");
        try(var connection=DriverManager.getConnection(required("REPLICA_DB_URL"),owner,required("REPLICA_MIGRATION_PASSWORD"))) {
            connection.setAutoCommit(false);
            try { new DemoDataset(connection).load(encoder,passwords); connection.commit(); }
            catch(Exception error) {
                connection.rollback();
                // Do not log SQL parameters, hashes, or passwords in exception chains.
                throw new IllegalStateException("Demo load cancelled; existing data preserved. Check dataset identifiers and local credentials.");
            }
        }
        System.out.println("Synthetic library demonstration loaded; no passwords printed.");
    }
    private String required(String key) {
        String value=environment.getProperty(key);
        if(value==null || value.isBlank()) throw new IllegalStateException("Missing local configuration: "+key);
        return value;
    }
}
